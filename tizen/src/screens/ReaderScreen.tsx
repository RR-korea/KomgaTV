// 페이지 리더 (Android T4·T5와 같은 규칙과 옵션, PLAN Z5).
// 좌/우 = 이전/다음 펼침(RTL이면 반대), OK = 정보 → 한 번 더 OK = 보기 설정, 위/아래 = 정보가 열려 있으면 10쪽 이동,
// 닫혀 있으면 화면보다 큰 쪽을 반 화면씩 스크롤. Back = 패널/정보 닫기 → 진행 저장 후 나가기.
import { useEffect, useMemo, useRef, useState } from 'preact/hooks';
import type { KomgaBook } from '../api/types';
import { ImagePaths } from '../api/url';
import { fmt, t } from '../i18n';
import { current, focusKeyOrFirst, move } from '../platform/focus';
import { useKeys } from '../platform/input';
import type { AppKey } from '../platform/keys';
import { pop, type RouteEntry } from '../platform/router';
import { FALLBACK_SIZE, layoutSpread, type Size } from '../reader/layout';
import { ProgressSaver } from '../reader/progress';
import { buildSpreads, highestPage, type PageInfo, spreadContains, spreadIndexOf } from '../reader/spreads';
import {
  type Direction,
  type PageLayout,
  readerPrefs,
  type ReaderSettings,
  saveReaderPrefs,
  type ScaleType,
} from '../state/readerPrefs';
import { type LoadError, requireSession, session as sessionStore, toLoadError, trackWrite } from '../state/session';
import { useStore } from '../state/store';
import { Button, ErrorView, LoadingView, ProgressBar, useCachedImage } from '../ui/components';

const SCREEN: Size = { width: 1920, height: 1080 };
const JUMP_PAGES = 10;

interface BookData {
  book: KomgaBook;
  pages: PageInfo[];
  /** 시리즈 메타데이터의 방향 (LTR/RTL만), 없으면 null */
  seriesDirection: Direction | null;
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
  const direction = series.metadata.readingDirection;
  return {
    book,
    pages: pageInfos,
    seriesDirection: direction === 'LEFT_TO_RIGHT' || direction === 'RIGHT_TO_LEFT' ? direction : null,
  };
}

