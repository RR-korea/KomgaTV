import { describe, expect, it } from 'vitest';
import {
  defaultReaderSettings,
  effectiveSettings,
  parseHiddenPages,
  parseReaderSettings,
} from '../src/state/readerPrefs';
import { imageFilter } from '../src/reader/enhance';
import { workBox, workSize } from '../src/image/pageWork';
import { buildSpreads, pageIndexAtOrAfter, spreadIndexAtOrAfter, withoutHidden } from '../src/reader/spreads';
import { applyViewOption, cycledValue, viewOptions } from '../src/ui/viewSettings';

describe('작품별 확대 설정 (Android 0.2.5)', () => {
  it('저장 값: 100~150, 밖이면 끝값, 없으면 100 (Android 1.0.21)', () => {
    expect(parseReaderSettings({ zoomPercent: 115 }).zoomPercent).toBe(115);
    expect(parseReaderSettings({ zoomPercent: 300 }).zoomPercent).toBe(150);
    expect(parseReaderSettings({ zoomPercent: 50 }).zoomPercent).toBe(100);
    expect(parseReaderSettings({ zoomPercent: 'x' }).zoomPercent).toBe(100);
    expect(parseReaderSettings({}).zoomPercent).toBe(100);
  });

  it('앱 기본값에는 확대가 없다 — 작품 설정이 없으면 100%', () => {
    const defaults = { ...defaultReaderSettings, zoomPercent: 150 };
    expect(effectiveSettings(defaults, null, null).zoomPercent).toBe(100);
    expect(effectiveSettings(defaults, { ...defaultReaderSettings, zoomPercent: 110 }, null).zoomPercent).toBe(110);
  });

  it('패널 줄 (Android 0.2.9): 크기 맞춤 → 화면 전환 → 확대, 작은 이미지 키우기 없음', () => {
    const keys = (mode: Parameters<typeof viewOptions>[1]) => viewOptions(defaultReaderSettings, mode).map((o) => o.key);
    expect(keys('reader-paged')).toEqual([
      'opt-type', 'opt-layout', 'opt-offset', 'opt-direction', 'opt-scale', 'opt-transition', 'opt-zoom',
      // 여백 잘라내기·칸 간격 없애기는 확대 바로 아래 (Android 1.0.33), 그다음 대비·밝기
      'opt-crop', 'opt-gutters', 'opt-contrast', 'opt-brightness', 'opt-enhance',
    ]);
    // 대비·밝기는 두 모드 모두, 작품 설정에서만 (Android 1.0.2·1.0.9)
    expect(keys('reader-continuous')).toEqual([
      'opt-type', 'opt-padding', 'opt-spacing', 'opt-contrast', 'opt-brightness', 'opt-enhance',
    ]);
    expect(keys('defaults')).toContain('opt-transition');
    for (const key of ['opt-zoom', 'opt-contrast', 'opt-brightness', 'opt-crop', 'opt-gutters']) {
      expect(keys('defaults')).not.toContain(key);
    }
  });

  it('◀▶ 1%씩, 끝에서 돌지 않는다', () => {
    const zoomOf = (percent: number) =>
      viewOptions({ ...defaultReaderSettings, zoomPercent: percent }, 'reader-paged').find((o) => o.key === 'opt-zoom')!;
    expect(cycledValue(zoomOf(100), 1)).toBe(101);
    expect(cycledValue(zoomOf(100), -1)).toBeNull();
    expect(cycledValue(zoomOf(120), 1)).toBe(121);
    expect(cycledValue(zoomOf(150), 1)).toBeNull();
    expect(zoomOf(115).labels[zoomOf(115).values.indexOf(115)]).toBe('115%');
    expect(applyViewOption(defaultReaderSettings, 'opt-zoom', 101).zoomPercent).toBe(101);
  });
});

