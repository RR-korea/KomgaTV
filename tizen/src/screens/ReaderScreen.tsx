// 리더 (Android T4·T5·T6·T14 + 다음 권으로 이어 읽기와 같은 규칙과 옵션, PLAN Z5). 두 가지 종류:
// - 페이지 넘김(PAGED): 좌/우 = 이전/다음 펼침(RTL이면 반대), 위/아래 = 화면보다 큰 쪽을 반 화면씩 스크롤
// - 세로 스크롤(CONTINUOUS, 웹툰): 위/아래 = 한 화면씩, 좌/우 = 이전/다음 쪽 맨 위
// 공통: OK = 정보 → 한 번 더 OK = 보기 설정(이 작품), 정보가 열려 있으면 위/아래 = 10쪽 이동, 채널 위/아래 = 10쪽 이동,
//       Back = 패널/정보 닫기 → 진행 저장 후 나가기.
// 책 끝(KMReader처럼): 마지막 쪽 다음에 책 끝 화면(다 읽은 책 ↔ 다음 권), 앞으로 한 번 더 = 다음 권 1쪽.
//   끝까지 3펼침 이하에서 다음 권을 찾고 첫 쪽들을 미리 받아, 로딩 화면 없이 같은 화면에서 바꾼다. 다음 권이 EPUB이면 EPUB 리더로.
// 책 처음(Android 이전 권으로 가기): 첫 펼침에서 뒤로 = 책 첫 화면(이전 권 ‹ 지금 권), 한 번 더 = 이전 권 마지막 쪽.
//   처음 근처에서 이전 권을 찾고 마지막 두 쪽을 미리 받는다. 세로 스크롤은 1쪽 위 책 첫 카드, 맨 위에서 위 = 이전 권 끝.
// PDF 책은 원본 쪽에서 JPEG를 꺼내 그린다(session.loadPage). 쪽이 0.25초 안에 안 뜨면 "불러오는 중…".
import type { ComponentChildren } from 'preact';
import { useEffect, useLayoutEffect, useMemo, useRef, useState } from 'preact/hooks';
import { bookDisplayTitle } from '../api/display';
import type { KomgaBook, ReadingDirection } from '../api/types';
import { ImagePaths, pageKey } from '../api/url';
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
  maxScroll,
  scrollByScreen,
  scrollOf,
  stepPage,
  visiblePages,
  withCards,
} from '../reader/continuous';
import { type BoundaryLayout, boundaryLayout } from '../reader/boundary';
import {
  FALLBACK_SIZE,
  layoutSpread,
  PAN_STEPS,
  type Size,
  type SpreadLayout,
  spreadPosition,
  zoomLayout,
} from '../reader/layout';
import { chooseTransition, RAPID_FLIP_MS, slideFrom, transitionMs } from '../reader/transition';
import { imageFilter, lanczosWindow } from '../reader/enhance';
import { cachedLanczos, lanczosBitmap, lanczosKey } from '../image/lanczosPool';
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
/** 세로 스크롤 맨 아래 책 끝 카드·맨 위 책 첫 카드 높이 */
/** 세로 스크롤의 책 첫·끝 카드 높이 = 한 화면 (Android ContinuousPages와 같음) */
const END_CARD_HEIGHT = 1080;
const START_CARD_HEIGHT = 1080;
/** 쪽이 이만큼 안에 안 뜨면 로딩 표시 (Android와 같음) */
const SPINNER_DELAY_MS = 250;

const isPdfBook = (book: KomgaBook) => book.media.mediaProfile === 'PDF';

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

/** 진행 중인 화면 전환 (Android 0.2.9) */
interface SpreadTransition {
  id: number;
  kind: 'SLIDE' | 'FADE' | 'OFF';
  /** 밀기: 새 펼침이 들어오는 쪽 (1 오른쪽, −1 왼쪽) */
  from: 1 | -1;
  toIndex: number;
  bookId: string;
  outgoing: { pages: PageInfo[]; layout: SpreadLayout; position: { left: number; top: number } };
}

