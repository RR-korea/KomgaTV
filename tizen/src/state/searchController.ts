// 검색 입력 → 디바운스 400ms → 검색 (Android T8과 같은 규칙).
// - 입력이 멈추고 400ms 뒤에 찾는다. 새 입력이 오면 기다리던 검색은 버린다
// - 앞뒤 공백만 다르면 다시 찾지 않는다, 빈 입력이면 처음 상태
// - 늦게 도착한 이전 검색 결과는 버린다 (순번)

export const SEARCH_DEBOUNCE_MS = 400;

export type SearchState<T> =
  | { kind: 'idle' }
  | { kind: 'loading'; query: string }
  | { kind: 'done'; query: string; result: T }
  | { kind: 'error'; query: string; error: unknown };

export class SearchController<T> {
  private timer: ReturnType<typeof setTimeout> | null = null;
  private lastQuery = '';
  private sequence = 0;

  constructor(
    private readonly search: (query: string) => Promise<T>,
    private readonly onState: (state: SearchState<T>) => void,
    private readonly delayMs = SEARCH_DEBOUNCE_MS,
  ) {}

  input(text: string): void {
    const query = text.trim();
    if (query === this.lastQuery) return;
    this.lastQuery = query;
    this.cancelTimer();
    const sequence = ++this.sequence;
    if (!query) {
      this.onState({ kind: 'idle' });
      return;
    }
    this.timer = setTimeout(() => {
      this.timer = null;
      this.onState({ kind: 'loading', query });
      this.search(query).then(
        (result) => {
          if (sequence === this.sequence) this.onState({ kind: 'done', query, result });
        },
        (error: unknown) => {
          if (sequence === this.sequence) this.onState({ kind: 'error', query, error });
        },
      );
    }, this.delayMs);
  }

  /** 오류 뒤 다시 시도 */
  retry(): void {
    const query = this.lastQuery;
    this.lastQuery = '';
    this.input(query);
  }

  dispose(): void {
    this.cancelTimer();
    this.sequence++;
  }

  private cancelTimer(): void {
    if (this.timer !== null) clearTimeout(this.timer);
    this.timer = null;
  }
}
