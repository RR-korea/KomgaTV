// 화질 개선 Lanczos2/3/4 (Android 1.0.1): 받은 쪽을 화면에 그릴 크기로 CPU 리샘플링해 1:1로 그린다.
// - 계산은 Web Worker 여러 개(코어 수, 최대 4)에 출력 줄을 나눠 맡긴다. Worker마다 그 줄에 필요한 원본 줄만 넘긴다.
// - 한 번에 한 쪽씩(화면에 보일 쪽 먼저, 미리 처리는 뒤로). 결과는 ImageBitmap으로 몇 장 남겨 다시 그릴 때 바로 쓴다.
// - Worker를 못 쓰는 환경이면 메인 스레드에서 한다.
import { kernel, lanczosResize, sourceRows } from './lanczos';

declare const LANCZOS_WORKER_SOURCE: string;

/** 처리한 결과를 몇 장 남길지 (1080p 두 쪽 ≈ 장당 8~12MB) */
const MAX_CACHED = 8;
/** 크기가 이만큼 안쪽으로 같으면 그대로 그린다 */
const SAME_SIZE_PX = 1;

interface Part {
  onDone: (y0: number, out: Uint8ClampedArray) => void;
  onError: (error: unknown) => void;
}

let workers: Worker[] | null = null;
let workersFailed = false;
let nextPartId = 0;
const parts = new Map<number, Part>();

function failWorkers(error: unknown): void {
  workersFailed = true;
  document.documentElement.setAttribute('data-lanczos', 'main');
  parts.forEach((part) => part.onError(error));
  parts.clear();
  workers?.forEach((worker) => worker.terminate());
  workers = null;
}

function getWorkers(): Worker[] | null {
  if (workers || workersFailed) return workers;
  try {
    if (typeof Worker === 'undefined' || typeof LANCZOS_WORKER_SOURCE === 'undefined') throw new Error('no worker');
    const url = URL.createObjectURL(new Blob([LANCZOS_WORKER_SOURCE], { type: 'text/javascript' }));
    const count = Math.max(1, Math.min(4, navigator.hardwareConcurrency || 2));
    const created: Worker[] = [];
    for (let i = 0; i < count; i++) {
      const worker = new Worker(url);
      worker.onmessage = (event: MessageEvent<{ id: number; y0: number; out: Uint8ClampedArray }>) => {
        const part = parts.get(event.data.id);
        if (!part) return;
        parts.delete(event.data.id);
        part.onDone(event.data.y0, event.data.out);
      };
      worker.onerror = (event) => failWorkers(event);
      created.push(worker);
    }
    workers = created;
  } catch {
    workersFailed = true;
    workers = null;
  }
  // 시험·진단용: 어느 쪽으로 계산하는지 (smoke·에뮬레이터에서 확인)
  document.documentElement.setAttribute('data-lanczos', workers ? `workers-${workers.length}` : 'main');
  console.info(`[TV Reader] lanczos: ${workers ? `workers-${workers.length}` : 'main thread'}`);
  return workers;
}

/** RGBA 픽셀을 Lanczos(a)로 다시 맞춘다 — Worker들이 출력 줄을 나눠 계산 */
function resample(src: ImageData, newWidth: number, newHeight: number, a: number): Promise<Uint8ClampedArray> {
  const pool = getWorkers();
  if (!pool) return Promise.resolve(lanczosResize(src.data, src.width, src.height, newWidth, newHeight, a));
  const ky = kernel(src.height, newHeight, a);
  const count = Math.min(pool.length, newHeight);
  const rowBytes = src.width * 4;
  return new Promise((resolve, reject) => {
    const out = new Uint8ClampedArray(newWidth * newHeight * 4);
    let left = count;
    for (let i = 0; i < count; i++) {
      const y0 = Math.floor((newHeight * i) / count);
      const y1 = Math.floor((newHeight * (i + 1)) / count);
      const { from, to } = sourceRows(ky, y0, y1);
      const pixels = src.data.slice(from * rowBytes, (to + 1) * rowBytes);
      const id = ++nextPartId;
      parts.set(id, {
        onDone: (start, rows) => {
          out.set(rows, start * newWidth * 4);
          if (--left === 0) resolve(out);
        },
        onError: reject,
      });
      pool[i].postMessage(
        {
          id,
          pixels,
          width: src.width,
          height: src.height,
          newWidth,
          newHeight,
          a,
          y0,
          y1,
          firstRow: from,
        },
        [pixels.buffer],
      );
    }
  });
}

