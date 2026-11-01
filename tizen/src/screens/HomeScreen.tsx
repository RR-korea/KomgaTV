// 홈 (Android T3·T9, Komelia 홈과 같은 줄): 읽는 중 → 다음 권 → 최근 추가된 책 → 최근 추가된 시리즈
// → 최근 업데이트된 시리즈 → 라이브러리. 빈 줄은 그리지 않는다.
// 읽는 중·라이브러리는 필수(실패하면 오류 화면), 나머지 줄은 실패하면 그 줄만 빈다.
// 돌아오면 진행 저장을 기다렸다가 조용히 새로고침하고 연 항목에 포커스.
import type { ComponentChildren } from 'preact';
import { useEffect, useRef, useState } from 'preact/hooks';
import { readStatusIs } from '../api/conditions';
import { BookSort, type KomgaBook, type KomgaLibrary, type KomgaSeries, type Page } from '../api/types';
import { t } from '../i18n';
import { push, type RouteEntry } from '../platform/router';
import { awaitWrites, type LoadError, requireSession, toLoadError } from '../state/session';
import { BookCard, SeriesCard } from '../ui/cards';
import { Button, ErrorView, LoadingView, Row, TextCard } from '../ui/components';
import { useEntryFocus } from '../ui/entryFocus';
import { ActionMenu, type MenuAction, showToast } from '../ui/overlays';
import { focusKeyOrFirst } from '../platform/focus';

const ROW_SIZE = 20;

interface HomeData {
  reading: KomgaBook[];
  onDeck: KomgaBook[];
  latestBooks: KomgaBook[];
  newSeries: KomgaSeries[];
  updatedSeries: KomgaSeries[];
  libraries: KomgaLibrary[];
}

const optional = <T,>(request: Promise<Page<T>>): Promise<T[]> => request.then((page) => page.content, () => []);

async function loadHome(): Promise<HomeData> {
  const { client } = requireSession();
  await awaitWrites();
  const page = { page: 0, size: ROW_SIZE };
  const [reading, libraries, onDeck, latestBooks, newSeries, updatedSeries] = await Promise.all([
    client.getBookList({ condition: readStatusIs('IN_PROGRESS') }, { ...page, sort: BookSort.readDateDesc }),
    client.getLibraries(),
    optional(client.getBooksOnDeck(page)),
    optional(client.getLatestBooks(page)),
    optional(client.getNewSeries(page)),
    optional(client.getUpdatedSeries(page)),
  ]);
  return { reading: reading.content, onDeck, latestBooks, newSeries, updatedSeries, libraries };
}

/** 첫 줄의 첫 카드 키 (처음 들어올 때 포커스) */
function firstKey(data: HomeData): string {
  if (data.reading.length) return `reading-book-${data.reading[0].id}`;
  if (data.onDeck.length) return `ondeck-book-${data.onDeck[0].id}`;
  if (data.latestBooks.length) return `latest-book-${data.latestBooks[0].id}`;
  if (data.newSeries.length) return `new-series-${data.newSeries[0].id}`;
  if (data.updatedSeries.length) return `updated-series-${data.updatedSeries[0].id}`;
  if (data.libraries.length) return `library-${data.libraries[0].id}`;
  return 'search';
}

function HomeRow({ id, title, children, show }: { id: string; title: string; children: ComponentChildren; show: boolean }) {
  return show ? (
    <Row section={id} title={title}>
      {children}
    </Row>
  ) : null;
}

