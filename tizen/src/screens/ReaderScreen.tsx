// 리더 (Android T4·T5·T6·T14 + 다음 권으로 이어 읽기와 같은 규칙과 옵션, PLAN Z5). 두 가지 종류:
// - 페이지 넘김(PAGED): 좌/우 = 이전/다음 펼침(RTL이면 반대), 위/아래 = 화면보다 큰 쪽을 반 화면씩 스크롤
// - 세로 스크롤(CONTINUOUS, 웹툰): 위/아래 = 한 화면씩, 좌/우 = 이전/다음 쪽 맨 위
// 공통: OK = 정보 → 한 번 더 OK = 보기 설정(이 작품), 정보가 열려 있으면 위/아래 = 10쪽 이동, 채널 위/아래 = 10쪽 이동,
//       Back = 패널/정보 닫기 → 진행 저장 후 나가기.
// 책 끝(KMReader처럼): 마지막 쪽 다음에 책 끝 화면(다 읽은 책 ↔ 다음 권), 앞으로 한 번 더 = 다음 권 1쪽.
//   끝까지 3펼침 이하에서 다음 권을 찾고 첫 쪽들을 미리 받아, 로딩 화면 없이 같은 화면에서 바꾼다. 다음 권이 EPUB이면 EPUB 리더로.
import { useEffect, useMemo, useRef, useState } from 'preact/hooks';
import { bookDisplayTitle } from '../api/display';
import type { KomgaBook, ReadingDirection } from '../api/types';
import { ImagePaths } from '../api/url';
import { fmt, t } from '../i18n';
import { current, focusKeyOrFirst, move } from '../platform/focus';
import { useKeys } from '../platform/input';
import type { AppKey } from '../platform/keys';
import { pop, type RouteEntry } from '../platform/router';
import {
  type Anchor,
  anchorOf,
  type ContinuousLayout,
  layoutContinuous,
  scrollByScreen,
  scrollOf,
  stepPage,
  visiblePages,
} from '../reader/continuous';
import { FALLBACK_SIZE, layoutSpread, type Size } from '../reader/layout';
import { ProgressSaver } from '../reader/progress';
import { buildSpreads, highestPage, type PageInfo, spreadContains, spreadIndexOf } from '../reader/spreads';
import {
  clearSeriesSettings,
  effectiveSettings,
  loadSeriesSettings,
  type ReaderSettings,
  type ReaderType,
  readerPrefs,
  saveSeriesSettings,
  startPage,
} from '../state/readerPrefs';
import { type LoadError, requireSession, session as sessionStore, toLoadError, trackWrite } from '../state/session';
import { useStore } from '../state/store';
import { Button, ErrorView, LoadingView, ProgressBar, useCachedImage } from '../ui/components';
import { applyViewOption, cycledValue, type ViewKey, viewOptions, ViewRows } from '../ui/viewSettings';
import { EpubReader } from './EpubReader';

const SCREEN: Size = { width: 1920, height: 1080 };
const JUMP_PAGES = 10;
/** 끝까지 이만큼 펼침이 남으면 다음 권을 찾고 미리 받는다 */
const NEXT_BOOK_LOOKAHEAD = 3;
/** 세로 스크롤 맨 아래 책 끝 카드 높이 */
const END_CARD_HEIGHT = 720;

interface BookData {
  book: KomgaBook;
  pages: PageInfo[];
  seriesDirection: ReadingDirection | null;
  /** EPUB이면 글 리더로 넘긴다 (Android T12) */
  epub?: boolean;
}

async function loadBook(bookId: string): Promise<BookData> {
  const { client } = requireSession();
  const book = await client.getBook(bookId);
  if (book.media.mediaProfile === 'EPUB') return { book, pages: [], seriesDirection: null, epub: true };
  const [series, pages] = await Promise.all([
    client.getSeries(book.seriesId),
    client.getBookPages(bookId).catch(() => []),
  ]);
  const pageInfos: PageInfo[] = pages.length
    ? pages.map((page) => ({ number: page.number, width: page.width, height: page.height }))
    : Array.from({ length: Math.max(1, book.media.pagesCount) }, (_unused, i) => ({ number: i + 1, width: null, height: null }));
  return { book, pages: pageInfos, seriesDirection: series.metadata.readingDirection };
}

