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

  it('좌/우는 같은 줄(겹침)의 후보만 — 다른 줄의 더 가까운 후보는 아니다', () => {
    expect(score(from, rect(1200, 500), 'right')).not.toBeNull();
    expect(score(from, rect(750, 900), 'right')).toBeNull();
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

  it('좌/우는 같은 줄에서만 — 위 버튼에서 왼쪽을 눌러 아래 카드로 가지 않는다', () => {
    const topButton = rect(1500, 40, 150, 80);
    expect(score(topButton, rect(100, 200), 'left')).toBeNull();
    expect(score(topButton, rect(1300, 40, 150, 80), 'left')).not.toBeNull();
  });

  it('확대된 포커스 카드 옆 카드는 위/아래 후보가 아니다 (중심이 몇 px 달라도)', () => {
    const focusedCard = { left: 89, top: 46, right: 343, bottom: 542 }; // 1.06배
    const neighbour = { left: 375, top: 61, right: 615, bottom: 529 }; // 보통 크기, 중심이 1px 위
    expect(score(focusedCard, neighbour, 'up')).toBeNull();
    expect(score(focusedCard, neighbour, 'down')).toBeNull();
  });

  it('위로는 다른 줄 후보도 받는다 (카드 → 위 버튼)', () => {
    expect(score(rect(100, 200), rect(1500, 40, 150, 80), 'up')).not.toBeNull();
  });

  it('넓은 입력칸에서 아래 → 버튼 줄의 첫 버튼', () => {
    const field = rect(360, 300, 1200, 100);
    const first = score(field, rect(360, 500, 260, 80), 'down')!;
    const second = score(field, rect(650, 500, 400, 80), 'down')!;
    expect(first).toBeLessThan(second);
  });
});
