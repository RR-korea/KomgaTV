// 쪽 그림 처리 (Android 1.0.12~1.0.45: 여백 잘라내기 → 칸 간격 없애기 → 빈 줄 자르기) 를 Worker 하나에 맡긴다.
// - 받은 쪽(object URL)을 화면에서 차지할 칸 크기로 줄인 뒤(Android 의 디코딩 크기와 같음) 처리한다.
// - 한 번에 한 쪽씩 (Android pageWorkLock — 큰 배열이 겹치면 TV 메모리가 모자란다), 화면에 보일 쪽 먼저.
// - 결과는 ImageBitmap 으로 몇 장 남겨 다시 그릴 때·Lanczos 가 바로 쓴다.
// - Worker를 못 쓰는 환경이면 화면 스레드에서 한다.
import {
  argbToRgba,
  type PageWorkOptions,
  processPage,
  rgbaToArgb,
  workSize,
} from './pageWork';

declare const PAGE_WORKER_SOURCE: string;

/** 처리한 결과를 몇 장 남길지 (지금 펼침 + 앞뒤 펼침들, 장당 4MB 안팎) */
const MAX_CACHED = 12;

export interface PageWorkResult {
  bitmap: ImageBitmap;
  width: number;
  height: number;
}

interface Reply {
  id: number;
  rgba: Uint8ClampedArray;
  width: number;
  height: number;
}

let worker: Worker | null = null;
let workerFailed = false;
let nextId = 0;
const waiting = new Map<number, { resolve: (reply: Reply) => void; reject: (error: unknown) => void }>();

function failWorker(error: unknown): void {
  workerFailed = true;
  document.documentElement.setAttribute('data-pagework', 'main');
  waiting.forEach((job) => job.reject(error));
  waiting.clear();
  worker?.terminate();
  worker = null;
}

function getWorker(): Worker | null {
  if (worker || workerFailed) return worker;
  try {
    if (typeof Worker === 'undefined' || typeof PAGE_WORKER_SOURCE === 'undefined') throw new Error('no worker');
    const url = URL.createObjectURL(new Blob([PAGE_WORKER_SOURCE], { type: 'text/javascript' }));
    const created = new Worker(url);
    created.onmessage = (event: MessageEvent<Reply>) => {
      const job = waiting.get(event.data.id);
      if (!job) return;
      waiting.delete(event.data.id);
      job.resolve(event.data);
    };
    created.onerror = (event) => failWorker(event);
    worker = created;
  } catch {
    workerFailed = true;
    worker = null;
  }
  // 시험·진단용: 어디서 계산하는지 (smoke·에뮬레이터에서 확인)
  document.documentElement.setAttribute('data-pagework', worker ? 'worker' : 'main');
  return worker;
}

function compute(rgba: Uint8ClampedArray, width: number, height: number, options: PageWorkOptions): Promise<Reply> {
  const active = getWorker();
  if (!active) {
    const done = processPage({ pixels: rgbaToArgb(rgba), width, height }, options.crop, options.removeGutters);
    return Promise.resolve({ id: 0, rgba: argbToRgba(done.pixels), width: done.width, height: done.height });
  }
  return new Promise((resolve, reject) => {
    const id = ++nextId;
    waiting.set(id, { resolve, reject });
    active.postMessage({ id, rgba, width, height, crop: options.crop, removeGutters: options.removeGutters }, [rgba.buffer]);
  });
}

async function process(url: string, options: PageWorkOptions): Promise<PageWorkResult> {
  const started = Date.now();
  const blob = await (await fetch(url)).blob();
  const source = await createImageBitmap(blob);
  const size = workSize(source.width, source.height, options.boxWidth, options.boxHeight);
  const canvas = document.createElement('canvas');
  canvas.width = size.width;
  canvas.height = size.height;
  const context = canvas.getContext('2d');
  if (!context) return { bitmap: source, width: source.width, height: source.height };
  context.imageSmoothingEnabled = true;
  context.imageSmoothingQuality = 'high';
  context.drawImage(source, 0, 0, size.width, size.height);
  source.close();
  const pixels = context.getImageData(0, 0, size.width, size.height);
  canvas.width = 0; // 메모리 바로 놓기
  const reply = await compute(pixels.data, size.width, size.height, options);
  const bitmap = await createImageBitmap(
    new ImageData(reply.rgba as Uint8ClampedArray<ArrayBuffer>, reply.width, reply.height),
  );
  // 진단용: TV에서 쪽마다 얼마나 걸리는지
  console.info(
    `[TV Reader] pagework ${options.crop}${options.removeGutters ? '+gutters' : ''} ${size.width}x${size.height} → ` +
      `${reply.width}x${reply.height}: ${Date.now() - started}ms`,
  );
  return { bitmap, width: reply.width, height: reply.height };
}

// ---------- 결과 캐시 + 한 장씩 처리하는 대기열 ----------

const cache = new Map<string, PageWorkResult>(); // 삽입 순서 = 오래된 순
const inflight = new Map<string, Promise<PageWorkResult>>();
interface Job {
  key: string;
  wanted: boolean;
  start: () => void;
}
const queue: Job[] = [];
let running = false;

function pump(): void {
  if (running || !queue.length) return;
  // 화면에 보일 쪽 먼저 (같은 급이면 먼저 넣은 것 — Chrome 69 의 sort 는 안정 정렬이 아니라 직접 고른다)
  let pick = 0;
  for (let i = 1; i < queue.length; i++) if (queue[i].wanted && !queue[pick].wanted) pick = i;
  const job = queue.splice(pick, 1)[0];
  running = true;
  job.start();
}

function remember(key: string, result: PageWorkResult): void {
  cache.delete(key);
  cache.set(key, result);
  while (cache.size > MAX_CACHED) {
    const oldest = cache.keys().next().value as string;
    cache.get(oldest)?.bitmap.close();
    cache.delete(oldest);
  }
}

export function pageWorkKey(pageKey: string, options: PageWorkOptions): string {
  const gutters = options.removeGutters ? 'g' : 'n';
  return [pageKey, options.crop, gutters, `${options.boxWidth}x${options.boxHeight}`].join('|');
}

/** 이미 처리해 둔 결과 (없으면 null) */
export function cachedPageWork(key: string): PageWorkResult | null {
  const hit = cache.get(key);
  if (!hit) return null;
  cache.delete(key);
  cache.set(key, hit);
  return hit;
}

/**
 * 쪽(object URL)을 설정대로 처리한 결과. 같은 요청은 한 번만.
 * wanted = 지금 화면에 보일 쪽 (미리 처리보다 먼저).
 */
export function pageWork(key: string, url: string, options: PageWorkOptions, wanted: boolean): Promise<PageWorkResult> {
  const hit = cachedPageWork(key);
  if (hit) return Promise.resolve(hit);
  const existing = inflight.get(key);
  if (existing) {
    if (wanted) queue.forEach((job) => job.key === key && (job.wanted = true));
    return existing;
  }
  const promise = new Promise<PageWorkResult>((resolve, reject) => {
    queue.push({
      key,
      wanted,
      start: () => {
        process(url, options)
          .then(
            (result) => {
              remember(key, result);
              resolve(result);
            },
            reject,
          )
          .then(() => {
            inflight.delete(key);
            running = false;
            pump();
          });
      },
    });
    pump();
  });
  inflight.set(key, promise);
  return promise;
}

/** 서버 변경·로그아웃 때 */
export function clearPageWork(): void {
  cache.forEach((result) => result.bitmap.close());
  cache.clear();
}
