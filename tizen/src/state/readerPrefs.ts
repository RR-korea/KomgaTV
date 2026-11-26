// 리더 보기 설정. 이름·기본값은 Komelia ImageReaderSettings(Apache-2.0)와 같다 (Android T5·T6과 동일).
import type { ReadingDirection } from '../api/types';
import { CROP_BORDERS, type CropBorders } from '../image/pageWork';
import { Store } from './store';

export { CROP_BORDERS, type CropBorders };

export type ReaderType = 'PAGED' | 'CONTINUOUS';
export type PageLayout = 'SINGLE_PAGE' | 'DOUBLE_PAGES' | 'DOUBLE_PAGES_NO_COVER';
export type ScaleType = 'SCREEN' | 'FIT_WIDTH' | 'FIT_HEIGHT' | 'ORIGINAL';
export type Direction = 'LEFT_TO_RIGHT' | 'RIGHT_TO_LEFT';
/**
 * 화질 개선 (Android T14·1.0.1): 끄기 / 부드럽게 확대 / Lanczos2·3·4(CPU 리샘플링) / 선명하게 / 더 선명하게.
 * Lanczos3의 저장 이름은 Android처럼 'LANCZOS'.
 */
export type ImageEnhancement = 'OFF' | 'SMOOTH' | 'LANCZOS2' | 'LANCZOS' | 'LANCZOS4' | 'SHARPEN' | 'SHARPEN_STRONG';
/** 페이지 넘김 화면 전환 효과 (Android 0.2.9): 끄기 / 읽는 방향으로 밀기(0.18초) / 겹쳐 바꾸기(0.15초) */
export type PageTransition = 'OFF' | 'SLIDE' | 'FADE';

export interface ReaderSettings {
  readerType: ReaderType;
  pageLayout: PageLayout;
  scaleType: ScaleType;
  readingDirection: Direction;
  /** 세로 스크롤 좌우 여백 (화면 너비 비율, 0~0.45) */
  continuousPadding: number;
  /** 세로 스크롤 쪽 사이 간격 (dp, 0~64) */
  continuousPageSpacing: number;
  /** EPUB 글자 크기 (%, 60~200) — Android T12 epubTextZoom */
  epubTextZoom: number;
  imageEnhancement: ImageEnhancement;
  /**
   * 페이지 넘김에서 크기 맞춤 결과를 더 확대하는 비율 % (100~150, 1% 단위, Android 1.0.21).
   * 스캔 여백을 잘라 크게 볼 때 — 작품별로만 바꾼다 (앱 기본값은 늘 100).
   */
  zoomPercent: number;
  pageTransition: PageTransition;
  /** 대비 % (작품별, 50~190, 5% 단위, 100 = 그대로, Android 1.0.1) — 흐린 스캔본을 진하게 */
  contrastPercent: number;
  /** 밝기 % (작품별, 10~150, 5% 단위, 100 = 그대로, Android 1.0.9) — 어두운 스캔본을 밝게, 밤에는 어둡게 */
  brightnessPercent: number;
  /**
   * 확대했을 때 위아래로 옮겨 둔 자리 ‰ (작품별, −1000 맨 위 … 0 가운데 … 1000 맨 아래, 125 단위, Android 1.0.38).
   * 쪽을 넘겨도 그 자리를 유지한다.
   */
  zoomPanPermille: number;
  /** 칸 사이 위아래 여백을 없애고 칸들을 쪽 가운데로 모은다 (작품별, 페이지 넘김, Android 1.0.12 — 실험적) */
  removeGutters: boolean;
  /** 쪽 둘레의 빈 여백(흰·검은 스캔 여백, 칸 밖 쪽 번호·광고)을 잘라내 쪽을 화면에 더 크게 (작품별, 페이지 넘김, Android 1.0.33) */
  cropBorders: CropBorders;
}

export const defaultReaderSettings: ReaderSettings = {
  readerType: 'PAGED',
  pageLayout: 'SINGLE_PAGE',
  scaleType: 'SCREEN',
  readingDirection: 'LEFT_TO_RIGHT',
  continuousPadding: 0,
  continuousPageSpacing: 0,
  epubTextZoom: 100,
  imageEnhancement: 'OFF',
  zoomPercent: 100,
  pageTransition: 'SLIDE',
  contrastPercent: 100,
  brightnessPercent: 100,
  zoomPanPermille: 0,
  removeGutters: false,
  cropBorders: 'OFF',
};

