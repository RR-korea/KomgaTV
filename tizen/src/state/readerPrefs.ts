// 리더 보기 설정. 이름·기본값은 Komelia ImageReaderSettings(Apache-2.0)와 같다 (Android T5·T6과 동일).
import type { ReadingDirection } from '../api/types';
import { Store } from './store';

export type ReaderType = 'PAGED' | 'CONTINUOUS';
export type PageLayout = 'SINGLE_PAGE' | 'DOUBLE_PAGES' | 'DOUBLE_PAGES_NO_COVER';
export type ScaleType = 'SCREEN' | 'FIT_WIDTH' | 'FIT_HEIGHT' | 'ORIGINAL';
export type Direction = 'LEFT_TO_RIGHT' | 'RIGHT_TO_LEFT';
/** 화질 개선 (Android T14): 끄기 / 부드럽게 확대 / 선명하게 / 더 선명하게 */
export type ImageEnhancement = 'OFF' | 'SMOOTH' | 'SHARPEN' | 'SHARPEN_STRONG';

export interface ReaderSettings {
  readerType: ReaderType;
  pageLayout: PageLayout;
  scaleType: ScaleType;
  stretchToFit: boolean;
  readingDirection: Direction;
  /** 세로 스크롤 좌우 여백 (화면 너비 비율, 0~0.45) */
  continuousPadding: number;
  /** 세로 스크롤 쪽 사이 간격 (dp, 0~64) */
  continuousPageSpacing: number;
  /** EPUB 글자 크기 (%, 60~200) — Android T12 epubTextZoom */
  epubTextZoom: number;
  imageEnhancement: ImageEnhancement;
  /**
   * 페이지 넘김에서 크기 맞춤 결과를 더 확대하는 비율 % (100~300, 1% 단위, Android 0.2.5).
   * 스캔 여백을 잘라 크게 볼 때 — 작품별로만 바꾼다 (앱 기본값은 늘 100).
   */
  zoomPercent: number;
}

export const defaultReaderSettings: ReaderSettings = {
  readerType: 'PAGED',
  pageLayout: 'SINGLE_PAGE',
  scaleType: 'SCREEN',
  stretchToFit: true,
  readingDirection: 'LEFT_TO_RIGHT',
  continuousPadding: 0,
  continuousPageSpacing: 0,
  epubTextZoom: 100,
  imageEnhancement: 'OFF',
  zoomPercent: 100,
};

export const READER_TYPES: ReaderType[] = ['PAGED', 'CONTINUOUS'];
export const PAGE_LAYOUTS: PageLayout[] = ['SINGLE_PAGE', 'DOUBLE_PAGES', 'DOUBLE_PAGES_NO_COVER'];
export const SCALE_TYPES: ScaleType[] = ['SCREEN', 'FIT_WIDTH', 'FIT_HEIGHT', 'ORIGINAL'];
export const DIRECTIONS: Direction[] = ['LEFT_TO_RIGHT', 'RIGHT_TO_LEFT'];
export const ENHANCEMENTS: ImageEnhancement[] = ['OFF', 'SMOOTH', 'SHARPEN', 'SHARPEN_STRONG'];
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
    stretchToFit: typeof p.stretchToFit === 'boolean' ? p.stretchToFit : d.stretchToFit,
    readingDirection: oneOf(DIRECTIONS, p.readingDirection, d.readingDirection),
    continuousPadding: oneOf(PADDING_STEPS, p.continuousPadding, d.continuousPadding),
    continuousPageSpacing: oneOf(SPACING_STEPS, p.continuousPageSpacing, d.continuousPageSpacing),
    epubTextZoom:
      typeof p.epubTextZoom === 'number' && p.epubTextZoom >= 60 && p.epubTextZoom <= 200 && p.epubTextZoom % 10 === 0
        ? p.epubTextZoom
        : d.epubTextZoom,
    imageEnhancement: oneOf(ENHANCEMENTS, p.imageEnhancement, d.imageEnhancement),
    zoomPercent:
      typeof p.zoomPercent === 'number' && Number.isInteger(p.zoomPercent) && p.zoomPercent >= 100 && p.zoomPercent <= 300
        ? p.zoomPercent
        : d.zoomPercent,
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
  // 확대는 작품 설정에만 있다
  return { ...defaults, readerType, readingDirection, zoomPercent: defaultReaderSettings.zoomPercent };
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
