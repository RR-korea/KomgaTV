import { describe, expect, it } from 'vitest';
import {
  anchorOf,
  DP,
  layoutContinuous,
  maxScroll,
  scrollByScreen,
  scrollOf,
  stepPage,
  visiblePages,
  withCards,
} from '../src/reader/continuous';
import {
  effectiveSettings,
  parseReaderSettings,
  parseSeriesSettings,
  resolveReaderType,
  startPage,
} from '../src/state/readerPrefs';

const screen = { width: 1920, height: 1080 };
const webtoon = { width: 800, height: 12000 }; // 화면보다 훨씬 긴 쪽
const small = { width: 400, height: 600 };

describe('layoutContinuous', () => {
  it('쪽 너비 = 화면 너비 × (1 − 2 × 여백), 높이는 비율대로, 가운데 정렬', () => {
    const layout = layoutContinuous([webtoon], screen, 0.25, 0, true);
    expect(layout.pages[0]).toEqual({ top: 0, left: 480, width: 960, height: 14400 });
    expect(layout.total).toBe(14400);
  });

  it('쪽 사이 간격 (dp → px)', () => {
    const layout = layoutContinuous([small, small], screen, 0, 8, true);
    expect(layout.pages[1].top).toBe(layout.pages[0].height + 8 * DP);
  });

  it('작은 이미지 키우기 꺼짐: 원본 너비보다 넓히지 않는다', () => {
    const layout = layoutContinuous([small], screen, 0, 0, false);
    expect(layout.pages[0].width).toBe(400);
    expect(layout.pages[0].height).toBe(600);
  });
});

describe('스크롤·기준 쪽', () => {
  const layout = layoutContinuous([small, webtoon, small], screen, 0, 0, true);
  // small → 1920×2880, webtoon → 1920×28800

  it('기준 쪽 ↔ 화면 위치 왕복', () => {
    const anchor = anchorOf(layout, 5000);
    expect(anchor).toEqual({ index: 1, offset: 5000 - 2880 });
    expect(scrollOf(layout, anchor, screen.height)).toBe(5000);
  });

  it('한 화면씩 내려가고 끝에서 멈춘다', () => {
    expect(scrollByScreen(layout, 0, screen.height, 1)).toBe(1080);
    const end = maxScroll(layout, screen.height);
    expect(scrollByScreen(layout, end - 10, screen.height, 1)).toBe(end);
    expect(scrollByScreen(layout, 100, screen.height, -1)).toBe(0);
  });

  it('보이는 쪽과 완독', () => {
    expect(visiblePages(layout, 2500, screen.height)).toEqual({ first: 0, last: 1, endReached: false });
    const end = maxScroll(layout, screen.height);
    expect(visiblePages(layout, end, screen.height).endReached).toBe(true);
  });

  it('좌/우: 다음 쪽 맨 위, 이전은 지금 쪽 맨 위 → 앞 쪽', () => {
    expect(stepPage({ index: 1, offset: 300 }, 3, 1)).toEqual({ index: 2, offset: 0 });
    expect(stepPage({ index: 1, offset: 300 }, 3, -1)).toEqual({ index: 1, offset: 0 });
    expect(stepPage({ index: 1, offset: 0 }, 3, -1)).toEqual({ index: 0, offset: 0 });
    expect(stepPage({ index: 2, offset: 0 }, 3, 1)).toEqual({ index: 2, offset: 0 });
  });

  it('위쪽 쪽 높이가 바뀌어도 기준 쪽 위치는 그대로', () => {
    const anchor = { index: 2, offset: 100 };
    const before = layoutContinuous([small, small, small], screen, 0, 0, true);
    const after = layoutContinuous([small, webtoon, small], screen, 0, 0, true);
    expect(scrollOf(after, anchor, 1080) - after.pages[2].top).toBe(scrollOf(before, anchor, 1080) - before.pages[2].top);
  });
});

