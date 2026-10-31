import { describe, expect, it } from 'vitest';
import { toAppKey } from '../src/platform/keys';

const event = (keyCode: number) => ({ keyCode }) as KeyboardEvent;

describe('toAppKey', () => {
  it('Tizen 리모컨 키 코드', () => {
    expect(toAppKey(event(10009))).toBe('back');
    expect(toAppKey(event(13))).toBe('enter');
    expect(toAppKey(event(37))).toBe('left');
    expect(toAppKey(event(10252))).toBe('playPause');
  });

  it('PC 개발용 Escape도 back', () => {
    expect(toAppKey(event(27))).toBe('back');
  });

  it('모르는 키는 null', () => {
    expect(toAppKey(event(65))).toBeNull();
  });
});
