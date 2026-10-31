// 검색 (Android T8): 입력칸 + 시리즈·책 결과 두 줄. 입력이 멈추고 400ms 뒤 Komga 전문 검색.
// 포커스: 들어오면 입력칸, 아래 = 첫 결과, 연 카드에서 돌아오면 그 카드.
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

interface SearchResult {
  series: Page<KomgaSeries>;
  books: Page<KomgaBook>;
}

async function searchKomga(query: string): Promise<SearchResult> {
  const { client } = requireSession();
  const page = { page: 0, size: RESULT_SIZE };
  const [series, books] = await Promise.all([
    client.getSeriesList({ fullTextSearch: query }, { ...page, sort: SeriesSort.titleAsc }),
    client.getBookList({ fullTextSearch: query }, { ...page, sort: BookSort.numberAsc }),
  ]);
  return { series, books };
}

export function SearchScreen({ entry, active }: { entry: RouteEntry; active: boolean }) {
  const root = useRef<HTMLDivElement>(null);
  const [text, setText] = useState('');
  const [state, setState] = useState<SearchState<SearchResult>>({ kind: 'idle' });
  const controller = useRef<SearchController<SearchResult> | null>(null);
  if (!controller.current) controller.current = new SearchController(searchKomga, setState);

  useEffect(() => () => controller.current?.dispose(), []);

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
      const { series, books } = state.result;
      body =
        series.content.length + books.content.length === 0 ? (
          <div class="search-message">{fmt(t.searchEmpty, state.query)}</div>
        ) : (
          <>
            {series.content.length > 0 && (
              <Row section="search-series" title={fmt(t.searchSeriesTitle, series.totalElements)}>
                {series.content.map((item) => (
                  <SeriesCard key={item.id} series={item} row="search" />
                ))}
              </Row>
            )}
            {books.content.length > 0 && (
              <Row section="search-books" title={fmt(t.searchBooksTitle, books.totalElements)}>
                {books.content.map((item) => (
                  <BookCard key={item.id} book={item} row="search" />
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