export function HomeScreen({ entry, active }: { entry: RouteEntry; active: boolean }) {
  const root = useRef<HTMLDivElement>(null);
  const [data, setData] = useState<HomeData | null>(null);
  const [error, setError] = useState<LoadError | null>(null);
  const [version, setVersion] = useState(0);
  /** OK 길게 누른 라이브러리 → 라이브러리 메뉴 (Android: 스캔·휴지통 비우기, 관리자) */
  const [menuLibrary, setMenuLibrary] = useState<KomgaLibrary | null>(null);

  const libraryAction = (run: () => Promise<void>, done: string) =>
    run().then(
      () => showToast(done),
      () => showToast(t.libraryActionFailed),
    );

  const libraryMenu = (library: KomgaLibrary): MenuAction[] => {
    const { client } = requireSession();
    return [
      {
        key: 'menu-scan',
        label: t.libraryActionScan,
        run: () => void libraryAction(() => client.scanLibrary(library.id), t.libraryActionDoneScan),
      },
      {
        key: 'menu-trash',
        label: t.libraryActionEmptyTrash,
        run: () => void libraryAction(() => client.emptyTrash(library.id), t.libraryActionDoneTrash),
      },
    ];
  };

  const closeMenu = () => {
    const key = menuLibrary ? `library-${menuLibrary.id}` : undefined;
    setMenuLibrary(null);
    setTimeout(() => root.current && key && focusKeyOrFirst(root.current, key), 0);
  };

  const load = (silent: boolean) => {
    if (!silent) {
      setData(null);
      setError(null);
    }
    loadHome().then(
      (next) => {
        setData(next);
        setError(null);
        setVersion((v) => v + 1);
      },
      (failure: unknown) => {
        // 조용한 새로고침이 실패하면 지금 보이는 내용을 그대로 둔다
        if (!silent) setError(toLoadError(failure));
      },
    );
  };

  useEffect(() => load(false), []);
  useEffect(() => {
    if (entry.returns > 0) load(true);
  }, [entry.returns]);

  useEntryFocus(
    root,
    entry,
    active,
    data || error ? version + (error ? 0.5 : 0) : null,
    error ? 'retry' : data ? firstKey(data) : undefined,
  );

  return (
    <div ref={root} class="home" data-scroll="y" data-scroll-margin="60">
      <header class="top-bar">
        <div class="app-name">{t.appName}</div>
        <div class="button-row top-actions">
          <Button focusKey="search" onClick={() => push({ name: 'search' })}>
            {t.homeSearch}
          </Button>
          <Button focusKey="settings" onClick={() => push({ name: 'settings', first: false })}>
            {t.homeServerSettings}
          </Button>
        </div>
      </header>
      {error ? (
        <ErrorView error={error} onRetry={() => load(false)} />
      ) : !data ? (
        <LoadingView />
      ) : (
        <>
          <HomeRow id="reading" title={t.homeContinueReading} show={data.reading.length > 0}>
            {data.reading.map((book) => (
              <BookCard key={book.id} book={book} row="reading" />
            ))}
          </HomeRow>
          <HomeRow id="ondeck" title={t.homeOnDeck} show={data.onDeck.length > 0}>
            {data.onDeck.map((book) => (
              <BookCard key={book.id} book={book} row="ondeck" />
            ))}
          </HomeRow>
          <HomeRow id="latest" title={t.homeLatestBooks} show={data.latestBooks.length > 0}>
            {data.latestBooks.map((book) => (
              <BookCard key={book.id} book={book} row="latest" />
            ))}
          </HomeRow>
          <HomeRow id="new" title={t.homeNewSeries} show={data.newSeries.length > 0}>
            {data.newSeries.map((series) => (
              <SeriesCard key={series.id} series={series} row="new" />
            ))}
          </HomeRow>
          <HomeRow id="updated" title={t.homeUpdatedSeries} show={data.updatedSeries.length > 0}>
            {data.updatedSeries.map((series) => (
              <SeriesCard key={series.id} series={series} row="updated" />
            ))}
          </HomeRow>
          <Row section="libraries" title={t.homeLibraries}>
            {data.libraries.map((library) => (
              <TextCard
                key={library.id}
                focusKey={`library-${library.id}`}
                title={library.name}
                onClick={() => push({ name: 'library', libraryId: library.id, title: library.name })}
                onLongPress={() => setMenuLibrary(library)}
              />
            ))}
          </Row>
        </>
      )}
      {menuLibrary && (
        <ActionMenu
          title={menuLibrary.name}
          hint={t.libraryActionHint}
          actions={libraryMenu(menuLibrary)}
          onClose={closeMenu}
        />
      )}
    </div>
  );
}
