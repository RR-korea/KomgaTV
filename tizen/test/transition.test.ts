import { describe, expect, it } from 'vitest';
import { chooseTransition, slideFrom } from '../src/reader/transition';

describe('화면 전환 효과 (Android 0.2.9)', () => {
  it('한 칸 넘길 때만, 연타·안 받은 쪽·점프는 바로', () => {
    expect(chooseTransition('SLIDE', 1, false, true)).toBe('SLIDE');
    expect(chooseTransition('FADE', -1, false, true)).toBe('FADE');
    expect(chooseTransition('SLIDE', 0, false, true)).toBe('OFF');
    expect(chooseTransition('SLIDE', 1, true, true)).toBe('OFF');
    expect(chooseTransition('SLIDE', 1, false, false)).toBe('OFF');
    expect(chooseTransition('OFF', 1, false, true)).toBe('OFF');
  });

  it('밀기는 읽기 방향을 따른다', () => {
    expect(slideFrom(1, false)).toBe(1); // 왼쪽→오른쪽: 다음 펼침은 오른쪽에서
    expect(slideFrom(-1, false)).toBe(-1);
    expect(slideFrom(1, true)).toBe(-1); // 오른쪽→왼쪽: 다음 펼침은 왼쪽에서
    expect(slideFrom(-1, true)).toBe(1);
  });
});