function ReaderPage({
  bookId,
  page,
  size,
  onNaturalSize,
}: {
  bookId: string;
  page: PageInfo;
  size: Size;
  onNaturalSize: (pageNumber: number, size: Size) => void;
}) {
  const currentSession = useStore(sessionStore);
  const image = useCachedImage(currentSession?.pages, ImagePaths.bookPage(bookId, page.number));
  return (
    <div class="reader-page" style={{ width: size.width, height: size.height }}>
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

interface PanelOption<T> {
  key: string;
  label: string;
  values: T[];
  labels: string[];
  value: T;
  enabled: boolean;
  set: (value: T) => void;
}

function optionText<T>(option: PanelOption<T>): string {
  return option.labels[option.values.indexOf(option.value)] ?? '';
}

export function ReaderScreen({ entry, active }: { entry: RouteEntry; active: boolean }) {
  const route = entry.route as Extract<RouteEntry['route'], { name: 'reader' }>;
  const prefs = useStore(readerPrefs);
  const [data, setData] = useState<BookData | null>(null);
  const [error, setError] = useState<LoadError | null>(null);
  const [direction, setDirection] = useState<Direction>(prefs.readingDirection);
  const [offset, setOffset] = useState(false); // 쪽 맞춤 밀기 — 이 세션 동안만
  /** 지금 펼침의 기준 쪽. 배치가 바뀌어도 이 쪽이 든 펼침을 보여 준다 */
  const [anchorPage, setAnchorPage] = useState(1);
  const [overlay, setOverlay] = useState(false);
  const [panel, setPanel] = useState(false);
  const [scrollY, setScrollY] = useState(0);
  const [naturalSizes, setNaturalSizes] = useState<Record<number, Size>>({});
  const saver = useRef<ProgressSaver | null>(null);
  const exiting = useRef(false);
  const root = useRef<HTMLDivElement>(null);

  const load = () => {
    setError(null);
    loadBook(route.bookId).then(
      (loaded) => {
        const progress = loaded.book.readProgress;
        setDirection(loaded.seriesDirection ?? readerPrefs.get().readingDirection);
        setAnchorPage(progress?.page ?? 1);
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

  const spreads = useMemo(
    () => (data ? buildSpreads(data.pages, prefs.pageLayout, offset) : []),
    [data, prefs.pageLayout, offset],
  );
  const index = spreadIndexOf(spreads, anchorPage);
  const spread = spreads[index] ?? [];
  const totalPages = data?.pages.length ?? 0;

  const sizes = spread.map(
    (page) =>
      naturalSizes[page.number] ??
      (page.width && page.height ? { width: page.width, height: page.height } : FALLBACK_SIZE),
  );
  const layout = layoutSpread(sizes, prefs.scaleType, prefs.stretchToFit, SCREEN);
  const maxScroll = Math.max(0, layout.height - SCREEN.height);

  // 펼침이 바뀌면 맨 위부터
  useEffect(() => setScrollY(0), [index, spreads]);

  // 앞뒤 2펼침 프리로드
  useEffect(() => {
    const cache = sessionStore.get()?.pages;
    if (!cache || !data) return;
    for (const near of [index + 1, index - 1, index + 2, index - 2]) {
      spreads[near]?.forEach((page) => cache.prefetch(ImagePaths.bookPage(route.bookId, page.number)));
    }
  }, [index, spreads]);

  // 정보·패널 포커스
  useEffect(() => {
    const element = root.current;
    if (!element || !active) return;
    if (panel) focusKeyOrFirst(element.querySelector('.reader-panel') ?? element, 'opt-layout');
    else if (overlay) focusKeyOrFirst(element, 'reader-settings');
    else (document.activeElement as HTMLElement | null)?.blur();
  }, [overlay, panel, active]);

  const goTo = (target: number) => {
    if (!data || target < 0 || target >= spreads.length || target === index) return;
    const next = spreads[target];
    setAnchorPage(next[0].number);
    saver.current?.schedule(highestPage(next), spreadContains(next, totalPages));
  };

  const jump = (delta: number) => {
    if (!spread.length) return;
    const targetPage = Math.min(totalPages, Math.max(1, spread[0].number + delta));
    goTo(spreadIndexOf(spreads, targetPage));
  };

  const scrollBy = (sign: number) => {
    setScrollY((y) => Math.min(maxScroll, Math.max(0, y + (sign * SCREEN.height) / 2)));
  };

  const exit = () => {
    if (exiting.current) return;
    exiting.current = true;
    void saver.current?.flush();
    pop();
  };

  const setPrefs = (patch: Partial<ReaderSettings>) => saveReaderPrefs(patch);

  const options: PanelOption<unknown>[] = [
    {
      key: 'opt-layout',
      label: t.readerLayout,
      values: ['SINGLE_PAGE', 'DOUBLE_PAGES', 'DOUBLE_PAGES_NO_COVER'] as PageLayout[],
      labels: [t.readerLayoutSingle, t.readerLayoutDouble, t.readerLayoutDoubleNoCover],
      value: prefs.pageLayout,
      enabled: true,
      set: (value) => setPrefs({ pageLayout: value as PageLayout }),
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
      values: ['LEFT_TO_RIGHT', 'RIGHT_TO_LEFT'] as Direction[],
      labels: [t.readerDirectionLtr, t.readerDirectionRtl],
      value: direction,
      enabled: true,
      // 지금 책에 바로 적용하고 기본값으로도 저장 (Komelia·Android와 같음)
      set: (value) => {
        setDirection(value as Direction);
        setPrefs({ readingDirection: value as Direction });
      },
    },
    {
      key: 'opt-scale',
      label: t.readerScale,
      values: ['SCREEN', 'FIT_WIDTH', 'FIT_HEIGHT', 'ORIGINAL'] as ScaleType[],
      labels: [t.readerScaleScreen, t.readerScaleFitWidth, t.readerScaleFitHeight, t.readerScaleOriginal],
      value: prefs.scaleType,
      enabled: true,
      set: (value) => setPrefs({ scaleType: value as ScaleType }),
    },
    {
      key: 'opt-stretch',
      label: t.readerStretch,
      values: [false, true],
      labels: [t.off, t.on],
      value: prefs.stretchToFit,
      enabled: true,
      set: (value) => setPrefs({ stretchToFit: value as boolean }),
    },
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
    const rtl = direction === 'RIGHT_TO_LEFT';
    switch (key) {
      case 'left':
        goTo(rtl ? index + 1 : index - 1);
        break;
      case 'right':
        goTo(rtl ? index - 1 : index + 1);
        break;
      case 'enter':
        if (overlay) setPanel(true);
        else setOverlay(true);
        break;
      case 'up':
        if (overlay) jump(JUMP_PAGES);
        else scrollBy(-1);
        break;
      case 'down':
        if (overlay) jump(-JUMP_PAGES);
        else scrollBy(1);
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

  const shownPages = direction === 'RIGHT_TO_LEFT' ? spread.slice().reverse() : spread;
  const shownSizes = direction === 'RIGHT_TO_LEFT' ? layout.pages.slice().reverse() : layout.pages;
  const top = layout.height < SCREEN.height ? (SCREEN.height - layout.height) / 2 : -scrollY;
  const left = (SCREEN.width - layout.width) / 2;
  const shownHighest = highestPage(spread);
  const bookTitle = data.book.metadata.title || data.book.name;

  return (
    <div ref={root} class="reader">
      <div class="reader-spread" style={{ transform: `translate(${Math.round(left)}px, ${Math.round(top)}px)` }}>
        {shownPages.map((page, i) => (
          <ReaderPage
            key={page.number}
            bookId={route.bookId}
            page={page}
            size={shownSizes[i]}
            onNaturalSize={(number, size) =>
              setNaturalSizes((previous) =>
                previous[number]?.width === size.width && previous[number]?.height === size.height
                  ? previous
                  : { ...previous, [number]: size },
              )
            }
          />
        ))}
      </div>

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
                {spread.length > 1 || prefs.pageLayout !== 'SINGLE_PAGE'
                  ? fmt(t.readerSpreadOf, index + 1, spreads.length, spread.map((page) => page.number).join(', '), totalPages)
                  : fmt(t.readerPageOf, shownHighest, totalPages)}
              </div>
              <div>{`${Math.round((shownHighest / Math.max(1, totalPages)) * 100)}%`}</div>
            </div>
            <ProgressBar value={shownHighest / Math.max(1, totalPages)} />
            <div class="reader-help">{t.readerHelp}</div>
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
                  <span>{optionText(option)}</span>
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
