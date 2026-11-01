import { describe, expect, it } from 'vitest';
import { defaultReaderSettings, effectiveSettings, parseReaderSettings } from '../src/state/readerPrefs';
import { applyViewOption, cycledValue, viewOptions } from '../src/ui/viewSettings';

describe('작품별 확대 설정 (Android 0.2.5)', () => {
  it('저장 값: 100~300 정수만, 아니면 100', () => {
    expect(parseReaderSettings({ zoomPercent: 125 }).zoomPercent).toBe(125);
    expect(parseReaderSettings({ zoomPercent: 301 }).zoomPercent).toBe(100);
    expect(parseReaderSettings({ zoomPercent: 12.5 }).zoomPercent).toBe(100);
    expect(parseReaderSettings({}).zoomPercent).toBe(100);
  });

  it('앱 기본값에는 확대가 없다 — 작품 설정이 없으면 100%', () => {
    const defaults = { ...defaultReaderSettings, zoomPercent: 150 };
    expect(effectiveSettings(defaults, null, null).zoomPercent).toBe(100);
    expect(effectiveSettings(defaults, { ...defaultReaderSettings, zoomPercent: 130 }, null).zoomPercent).toBe(130);
  });

  it('확대 줄은 페이지 넘김 리더 패널에만, 크기 맞춤 바로 아래', () => {
    const keys = (mode: Parameters<typeof viewOptions>[1]) => viewOptions(defaultReaderSettings, mode).map((o) => o.key);
    expect(keys('reader-paged').slice(4, 6)).toEqual(['opt-scale', 'opt-zoom']);
    expect(keys('reader-continuous')).not.toContain('opt-zoom');
    expect(keys('defaults')).not.toContain('opt-zoom');
  });

  it('◀▶ 1%씩, 끝에서 돌지 않는다', () => {
    const zoomOf = (percent: number) =>
      viewOptions({ ...defaultReaderSettings, zoomPercent: percent }, 'reader-paged').find((o) => o.key === 'opt-zoom')!;
    expect(cycledValue(zoomOf(100), 1)).toBe(101);
    expect(cycledValue(zoomOf(100), -1)).toBeNull();
    expect(cycledValue(zoomOf(300), 1)).toBeNull();
    expect(zoomOf(125).labels[zoomOf(125).values.indexOf(125)]).toBe('125%');
    expect(applyViewOption(defaultReaderSettings, 'opt-zoom', 101).zoomPercent).toBe(101);
  });
});
