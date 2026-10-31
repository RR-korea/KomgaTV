// 리더 (Android T4·T5·T6과 같은 규칙과 옵션, PLAN Z5). 두 가지 종류:
// - 페이지 넘김(PAGED): 좌/우 = 이전/다음 펼침(RTL이면 반대), 위/아래 = 화면보다 큰 쪽을 반 화면씩 스크롤
// - 세로 스크롤(CONTINUOUS, 웹툰): 위/아래 = 한 화면씩, 좌/우 = 이전/다음 쪽 맨 위
// 공통: OK = 정보 → 한 번 더 OK = 보기 설정, 정보가 열려 있으면 위/아래 = 10쪽 이동, 채널 위/아래 = 10쪽 이동,
//       Back = 패널/정보 닫기 → 진행 저장 후 나가기.
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
  DIRECTIONS,
  type Direction,
  PADDING_STEPS,
  PAGE_LAYOUTS,
  READER_TYPES,
  type ReaderType,
  readerPrefs,
  resolveReaderType,
  saveReaderPrefs,
  SCALE_TYPES,
  SPACING_STEPS,
  startPage,
} from '../state/readerPrefs';
import { type LoadError, requireSession, session as sessionStore, toLoadError, trackWrite } from '../state/session';
import { useStore } from '../state/store';
import { Button, ErrorView, LoadingView, ProgressBar, useCachedImage } from '../ui/components';

const SCREEN: Size = { width: 1920, height: 1080 };
const JUMP_PAGES = 10;

interface BookData {
  book: KomgaBook;
  pages: PageInfo[];
  seriesDirection: ReadingDirection | null;
}