/**
 * Lanczos로 화면 크기에 맞춘 그림 (Android 1.0.1). 처리가 끝날 때까지는 null — 그동안은 보통 그림을 보인다.
 */
function useLanczos(sourceKey: string, url: string | null, width: number, height: number, a: number | null) {
  const w = Math.round(width);
  const h = Math.round(height);
  const key = a && url && w > 0 && h > 0 ? lanczosKey(sourceKey, w, h, a) : null;
  const [result, setResult] = useState<{ key: string; bitmap: ImageBitmap } | null>(null);
  useEffect(() => {
    if (!key || !url || !a) return undefined;
    const hit = cachedLanczos(key);
    if (hit) {
      setResult({ key, bitmap: hit });
      return undefined;
    }
    let alive = true;
    lanczosBitmap(key, url, w, h, a, true).then(
      (bitmap) => alive && setResult({ key, bitmap }),
      () => undefined, // 못 하면 보통 그림 그대로
    );
    return () => {
      alive = false;
    };
  }, [key]);
  return result && result.key === key ? result.bitmap : null;
}

function LanczosCanvas({ bitmap, filter }: { bitmap: ImageBitmap; filter?: string }) {
  const ref = useRef<HTMLCanvasElement>(null);
  const [drawn, setDrawn] = useState(false);
  // 그리기 전에 화면에 나오지 않게 (빈 칸이 한 번 비치지 않게) 레이아웃 단계에서 그린다
  useLayoutEffect(() => {
    const canvas = ref.current;
    const context = canvas?.getContext('2d');
    if (!canvas || !context) return;
    try {
      context.drawImage(bitmap, 0, 0);
      setDrawn(true);
    } catch {
      setDrawn(false); // 캐시에서 빠져 닫힌 그림 — 보통 그림 그대로
    }
  }, [bitmap]);
  return (
    <canvas
      ref={ref}
      class={`reader-image reader-canvas${drawn ? '' : ' pending'}`}
      width={bitmap.width}
      height={bitmap.height}
      style={filter ? { filter } : undefined}
    />
  );
}

function ReaderPage({
  bookId,
  pdf,
  page,
  style,
  width,
  height,
  lanczos,
  filter,
  onNaturalSize,
}: {
  bookId: string;
  pdf: boolean;
  page: PageInfo;
  style: Record<string, string>;
  /** 그릴 크기 (px) — Lanczos가 이 크기로 맞춘다 */
  width: number;
  height: number;
  /** Lanczos 창 크기, 아니면 null */
  lanczos: number | null;
  /** 선명하게·대비 CSS filter */
  filter?: string;
  onNaturalSize: (pageNumber: number, size: Size) => void;
}) {
  const currentSession = useStore(sessionStore);
  const key = pageKey(bookId, page.number, pdf);
  const image = useCachedImage(currentSession?.pages, key);
  const resampled = useLanczos(key, image.url, width, height, lanczos);
  // 0.25초 안에 안 뜨면 스피너 + "불러오는 중…"
  const [slow, setSlow] = useState(false);
  useEffect(() => {
    setSlow(false);
    const timer = setTimeout(() => setSlow(true), SPINNER_DELAY_MS);
    return () => clearTimeout(timer);
  }, [bookId, page.number]);
  return (
    // Preact 11은 숫자 스타일 값에 px를 붙이지 않는다 — 단위를 직접 쓴다
    <div class="reader-page" style={style}>
      {image.url ? (
        <>
          <img
            class="reader-image"
            src={image.url}
            alt=""
            style={filter ? { filter } : undefined}
            onLoad={(event) => {
              const img = event.currentTarget as HTMLImageElement;
              onNaturalSize(page.number, { width: img.naturalWidth, height: img.naturalHeight });
            }}
          />
          {resampled && <LanczosCanvas bitmap={resampled} filter={filter} />}
        </>
      ) : image.failed ? (
        <div class="reader-page-status failed">{t.readerPageLoadFailed}</div>
      ) : slow ? (
        <div class="reader-page-status">
          <div class="spinner" />
          {t.readerLoading}
        </div>
      ) : null}
    </div>
  );
}

