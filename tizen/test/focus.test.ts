import { describe, expect, it } from 'vitest';
import { type Rect, score } from '../src/platform/focus';

const rect = (left: number, top: number, width = 200, height = 300): Rect => ({
  left,
  top,
  right: left + width,
  bottom: top + height,
});

describe('score', () => {
  const from = rect(500, 500);

  it('그 방향이 아닌 후보는 제외한다', () => {
    expect(score(from, rect(100, 500), 'right')).toBeNull();
    expect(score(from, rect(900, 500), 'left')).toBeNull();
    expect(score(from, rect(500, 100), 'down')).toBeNull();
    expect(score(from, rect(500, 900), 'up')).toBeNull();
  });

  it('같은 줄의 가까운 후보가 먼 후보보다 낫다', () => {
    const near = score(from, rect(750, 500), 'right')!;
    const far = score(from, rect(1200, 500), 'right')!;
    expect(near).toBeLessThan(far);
  });

  it('같은 줄(겹침)의 후보가 다른 줄의 더 가까운 후보보다 낫다', () => {
    const sameRowFar = score(from, rect(1200, 500), 'right')!;
    const otherRowNear = score(from, rect(750, 900), 'right')!;
    expect(sameRowFar).toBeLessThan(otherRowNear);
  });

  it('아래로 갈 때 같은 열을 우선한다', () => {
    const sameColumn = score(from, rect(500, 900), 'down')!;
    const diagonal = score(from, rect(800, 850), 'down')!;
    expect(sameColumn).toBeLessThan(diagonal);
  });

  it('겹치는 후보 중에서는 시작 가장자리가 맞는 쪽', () => {
    const aligned = score(from, rect(500, 900), 'down')!;
    const shifted = score(from, rect(600, 900), 'down')!;
    expect(aligned).toBeLessThan(shifted);
  });

  it('넓은 입력칸에서 아래 → 버튼 줄의 첫 버튼', () => {
    const field = rect(360, 300, 1200, 100);
    const first = score(field, rect(360, 500, 260, 80), 'down')!;
    const second = score(field, rect(650, 500, 400, 80), 'down')!;
    expect(first).toBeLessThan(second);
  });
});