describe('화면 전환 설정', () => {
  it('기본 밀기, 모르는 값은 기본값', () => {
    expect(defaultReaderSettings.pageTransition).toBe('SLIDE');
    expect(parseReaderSettings({ pageTransition: 'FADE' }).pageTransition).toBe('FADE');
    expect(parseReaderSettings({ pageTransition: 'SPIN' }).pageTransition).toBe('SLIDE');
  });
  it('예전 stretchToFit 값은 버린다', () => {
    expect('stretchToFit' in parseReaderSettings({ stretchToFit: false })).toBe(false);
  });
});

describe('작품별 설정 (Android 1.0.7~1.0.45): 밝기·확대 자리·여백 잘라내기·칸 간격 없애기', () => {
  it('저장 값: 범위 밖이면 끝값, 모르는 값은 기본값', () => {
    const d = defaultReaderSettings;
    expect([d.brightnessPercent, d.zoomPanPermille, d.removeGutters, d.cropBorders]).toEqual([100, 0, false, 'OFF']);
    expect(parseReaderSettings({ brightnessPercent: 62 }).brightnessPercent).toBe(60);
    expect(parseReaderSettings({ brightnessPercent: 5 }).brightnessPercent).toBe(10);
    expect(parseReaderSettings({ brightnessPercent: 400 }).brightnessPercent).toBe(150);
    expect(parseReaderSettings({ zoomPanPermille: -3000 }).zoomPanPermille).toBe(-1000);
    expect(parseReaderSettings({ zoomPanPermille: 375 }).zoomPanPermille).toBe(375);
    expect(parseReaderSettings({ removeGutters: true }).removeGutters).toBe(true);
    expect(parseReaderSettings({ removeGutters: 'yes' }).removeGutters).toBe(false);
    expect(parseReaderSettings({ cropBorders: 'TOP_BOTTOM' }).cropBorders).toBe('TOP_BOTTOM');
    expect(parseReaderSettings({ cropBorders: 'DIAGONAL' }).cropBorders).toBe('OFF');
  });

  it('앱 기본값에는 없다 — 작품 설정이 없으면 모두 꺼짐', () => {
    const defaults = {
      ...defaultReaderSettings,
      brightnessPercent: 50,
      zoomPanPermille: 500,
      removeGutters: true,
      cropBorders: 'ALL' as const,
    };
    const used = effectiveSettings(defaults, null, null);
    expect([used.brightnessPercent, used.zoomPanPermille, used.removeGutters, used.cropBorders]).toEqual([100, 0, false, 'OFF']);
    expect(effectiveSettings(defaultReaderSettings, defaults, null).cropBorders).toBe('ALL');
  });

  it('패널: 밝기 5%씩(끝에서 안 돈다), 여백 잘라내기 네 값, 칸 간격 켬/끔', () => {
    const option = (settings: typeof defaultReaderSettings, key: string) =>
      viewOptions(settings, 'reader-paged').find((o) => o.key === key)!;
    expect(cycledValue(option(defaultReaderSettings, 'opt-brightness'), 1)).toBe(105);
    expect(cycledValue(option({ ...defaultReaderSettings, brightnessPercent: 150 }, 'opt-brightness'), 1)).toBeNull();
    expect(cycledValue(option({ ...defaultReaderSettings, brightnessPercent: 10 }, 'opt-brightness'), -1)).toBeNull();
    expect(option(defaultReaderSettings, 'opt-crop').values).toEqual(['OFF', 'TOP_BOTTOM', 'LEFT_RIGHT', 'ALL']);
    expect(cycledValue(option(defaultReaderSettings, 'opt-crop'), -1)).toBe('ALL');
    expect(cycledValue(option(defaultReaderSettings, 'opt-gutters'), 1)).toBe(true);
    expect(applyViewOption(defaultReaderSettings, 'opt-crop', 'LEFT_RIGHT').cropBorders).toBe('LEFT_RIGHT');
    expect(applyViewOption(defaultReaderSettings, 'opt-gutters', true).removeGutters).toBe(true);
    expect(applyViewOption(defaultReaderSettings, 'opt-brightness', 80).brightnessPercent).toBe(80);
  });

  it('밝기는 대비 뒤에 곱한다 (Android 색 행렬: gain = c × b)', () => {
    expect(imageFilter('OFF', 100, 100)).toBeUndefined();
    expect(imageFilter('OFF', 100, 60)).toBe('brightness(0.6)');
    expect(imageFilter('SHARPEN', 130, 120)).toBe('url(#ktv-sharpen) contrast(1.3) brightness(1.2)');
  });

  it('처리 크기: 화면에서 차지할 칸에 맞춰 줄이기만 한다 (Android 디코딩 크기)', () => {
    const screen = { width: 1920, height: 1080 };
    expect(workBox('SCREEN', 2, screen)).toEqual({ width: 960, height: 1080 });
    expect(workBox('SCREEN', 1, screen)).toEqual({ width: 1920, height: 1080 });
    expect(workBox('FIT_HEIGHT', 2, screen)).toEqual({ width: 1920, height: 1080 });
    expect(workBox('FIT_WIDTH', 2, screen)).toEqual({ width: 960, height: 3240 });
    expect(workBox('ORIGINAL', 1, screen)).toEqual({ width: 3840, height: 3240 });
    expect(workSize(1500, 2168, 960, 1080)).toEqual({ width: 747, height: 1080 });
    expect(workSize(600, 800, 960, 1080)).toEqual({ width: 600, height: 800 }); // 키우지 않는다
    expect(workSize(3000, 2168, 1920, 1080)).toEqual({ width: 1494, height: 1080 }); // 두 쪽을 한 장에 스캔한 쪽
  });
});

