// 시리즈 상세: 정보 + 책 그리드 (Android T3). 돌아오면 책 진행도를 조용히 다시 읽는다.
import { useEffect, useRef, useState } from 'preact/hooks';
import { seriesIs } from '../api/conditions';
import { bookDisplayTitle, bookProgress } from '../api/display';
import { BookSort, type KomgaBook, type KomgaSeries } from '../api/types';
import { ImagePaths } from '../api/url';
import { fmt, t } from '../i18n';
import { push, type RouteEntry } from '../platform/router';
import { awaitWrites, type LoadError, requireSession, session as sessionStore, toLoadError } from '../state/session';
import { useStore } from '../state/store';
import { ErrorView, LoadingView, PosterCard, useCachedImage } from '../ui/components';
import { useEntryFocus } from '../ui/entryFocus';
import { ActionMenu, type MenuAction, showToast } from '../ui/overlays';
import { focusKeyOrFirst } from '../platform/focus';

const PAGE_SIZE = 100;

interface SeriesData {
  series: KomgaSeries;
  books: KomgaBook[];
}

async function loadAllBooks(seriesId: string): Promise<KomgaBook[]> {
  const { client } = requireSession();
  const books: KomgaBook[] = [];
  for (let page = 0; ; page++) {
    const result = await client.getBookList({ condition: seriesIs(seriesId) }, { page, size: PAGE_SIZE, sort: BookSort.numberAsc });
    books.push(...result.content);
    if (result.last || result.content.length === 0) return books;
  }
}

async function loadSeries(seriesId: string): Promise<SeriesData> {
  const { client } = requireSession();
  const [series, books] = await Promise.all([client.getSeries(seriesId), loadAllBooks(seriesId)]);
  return { series, books };
}

function statusText(status: string): string | null {
  switch (status) {
    case 'ENDED':
      return t.seriesStatusEnded;
    case 'ONGOING':
      return t.seriesStatusOngoing;
    case 'ABANDONED':
      return t.seriesStatusAbandoned;
    case 'HIATUS':
      return t.seriesStatusHiatus;
    default:
      return null;
  }
}

function bookSubtitle(book: KomgaBook): string {
  const progress = book.readProgress;
  if (!progress) return t.seriesUnread;
  if (progress.completed) return t.seriesCompleted;
  return fmt(t.seriesProgress, progress.page, book.media.pagesCount);
}

function SeriesHeader({ series }: { series: KomgaSeries }) {
  const current = useStore(sessionStore);
  const image = useCachedImage(current?.thumbnails, ImagePaths.seriesThumbnail(series.id));
  const status = statusText(series.metadata.status);
  return (
    <div class="series-header">
      <div class="series-cover">{image.url && <img class="card-image" src={image.url} alt="" />}</div>
      <div class="series-info">
        <h1 class="series-title">{series.metadata.title || series.name}</h1>
        <div class="series-meta">
          {status && <span>{fmt(t.seriesStatus, status)}</span>}
          {series.metadata.publisher && <span>{fmt(t.seriesPublisher, series.metadata.publisher)}</span>}
          <span>{fmt(t.seriesBooksCount, series.booksCount)}</span>
        </div>
        {series.metadata.summary && <p class="series-summary">{series.metadata.summary}</p>}
      </div>
    </div>
  );
}

export function SeriesScreen({ entry, active }: { entry: RouteEntry; active: boolean }) {
  const route = entry.route as Extract<RouteEntry['route'], { name: 'series' }>;
  const root = useRef<HTMLDivElement>(null);
  const [data, setData] = useState<SeriesData | null>(null);
  const [error, setError] = useState<LoadError | null>(null);
  const [version, setVersion] = useState(0);
  /** OK 길게 누른 책 → 책 메뉴 (Android T13) */
  const [menuBook, setMenuBook] = useState<KomgaBook | null>(null);

  const load = () => {
    setData(null);
    setError(null);
    loadSeries(route.seriesId).then(
      (next) => {
        setData(next);
        setVersion((v) => v + 1);
      },
      (failure: unknown) => setError(toLoadError(failure)),
    );
  };

  useEffect(load, []);

  // 진행도만 다시 읽는다 (화면은 그대로)
  const refreshBooks = () =>
    awaitWrites()
      .then(() => loadAllBooks(route.seriesId))
      .then(
        (books) => {
          setData((previous) => previous && { ...previous, books });
          setVersion((v) => v + 1);
        },
        () => undefined,
      );

  // 리더에서 돌아오면
  useEffect(() => {
    if (entry.returns > 0) void refreshBooks();
  }, [entry.returns]);

  const bookAction = (run: () => Promise<void>, done: string) => {
    run().then(
      () => {
        showToast(done);
        void refreshBooks();
      },
      () => showToast(t.bookActionFailed),
    );
  };

  const bookMenu = (book: KomgaBook): MenuAction[] => {
    const { client } = requireSession();
    return [
      {
        key: 'menu-read',
        label: t.bookActionMarkRead,
        run: () => bookAction(() => client.markRead(book.id, book.media.pagesCount), t.bookActionDoneRead),
      },
      {
        key: 'menu-unread',
        label: t.bookActionMarkUnread,
        run: () => bookAction(() => client.markUnread(book.id), t.bookActionDoneUnread),
      },
      {
        key: 'menu-analyze',
        label: t.bookActionAnalyze,
        run: () => bookAction(() => client.analyzeBook(book.id), t.bookActionDoneAnalyze),
      },
    ];
  };

  // 메뉴를 닫으면 그 책 카드로 포커스를 돌려준다
  const closeMenu = () => {
    const key = menuBook ? `book-${menuBook.id}` : undefined;
    setMenuBook(null);
    setTimeout(() => root.current && key && focusKeyOrFirst(root.current, key), 0);
  };

  const defaultKey = data?.books.length ? `book-${data.books[0].id}` : undefined;
  useEntryFocus(root, entry, active, data ? version : error ? -1 : null, error ? 'retry' : defaultKey);

  return (
    <div ref={root} class="series" data-scroll="y" data-scroll-margin="60">
      {error ? (
        <ErrorView error={error} onRetry={load} />
      ) : !data ? (
        <LoadingView />
      ) : (
        <>
          <SeriesHeader series={data.series} />
          <h2 class="row-title">{t.seriesBooksHeader}</h2>
          <div class="grid" data-section="books">
            {data.books.map((book, index) => (
              <PosterCard
                key={book.id}
                index={index}
                focusKey={`book-${book.id}`}
                imagePath={ImagePaths.bookThumbnail(book.id)}
                title={bookDisplayTitle(book)}
                subtitle={bookSubtitle(book)}
                progress={bookProgress(book)}
                onClick={() => push({ name: 'reader', bookId: book.id })}
                onLongPress={() => setMenuBook(book)}
              />
            ))}
          </div>
        </>
      )}
      {menuBook && <ActionMenu title={bookDisplayTitle(menuBook)} actions={bookMenu(menuBook)} onClose={closeMenu} />}
    </div>
  );
}
