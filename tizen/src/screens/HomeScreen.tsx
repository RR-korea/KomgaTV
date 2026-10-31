// 홈: 이어 읽기 행 + 라이브러리 행 (Android T3). 돌아오면 조용히 새로고침하고 연 항목에 포커스.
import { useEffect, useRef, useState } from 'preact/hooks';
import { readStatusIs } from '../api/conditions';
import { bookDisplayTitle, bookProgress } from '../api/display';
import { BookSort, type KomgaBook, type KomgaLibrary } from '../api/types';
import { ImagePaths } from '../api/url';
import { fmt, t } from '../i18n';
import { push, type RouteEntry } from '../platform/router';
import { awaitWrites, type LoadError, requireSession, toLoadError } from '../state/session';
import { Button, ErrorView, LoadingView, PosterCard, Row, TextCard } from '../ui/components';
import { useEntryFocus } from '../ui/entryFocus';

interface HomeData {
  books: KomgaBook[];
  libraries: KomgaLibrary[];
}

async function loadHome(): Promise<HomeData> {
  const { client } = requireSession();
  await awaitWrites();
  const [books, libraries] = await Promise.all([
    client.getBookList({ condition: readStatusIs('IN_PROGRESS') }, { page: 0, size: 20, sort: BookSort.readDateDesc }),
    client.getLibraries(),
  ]);
  return { books: books.content, libraries };
}

export function HomeScreen({ entry, active }: { entry: RouteEntry; active: boolean }) {
  const root = useRef<HTMLDivElement>(null);
  const [data, setData] = useState<HomeData | null>(null);
  const [error, setError] = useState<LoadError | null>(null);
  const [version, setVersion] = useState(0);

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

  const defaultKey = data?.books.length ? `book-${data.books[0].id}` : data?.libraries.length ? `library-${data.libraries[0].id}` : 'settings';
  useEntryFocus(root, entry, active, data || error ? version + (error ? 0.5 : 0) : null, error ? 'retry' : defaultKey);

  return (
    <div ref={root} class="home" data-scroll="y" data-scroll-margin="60">
      <header class="top-bar">
        <div class="app-name">{t.appName}</div>
        <Button focusKey="settings" onClick={() => push({ name: 'settings', first: false })}>
          {t.homeServerSettings}
        </Button>
      </header>
      {error ? (
        <ErrorView error={error} onRetry={() => load(false)} />
      ) : !data ? (
        <LoadingView />
      ) : (
        <>
          <Row section="continue" title={t.homeContinueReading}>
            {data.books.length === 0 && <div class="empty">{t.homeEmpty}</div>}
            {data.books.map((book) => (
              <PosterCard
                key={book.id}
                focusKey={`book-${book.id}`}
                imagePath={ImagePaths.bookThumbnail(book.id)}
                title={bookDisplayTitle(book)}
                subtitle={
                  book.readProgress?.completed
                    ? t.homeBookCompleted
                    : fmt(t.homeBookProgress, book.readProgress?.page ?? 0, book.media.pagesCount)
                }
                progress={bookProgress(book)}
                onClick={() => push({ name: 'reader', bookId: book.id })}
              />
            ))}
          </Row>
          <Row section="libraries" title={t.homeLibraries}>
            {data.libraries.map((library) => (
              <TextCard
                key={library.id}
                focusKey={`library-${library.id}`}
                title={library.name}
                onClick={() => push({ name: 'library', libraryId: library.id, title: library.name })}
              />
            ))}
          </Row>
        </>
      )}
    </div>
  );
}