function ReaderPage({
  bookId,
  page,
  style,
  onNaturalSize,
}: {
  bookId: string;
  page: PageInfo;
  style: Record<string, string>;
  onNaturalSize: (pageNumber: number, size: Size) => void;
}) {
  const currentSession = useStore(sessionStore);
  const image = useCachedImage(currentSession?.pages, ImagePaths.bookPage(bookId, page.number));
  return (
    // Preact 11은 숫자 스타일 값에 px를 붙이지 않는다 — 단위를 직접 쓴다
    <div class="reader-page" style={style}>
      {image.url ? (
        <img
          class="reader-image"
          src={image.url}
          alt=""
          onLoad={(event) => {
            const img = event.currentTarget as HTMLImageElement;
            onNaturalSize(page.number, { width: img.naturalWidth, height: img.naturalHeight });
          }}
        />
      ) : (
        <div class="reader-page-status">{image.failed ? '!' : t.readerLoading}</div>
      )}
    </div>
  );
}

/** 다음 권 상태: undefined = 아직 안 찾음, 'loading' = 찾는 중, null = 마지막 권 */
type NextBook = KomgaBook | null | 'loading' | undefined;

/** 책 끝 화면 (다 읽은 책 ↔ 다음 권) */
function EndView({ book, next }: { book: KomgaBook; next: NextBook }) {
  const currentSession = useStore(sessionStore);
  const cover = useCachedImage(
    currentSession?.thumbnails,
    next && next !== 'loading' ? ImagePaths.bookThumbnail(next.id) : null,
  );
  return (
    <div class="end-view">
      <div class="end-column">
        <div class="end-label">{t.endPageFinished}</div>
        <div class="end-title">{bookDisplayTitle(book)}</div>
      </div>
      <div class="end-arrow">→</div>
      <div class="end-column">
        <div class="end-label">{t.endPageNext}</div>
        {next === 'loading' || next === undefined ? (
          <div class="end-title muted">{t.endPageFindingNext}</div>
        ) : next === null ? (
          <div class="end-title muted">{t.endPageLastBook}</div>
        ) : (
          <>
            <div class="end-cover">{cover.url && <img class="card-image" src={cover.url} alt="" />}</div>
            <div class="end-title">{bookDisplayTitle(next)}</div>
          </>
        )}
      </div>
      <div class="end-hint">{next && next !== 'loading' ? t.endPageHintNext : t.endPageHintLast}</div>
    </div>
  );
}

const px = (value: number) => `${Math.round(value)}px`;

