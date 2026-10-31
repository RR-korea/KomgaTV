// 세로 스크롤(웹툰) 리더 배치 계산 (Komelia CONTINUOUS, Android T6과 같은 규칙). 순수 함수 — 테스트한다.
// Komelia(Apache-2.0) ContinuousReaderState.kt 규칙 — tizen/THIRD_PARTY_NOTICES.md
// 화면 위치는 "기준 쪽 + 그 쪽 안의 거리"로 들고 다닌다. 위쪽 쪽의 실제 크기를 늦게 알아 높이가 바뀌어도 보던 곳이 흔들리지 않는다.
import type { Size } from './layout';

export interface PagePlacement {
  top: number;
  left: number;
  width: number;
  height: number;
}

export interface ContinuousLayout {
  pages: PagePlacement[];
  /** 전체 높이 */
  total: number;
}

export interface Anchor {
  /** 0부터인 쪽 인덱스 */
  index: number;
  /** 그 쪽 맨 위에서 화면 맨 위까지 거리(px, 0 이상) */
  offset: number;
}

/** dp → 화면 px. 1080p TV의 Android 밀도(xhdpi = 2)와 맞춘다. */
export const DP = 2;

export function layoutContinuous(
  sizes: Size[],
  screen: Size,
  padding: number,
  spacingDp: number,
  stretchToFit: boolean,
): ContinuousLayout {
  const columnWidth = Math.round(screen.width * (1 - 2 * padding));
  const spacing = spacingDp * DP;
  const pages: PagePlacement[] = [];
  let top = 0;
  sizes.forEach((size, i) => {
    // 작은 이미지 키우기가 꺼져 있으면 원본 너비보다 넓히지 않는다
    const width = stretchToFit ? columnWidth : Math.min(columnWidth, size.width);
    const height = Math.max(1, Math.round((width * size.height) / Math.max(1, size.width)));
    if (i > 0) top += spacing;
    pages.push({ top, left: Math.round((screen.width - width) / 2), width, height });
    top += height;
  });
  return { pages, total: top };
}

export function maxScroll(layout: ContinuousLayout, screenHeight: number): number {
  return Math.max(0, layout.total - screenHeight);
}

export function scrollOf(layout: ContinuousLayout, anchor: Anchor, screenHeight: number): number {
  const page = layout.pages[anchor.index];
  if (!page) return 0;
  return Math.min(maxScroll(layout, screenHeight), Math.max(0, page.top + anchor.offset));
}

/** 화면 위치 → 기준 쪽(화면 맨 위가 걸친 쪽, 쪽 사이 간격이면 다음 쪽). */
export function anchorOf(layout: ContinuousLayout, scroll: number): Anchor {
  const { pages } = layout;
  for (let i = 0; i < pages.length; i++) {
    const bottom = pages[i].top + pages[i].height;
    if (scroll < bottom) return { index: i, offset: Math.max(0, scroll - pages[i].top) };
  }
  const last = Math.max(0, pages.length - 1);
  return { index: last, offset: pages.length ? Math.max(0, scroll - pages[last].top) : 0 };
}

export interface Visible {
  /** 0부터, 화면에 조금이라도 보이는 첫·마지막 쪽 */
  first: number;
  last: number;
  /** 마지막 쪽의 끝이 화면에 들어왔는지 (완독) */
  endReached: boolean;
}

export function visiblePages(layout: ContinuousLayout, scroll: number, screenHeight: number): Visible {
  const bottom = scroll + screenHeight;
  let first = -1;
  let last = -1;
  layout.pages.forEach((page, i) => {
    if (page.top < bottom && page.top + page.height > scroll) {
      if (first < 0) first = i;
      last = i;
    }
  });
  if (first < 0) first = last = anchorOf(layout, scroll).index;
  const end = layout.pages[layout.pages.length - 1];
  return { first, last, endReached: !!end && end.top + end.height <= bottom + 1 };
}

/** 한 화면 아래/위로 (Komelia scrollScreenForward/Backward). */
export function scrollByScreen(layout: ContinuousLayout, scroll: number, screenHeight: number, direction: 1 | -1): number {
  return Math.min(maxScroll(layout, screenHeight), Math.max(0, scroll + direction * screenHeight));
}

/** 좌/우: 다음 쪽 맨 위, 이전은 지금 쪽 중간이면 그 쪽 맨 위·맨 위면 앞 쪽 맨 위. */
export function stepPage(anchor: Anchor, pageCount: number, direction: 1 | -1): Anchor {
  if (direction > 0) return { index: Math.min(pageCount - 1, anchor.index + 1), offset: 0 };
  if (anchor.offset > 0) return { index: anchor.index, offset: 0 };
  return { index: Math.max(0, anchor.index - 1), offset: 0 };
}
