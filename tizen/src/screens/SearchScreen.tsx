// 검색 (Android T8·T10): 입력칸 + 시리즈·책 결과 두 줄. 입력이 멈추고 400ms 뒤 Komga 전문 검색.
// 줄마다 50개씩, 끝에서 5번째 카드에 포커스가 오면 다음 쪽을 이어 불러온다.
// 포커스: 들어오면 입력칸, 아래 = 첫 결과(시리즈가 없으면 책 줄), 연 카드에서 돌아오면 그 카드.
import { useEffect, useRef, useState } from 'preact/hooks';
import { BookSort, type KomgaBook, type KomgaSeries, type Page, SeriesSort } from '../api/types';
import { fmt, t } from '../i18n';
import type { RouteEntry } from '../platform/router';
import { type SearchState, SearchController } from '../state/searchController';
import { requireSession, toLoadError } from '../state/session';
import { BookCard, SeriesCard } from '../ui/cards';
import { ErrorView, Row, TextField } from '../ui/components';
import { useEntryFocus } from '../ui/entryFocus';

const RESULT_SIZE = 50;
/** 줄 끝에서 이만큼 안쪽 카드에 포커스가 오면 다음 쪽 (Android T10) */
const PREFETCH_DISTANCE = 5;

interface SearchResult {
  series: Page<KomgaSeries>;
  books: Page<KomgaBook>;
}

const fetchSeries = (query: string, page: number) =>
  requireSession().client.getSeriesList({ fullTextSearch: query }, { page, size: RESULT_SIZE, sort: SeriesSort.titleAsc });
const fetchBooks = (query: string, page: number) =>
  requireSession().client.getBookList({ fullTextSearch: query }, { page, size: RESULT_SIZE, sort: BookSort.numberAsc });

async function searchKomga(query: string): Promise<SearchResult> {
  const [series, books] = await Promise.all([fetchSeries(query, 0), fetchBooks(query, 0)]);
  return { series, books };
}

/** 한 줄의 이어 불러온 결과 */
interface RowPages<T> {
  items: T[];
  nextPage: number;
  last: boolean;
  total: number;
}

const fromPage = <T,>(page: Page<T>): RowPages<T> => ({
  items: page.content,
  nextPage: page.number + 1,
  last: page.last,
  total: page.totalElements,
});

const appendPage = <T,>(row: RowPages<T>, page: Page<T>): RowPages<T> => ({
  items: row.items.concat(page.content),
  nextPage: page.number + 1,
  last: page.last,
  total: page.totalElements,
});

export function SearchScreen({ entry, active }: { entry: RouteEntry; active: boolean }) {
  const root = useRef<HTMLDivElement>(null);
  const [text, setText] = useState('');
  const [state, setState] = useState<SearchState<SearchResult>>({ kind: 'idle' });
  const [seriesRow, setSeriesRow] = useState<RowPages<KomgaSeries> | null>(null);
  const [booksRow, setBooksRow] = useState<RowPages<KomgaBook> | null>(null);
  const loading = useRef({ series: false, books: false });
  const controller = useRef<SearchController<SearchResult> | null>(null);
  if (!controller.current) controller.current = new SearchController(searchKomga, setState);

  useEffect(() => () => controller.current?.dispose(), []);

  // 새 검색 결과가 오면 줄을 처음부터
  useEffect(() => {
    if (state.kind !== 'done') return;
    setSeriesRow(fromPage(state.result.series));
    setBooksRow(fromPage(state.result.books));
  }, [state]);

  const query = state.kind === 'done' ? state.query : '';

  // 끝 근처 카드에 포커스가 오면 그 줄의 다음 쪽
  useEffect(() => {
    const element = root.current;
    if (!element) return undefined;
    const onFocusIn = (event: FocusEvent) => {
      const target = event.target as HTMLElement;
      const index = Number(target.dataset.index);
      const section = target.closest<HTMLElement>('[data-section]')?.dataset.section;
      if (Number.isNaN(index) || !query) return;
      if (section === 'search-series' && seriesRow && !seriesRow.last && !loading.current.series) {
        if (index < seriesRow.items.length - PREFETCH_DISTANCE) return;
        loading.current.series = true;
        fetchSeries(query, seriesRow.nextPage)
          .then((page) => setSeriesRow((row) => (row ? appendPage(row, page) : row)), () => undefined)
          .then(() => (loading.current.series = false));
      } else if (section === 'search-books' && booksRow && !booksRow.last && !loading.current.books) {
        if (index < booksRow.items.length - PREFETCH_DISTANCE) return;
        loading.current.books = true;
        fetchBooks(query, booksRow.nextPage)
          .then((page) => setBooksRow((row) => (row ? appendPage(row, page) : row)), () => undefined)
          .then(() => (loading.current.books = false));
      }
    };
    element.addEventListener('focusin', onFocusIn);
    return () => element.removeEventListener('focusin', onFocusIn);
  }, [query, seriesRow, booksRow]);

  const onInput = (value: string) => {
    setText(value);
    controller.current?.input(value);
  };

  useEntryFocus(root, entry, active, state.kind === 'done' ? state.query : 0, 'query');

  let body;
  switch (state.kind) {
    case 'idle':
      body = <div class="search-message">{t.searchHint}</div>;
      break;
    case 'loading':
      body = <div class="search-message">{t.searchLoading}</div>;
      break;
    case 'error':
      body = <ErrorView error={toLoadError(state.error)} onRetry={() => controller.current?.retry()} />;
      break;
    case 'done': {
      const series = seriesRow ?? fromPage(state.result.series);
      const books = booksRow ?? fromPage(state.result.books);
      body =
        series.items.length + books.items.length === 0 ? (
          <div class="search-message">{fmt(t.searchEmpty, state.query)}</div>
        ) : (
          <>
            {series.items.length > 0 && (
              <Row section="search-series" title={fmt(t.searchSeriesTitle, series.total)}>
                {series.items.map((item, i) => (
                  <SeriesCard key={item.id} series={item} row="search" index={i} />
                ))}
              </Row>
            )}
            {books.items.length > 0 && (
              <Row section="search-books" title={fmt(t.searchBooksTitle, books.total)}>
                {books.items.map((item, i) => (
                  <BookCard key={item.id} book={item} row="search" index={i} />
                ))}
              </Row>
            )}
          </>
        );
      break;
    }
  }

  return (
    <div ref={root} class="search" data-scroll="y" data-scroll-margin="60">
      <header class="top-bar">
        <div class="screen-title">{t.searchTitle}</div>
      </header>
      <div class="search-field">
        <TextField focusKey="query" label={t.searchTitle} value={text} onInput={onInput} placeholder={t.searchPlaceholder} />
      </div>
      {body}
    </div>
  );
}
