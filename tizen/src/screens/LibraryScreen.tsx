// 라이브러리 → 시리즈 그리드, 50개씩 이어 불러오기 (Android T3).
import { useEffect, useRef, useState } from 'preact/hooks';
import { libraryIs } from '../api/conditions';
import { type KomgaSeries, SeriesSort } from '../api/types';
import { ImagePaths } from '../api/url';
import { fmt, t } from '../i18n';
import { push, type RouteEntry } from '../platform/router';
import { type LoadError, requireSession, toLoadError } from '../state/session';
import { ErrorView, LoadingView, PosterCard } from '../ui/components';
import { useEntryFocus } from '../ui/entryFocus';

const PAGE_SIZE = 50;
/** 포커스가 끝에서 이만큼 안쪽에 오면 다음 쪽을 부른다 (2줄) */
const PREFETCH_DISTANCE = 12;

interface GridState {
  items: KomgaSeries[];
  nextPage: number;
  last: boolean;
}

export function LibraryScreen({ entry, active }: { entry: RouteEntry; active: boolean }) {
  const route = entry.route as Extract<RouteEntry['route'], { name: 'library' }>;
  const root = useRef<HTMLDivElement>(null);
  const grid = useRef<HTMLDivElement>(null);
  const [state, setState] = useState<GridState | null>(null);
  const [error, setError] = useState<LoadError | null>(null);
  const loading = useRef(false);

  const loadMore = (from: GridState | null) => {
    if (loading.current || from?.last) return;
    const { client } = requireSession();
    loading.current = true;
    const page = from?.nextPage ?? 0;
    client
      .getSeriesList({ condition: libraryIs(route.libraryId) }, { page, size: PAGE_SIZE, sort: SeriesSort.titleAsc })
      .then(
        (result) => {
          setState((previous) => ({
            items: [...(previous?.items ?? []), ...result.content],
            nextPage: page + 1,
            last: result.last,
          }));
          setError(null);
        },
        (failure: unknown) => {
          if (!from) setError(toLoadError(failure));
        },
      )
      .then(() => {
        loading.current = false;
      });
  };

  useEffect(() => {
    try {
      loadMore(null);
    } catch (failure) {
      setError(toLoadError(failure));
    }
  }, []);

  // 포커스가 끝 근처에 오면 더 불러온다
  useEffect(() => {
    const element = grid.current;
    if (!element) return undefined;
    const onFocusIn = (event: FocusEvent) => {
      const index = Number((event.target as HTMLElement).dataset.index);
      if (state && !Number.isNaN(index) && index >= state.items.length - PREFETCH_DISTANCE) loadMore(state);
    };
    element.addEventListener('focusin', onFocusIn);
    return () => element.removeEventListener('focusin', onFocusIn);
  }, [state]);

  useEntryFocus(root, entry, active, state ? state.items.length : error ? -1 : null, error ? 'retry' : undefined);

  return (
    <div ref={root} class="library">
      <header class="top-bar">
        <div class="screen-title">{route.title}</div>
      </header>
      {error ? (
        <ErrorView
          error={error}
          onRetry={() => {
            setError(null);
            loadMore(null);
          }}
        />
      ) : !state ? (
        <LoadingView />
      ) : state.items.length === 0 ? (
        <div class="center-message">{t.libraryEmpty}</div>
      ) : (
        <div class="grid-scroll" data-scroll="y" data-scroll-margin="60">
          <div ref={grid} class="grid" data-section="grid">
            {state.items.map((series, index) => (
              <PosterCard
                key={series.id}
                index={index}
                focusKey={`series-${series.id}`}
                imagePath={ImagePaths.seriesThumbnail(series.id)}
                title={series.metadata.title || series.name}
                subtitle={fmt(t.seriesBooksCount, series.booksCount)}
                progress={series.booksCount > 0 && series.booksReadCount > 0 ? series.booksReadCount / series.booksCount : null}
                onClick={() => push({ name: 'series', seriesId: series.id })}
              />
            ))}
          </div>
          {!state.last && <div class="loading-more">{t.loadingMore}</div>}
        </div>
      )}
    </div>
  );
}
