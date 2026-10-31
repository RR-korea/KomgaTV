// 리더 보기 설정. 이름·기본값은 Komelia ImageReaderSettings(Apache-2.0)와 같다 (Android T5와 동일).
import { Store } from './store';

export type PageLayout = 'SINGLE_PAGE' | 'DOUBLE_PAGES' | 'DOUBLE_PAGES_NO_COVER';
export type ScaleType = 'SCREEN' | 'FIT_WIDTH' | 'FIT_HEIGHT' | 'ORIGINAL';
export type Direction = 'LEFT_TO_RIGHT' | 'RIGHT_TO_LEFT';

export interface ReaderSettings {
  pageLayout: PageLayout;
  scaleType: ScaleType;
  stretchToFit: boolean;
  readingDirection: Direction;
}

export const defaultReaderSettings: ReaderSettings = {
  pageLayout: 'SINGLE_PAGE',
  scaleType: 'SCREEN',
  stretchToFit: true,
  readingDirection: 'LEFT_TO_RIGHT',
};

const STORAGE_KEY = 'komgatv.reader';

const pageLayouts: PageLayout[] = ['SINGLE_PAGE', 'DOUBLE_PAGES', 'DOUBLE_PAGES_NO_COVER'];
const scaleTypes: ScaleType[] = ['SCREEN', 'FIT_WIDTH', 'FIT_HEIGHT', 'ORIGINAL'];
const directions: Direction[] = ['LEFT_TO_RIGHT', 'RIGHT_TO_LEFT'];

function load(): ReaderSettings {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return defaultReaderSettings;
    const parsed = JSON.parse(raw) as Partial<ReaderSettings>;
    return {
      pageLayout: pageLayouts.includes(parsed.pageLayout as PageLayout)
        ? (parsed.pageLayout as PageLayout)
        : defaultReaderSettings.pageLayout,
      scaleType: scaleTypes.includes(parsed.scaleType as ScaleType)
        ? (parsed.scaleType as ScaleType)
        : defaultReaderSettings.scaleType,
      stretchToFit: typeof parsed.stretchToFit === 'boolean' ? parsed.stretchToFit : defaultReaderSettings.stretchToFit,
      readingDirection: directions.includes(parsed.readingDirection as Direction)
        ? (parsed.readingDirection as Direction)
        : defaultReaderSettings.readingDirection,
    };
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
