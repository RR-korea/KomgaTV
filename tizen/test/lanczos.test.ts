import { describe, expect, it } from 'vitest';
import { kernel, lanczosResize, lanczosRows, sourceRows } from '../src/image/lanczos';
import { imageFilter, lanczosWindow } from '../src/reader/enhance';
import { defaultReaderSettings, parseReaderSettings } from '../src/state/readerPrefs';

function solid(width: number, height: number, rgba: number[]): Uint8ClampedArray {
  const out = new Uint8ClampedArray(width * height * 4);
  for (let i = 0; i < width * height; i++) out.set(rgba, i * 4);
  return out;
}

describe('Lanczos 리샘플링 (Android 1.0.1 Lanczos.kt)', () => {
  it('가중치 합은 1 (키울 때·줄일 때)', () => {
    for (const [src, dst] of [
      [10, 25],
      [30, 7],
    ]) {
      const k = kernel(src, dst, 3);
      for (let i = 0; i < dst; i++) {
        let sum = 0;
        for (let j = 0; j < k.count[i]; j++) sum += k.weights[k.offset[i] + j];
        expect(sum).toBeCloseTo(1, 5);
      }
    }
  });

  it('한 색은 크기를 바꿔도 그대로', () => {
    const out = lanczosResize(solid(8, 6, [200, 100, 50, 255]), 8, 6, 20, 15, 3);
    expect(out.length).toBe(20 * 15 * 4);
    expect(Array.from(out.slice(0, 4))).toEqual([200, 100, 50, 255]);
    expect(Array.from(out.slice(-4))).toEqual([200, 100, 50, 255]);
  });

  it('줄을 나눠 계산해도(Worker) 한 번에 한 것과 같다', () => {
    const w = 9;
    const h = 13;
    const src = new Uint8ClampedArray(w * h * 4);
    for (let i = 0; i < src.length; i++) src[i] = (i * 37) % 256;
    const full = lanczosResize(src, w, h, 17, 29, 4);
    const ky = kernel(h, 29, 4);
    const parts: Uint8ClampedArray[] = [];
    for (const [y0, y1] of [
      [0, 10],
      [10, 20],
      [20, 29],
    ]) {
      const { from, to } = sourceRows(ky, y0, y1);
      const slice = src.slice(from * w * 4, (to + 1) * w * 4);
      parts.push(lanczosRows(slice, w, h, 17, 29, 4, y0, y1, from));
    }
    const joined = new Uint8ClampedArray(full.length);
    let at = 0;
    parts.forEach((part) => {
      joined.set(part, at);
      at += part.length;
    });
    expect(Array.from(joined)).toEqual(Array.from(full));
  });

  it('검정·흰 경계를 키우면 바이리니어보다 또렷하다 (경계 옆 픽셀이 더 극단)', () => {
    const src = new Uint8ClampedArray(8 * 1 * 4);
    for (let x = 0; x < 8; x++) src.set(x < 4 ? [0, 0, 0, 255] : [255, 255, 255, 255], x * 4);
    const out = lanczosResize(src, 8, 1, 32, 1, 3);
    // 경계 바로 왼쪽(14번)은 바이리니어라면 회색에 가깝지만 Lanczos는 어둡게 남는다
    expect(out[13 * 4]).toBeLessThan(40);
    expect(out[18 * 4]).toBeGreaterThan(215);
  });
});

describe('대비·화질 개선 설정 (Android 1.0.1~1.0.2)', () => {
  it('Lanczos 창 크기', () => {
    expect(lanczosWindow('LANCZOS2')).toBe(2);
    expect(lanczosWindow('LANCZOS')).toBe(3);
    expect(lanczosWindow('LANCZOS4')).toBe(4);
    expect(lanczosWindow('SHARPEN')).toBeNull();
  });

  it('filter: 선명하게 + 대비, 100%면 대비 없음', () => {
    expect(imageFilter('OFF', 100)).toBeUndefined();
    expect(imageFilter('SHARPEN', 130)).toBe('url(#ktv-sharpen) contrast(1.3)');
    expect(imageFilter('LANCZOS', 80)).toBe('contrast(0.8)');
  });

  it('대비 저장 값: 50~150, 5% 단위, 기본 100', () => {
    expect(defaultReaderSettings.contrastPercent).toBe(100);
    expect(parseReaderSettings({ contrastPercent: 130 }).contrastPercent).toBe(130);
    expect(parseReaderSettings({ contrastPercent: 133 }).contrastPercent).toBe(135);
    expect(parseReaderSettings({ contrastPercent: 300 }).contrastPercent).toBe(150);
    expect(parseReaderSettings({ contrastPercent: 10 }).contrastPercent).toBe(50);
    expect(parseReaderSettings({ imageEnhancement: 'LANCZOS4' }).imageEnhancement).toBe('LANCZOS4');
  });
});
