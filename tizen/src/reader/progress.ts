// 읽기 진행 저장: 쪽을 넘기면 2초 뒤 저장(디바운스), 나갈 때는 바로 (Android T4·T5와 같음).

export type SaveFn = (page: number, completed: boolean) => Promise<void>;

export const SAVE_DEBOUNCE_MS = 2000;

export class ProgressSaver {
  private timer: ReturnType<typeof setTimeout> | null = null;
  private pending: { page: number; completed: boolean } | null = null;
  private saved: { page: number; completed: boolean } | null;

  constructor(
    private readonly save: SaveFn,
    initial: { page: number; completed: boolean } | null,
    private readonly delayMs = SAVE_DEBOUNCE_MS,
  ) {
    this.saved = initial;
  }

  /** 사용자가 쪽을 옮겼을 때 */
  schedule(page: number, completed: boolean): void {
    this.pending = { page, completed };
    this.cancelTimer();
    this.timer = setTimeout(() => void this.flush(), this.delayMs);
  }

  /** 기다리는 저장을 바로 보낸다 (나갈 때). 바뀐 게 없으면 아무것도 안 한다. */
  flush(): Promise<void> {
    this.cancelTimer();
    const next = this.pending;
    this.pending = null;
    if (!next) return Promise.resolve();
    if (this.saved && this.saved.page === next.page && this.saved.completed === next.completed) return Promise.resolve();
    return this.save(next.page, next.completed).then(
      () => {
        this.saved = next;
      },
      () => undefined, // 실패는 조용히 — 다음에 쪽을 넘기면 다시 저장한다
    );
  }

  private cancelTimer(): void {
    if (this.timer !== null) clearTimeout(this.timer);
    this.timer = null;
  }
}