/** 다음 권 상태: undefined = 아직 안 찾음, 'loading' = 찾는 중, null = 마지막 권 */
type NextBook = KomgaBook | null | 'loading' | undefined;

/** 책 한 권 자리 (표지 + 제목). 지금 권은 흐리게 — Android BookSlot */
function BookSlot({ label, book, dimmed }: { label: string; book: KomgaBook; dimmed: boolean }) {
  const currentSession = useStore(sessionStore);
  const cover = useCachedImage(currentSession?.thumbnails, ImagePaths.bookThumbnail(book.id));
  return (
    <div class={`end-column${dimmed ? ' dimmed' : ''}`}>
      <div class="end-label">{label}</div>
      <div class="end-cover">{cover.url && <img class="card-image" src={cover.url} alt="" />}</div>
      <div class="end-title">{bookDisplayTitle(book)}</div>
    </div>
  );
}

/** 갈 곳(다음·이전 권) 자리: 찾는 중 / 없음 / 책 */
function TargetSlot({
  label,
  target,
  finding,
  none,
}: {
  label: string;
  target: NextBook;
  finding: string;
  none: string;
}) {
  if (target && target !== 'loading') return <BookSlot label={label} book={target} dimmed={false} />;
  return (
    <div class="end-column">
      <div class="end-label">{label}</div>
      <div class="end-title muted">{target === null ? none : finding}</div>
    </div>
  );
}

/** 지금 권과 갈 권을 읽기 방향에 맞춰 놓는다 — 갈 권은 그리로 가는 키 쪽에 (Android 0.2.4 BoundaryLayout) */
function BoundaryPage({
  layout,
  current,
  destination,
  hint,
}: {
  layout: BoundaryLayout;
  current: ComponentChildren;
  destination: ComponentChildren;
  hint: string;
}) {
  return (
    <div class="end-view">
      {layout.destinationOnLeft ? destination : current}
      <div class="end-arrow">{layout.arrow}</div>
      {layout.destinationOnLeft ? current : destination}
      <div class="end-hint">{hint}</div>
    </div>
  );
}

/** 책 끝 화면: 다 읽은 책 → 다음 권 */
function EndView({ book, next, layout }: { book: KomgaBook; next: NextBook; layout: BoundaryLayout }) {
  const found = !!next && next !== 'loading';
  return (
    <BoundaryPage
      layout={layout}
      current={<BookSlot label={t.endPageFinished} book={book} dimmed />}
      destination={
        <TargetSlot label={t.endPageNext} target={next} finding={t.endPageFindingNext} none={t.endPageLastBook} />
      }
      hint={found ? fmt(t.endPageHintNext, layout.goKey) : fmt(t.endPageHintLast, layout.backKey)}
    />
  );
}