export function ReaderScreen({ entry, active }: { entry: RouteEntry; active: boolean }) {
  const route = entry.route as Extract<RouteEntry['route'], { name: 'reader' }>;
  const defaults = useStore(readerPrefs);
  /** 지금 읽는 책 (다음 권으로 넘어가면 바뀐다) */
  const [bookId, setBookId] = useState(route.bookId);
  const [data, setData] = useState<BookData | null>(null);
  const [error, setError] = useState<LoadError | null>(null);
  /** 이 작품(시리즈)만의 보기 설정. null이면 앱 기본값 + 시리즈 규칙 (Android T14) */
  const [seriesSettings, setSeriesSettings] = useState<ReaderSettings | null>(null);
  const [offset, setOffset] = useState(false); // 한 쪽 밀기 — 이 세션 동안만
  /** 페이지 넘김: 지금 펼침의 기준 쪽. 배치가 바뀌어도 이 쪽이 든 펼침을 보여 준다 */
  const [anchorPage, setAnchorPage] = useState(1);
  /** 세로 스크롤: 화면 맨 위가 걸친 쪽(0부터)과 그 안의 거리 */
  const [anchor, setAnchor] = useState<Anchor>({ index: 0, offset: 0 });
  const [overlay, setOverlay] = useState(false);
  const [panel, setPanel] = useState(false);
  const [scrollY, setScrollY] = useState(0);
  const [naturalSizes, setNaturalSizes] = useState<Record<number, Size>>({});
  /** 페이지 넘김의 책 끝 화면 */
  const [atEnd, setAtEnd] = useState(false);
  const [nextBook, setNextBook] = useState<NextBook>(undefined);
  const saver = useRef<ProgressSaver | null>(null);
  const moved = useRef(false);
  const exiting = useRef(false);
  const root = useRef<HTMLDivElement>(null);

  /** 책을 읽는다. 다음 권으로 넘어갈 때는 지금 화면을 둔 채 바꾼다(로딩 화면 없이). */
  const load = (id: string) => {
    setError(null);
    loadBook(id).then(
      (loaded) => {
        setBookId(id);
        setOffset(false);
        setAtEnd(false);
        setNextBook(undefined);
        setNaturalSizes({});
        setOverlay(false);
        setPanel(false);
        moved.current = false;
        if (loaded.epub) {
          saver.current = null;
          setData(loaded);
          return;
        }
        const progress = loaded.book.readProgress;
        const first = startPage(progress);
        setSeriesSettings(loadSeriesSettings(loaded.book.seriesId));
        setAnchorPage(first);
        setAnchor({ index: Math.min(loaded.pages.length - 1, first - 1), offset: 0 });
        saver.current = new ProgressSaver(
          (page, completed) => trackWrite(requireSession().client.markReadProgress(id, page, completed)),
          progress ? { page: progress.page, completed: progress.completed } : null,
        );
        setData(loaded);
      },
      (failure: unknown) => setError(toLoadError(failure)),
    );
  };

  useEffect(() => load(route.bookId), []);
  // 화면이 어떤 식으로 사라지든 기다리던 진행은 보낸다
  useEffect(() => () => void saver.current?.flush(), []);

  const settings = effectiveSettings(defaults, seriesSettings, data?.seriesDirection ?? null);
  const readerType = settings.readerType;
  const direction = settings.readingDirection;
  const pages = data?.pages ?? [];
  const totalPages = pages.length;
  const sizeOf = (page: PageInfo): Size =>
    naturalSizes[page.number] ?? (page.width && page.height ? { width: page.width, height: page.height } : FALLBACK_SIZE);

  // ---------- 페이지 넘김 ----------
  const spreads = useMemo(() => buildSpreads(pages, settings.pageLayout, offset), [data, settings.pageLayout, offset]);
  const index = spreadIndexOf(spreads, anchorPage);
  const spread = spreads[index] ?? [];
  const pagedLayout = layoutSpread(spread.map(sizeOf), settings.scaleType, settings.stretchToFit, SCREEN);
  const pagedMaxScroll = Math.max(0, pagedLayout.height - SCREEN.height);

  // ---------- 세로 스크롤 (맨 아래 책 끝 카드 자리까지) ----------
  const continuous: ContinuousLayout = useMemo(() => {
    const layout = layoutContinuous(
      pages.map(sizeOf),
      SCREEN,
      settings.continuousPadding,
      settings.continuousPageSpacing,
      settings.stretchToFit,
    );
    return { pages: layout.pages, total: layout.total + END_CARD_HEIGHT };
  }, [data, naturalSizes, settings.continuousPadding, settings.continuousPageSpacing, settings.stretchToFit]);
  const continuousScroll = scrollOf(continuous, anchor, SCREEN.height);
  const visible = visiblePages(continuous, continuousScroll, SCREEN.height);
  const continuousAtBottom = continuousScroll >= continuous.total - SCREEN.height - 1;

  const isContinuous = readerType === 'CONTINUOUS';
  /** 정보 표시·진행 저장에 쓰는 "보이는 쪽 중 가장 큰 번호" */
  const shownHighest = isContinuous ? pages[visible.last]?.number ?? 1 : highestPage(spread);

  // 펼침이 바뀌면 맨 위부터
  useEffect(() => setScrollY(0), [index, spreads]);

  // 프리로드: 페이지 넘김은 앞뒤 2펼침, 세로 스크롤은 화면 아래 다음 2쪽
  useEffect(() => {
    const cache = sessionStore.get()?.pages;
    if (!cache || !data || data.epub) return;
    const near: PageInfo[] = isContinuous
      ? pages.slice(visible.last + 1, visible.last + 3)
      : [index + 1, index - 1, index + 2, index - 2].reduce<PageInfo[]>((all, i) => all.concat(spreads[i] ?? []), []);
    near.forEach((page) => cache.prefetch(ImagePaths.bookPage(bookId, page.number)));
  }, [isContinuous, index, spreads, visible.last, bookId]);

  // 끝이 가까우면 다음 권을 찾고 첫 쪽들을 미리 받는다
  const nearEnd = isContinuous ? visible.last >= totalPages - 1 - NEXT_BOOK_LOOKAHEAD : index >= spreads.length - 1 - NEXT_BOOK_LOOKAHEAD;
  useEffect(() => {
    if (!data || data.epub || !nearEnd || nextBook !== undefined) return;
    setNextBook('loading');
    const { client } = requireSession();
    client.getNextBook(data.book.id).then(
      (next) => {
        setNextBook(next);
        if (!next) return;
        if (next.media.mediaProfile !== 'EPUB') {
          [1, 2].forEach((n) => sessionStore.get()?.pages.prefetch(ImagePaths.bookPage(next.id, n)));
        }
        sessionStore.get()?.thumbnails.prefetch(ImagePaths.bookThumbnail(next.id));
      },
      () => setNextBook(null),
    );
  }, [nearEnd, data]);

  // 세로 스크롤 진행 저장: 보이는 쪽 중 큰 번호, 마지막 쪽 끝이 보이면 완독
  useEffect(() => {
    if (!isContinuous || !data || !moved.current) return;
    saver.current?.schedule(shownHighest, visible.endReached);
  }, [isContinuous, shownHighest, visible.endReached]);

  // 정보·패널 포커스
  useEffect(() => {
    const element = root.current;
    if (!element || !active) return;
    if (panel) focusKeyOrFirst(element.querySelector('.reader-panel') ?? element, 'opt-type');
    else if (overlay) focusKeyOrFirst(element, 'reader-settings');
    else (document.activeElement as HTMLElement | null)?.blur();
  }, [overlay, panel, active]);

  /** 다음 권으로: 지금 책 완독을 보낸 뒤 같은 화면에서 바꾼다 */
  const openNextBook = () => {
    if (!nextBook || nextBook === 'loading') return;
    const target = nextBook;
    const done = saver.current?.flush() ?? Promise.resolve();
    void done.then(() => load(target.id));
  };

  // 보기 설정 패널: 이 작품에만 저장 (Android T14)
  const applySetting = (key: ViewKey, value: unknown) => {
    if (key === 'opt-offset') {
      setOffset(value as boolean);
      return;
    }
    const next = applyViewOption(settings, key, value);
    if (key === 'opt-type' && next.readerType !== readerType) {
      // 종류를 바꿀 때 보던 쪽을 이어 간다
      if (next.readerType === 'CONTINUOUS') setAnchor({ index: Math.max(0, (spread[0]?.number ?? 1) - 1), offset: 0 });
      else setAnchorPage(pages[visible.first]?.number ?? 1);
      setAtEnd(false);
    }
    if (data) saveSeriesSettings(data.book.seriesId, next);
    setSeriesSettings(next);
  };

  const resetSeries = () => {
    if (!data) return;
    const before = readerType;
    clearSeriesSettings(data.book.seriesId);
    setSeriesSettings(null);
    const after: ReaderType = effectiveSettings(defaults, null, data.seriesDirection).readerType;
    if (after !== before) {
      if (after === 'CONTINUOUS') setAnchor({ index: Math.max(0, (spread[0]?.number ?? 1) - 1), offset: 0 });
      else setAnchorPage(pages[visible.first]?.number ?? 1);
    }
  };

  const goToSpread = (target: number) => {
    if (!data || target < 0 || target === index) return;
    if (target >= spreads.length) {
      setAtEnd(true); // 마지막 펼침 다음 = 책 끝 화면
      return;
    }
    const next = spreads[target];
    setAnchorPage(next[0].number);
    saver.current?.schedule(highestPage(next), spreadContains(next, totalPages));
  };

  const goToAnchor = (next: Anchor) => {
    moved.current = true;
    setAnchor(next);
  };

  const jump = (delta: number) => {
    if (!data) return;
    setAtEnd(false);
    if (isContinuous) {
      const target = Math.min(totalPages - 1, Math.max(0, anchor.index + delta));
      goToAnchor({ index: target, offset: 0 });
      return;
    }
    if (!spread.length) return;
    const targetPage = Math.min(totalPages, Math.max(1, spread[0].number + delta));
    goToSpread(spreadIndexOf(spreads, targetPage));
  };

  const scrollVertical = (sign: 1 | -1) => {
    if (isContinuous) {
      if (sign > 0 && continuousAtBottom) {
        openNextBook(); // 맨 아래 책 끝 카드에서 한 번 더 = 다음 권
        return;
      }
      goToAnchor(anchorOf(continuous, scrollByScreen(continuous, continuousScroll, SCREEN.height, sign)));
    } else {
      setScrollY((y) => Math.min(pagedMaxScroll, Math.max(0, y + (sign * SCREEN.height) / 2)));
    }
  };

  const horizontal = (sign: 1 | -1) => {
    if (isContinuous) {
      if (sign > 0 && continuousAtBottom) {
        openNextBook();
        return;
      }
      goToAnchor(stepPage(anchor, totalPages, sign));
      return;
    }
    // 오른쪽 키 = +1. RTL이면 반대
    const forward = direction === 'RIGHT_TO_LEFT' ? -sign : sign;
    if (atEnd) {
      if (forward > 0) openNextBook();
      else setAtEnd(false); // 이전 쪽 키 = 마지막 쪽으로
      return;
    }
    goToSpread(index + forward);
  };

  const exit = () => {
    if (exiting.current) return;
    exiting.current = true;
    void saver.current?.flush();
    pop();
  };

  const options = viewOptions(settings, isContinuous ? 'reader-continuous' : 'reader-paged', offset);

  const cycle = (key: string | undefined, delta: number) => {
    const option = options.find((candidate) => candidate.key === key);
    if (!option) return;
    const value = cycledValue(option, delta);
    if (value !== null) applySetting(option.key, value);
  };

  const onKey = (key: AppKey): boolean => {
    if (!data) {
      if (key === 'back') {
        exit();
        return true;
      }
      return false; // 오류 화면의 다시 시도 버튼은 기본 처리로
    }
    if (panel) {
      const focusedKey = current()?.dataset.key;
      switch (key) {
        case 'back':
          setPanel(false);
          break;
        case 'up':
        case 'down':
          move(key);
          break;
        case 'left':
        case 'right':
        case 'enter':
          if (focusedKey === 'panel-close') {
            if (key === 'enter') setPanel(false);
          } else if (focusedKey === 'panel-reset') {
            if (key === 'enter') {
              resetSeries();
              const element = root.current?.querySelector<HTMLElement>('.reader-panel');
              if (element) focusKeyOrFirst(element, 'opt-type');
            }
          } else {
            cycle(focusedKey, key === 'left' ? -1 : 1);
          }
          break;
        default:
          break;
      }
      return true;
    }
    if (atEnd && !overlay) {
      // 책 끝 화면: 다음 쪽 키 / OK = 다음 권, 이전 쪽 키 = 마지막 쪽, Back = 나가기
      switch (key) {
        case 'left':
          horizontal(-1);
          break;
        case 'right':
          horizontal(1);
          break;
        case 'enter':
          openNextBook();
          break;
        case 'back':
          exit();
          break;
        default:
          break;
      }
      return true;
    }
    switch (key) {
      case 'left':
        horizontal(-1);
        break;
      case 'right':
        horizontal(1);
        break;
      case 'enter':
        if (overlay) setPanel(true);
        else setOverlay(true);
        break;
      case 'up':
        if (overlay) jump(JUMP_PAGES);
        else scrollVertical(-1);
        break;
      case 'down':
        if (overlay) jump(-JUMP_PAGES);
        else scrollVertical(1);
        break;
      case 'channelUp':
        jump(JUMP_PAGES);
        break;
      case 'channelDown':
        jump(-JUMP_PAGES);
        break;
      case 'back':
        if (overlay) setOverlay(false);
        else exit();
        break;
      default:
        break;
    }
    return true;
  };

  useKeys(onKey, active && !data?.epub);

  if (data?.epub) return <EpubReader key={data.book.id} book={data.book} active={active} />;

  if (error) {
    return (
      <div ref={root} class="reader">
        <ErrorView error={error} onRetry={() => load(bookId)} />
      </div>
    );
  }
  if (!data) {
    return (
      <div ref={root} class="reader">
        <LoadingView text={t.readerLoading} />
      </div>
    );
  }

  const onNaturalSize = (number: number, size: Size) =>
    setNaturalSizes((previous) =>
      previous[number]?.width === size.width && previous[number]?.height === size.height
        ? previous
        : { ...previous, [number]: size },
    );
  const bookTitle = bookDisplayTitle(data.book);

  let content;
  if (isContinuous) {
    // 화면 위아래 한 화면 안쪽의 쪽만 그린다 (나머지는 자리만 차지) — TV 메모리
    const from = continuousScroll - SCREEN.height;
    const to = continuousScroll + SCREEN.height * 2;
    const endTop = continuous.total - END_CARD_HEIGHT;
    content = (
      <div
        class="reader-column"
        style={{ height: px(continuous.total), transform: `translateY(${px(-continuousScroll)})` }}
      >
        {continuous.pages.map((placement, i) =>
          placement.top + placement.height < from || placement.top > to ? null : (
            <ReaderPage
              key={`${bookId}-${pages[i].number}`}
              bookId={bookId}
              page={pages[i]}
              style={{
                position: 'absolute',
                top: px(placement.top),
                left: px(placement.left),
                width: px(placement.width),
                height: px(placement.height),
              }}
              onNaturalSize={onNaturalSize}
            />
          ),
        )}
        {endTop < to && (
          <div class="end-card" style={{ top: px(endTop), height: px(END_CARD_HEIGHT) }}>
            <EndView book={data.book} next={nextBook} />
          </div>
        )}
      </div>
    );
  } else if (atEnd) {
    content = <EndView book={data.book} next={nextBook} />;
  } else {
    const rtl = direction === 'RIGHT_TO_LEFT';
    const shownPages = rtl ? spread.slice().reverse() : spread;
    const shownSizes = rtl ? pagedLayout.pages.slice().reverse() : pagedLayout.pages;
    const top = pagedLayout.height < SCREEN.height ? (SCREEN.height - pagedLayout.height) / 2 : -scrollY;
    const left = (SCREEN.width - pagedLayout.width) / 2;
    content = (
      <div class="reader-spread" style={{ transform: `translate(${px(left)}, ${px(top)})` }}>
        {shownPages.map((page, i) => (
          <ReaderPage
            key={`${bookId}-${page.number}`}
            bookId={bookId}
            page={page}
            style={{ width: px(shownSizes[i].width), height: px(shownSizes[i].height) }}
            onNaturalSize={onNaturalSize}
          />
        ))}
      </div>
    );
  }

  return (
    <div ref={root} class={`reader enhance-${settings.imageEnhancement.toLowerCase()}`}>
      {content}

      {overlay && !panel && (
        <div class="reader-overlay">
          <div class="reader-overlay-bar">
            <div class="reader-overlay-row">
              <div class="reader-title">{bookTitle}</div>
              <Button focusKey="reader-settings" onClick={() => setPanel(true)}>
                {t.readerSettings}
              </Button>
            </div>
            <div class="reader-overlay-row">
              <div>
                {!isContinuous && (spread.length > 1 || settings.pageLayout !== 'SINGLE_PAGE')
                  ? fmt(t.readerSpreadOf, index + 1, spreads.length, spread.map((page) => page.number).join(', '), totalPages)
                  : fmt(t.readerPageOf, shownHighest, totalPages)}
              </div>
              <div>{`${Math.round((shownHighest / Math.max(1, totalPages)) * 100)}%`}</div>
            </div>
            <ProgressBar value={shownHighest / Math.max(1, totalPages)} />
            <div class="reader-help">{isContinuous ? t.readerHelpContinuous : t.readerHelp}</div>
          </div>
        </div>
      )}

      {panel && (
        <div class="reader-panel-backdrop">
          <div class="reader-panel" data-focus-trap>
            <h2 class="reader-panel-title">{t.readerSettingsTitle}</h2>
            <div class="reader-panel-status">
              {seriesSettings ? t.readerSettingsSeriesCustom : t.readerSettingsSeriesDefault}
            </div>
            <ViewRows options={options} onCycle={(key, delta) => cycle(key, delta)} />
            <div class="panel-buttons">
              {seriesSettings && (
                <Button focusKey="panel-reset" class="panel-close" onClick={resetSeries}>
                  {t.readerSettingsReset}
                </Button>
              )}
              <Button focusKey="panel-close" class="panel-close" onClick={() => setPanel(false)}>
                {t.close}
              </Button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
