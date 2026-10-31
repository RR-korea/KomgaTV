// 리모컨 방향키 공간 내비게이션 (PLAN Z4).
// 포커스 대상 = `.focusable` 요소(tabindex=-1). 방향키를 누르면 화면 좌표에서 그 방향으로 가장 가까운 대상으로 옮긴다.
// `[data-focus-trap]` 안에 포커스가 있으면 그 안에서만 움직인다(패널·대화상자).
// `[data-section]`은 마지막으로 포커스했던 자식을 기억해, 다른 섹션에서 들어올 때 그 자식으로 간다(행·그리드).

export type Direction = 'left' | 'right' | 'up' | 'down';

export interface Rect {
  left: number;
  top: number;
  right: number;
  bottom: number;
}

export const FOCUSABLE_CLASS = 'focusable';
const SELECTOR = `.${FOCUSABLE_CLASS}`;

/** 방향 기준 거리 점수. 작을수록 좋다. 그 방향에 없으면 null. */
export function score(from: Rect, to: Rect, direction: Direction): number | null {
  const fromCenterX = (from.left + from.right) / 2;
  const fromCenterY = (from.top + from.bottom) / 2;
  const toCenterX = (to.left + to.right) / 2;
  const toCenterY = (to.top + to.bottom) / 2;

  let primary: number;
  let overlap: number;
  let offAxis: number;
  switch (direction) {
    case 'left':
      primary = from.left - to.right;
      if (toCenterX >= fromCenterX) return null;
      overlap = Math.min(from.bottom, to.bottom) - Math.max(from.top, to.top);
      offAxis = Math.abs(fromCenterY - toCenterY);
      break;
    case 'right':
      primary = to.left - from.right;
      if (toCenterX <= fromCenterX) return null;
      overlap = Math.min(from.bottom, to.bottom) - Math.max(from.top, to.top);
      offAxis = Math.abs(fromCenterY - toCenterY);
      break;
    case 'up':
      primary = from.top - to.bottom;
      if (toCenterY >= fromCenterY) return null;
      overlap = Math.min(from.right, to.right) - Math.max(from.left, to.left);
      offAxis = Math.abs(fromCenterX - toCenterX);
      break;
    case 'down':
      primary = to.top - from.bottom;
      if (toCenterY <= fromCenterY) return null;
      overlap = Math.min(from.right, to.right) - Math.max(from.left, to.left);
      offAxis = Math.abs(fromCenterX - toCenterX);
      break;
  }
  // 겹치는 후보(같은 줄·같은 열)를 강하게 우선하고, 아니면 축에서 벗어난 거리를 크게 벌준다
  const alignedPenalty = overlap > 0 ? 0 : offAxis * 3 + 1000;
  return Math.max(primary, 0) * 2 + offAxis * 0.5 + alignedPenalty;
}

function isVisible(element: HTMLElement): boolean {
  const rect = element.getBoundingClientRect();
  return rect.width > 0 && rect.height > 0;
}

function focusables(root: ParentNode): HTMLElement[] {
  return Array.from(root.querySelectorAll<HTMLElement>(SELECTOR)).filter(isVisible);
}

function trapOf(element: Element | null): HTMLElement | null {
  return element?.closest<HTMLElement>('[data-focus-trap]') ?? null;
}

/** 현재 포커스된 대상 (focusable이 아니면 null) */
export function current(): HTMLElement | null {
  const active = document.activeElement as HTMLElement | null;
  return active && active.classList.contains(FOCUSABLE_CLASS) ? active : null;
}

/** 포커스를 주고 보이도록 스크롤한다. */
export function focusElement(element: HTMLElement): void {
  element.focus({ preventScroll: true });
  element.scrollIntoView({ block: 'center', inline: 'center', behavior: 'smooth' });
  const section = element.closest<HTMLElement>('[data-section]');
  if (section) section.dataset.lastKey = element.dataset.key ?? '';
}

/** 섹션이 기억한 자식(없으면 첫 자식)에 포커스. 대상이 없으면 false. */
export function focusSection(section: HTMLElement): boolean {
  const key = section.dataset.lastKey;
  const target = (key && findByKey(section, key)) || focusables(section)[0];
  if (!target) return false;
  focusElement(target);
  return true;
}

export function findByKey(root: ParentNode, key: string): HTMLElement | null {
  const all = Array.from(root.querySelectorAll<HTMLElement>(SELECTOR));
  return all.find((element) => element.dataset.key === key && isVisible(element)) ?? null;
}

/** 키 → 그 항목, 없으면 root 안의 첫 대상. 돌아왔을 때 "연 항목"에 포커스하는 규칙(Z4). */
export function focusKeyOrFirst(root: ParentNode, key?: string | null): boolean {
  const target = (key && findByKey(root, key)) || focusables(root)[0];
  if (!target) return false;
  focusElement(target);
  return true;
}

/** 방향키 한 번 처리. 옮겼으면 true. */
export function move(direction: Direction): boolean {
  const from = current();
  const scope: ParentNode = trapOf(from) ?? document;
  if (!from) {
    const first = focusables(scope)[0];
    if (first) focusElement(first);
    return first !== undefined;
  }
  const fromRect = from.getBoundingClientRect();
  const fromSection = from.closest('[data-section]');
  let best: HTMLElement | null = null;
  let bestScore = Infinity;
  for (const candidate of focusables(scope)) {
    if (candidate === from) continue;
    const value = score(fromRect, candidate.getBoundingClientRect(), direction);
    if (value !== null && value < bestScore) {
      best = candidate;
      bestScore = value;
    }
  }
  if (!best) return false;
  // 다른 섹션으로 넘어가면 그 섹션이 기억한 자식으로 간다
  const toSection = best.closest<HTMLElement>('[data-section]');
  if (toSection && toSection !== fromSection && toSection.dataset.lastKey) {
    const remembered = findByKey(toSection, toSection.dataset.lastKey);
    if (remembered) best = remembered;
  }
  focusElement(best);
  return true;
}

/** OK 키: 포커스된 대상을 클릭한 것으로 친다. */
export function activate(): boolean {
  const target = current();
  if (!target) return false;
  target.click();
  return true;
}