/** 책 첫 화면: 지금 권 → 이전 권 */
function StartView({ book, previous, layout }: { book: KomgaBook; previous: NextBook; layout: BoundaryLayout }) {
  const found = !!previous && previous !== 'loading';
  return (
    <BoundaryPage
      layout={layout}
      current={<BookSlot label={t.startPageCurrent} book={book} dimmed />}
      destination={
        <TargetSlot
          label={t.startPagePrevious}
          target={previous}
          finding={t.startPageFindingPrevious}
          none={t.startPageFirstBook}
        />
      }
      hint={found ? fmt(t.startPageHintPrevious, layout.goKey) : fmt(t.startPageHintFirst, layout.backKey)}
    />
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
  /** 확대한 화면·높이 맞춤에서 ▲▼로 본 위치 (−PAN_STEPS 위 끝 … +PAN_STEPS 아래 끝) */
  const [panStep, setPanStep] = useState(0);
  const [naturalSizes, setNaturalSizes] = useState<Record<number, Size>>({});
  /** 페이지 넘김의 책 끝 화면 */
  const [atEnd, setAtEnd] = useState(false);
  const [nextBook, setNextBook] = useState<NextBook>(undefined);
  /** 페이지 넘김의 책 첫 화면 */
  const [atStart, setAtStart] = useState(false);
  const [previousBook, setPreviousBook] = useState<NextBook>(undefined);
  const saver = useRef<ProgressSaver | null>(null);
  const moved = useRef(false);
  /** 마지막으로 펼침을 넘긴 때 (빠른 연타면 효과 없이) */
  const lastFlip = useRef(0);
  /** 책이 바뀔 때마다 +1 — 늦게 도착한 옛 책의 다음·이전 권 찾기 결과를 버린다 (Android: bookId 비교) */
  const generation = useRef(0);
  /** 다음·이전 권으로 바꾸는 중: 새 책이 뜰 때까지 넘김 키를 무시한다 (두 번 열기·옛 책 진행 되감기 방지) */
  const switching = useRef(false);
  /** 이전 권을 세로 스크롤로 열 때: 책 끝이 화면 아래에 오게 (Android bottomScreenTileIndex) */
  const openAtEnd = useRef(false);
  /** 지금 진행 중인 화면 전환: 나가는 펼침 그대로와 들어오는 펼침 번호 */
  const [transition, setTransition] = useState<SpreadTransition | null>(null);
  const exiting = useRef(false);
  const root = useRef<HTMLDivElement>(null);

  /** 책을 읽는다. 다음 권으로 넘어갈 때는 지금 화면을 둔 채 바꾼다(로딩 화면 없이). */
  const load = (id: string, startAt: 'progress' | 'last' = 'progress') => {
    setError(null);
    generation.current++;
    loadBook(id).then(
      (loaded) => {
        switching.current = false;
        setBookId(id);
        setOffset(false);
        setAtEnd(false);
        setNextBook(undefined);
        setAtStart(false);
        setPreviousBook(undefined);
        setNaturalSizes({});
        // 정보 표시는 그대로 둔다 — 정보를 열어 둔 채 넘겨 다음 권으로 가면 새 책 정보가 보인다 (Android와 같음)
        moved.current = false;
        if (loaded.epub) {
          saver.current = null;
          setData(loaded);
          return;
        }
        const progress = loaded.book.readProgress;
        // 이전 권으로 넘어가면 그 책의 마지막 쪽에서
        const first = startAt === 'last' ? Math.max(1, loaded.pages.length) : startPage(progress);
        openAtEnd.current = startAt === 'last';
        setSeriesSettings(loadSeriesSettings(loaded.book.seriesId));
        setAnchorPage(first);
        setAnchor({ index: Math.min(loaded.pages.length - 1, first - 1), offset: 0 });
        saver.current = new ProgressSaver(
          (page, completed) => trackWrite(requireSession().client.markReadProgress(id, page, completed)),
          progress ? { page: progress.page, completed: progress.completed } : null,
        );
        setData(loaded);
      },
      (failure: unknown) => {
        switching.current = false;
        setError(toLoadError(failure));
      },
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
  const pagedLayout = layoutSpread(spread.map(sizeOf), settings.scaleType, SCREEN);
  // 확대(Android 0.2.5): 크기 맞춤 결과를 펼침째 키운다
  const zoomedLayout = zoomLayout(pagedLayout, settings.zoomPercent);
  /** 확대 전에도 화면 높이 안이면 가운데 기준 + ▲▼ 위아래 보기, 넘치면 위에서부터 스크롤 */
  const pagedFixed = pagedLayout.height <= SCREEN.height;
  const pagedMaxScroll = Math.max(0, zoomedLayout.height - SCREEN.height);

  // ---------- 세로 스크롤 (맨 아래 책 끝 카드 자리까지) ----------
  const continuous: ContinuousLayout = useMemo(() => {
    const layout = layoutContinuous(
      pages.map(sizeOf),
      SCREEN,
      settings.continuousPadding,
      settings.continuousPageSpacing,
    );
    return withCards(layout, START_CARD_HEIGHT, END_CARD_HEIGHT);
  }, [data, naturalSizes, settings.continuousPadding, settings.continuousPageSpacing]);
  const continuousScroll = scrollOf(continuous, anchor, SCREEN.height);
  const visible = visiblePages(continuous, continuousScroll, SCREEN.height);
  const continuousAtBottom = continuousScroll >= continuous.total - SCREEN.height - 1;
  const continuousAtTop = continuousScroll <= 0;
  useEffect(() => {
    if (!openAtEnd.current || !data || data.epub) return;
    openAtEnd.current = false;
    if (isContinuous) setAnchor(anchorOf(continuous, Math.max(0, continuous.total - END_CARD_HEIGHT - SCREEN.height)));
  }, [data]);
  const isPdf = !!data && isPdfBook(data.book);
  const keyOf = (page: number) => pageKey(bookId, page, isPdf);

  const isContinuous = readerType === 'CONTINUOUS';
  /** 화질 개선 Lanczos 창 크기와 쪽 그림 filter (선명하게·대비) */
  const lanczosA = lanczosWindow(settings.imageEnhancement);
  const pageFilter = imageFilter(settings.imageEnhancement, settings.contrastPercent);
  /** 정보 표시·진행 저장에 쓰는 "보이는 쪽 중 가장 큰 번호" */
  const shownHighest = isContinuous ? pages[visible.last]?.number ?? 1 : highestPage(spread);

  // 펼침이 바뀌면 맨 위부터
  useEffect(() => {
    setScrollY(0);
    setPanStep(0);
  }, [index, spreads]);
  useEffect(() => setPanStep(0), [settings.zoomPercent]);

  // 미리 받기 (Android): 앞 4·뒤 2 펼침(세로 스크롤은 쪽), 가까운 것부터. 이웃이 아니게 된 미리 받기는 취소
  const neighbourKeys = (): string[] => {
    if (!data || data.epub) return [];
    const order = [1, -1, 2, -2, 3, 4];
    const near: PageInfo[] = isContinuous
      ? order.map((d) => pages[(d > 0 ? visible.last : visible.first) + d]).filter((page): page is PageInfo => !!page)
      : order.reduce<PageInfo[]>((all, d) => all.concat(spreads[index + d] ?? []), []);
    const keys = near.map((page) => keyOf(page.number));
    // 이웃 권의 첫·마지막 쪽 (미리 받은 것을 취소하지 않게)
    if (nextBook && nextBook !== 'loading' && nextBook.media.mediaProfile !== 'EPUB') {
      [1, 2].forEach((n) => keys.push(pageKey(nextBook.id, n, isPdfBook(nextBook))));
    }
    if (previousBook && previousBook !== 'loading' && previousBook.media.mediaProfile !== 'EPUB') {
      const last = previousBook.media.pagesCount;
      [last, last - 1].filter((n) => n >= 1).forEach((n) => keys.push(pageKey(previousBook.id, n, isPdfBook(previousBook))));
    }
    return keys;
  };
  useEffect(() => {
    const cache = sessionStore.get()?.pages;
    if (!cache || !data || data.epub) return;
    const keys = neighbourKeys();
    cache.retainPrefetches(keys);
    keys.forEach((key) => cache.prefetch(key));
  }, [isContinuous, index, spreads, visible.first, visible.last, bookId, nextBook, previousBook]);

  // Lanczos 미리 처리 (Android: 미리 받는 쪽에서 미리 처리): 앞·뒤 펼침을 그릴 크기로 — 넘기면 바로 또렷하게
  useEffect(() => {
    const cache = sessionStore.get()?.pages;
    if (!lanczosA || !cache || !data || data.epub || isContinuous) return undefined;
    let alive = true;
    [1, -1].forEach((d) => {
      const neighbour = spreads[index + d];
      if (!neighbour) return;
      const layout = zoomLayout(layoutSpread(neighbour.map(sizeOf), settings.scaleType, SCREEN), settings.zoomPercent);
      neighbour.forEach((page, i) => {
        const key = keyOf(page.number);
        const size = layout.pages[i];
        if (!size || cachedLanczos(lanczosKey(key, size.width, size.height, lanczosA))) return;
        cache.acquire(key).then(
          (url) => {
            const done = () => cache.release(key);
            if (!alive) return done();
            lanczosBitmap(lanczosKey(key, size.width, size.height, lanczosA), url, size.width, size.height, lanczosA, false).then(
              done,
              done,
            );
          },
          () => undefined,
        );
      });
    });
    return () => {
      alive = false;
    };
  }, [lanczosA, isContinuous, index, spreads, bookId, settings.scaleType, settings.zoomPercent]);

  // 끝이 가까우면 다음 권을 찾고 첫 쪽들을 미리 받는다
  const nearEnd = isContinuous ? visible.last >= totalPages - 1 - NEXT_BOOK_LOOKAHEAD : index >= spreads.length - 1 - NEXT_BOOK_LOOKAHEAD;
  useEffect(() => {
    if (!data || data.epub || !nearEnd || nextBook !== undefined) return;
    setNextBook('loading');
    const gen = generation.current;
    const { client } = requireSession();
    client.getNextBook(data.book.id).then(
      (next) => {
        if (gen !== generation.current) return; // 그새 다른 책으로 바뀌었다
        setNextBook(next);
        if (!next) return;
        if (next.media.mediaProfile !== 'EPUB') {
          [1, 2].forEach((n) => sessionStore.get()?.pages.prefetch(pageKey(next.id, n, isPdfBook(next))));
        }
        sessionStore.get()?.thumbnails.prefetch(ImagePaths.bookThumbnail(next.id));
      },
      () => gen === generation.current && setNextBook(null),
    );
  }, [nearEnd, data]);

  // 처음이 가까우면 이전 권을 찾고 마지막 두 쪽을 미리 받는다 — 8권을 열자마자 7권으로
  const nearStart = isContinuous ? visible.first <= NEXT_BOOK_LOOKAHEAD : index <= NEXT_BOOK_LOOKAHEAD;
  useEffect(() => {
    if (!data || data.epub || !nearStart || previousBook !== undefined) return;
    setPreviousBook('loading');
    const gen = generation.current;
    requireSession()
      .client.getPreviousBook(data.book.id)
      .then(
        (previous) => {
          if (gen !== generation.current) return; // 그새 다른 책으로 바뀌었다
          setPreviousBook(previous);
          if (!previous) return;
          if (previous.media.mediaProfile !== 'EPUB') {
            const last = previous.media.pagesCount;
            [last, last - 1]
              .filter((n) => n >= 1)
              .forEach((n) => sessionStore.get()?.pages.prefetch(pageKey(previous.id, n, isPdfBook(previous))));
          }
          sessionStore.get()?.thumbnails.prefetch(ImagePaths.bookThumbnail(previous.id));
        },
        () => gen === generation.current && setPreviousBook(null),
      );
  }, [nearStart, data]);

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
    if (!nextBook || nextBook === 'loading' || switching.current) return;
    switching.current = true;
    const target = nextBook;
    const done = saver.current?.flush() ?? Promise.resolve();
    void done.then(() => load(target.id));
  };

  /** 이전 권 마지막 쪽으로 (지금 책 진행은 그대로) */
  const openPreviousBook = () => {
    if (!previousBook || previousBook === 'loading' || switching.current) return;
    switching.current = true;
    const target = previousBook;
    const done = saver.current?.flush() ?? Promise.resolve();
    void done.then(() => load(target.id, 'last'));
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
    if (!data || target === index) return;
    if (target < 0) {
      setAtStart(true); // 첫 펼침 앞 = 책 첫 화면
      return;
    }
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
    setAtStart(false);
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
      if (sign < 0 && continuousAtTop) {
        openPreviousBook(); // 맨 위 책 첫 카드에서 위 = 이전 권 끝
        return;
      }
      goToAnchor(anchorOf(continuous, scrollByScreen(continuous, continuousScroll, SCREEN.height, sign)));
    } else if (pagedFixed) {
      if (pagedMaxScroll > 0) setPanStep((step) => Math.min(PAN_STEPS, Math.max(-PAN_STEPS, step + sign)));
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
      if (sign < 0 && continuousAtTop) {
        openPreviousBook();
        return;
      }
      // 1쪽 맨 위에서 이전 = 책 첫 카드
      if (sign < 0 && anchor.index === 0 && anchor.offset <= 0) {
        goToAnchor({ index: 0, offset: -START_CARD_HEIGHT });
        return;
      }
      // 책 첫 카드에서 다음 = 1쪽 맨 위, 마지막 쪽에서 다음 = 맨 아래 책 끝 카드 (한 번 더 = 다음 권) — Android와 같음
      if (sign > 0 && anchor.index === 0 && anchor.offset < 0) {
        goToAnchor({ index: 0, offset: 0 });
        return;
      }
      if (sign > 0 && anchor.index >= totalPages - 1) {
        goToAnchor(anchorOf(continuous, maxScroll(continuous, SCREEN.height)));
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
    if (atStart) {
      if (forward < 0) openPreviousBook();
      else setAtStart(false); // 다음 쪽 키 = 첫 쪽으로
      return;
    }
    flip(forward > 0 ? 1 : -1);
  };

  /** 한 칸 앞·뒤 펼침으로 — 화면 전환 효과는 여기서만 (Android AnimatedSpread) */
  const flip = (step: 1 | -1) => {
    const target = index + step;
    const now = Date.now();
    const rapid = now - lastFlip.current < RAPID_FLIP_MS;
    lastFlip.current = now;
    const incoming = spreads[target];
    const cache = sessionStore.get()?.pages;
    const ready = !!incoming && !!cache && incoming.every((page) => cache.has(keyOf(page.number)));
    const kind = chooseTransition(settings.pageTransition, step, rapid, ready);
    if (kind === 'OFF' || !data) {
      setTransition(null);
    } else {
      setTransition({
        id: now,
        kind,
        from: slideFrom(step, direction === 'RIGHT_TO_LEFT'),
        toIndex: target,
        bookId,
        outgoing: {
          pages: spread,
          layout: zoomedLayout,
          position: spreadPosition(pagedLayout, zoomedLayout, SCREEN, panStep, scrollY),
        },
      });
    }
    goToSpread(target);
  };

  // 효과가 끝나면 나가는 펼침을 뗀다
  useEffect(() => {
    if (!transition) return undefined;
    const timer = setTimeout(() => setTransition(null), transitionMs(transition.kind) + 40);
    return () => clearTimeout(timer);
  }, [transition]);

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
    // 다음·이전 권을 여는 중: 새 책이 뜰 때까지 넘김·OK는 무시 (Back은 나가기)
    if (switching.current && key !== 'back') return true;
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
    if (atStart && !overlay) {
      // 책 첫 화면: 이전 쪽 키 / OK = 이전 권 마지막 쪽, 다음 쪽 키 = 첫 쪽, Back = 나가기
      switch (key) {
        case 'left':
          horizontal(-1);
          break;
        case 'right':
          horizontal(1);
          break;
        case 'enter':
          openPreviousBook();
          break;
        case 'back':
          exit();
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
        else if (isContinuous && continuousAtBottom) openNextBook(); // 맨 아래 책 끝 카드
        else if (isContinuous && continuousAtTop) openPreviousBook(); // 맨 위 책 첫 카드
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
  const rtlReading = direction === 'RIGHT_TO_LEFT';

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
              pdf={isPdf}
              page={pages[i]}
              width={placement.width}
              height={placement.height}
              lanczos={lanczosA}
              filter={pageFilter}
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
        {from < START_CARD_HEIGHT && (
          <div class="end-card" style={{ top: '0px', height: px(START_CARD_HEIGHT) }}>
            <StartView book={data.book} previous={previousBook} layout={boundaryLayout(false, rtlReading, true)} />
          </div>
        )}
        {endTop < to && (
          <div class="end-card" style={{ top: px(endTop), height: px(END_CARD_HEIGHT) }}>
            <EndView book={data.book} next={nextBook} layout={boundaryLayout(true, rtlReading, true)} />
          </div>
        )}
      </div>
    );
  } else if (atEnd) {
    content = <EndView book={data.book} next={nextBook} layout={boundaryLayout(true, rtlReading, false)} />;
  } else if (atStart) {
    content = <StartView book={data.book} previous={previousBook} layout={boundaryLayout(false, rtlReading, false)} />;
  } else {
    const rtl = direction === 'RIGHT_TO_LEFT';
    const renderSpread = (shown: PageInfo[], layout: SpreadLayout, position: { left: number; top: number }) => {
      const shownPages = rtl ? shown.slice().reverse() : shown;
      const shownSizes = rtl ? layout.pages.slice().reverse() : layout.pages;
      return (
        <div class="reader-spread" style={{ transform: `translate(${px(position.left)}, ${px(position.top)})` }}>
          {shownPages.map((page, i) => (
            <ReaderPage
              key={`${bookId}-${page.number}`}
              bookId={bookId}
              pdf={isPdf}
              page={page}
              width={shownSizes[i]?.width ?? 0}
              height={shownSizes[i]?.height ?? 0}
              lanczos={lanczosA}
              filter={pageFilter}
              style={{ width: px(shownSizes[i]?.width ?? 0), height: px(shownSizes[i]?.height ?? 0) }}
              onNaturalSize={onNaturalSize}
            />
          ))}
        </div>
      );
    };
    // 화면 전환 중이면 나가는 펼침도 함께 그린다. 펼침마다 key가 같아 효과가 끝나도 다시 만들지 않는다
    const shownTransition = transition && transition.toIndex === index && transition.bookId === bookId ? transition : null;
    const layerKey = (shown: PageInfo[]) => `spread-${bookId}-${shown[0]?.number ?? 0}`;
    const animation = (entering: boolean): string => {
      if (!shownTransition) return '';
      if (shownTransition.kind === 'FADE') return entering ? ' fade-in' : ' fade-out';
      const side = entering ? shownTransition.from : -shownTransition.from;
      return `${entering ? ' slide-in-' : ' slide-out-'}${side > 0 ? 'right' : 'left'}`;
    };
    // 한 요소로 감싼다 — Fragment면 리더 종류를 바꿀 때 뒤의 패널까지 다시 만들어져 포커스를 잃는다
    content = (
      <div class="reader-paged">
        {shownTransition && (
          <div key={layerKey(shownTransition.outgoing.pages)} class={`reader-layer${animation(false)}`}>
            {renderSpread(shownTransition.outgoing.pages, shownTransition.outgoing.layout, shownTransition.outgoing.position)}
          </div>
        )}
        <div key={layerKey(spread)} class={`reader-layer${animation(true)}`}>
          {renderSpread(spread, zoomedLayout, spreadPosition(pagedLayout, zoomedLayout, SCREEN, panStep, scrollY))}
        </div>
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
            {/* 오른쪽→왼쪽이면 진행 막대도 오른쪽에서 찬다 (Android 0.2.4) */}
            <ProgressBar value={shownHighest / Math.max(1, totalPages)} rtl={rtlReading && !isContinuous} />
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
