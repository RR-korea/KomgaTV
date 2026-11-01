import { describe, expect, it } from 'vitest';
import { defaultReaderSettings, effectiveSettings, parseReaderSettings } from '../src/state/readerPrefs';
import { applyViewOption, cycledValue, viewOptions } from '../src/ui/viewSettings';

describe('작품별 확대 설정 (Android 0.2.5)', () => {
  it('저장 값: 100~120, 밖이면 끝값, 없으면 100 (Android 0.2.8)', () => {
    expect(parseReaderSettings({ zoomPercent: 115 }).zoomPercent).toBe(115);
    expect(parseReaderSettings({ zoomPercent: 300 }).zoomPercent).toBe(120);
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
      'opt-type', 'opt-layout', 'opt-offset', 'opt-direction', 'opt-scale', 'opt-transition', 'opt-zoom', 'opt-enhance',
    ]);
    expect(keys('reader-continuous')).toEqual(['opt-type', 'opt-padding', 'opt-spacing', 'opt-enhance']);
    expect(keys('defaults')).toContain('opt-transition');
    expect(keys('defaults')).not.toContain('opt-zoom');
  });

  it('◀▶ 1%씩, 끝에서 돌지 않는다', () => {
    const zoomOf = (percent: number) =>
      viewOptions({ ...defaultReaderSettings, zoomPercent: percent }, 'reader-paged').find((o) => o.key === 'opt-zoom')!;
    expect(cycledValue(zoomOf(100), 1)).toBe(101);
    expect(cycledValue(zoomOf(100), -1)).toBeNull();
    expect(cycledValue(zoomOf(120), 1)).toBeNull();
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