describe('리더 종류 우선순위 (Komelia)', () => {
  it('LTR/RTL → PAGED, WEBTOON → CONTINUOUS, VERTICAL/없음 → 저장값', () => {
    expect(resolveReaderType('LEFT_TO_RIGHT', 'CONTINUOUS')).toBe('PAGED');
    expect(resolveReaderType('RIGHT_TO_LEFT', 'CONTINUOUS')).toBe('PAGED');
    expect(resolveReaderType('WEBTOON', 'PAGED')).toBe('CONTINUOUS');
    expect(resolveReaderType('VERTICAL', 'CONTINUOUS')).toBe('CONTINUOUS');
    expect(resolveReaderType(null, 'PAGED')).toBe('PAGED');
  });
});

describe('이어 읽기 시작 쪽', () => {
  it('진행 없음·완독 → 1쪽, 아니면 저장된 쪽', () => {
    expect(startPage(null)).toBe(1);
    expect(startPage({ page: 40, completed: true })).toBe(1);
    expect(startPage({ page: 40, completed: false })).toBe(40);
  });
});

describe('parseReaderSettings', () => {
  it('모르는 값은 기본값, 옛 저장값(T5)도 읽는다', () => {
    const parsed = parseReaderSettings({ pageLayout: 'DOUBLE_PAGES', continuousPadding: 0.33, readerType: 'PANELS' });
    expect(parsed.pageLayout).toBe('DOUBLE_PAGES');
    expect(parsed.readerType).toBe('PAGED');
    expect(parsed.continuousPadding).toBe(0);
  });
});

describe('작품별 보기 설정 (Android T14)', () => {
  const defaults = { ...parseReaderSettings({}), pageLayout: 'DOUBLE_PAGES' as const, epubTextZoom: 120 };

  it('작품 설정이 없으면 앱 기본값 + 시리즈 규칙 (RTL → 페이지·오른쪽에서 왼쪽, WEBTOON → 세로)', () => {
    const rtl = effectiveSettings({ ...defaults, readerType: 'CONTINUOUS' }, null, 'RIGHT_TO_LEFT');
    expect(rtl.readerType).toBe('PAGED');
    expect(rtl.readingDirection).toBe('RIGHT_TO_LEFT');
    expect(rtl.pageLayout).toBe('DOUBLE_PAGES');
    expect(effectiveSettings(defaults, null, 'WEBTOON').readerType).toBe('CONTINUOUS');
  });

  it('작품 설정이 있으면 그것 (시리즈 규칙보다 우선), EPUB 글자 크기만 앱 기본값', () => {
    const series = { ...parseReaderSettings({}), readerType: 'CONTINUOUS' as const, epubTextZoom: 60 };
    const effective = effectiveSettings(defaults, series, 'RIGHT_TO_LEFT');
    expect(effective.readerType).toBe('CONTINUOUS');
    expect(effective.epubTextZoom).toBe(120);
  });

  it('저장 문자열 왕복, 잘못된 값은 기본값, 깨진 문자열은 빈 모음', () => {
    const saved = JSON.stringify({ S1: { readerType: 'CONTINUOUS', imageEnhancement: 'SHARPEN' }, S2: 'x', S3: { scaleType: 'NOPE' } });
    const parsed = parseSeriesSettings(saved);
    expect(Object.keys(parsed)).toEqual(['S1', 'S3']);
    expect(parsed.S1.imageEnhancement).toBe('SHARPEN');
    expect(parsed.S3.scaleType).toBe('SCREEN');
    expect(parseSeriesSettings('{oops')).toEqual({});
  });
});

describe('책 첫·끝 카드 (이전 권·다음 권)', () => {
  it('첫 쪽 위 카드 자리: 위로 올라가면 음수 거리, 다시 그 위치로', () => {
    const base = layoutContinuous([{ width: 800, height: 1200 }, { width: 800, height: 1200 }], { width: 1920, height: 1080 }, 0, 0, true);
    const layout = withCards(base, 720, 720);
    expect(layout.pages[0].top).toBe(720);
    expect(layout.total).toBe(base.total + 1440);
    const anchor = anchorOf(layout, 0);
    expect(anchor).toEqual({ index: 0, offset: -720 });
    expect(scrollOf(layout, anchor, 1080)).toBe(0);
    expect(scrollOf(layout, { index: 0, offset: 0 }, 1080)).toBe(720);
  });
});
