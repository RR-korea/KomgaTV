// 인증 이미지를 object URL로 보관하는 캐시 (PLAN Z3).
// 화면에 붙어 있는(참조 중인) 항목은 지우지 않고, 참조가 없는 항목만 오래된 순으로 해제한다.
// Android "이미지 대기열 분리·중복 제거·미리 받기 취소"와 같은 동작:
// - 같은 이미지 동시 요청은 한 번만 (pending)
// - 동시에 받는 수를 줄이고(maxConcurrent) 화면에 보일 그림을 미리 받기보다 먼저 — 진행 저장·목록 요청이 그림 뒤에서 기다리지 않게
// - 이웃이 아니게 된 미리 받기는 취소 (retainPrefetches)

export type BlobLoader = (key: string, signal: AbortSignal) => Promise<Blob>;

interface Entry {
  url: string;
  refs: number;
}

interface Job {
  key: string;
  controller: AbortController;
  /** 화면에 보일 그림이면 true (미리 받기보다 먼저) */
  wanted: boolean;
  start: () => void;
}

export class ImageCache {
  private readonly entries = new Map<string, Entry>(); // 삽입 순서 = 오래된 순
  private readonly pending = new Map<string, { promise: Promise<string>; job: Job }>();
  private readonly queue: Job[] = [];
  private running = 0;

  constructor(
    private readonly load: BlobLoader,
    /** 참조 없는 항목을 몇 개까지 남길지 */
    private readonly maxIdle: number,
    private readonly createUrl: (blob: Blob) => string = (blob) => URL.createObjectURL(blob),
    private readonly revokeUrl: (url: string) => void = (url) => URL.revokeObjectURL(url),
    /** 동시에 받는 수 */
    private readonly maxConcurrent = 4,
  ) {}

  /** 이미지를 받아 object URL을 돌려주고 참조를 하나 늘린다. 다 쓰면 release(key). */
  async acquire(key: string): Promise<string> {
    const hit = this.entries.get(key);
    if (hit) {
      hit.refs++;
      this.touch(key, hit);
      return hit.url;
    }
    const url = await this.fetch(key, true);
    const entry = this.entries.get(key);
    if (entry) entry.refs++;
    return url;
  }

  /** 받아 둔다 (참조는 늘리지 않음). 리더 이웃 쪽 미리 받기용. */
  prefetch(key: string): void {
    if (this.entries.has(key) || this.pending.has(key)) return;
    this.fetch(key, false).then(
      () => this.evict(),
      () => undefined,
    );
  }

  /** 이 목록에 없는, 아직 받는 중인 미리 받기는 취소한다 (화면에 보일 그림은 그대로) */
  retainPrefetches(keys: Iterable<string>): void {
    const keep = new Set(keys);
    this.pending.forEach(({ job }, key) => {
      if (!job.wanted && !keep.has(key)) job.controller.abort();
    });
  }

  release(key: string): void {
    const entry = this.entries.get(key);
    if (!entry || entry.refs === 0) return;
    entry.refs--;
    if (entry.refs === 0) this.touch(key, entry);
    this.evict();
  }

  /** 서버 변경·로그아웃 때 전부 해제 */
  clear(): void {
    this.entries.forEach((entry) => this.revokeUrl(entry.url));
    this.entries.clear();
    this.pending.forEach(({ job }) => job.controller.abort());
    this.pending.clear();
    this.queue.length = 0;
  }

  get size(): number {
    return this.entries.size;
  }

  private fetch(key: string, wanted: boolean): Promise<string> {
    const existing = this.pending.get(key);
    if (existing) {
      if (wanted && !existing.job.wanted) {
        // 미리 받던 그림이 화면에 보이게 됐다 — 대기열 앞으로, 취소 대상에서 빠진다
        existing.job.wanted = true;
        this.sortQueue();
      }
      return existing.promise;
    }
    const controller = new AbortController();
    let job!: Job;
    const promise = new Promise<string>((resolve, reject) => {
      job = {
        key,
        controller,
        wanted,
        start: () => {
          this.running++;
          this.load(key, controller.signal)
            .then(
              (blob) => {
                if (this.pending.get(key)?.job !== job) return reject(abortError());
                this.pending.delete(key);
                const url = this.createUrl(blob);
                this.entries.set(key, { url, refs: 0 });
                resolve(url);
              },
              (error: unknown) => {
                if (this.pending.get(key)?.job === job) this.pending.delete(key);
                reject(error);
              },
            )
            .then(() => {
              this.running--;
              this.pump();
            });
        },
      };
      controller.signal.addEventListener('abort', () => {
        const at = this.queue.indexOf(job);
        if (at >= 0) {
          // 아직 시작 전이면 대기열에서 뺀다
          this.queue.splice(at, 1);
          if (this.pending.get(key)?.job === job) this.pending.delete(key);
          reject(abortError());
        }
      });
    });
    this.pending.set(key, { promise, job });
    this.queue.push(job);
    this.sortQueue();
    this.pump();
    return promise;
  }

  private sortQueue(): void {
    // 화면에 보일 그림 먼저, 같은 무리 안에서는 들어온 순서 (안정 정렬)
    const wanted = this.queue.filter((job) => job.wanted);
    const rest = this.queue.filter((job) => !job.wanted);
    this.queue.length = 0;
    this.queue.push(...wanted, ...rest);
  }

  private pump(): void {
    while (this.running < this.maxConcurrent && this.queue.length > 0) {
      const job = this.queue.shift();
      if (job && !job.controller.signal.aborted) job.start();
    }
  }

  private touch(key: string, entry: Entry): void {
    this.entries.delete(key);
    this.entries.set(key, entry);
  }

  private evict(): void {
    let idle = 0;
    this.entries.forEach((entry) => {
      if (entry.refs === 0) idle++;
    });
    if (idle <= this.maxIdle) return;
    for (const [key, entry] of Array.from(this.entries)) {
      if (idle <= this.maxIdle) break;
      if (entry.refs > 0) continue;
      this.entries.delete(key);
      this.revokeUrl(entry.url);
      idle--;
    }
  }
}

function abortError(): Error {
  const error = new Error('aborted');
  error.name = 'AbortError';
  return error;
}