export const READER_TYPES: ReaderType[] = ['PAGED', 'CONTINUOUS'];
export const PAGE_LAYOUTS: PageLayout[] = ['SINGLE_PAGE', 'DOUBLE_PAGES', 'DOUBLE_PAGES_NO_COVER'];
export const SCALE_TYPES: ScaleType[] = ['SCREEN', 'FIT_WIDTH', 'FIT_HEIGHT', 'ORIGINAL'];
export const DIRECTIONS: Direction[] = ['LEFT_TO_RIGHT', 'RIGHT_TO_LEFT'];
export const ENHANCEMENTS: ImageEnhancement[] = ['OFF', 'SMOOTH', 'LANCZOS2', 'LANCZOS', 'LANCZOS4', 'SHARPEN', 'SHARPEN_STRONG'];
export const MIN_CONTRAST_PERCENT = 50;
export const MAX_CONTRAST_PERCENT = 190;
export const CONTRAST_STEP = 5;
export const MIN_BRIGHTNESS_PERCENT = 10;
export const MAX_BRIGHTNESS_PERCENT = 150;
export const BRIGHTNESS_STEP = 5;
export const MIN_ZOOM_PAN_PERMILLE = -1000;
export const MAX_ZOOM_PAN_PERMILLE = 1000;
/** ▲▼ 한 번에 움직이는 양 — 넘치는 높이의 1/16 (Android 1.0.38) */
export const ZOOM_PAN_STEP = 125;
export const TRANSITIONS: PageTransition[] = ['OFF', 'SLIDE', 'FADE'];
export const MIN_ZOOM_PERCENT = 100;
export const MAX_ZOOM_PERCENT = 150;
/** 패널에서 고르는 값 (Android T6과 같음) */
export const PADDING_STEPS = [0, 0.05, 0.1, 0.15, 0.2, 0.25, 0.3, 0.35, 0.4, 0.45];
export const SPACING_STEPS = [0, 8, 16, 24, 32, 40, 48, 56, 64];

const STORAGE_KEY = 'komgatv.reader';

function oneOf<T>(values: readonly T[], value: unknown, fallback: T): T {
  return values.includes(value as T) ? (value as T) : fallback;
}

/** 저장된 값 → 설정. 모르는 값은 기본값 (순수 함수, 테스트용). */
export function parseReaderSettings(raw: unknown): ReaderSettings {
  const d = defaultReaderSettings;
  if (!raw || typeof raw !== 'object') return d;
  const p = raw as Partial<Record<keyof ReaderSettings, unknown>>;
  return {
    readerType: oneOf(READER_TYPES, p.readerType, d.readerType),
    pageLayout: oneOf(PAGE_LAYOUTS, p.pageLayout, d.pageLayout),
    scaleType: oneOf(SCALE_TYPES, p.scaleType, d.scaleType),
    readingDirection: oneOf(DIRECTIONS, p.readingDirection, d.readingDirection),
    continuousPadding: oneOf(PADDING_STEPS, p.continuousPadding, d.continuousPadding),
    continuousPageSpacing: oneOf(SPACING_STEPS, p.continuousPageSpacing, d.continuousPageSpacing),
    epubTextZoom:
      typeof p.epubTextZoom === 'number' && p.epubTextZoom >= 60 && p.epubTextZoom <= 200 && p.epubTextZoom % 10 === 0
        ? p.epubTextZoom
        : d.epubTextZoom,
    imageEnhancement: oneOf(ENHANCEMENTS, p.imageEnhancement, d.imageEnhancement),
    // 범위 밖이면 끝값 (Android ReaderSettingsCodec과 같음 — 예전에 300%까지 저장된 값은 150%로)
    zoomPercent:
      typeof p.zoomPercent === 'number' && Number.isFinite(p.zoomPercent)
        ? Math.min(MAX_ZOOM_PERCENT, Math.max(MIN_ZOOM_PERCENT, Math.round(p.zoomPercent)))
        : d.zoomPercent,
    pageTransition: oneOf(TRANSITIONS, p.pageTransition, d.pageTransition),
    // 범위 밖이면 끝값, 5% 단위로
    contrastPercent:
      typeof p.contrastPercent === 'number' && Number.isFinite(p.contrastPercent)
        ? Math.min(
            MAX_CONTRAST_PERCENT,
            Math.max(MIN_CONTRAST_PERCENT, Math.round(p.contrastPercent / CONTRAST_STEP) * CONTRAST_STEP),
          )
        : d.contrastPercent,
    brightnessPercent:
      typeof p.brightnessPercent === 'number' && Number.isFinite(p.brightnessPercent)
        ? Math.min(
            MAX_BRIGHTNESS_PERCENT,
            Math.max(MIN_BRIGHTNESS_PERCENT, Math.round(p.brightnessPercent / BRIGHTNESS_STEP) * BRIGHTNESS_STEP),
          )
        : d.brightnessPercent,
    zoomPanPermille:
      typeof p.zoomPanPermille === 'number' && Number.isFinite(p.zoomPanPermille)
        ? Math.min(MAX_ZOOM_PAN_PERMILLE, Math.max(MIN_ZOOM_PAN_PERMILLE, Math.round(p.zoomPanPermille)))
        : d.zoomPanPermille,
    removeGutters: typeof p.removeGutters === 'boolean' ? p.removeGutters : d.removeGutters,
    cropBorders: oneOf(CROP_BORDERS, p.cropBorders, d.cropBorders),
  };
}