async function loadBook(bookId: string): Promise<BookData> {
  const { client } = requireSession();
  const book = await client.getBook(bookId);
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

interface PanelOption {
  key: string;
  label: string;
  values: readonly unknown[];
  labels: string[];
  value: unknown;
  enabled: boolean;
  set: (value: unknown) => void;
}

const px = (value: number) => `${Math.round(value)}px`;

export function ReaderScreen({ entry, active }: { entry: RouteEntry; active: boolean }) {
  const route = entry.route as Extract<RouteEntry['route'], { name: 'reader' }>;
  const prefs = useStore(readerPrefs);
  const [data, setData] = useState<BookData | null>(null);
  const [error, setError] = useState<LoadError | null>(null);
  const [readerType, setReaderType] = useState<ReaderType>(prefs.readerType);
  const [direction, setDirection] = useState<Direction>(prefs.readingDirection);
  const [offset, setOffset] = useState(false); // 쪽 맞춤 밀기 — 이 세션 동안만
  /** 페이지 넘김: 지금 펼침의 기준 쪽. 배치가 바뀌어도 이 쪽이 든 펼침을 보여 준다 */
  const [anchorPage, setAnchorPage] = useState(1);
  /** 세로 스크롤: 화면 맨 위가 걸친 쪽(0부터)과 그 안의 거리 */
  const [anchor, setAnchor] = useState<Anchor>({ index: 0, offset: 0 });
  const [overlay, setOverlay] = useState(false);
  const [panel, setPanel] = useState(false);
  const [scrollY, setScrollY] = useState(0);
  const [naturalSizes, setNaturalSizes] = useState<Record<number, Size>>({});
  const saver = useRef<ProgressSaver | null>(null);
  const moved = useRef(false);
  const exiting = useRef(false);
  const root = useRef<HTMLDivElement>(null);

  const load = () => {
    setError(null);
    loadBook(route.bookId).then(
      (loaded) => {
        const progress = loaded.book.readProgress;
        const saved = readerPrefs.get();
        const dir = loaded.seriesDirection;
        setReaderType(resolveReaderType(dir, saved.readerType));
        setDirection(dir === 'LEFT_TO_RIGHT' || dir === 'RIGHT_TO_LEFT' ? dir : saved.readingDirection);
        const first = startPage(progress);
        setAnchorPage(first);
        setAnchor({ index: Math.min(loaded.pages.length - 1, first - 1), offset: 0 });
        saver.current = new ProgressSaver(
          (page, completed) => trackWrite(requireSession().client.markReadProgress(route.bookId, page, completed)),
          progress ? { page: progress.page, completed: progress.completed } : null,
        );
        setData(loaded);
      },
      (failure: unknown) => setError(toLoadError(failure)),
    );
  };

  useEffect(load, []);
  // 화면이 어떤 식으로 사라지든 기다리던 진행은 보낸다
  useEffect(() => () => void saver.current?.flush(), []);

  const pages = data?.pages ?? [];
  const totalPages = pages.length;
  const sizeOf = (page: PageInfo): Size =>
    naturalSizes[page.number] ?? (page.width && page.height ? { width: page.width, height: page.height } : FALLBACK_SIZE);

  // ---------- 페이지 넘김 ----------
  const spreads = useMemo(() => buildSpreads(pages, prefs.pageLayout, offset), [data, prefs.pageLayout, offset]);
  const index = spreadIndexOf(spreads, anchorPage);
  const spread = spreads[index] ?? [];
  const pagedLayout = layoutSpread(spread.map(sizeOf), prefs.scaleType, prefs.stretchToFit, SCREEN);
  const pagedMaxScroll = Math.max(0, pagedLayout.height - SCREEN.height);

  // ---------- 세로 스크롤 ----------
  const continuous = useMemo(
    () =>
      layoutContinuous(pages.map(sizeOf), SCREEN, prefs.continuousPadding, prefs.continuousPageSpacing, prefs.stretchToFit),
    [data, naturalSizes, prefs.continuousPadding, prefs.continuousPageSpacing, prefs.stretchToFit],
  );
  const continuousScroll = scrollOf(continuous, anchor, SCREEN.height);
  const visible = visiblePages(continuous, continuousScroll, SCREEN.height);

  const isContinuous = readerType === 'CONTINUOUS';
  /** 정보 표시·진행 저장에 쓰는 "보이는 쪽 중 가장 큰 번호" */
  const shownHighest = isContinuous ? pages[visible.last]?.number ?? 1 : highestPage(spread);

  // 펼침이 바뀌면 맨 위부터
  useEffect(() => setScrollY(0), [index, spreads]);

  // 프리로드: 페이지 넘김은 앞뒤 2펼침, 세로 스크롤은 화면 아래 다음 2쪽
  useEffect(() => {
    const cache = sessionStore.get()?.pages;
    if (!cache || !data) return;
    const near: PageInfo[] = isContinuous
      ? pages.slice(visible.last + 1, visible.last + 3)
      : [index + 1, index - 1, index + 2, index - 2].reduce<PageInfo[]>((all, i) => all.concat(spreads[i] ?? []), []);
    near.forEach((page) => cache.prefetch(ImagePaths.bookPage(route.bookId, page.number)));
  }, [isContinuous, index, spreads, visible.last]);

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

  // 종류를 바꿀 때 보던 쪽을 이어 간다
  const switchType = (next: ReaderType) => {
    if (next === readerType) return;
    if (next === 'CONTINUOUS') setAnchor({ index: Math.max(0, (spread[0]?.number ?? 1) - 1), offset: 0 });
    else setAnchorPage(pages[visible.first]?.number ?? 1);
    setReaderType(next);
    saveReaderPrefs({ readerType: next }); // 지금 책에 적용하고 기본값으로 저장 (Komelia와 같음)
  };

  const goToSpread = (target: number) => {
    if (!data || target < 0 || target >= spreads.length || target === index) return;
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
      goToAnchor(anchorOf(continuous, scrollByScreen(continuous, continuousScroll, SCREEN.height, sign)));
    } else {
      setScrollY((y) => Math.min(pagedMaxScroll, Math.max(0, y + (sign * SCREEN.height) / 2)));
    }
  };

  const horizontal = (sign: 1 | -1) => {
    if (isContinuous) {
      goToAnchor(stepPage(anchor, totalPages, sign));
      return;
    }
    // 오른쪽 키 = +1. RTL이면 반대
    const forward = direction === 'RIGHT_TO_LEFT' ? -sign : sign;
    goToSpread(index + forward);
  };

  const exit = () => {
    if (exiting.current) return;
    exiting.current = true;
    void saver.current?.flush();
    pop();
  };

  const typeOption: PanelOption = {
    key: 'opt-type',
    label: t.readerType,
    values: READER_TYPES,
    labels: [t.readerTypePaged, t.readerTypeContinuous],
    value: readerType,
    enabled: true,
    set: (value) => switchType(value as ReaderType),
  };
  const stretchOption: PanelOption = {
    key: 'opt-stretch',
    label: t.readerStretch,
    values: [false, true],
    labels: [t.off, t.on],
    value: prefs.stretchToFit,
    enabled: true,
    set: (value) => saveReaderPrefs({ stretchToFit: value as boolean }),
  };
  const options: PanelOption[] = isContinuous
    ? [
        typeOption,
        {
          key: 'opt-padding',
          label: t.readerPadding,
          values: PADDING_STEPS,
          labels: PADDING_STEPS.map((p) => `${Math.round(p * 100)}%`),
          value: prefs.continuousPadding,
          enabled: true,
          set: (value) => saveReaderPrefs({ continuousPadding: value as number }),
        },
        {
          key: 'opt-spacing',
          label: t.readerSpacing,
          values: SPACING_STEPS,
          labels: SPACING_STEPS.map((s) => `${s}dp`),
          value: prefs.continuousPageSpacing,
          enabled: true,
          set: (value) => saveReaderPrefs({ continuousPageSpacing: value as number }),
        },
        stretchOption,
      ]
    : [
        typeOption,
        {
          key: 'opt-layout',
          label: t.readerLayout,
          values: PAGE_LAYOUTS,
          labels: [t.readerLayoutSingle, t.readerLayoutDouble, t.readerLayoutDoubleNoCover],
          value: prefs.pageLayout,
          enabled: true,
          set: (value) => saveReaderPrefs({ pageLayout: value as typeof prefs.pageLayout }),
        },
        {
          key: 'opt-offset',
          label: t.readerOffset,
          values: [false, true],
          labels: [t.off, t.on],
          value: offset,
          enabled: prefs.pageLayout !== 'SINGLE_PAGE',
          set: (value) => setOffset(value as boolean),
        },
        {
          key: 'opt-direction',
          label: t.readerDirection,
          values: DIRECTIONS,
          labels: [t.readerDirectionLtr, t.readerDirectionRtl],
          value: direction,
          enabled: true,
          // 지금 책에 바로 적용하고 기본값으로도 저장 (Komelia·Android와 같음)
          set: (value) => {
            setDirection(value as Direction);
            saveReaderPrefs({ readingDirection: value as Direction });
          },
        },
        {
          key: 'opt-scale',
          label: t.readerScale,
          values: SCALE_TYPES,
          labels: [t.readerScaleScreen, t.readerScaleFitWidth, t.readerScaleFitHeight, t.readerScaleOriginal],
          value: prefs.scaleType,
          enabled: true,
          set: (value) => saveReaderPrefs({ scaleType: value as typeof prefs.scaleType }),
        },
        stretchOption,
      ];

  const cycle = (key: string | undefined, delta: number) => {
    const option = options.find((candidate) => candidate.key === key);
    if (!option || !option.enabled) return;
    const at = option.values.indexOf(option.value);
    option.set(option.values[(at + delta + option.values.length) % option.values.length]);
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
          } else {
            cycle(focusedKey, key === 'left' ? -1 : 1);
          }
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

  useKeys(onKey, active);

  if (error) {
    return (
      <div ref={root} class="reader">
        <ErrorView error={error} onRetry={load} />
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
    content = (
      <div
        class="reader-column"
        style={{ height: px(continuous.total), transform: `translateY(${px(-continuousScroll)})` }}
      >
        {continuous.pages.map((placement, i) =>
          placement.top + placement.height < from || placement.top > to ? null : (
            <ReaderPage
              key={pages[i].number}
              bookId={route.bookId}
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
      </div>
    );
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
            key={page.number}
            bookId={route.bookId}
            page={page}
            style={{ width: px(shownSizes[i].width), height: px(shownSizes[i].height) }}
            onNaturalSize={onNaturalSize}
          />
        ))}
      </div>
    );
  }

  return (
    <div ref={root} class="reader">
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
                {!isContinuous && (spread.length > 1 || prefs.pageLayout !== 'SINGLE_PAGE')
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
            {options.map((option) => (
              <div
                key={option.key}
                class={`focusable panel-option${option.enabled ? '' : ' disabled'}`}
                tabIndex={-1}
                data-key={option.key}
                onClick={() => cycle(option.key, 1)}
              >
                <div class="panel-option-label">{option.label}</div>
                <div class="panel-option-value">
                  <span>{option.labels[option.values.indexOf(option.value)] ?? ''}</span>
                  {option.enabled && <span class="panel-option-arrows">‹ ›</span>}
                </div>
              </div>
            ))}
            <Button focusKey="panel-close" class="panel-close" onClick={() => setPanel(false)}>
              {t.close}
            </Button>
          </div>
        </div>
      )}
    </div>
  );
}
