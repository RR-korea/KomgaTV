// 인증 이미지를 object URL로 보관하는 캐시 (PLAN Z3).
// 화면에 붙어 있는(참조 중인) 항목은 지우지 않고, 참조가 없는 항목만 오래된 순으로 해제한다.

export type BlobLoader = (key: string) => Promise<Blob>;

interface Entry {
  url: string;
  refs: number;
}

export class ImageCache {
  private readonly entries = new Map<string, Entry>(); // 삽입 순서 = 오래된 순
  private readonly pending = new Map<string, Promise<string>>();

  constructor(
    private readonly load: BlobLoader,
    /** 참조 없는 항목을 몇 개까지 남길지 */
    private readonly maxIdle: number,
    private readonly createUrl: (blob: Blob) => string = (blob) => URL.createObjectURL(blob),
    private readonly revokeUrl: (url: string) => void = (url) => URL.revokeObjectURL(url),
  ) {}

  /** 이미지를 받아 object URL을 돌려주고 참조를 하나 늘린다. 다 쓰면 release(key). */
  async acquire(key: string): Promise<string> {
    const hit = this.entries.get(key);
    if (hit) {
      hit.refs++;
      this.touch(key, hit);
      return hit.url;
    }
    let promise = this.pending.get(key);
    if (!promise) {
      promise = this.load(key).then(
        (blob) => {
          this.pending.delete(key);
          const url = this.createUrl(blob);
          this.entries.set(key, { url, refs: 0 });
          return url;
        },
        (error: unknown) => {
          this.pending.delete(key);
          throw error;
        },
      );
      this.pending.set(key, promise);
    }
    const url = await promise;
    const entry = this.entries.get(key);
    if (entry) entry.refs++;
    return url;
  }

  /** 받아 둔 것만 미리 불러 둔다 (참조는 늘리지 않음). 리더 ±2 프리로드용. */
  prefetch(key: string): void {
    if (this.entries.has(key) || this.pending.has(key)) return;
    this.acquire(key).then(
      () => this.release(key),
      () => undefined,
    );
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
    this.pending.clear();
  }

  get size(): number {
    return this.entries.size;
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