function load(): ReaderSettings {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    return raw ? parseReaderSettings(JSON.parse(raw)) : defaultReaderSettings;
  } catch {
    return defaultReaderSettings;
  }
}

export const readerPrefs = new Store<ReaderSettings>(load());

export function saveReaderPrefs(patch: Partial<ReaderSettings>): void {
  readerPrefs.update(patch);
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(readerPrefs.get()));
  } catch {
    // 저장소를 못 쓰면 이번 세션에서만 적용
  }
}

/**
 * 리더 종류 우선순위 (Komelia ReaderState와 같음):
 * 시리즈 방향 LTR/RTL → PAGED, WEBTOON → CONTINUOUS, VERTICAL/없음 → 저장된 종류.
 */
export function resolveReaderType(seriesDirection: ReadingDirection | null, saved: ReaderType): ReaderType {
  switch (seriesDirection) {
    case 'LEFT_TO_RIGHT':
    case 'RIGHT_TO_LEFT':
      return 'PAGED';
    case 'WEBTOON':
      return 'CONTINUOUS';
    default:
      return saved;
  }
}

/** 이어 읽기 시작 쪽: 진행이 없거나 다 읽었으면 1쪽, 아니면 저장된 쪽 (Komelia와 같음). */
export function startPage(progress: { page: number; completed: boolean } | null): number {
  return !progress || progress.completed ? 1 : Math.max(1, progress.page);
}

// ---------- 작품별 보기 설정 (Android T14) ----------
// 리더의 보기 설정 패널은 그 작품(시리즈)에만 저장한다. 우선순위: 작품 설정 > (앱 기본값 + 시리즈 메타데이터 규칙).

const SERIES_KEY = 'komgatv.reader.series';

/** 앱 기본값 + 시리즈 메타데이터의 방향·웹툰 규칙 (Komelia와 같음) */
export function withSeriesRules(defaults: ReaderSettings, seriesDirection: ReadingDirection | null): ReaderSettings {
  const readerType = resolveReaderType(seriesDirection, defaults.readerType);
  const readingDirection =
    seriesDirection === 'LEFT_TO_RIGHT' || seriesDirection === 'RIGHT_TO_LEFT' ? seriesDirection : defaults.readingDirection;
  // 확대·확대 자리·대비·밝기·여백 잘라내기·칸 간격 없애기는 작품 설정에만 있다
  return {
    ...defaults,
    readerType,
    readingDirection,
    zoomPercent: defaultReaderSettings.zoomPercent,
    contrastPercent: defaultReaderSettings.contrastPercent,
    brightnessPercent: defaultReaderSettings.brightnessPercent,
    zoomPanPermille: defaultReaderSettings.zoomPanPermille,
    removeGutters: defaultReaderSettings.removeGutters,
    cropBorders: defaultReaderSettings.cropBorders,
  };
}