async function decode(url: string): Promise<ImageBitmap> {
  const blob = await (await fetch(url)).blob();
  return createImageBitmap(blob);
}

async function process(url: string, width: number, height: number, a: number): Promise<ImageBitmap> {
  const started = Date.now();
  const source = await decode(url);
  if (Math.abs(source.width - width) <= SAME_SIZE_PX && Math.abs(source.height - height) <= SAME_SIZE_PX) return source;
  const canvas = document.createElement('canvas');
  canvas.width = source.width;
  canvas.height = source.height;
  const context = canvas.getContext('2d');
  if (!context) return source;
  context.drawImage(source, 0, 0);
  const pixels = context.getImageData(0, 0, source.width, source.height);
  source.close();
  canvas.width = 0; // 메모리 바로 놓기
  const out = await resample(pixels, width, height, a);
  const bitmap = await createImageBitmap(new ImageData(out as Uint8ClampedArray<ArrayBuffer>, width, height));
  // 진단용: TV에서 쪽마다 얼마나 걸리는지
  console.info(`[TV Reader] lanczos${a} ${pixels.width}x${pixels.height} → ${width}x${height}: ${Date.now() - started}ms`);
  return bitmap;
}

// ---------- 결과 캐시 + 한 장씩 처리하는 대기열 ----------

const cache = new Map<string, ImageBitmap>(); // 삽입 순서 = 오래된 순
const inflight = new Map<string, Promise<ImageBitmap>>();
interface Job {
  key: string;
  wanted: boolean;
  start: () => void;
}
const queue: Job[] = [];
let running = false;

function pump(): void {
  if (running || !queue.length) return;
  // 화면에 보일 쪽 먼저
  queue.sort((x, y) => Number(y.wanted) - Number(x.wanted));
  const job = queue.shift();
  if (!job) return;
  running = true;
  job.start();
}

function remember(key: string, bitmap: ImageBitmap): void {
  cache.delete(key);
  cache.set(key, bitmap);
  while (cache.size > MAX_CACHED) {
    const oldest = cache.keys().next().value as string;
    cache.get(oldest)?.close();
    cache.delete(oldest);
  }
}

export function lanczosKey(pageKey: string, width: number, height: number, a: number): string {
  return `${pageKey}|${width}x${height}|${a}`;
}

/** 이미 처리해 둔 결과 (없으면 null) */
export function cachedLanczos(key: string): ImageBitmap | null {
  const hit = cache.get(key);
  if (!hit) return null;
  cache.delete(key);
  cache.set(key, hit);
  return hit;
}

/**
 * 쪽(object URL)을 width×height로 Lanczos(a) 처리한 ImageBitmap. 같은 요청은 한 번만.
 * wanted = 지금 화면에 보일 쪽 (미리 처리보다 먼저).
 */
export function lanczosBitmap(
  key: string,
  url: string,
  width: number,
  height: number,
  a: number,
  wanted: boolean,
): Promise<ImageBitmap> {
  const hit = cachedLanczos(key);
  if (hit) return Promise.resolve(hit);
  const existing = inflight.get(key);
  if (existing) {
    if (wanted) queue.forEach((job) => job.key === key && (job.wanted = true));
    return existing;
  }
  const promise = new Promise<ImageBitmap>((resolve, reject) => {
    queue.push({
      key,
      wanted,
      start: () => {
        process(url, Math.max(1, Math.round(width)), Math.max(1, Math.round(height)), a)
          .then(
            (bitmap) => {
              remember(key, bitmap);
              resolve(bitmap);
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
export function clearLanczos(): void {
  cache.forEach((bitmap) => bitmap.close());
  cache.clear();
}