describe('쪽 숨기기 (Android 1.0.8)', () => {
  const pages = [1, 2, 3, 4, 5].map((number) => ({ number, width: 1000, height: 1500 }));

  it('숨긴 쪽을 빼고 펼침을 만든다 — 쪽 번호는 원래 번호 그대로', () => {
    const shown = withoutHidden(pages, [2, 5]);
    expect(shown.map((page) => page.number)).toEqual([1, 3, 4]);
    expect(buildSpreads(shown, 'DOUBLE_PAGES_NO_COVER').map((spread) => spread.map((page) => page.number))).toEqual([
      [1, 3],
      [4],
    ]);
    expect(withoutHidden(pages, [])).toBe(pages);
    // 모두 숨긴 값이 저장돼 있어도 책은 열린다
    expect(withoutHidden(pages, [1, 2, 3, 4, 5])).toBe(pages);
  });

  it('숨긴 쪽 자리에서는 다음 쪽(펼침), 끝을 넘으면 마지막', () => {
    const shown = withoutHidden(pages, [2, 5]);
    const spreads = buildSpreads(shown, 'SINGLE_PAGE');
    expect(spreadIndexAtOrAfter(spreads, 2)).toBe(1); // 2쪽은 숨김 → 3쪽
    expect(spreadIndexAtOrAfter(spreads, 5)).toBe(2); // 5쪽은 숨김, 뒤가 없다 → 마지막(4쪽)
    expect(spreadIndexAtOrAfter(spreads, 1)).toBe(0);
    expect(spreadIndexAtOrAfter([], 3)).toBe(0);
    expect(pageIndexAtOrAfter(shown, 2)).toBe(1);
    expect(pageIndexAtOrAfter(shown, 9)).toBe(2);
  });

  it('저장 값: 책마다 쪽 번호 목록, 깨진 값은 버린다', () => {
    expect(parseHiddenPages('{"B1":[3,1,3,"x",0],"B2":[],"B3":"no"}')).toEqual({ B1: [1, 3] });
    expect(parseHiddenPages('not json')).toEqual({});
    expect(parseHiddenPages(null)).toEqual({});
  });
});