/** 작품에 쓸 설정. EPUB 글자 크기는 작품과 상관없이 앱 기본값. */
export function effectiveSettings(
  defaults: ReaderSettings,
  series: ReaderSettings | null,
  seriesDirection: ReadingDirection | null,
): ReaderSettings {
  return series ? { ...series, epubTextZoom: defaults.epubTextZoom } : withSeriesRules(defaults, seriesDirection);
}

/** 저장된 작품 설정 모음 문자열 → {시리즈 ID: 설정}. 깨진 값은 버리고 모르는 키·값은 기본값 */
export function parseSeriesSettings(raw: string | null): Record<string, ReaderSettings> {
  if (!raw) return {};
  try {
    const parsed: unknown = JSON.parse(raw);
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) return {};
    const result: Record<string, ReaderSettings> = {};
    for (const [id, value] of Object.entries(parsed as Record<string, unknown>)) {
      if (value && typeof value === 'object') result[id] = parseReaderSettings(value);
    }
    return result;
  } catch {
    return {};
  }
}

function readAllSeries(): Record<string, ReaderSettings> {
  try {
    return parseSeriesSettings(localStorage.getItem(SERIES_KEY));
  } catch {
    return {};
  }
}

function writeAllSeries(all: Record<string, ReaderSettings>): void {
  try {
    localStorage.setItem(SERIES_KEY, JSON.stringify(all));
  } catch {
    // 저장소를 못 쓰면 이번 세션에서만
  }
}

export function loadSeriesSettings(seriesId: string): ReaderSettings | null {
  return readAllSeries()[seriesId] ?? null;
}

export function saveSeriesSettings(seriesId: string, settings: ReaderSettings): void {
  writeAllSeries({ ...readAllSeries(), [seriesId]: settings });
}

/** 앱 기본값으로 되돌리기 */
export function clearSeriesSettings(seriesId: string): void {
  const all = readAllSeries();
  delete all[seriesId];
  writeAllSeries(all);
}

// ---------- 숨긴 쪽 (Android 1.0.8) ----------
// 광고·빈 쪽 등을 이 책에서 건너뛴다 — 이 기기에만 저장하고 서버 파일은 그대로. 쪽 번호는 원래 번호 그대로 쓴다.

const HIDDEN_PAGES_KEY = 'komgatv.reader.hiddenPages';

/** 저장된 문자열 → {책 ID: 숨긴 쪽 번호(오름차순)}. 깨진 값은 버린다 */
export function parseHiddenPages(raw: string | null): Record<string, number[]> {
  if (!raw) return {};
  try {
    const parsed: unknown = JSON.parse(raw);
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) return {};
    const result: Record<string, number[]> = {};
    for (const [id, value] of Object.entries(parsed as Record<string, unknown>)) {
      if (!Array.isArray(value)) continue;
      const pages = value.filter((n): n is number => typeof n === 'number' && Number.isInteger(n) && n >= 1);
      const unique = pages.filter((n, i) => pages.indexOf(n) === i).sort((a, b) => a - b);
      if (unique.length) result[id] = unique;
    }
    return result;
  } catch {
    return {};
  }
}

function readAllHiddenPages(): Record<string, number[]> {
  try {
    return parseHiddenPages(localStorage.getItem(HIDDEN_PAGES_KEY));
  } catch {
    return {};
  }
}

export function loadHiddenPages(bookId: string): number[] {
  return readAllHiddenPages()[bookId] ?? [];
}

/** 숨긴 쪽을 저장한다. 비어 있으면 지운다 */
export function saveHiddenPages(bookId: string, pages: number[]): void {
  const all = readAllHiddenPages();
  if (pages.length) all[bookId] = pages.slice().sort((a, b) => a - b);
  else delete all[bookId];
  try {
    localStorage.setItem(HIDDEN_PAGES_KEY, JSON.stringify(all));
  } catch {
    // 저장소를 못 쓰면 이번 세션에서만
  }
}
