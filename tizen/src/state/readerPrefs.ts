// 리더 보기 설정. 이름·기본값은 Komelia ImageReaderSettings(Apache-2.0)와 같다 (Android T5·T6과 동일).
import type { ReadingDirection } from '../api/types';
import { Store } from './store';

export type ReaderType = 'PAGED' | 'CONTINUOUS';
export type PageLayout = 'SINGLE_PAGE' | 'DOUBLE_PAGES' | 'DOUBLE_PAGES_NO_COVER';
export type ScaleType = 'SCREEN' | 'FIT_WIDTH' | 'FIT_HEIGHT' | 'ORIGINAL';
export type Direction = 'LEFT_TO_RIGHT' | 'RIGHT_TO_LEFT';

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
}

export const defaultReaderSettings: ReaderSettings = {
  readerType: 'PAGED',
  pageLayout: 'SINGLE_PAGE',
  scaleType: 'SCREEN',
  stretchToFit: true,
  readingDirection: 'LEFT_TO_RIGHT',
  continuousPadding: 0,
  continuousPageSpacing: 0,
};

export const READER_TYPES: ReaderType[] = ['PAGED', 'CONTINUOUS'];
export const PAGE_LAYOUTS: PageLayout[] = ['SINGLE_PAGE', 'DOUBLE_PAGES', 'DOUBLE_PAGES_NO_COVER'];
export const SCALE_TYPES: ScaleType[] = ['SCREEN', 'FIT_WIDTH', 'FIT_HEIGHT', 'ORIGINAL'];
export const DIRECTIONS: Direction[] = ['LEFT_TO_RIGHT', 'RIGHT_TO_LEFT'];
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
