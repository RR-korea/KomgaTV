// 펼침 크기 계산 (Komelia ScaleType 규칙). 한 펼침의 쪽들은 같은 높이로 나란히 놓는다.
// 작은 이미지도 늘 맞춰 키운다 (Android 0.2.9에서 "작은 이미지 키우기" 설정 제거).
import { MAX_ZOOM_PERCENT, MIN_ZOOM_PERCENT, type ScaleType } from '../state/readerPrefs';

export { MAX_ZOOM_PERCENT, MIN_ZOOM_PERCENT };

export interface Size {
  width: number;
  height: number;
}

export interface SpreadLayout {
  /** 쪽마다 표시 크기 (펼침 순서 그대로) */
  pages: Size[];
  width: number;
  height: number;
}

/** 크기를 모르는 쪽은 흔한 만화 비율로 둔다 (불러오면 실제 크기로 다시 잰다) */
export const FALLBACK_SIZE: Size = { width: 1000, height: 1500 };

export function layoutSpread(sizes: Size[], scale: ScaleType, screen: Size): SpreadLayout {
  if (!sizes.length) return { pages: [], width: 0, height: 0 };
  const ratios = sizes.map((size) => (size.height > 0 ? size.width / size.height : 1));
  const totalRatio = ratios.reduce((sum, ratio) => sum + ratio, 0) || 1;
  const naturalHeight = Math.max(...sizes.map((size) => size.height));

  let height: number;
  switch (scale) {
    case 'SCREEN':
      height = Math.min(screen.height, screen.width / totalRatio);
      break;
    case 'FIT_WIDTH':
      height = screen.width / totalRatio;
      break;
    case 'FIT_HEIGHT':
      height = screen.height;
      break;
    case 'ORIGINAL':
      height = naturalHeight;
      break;
  }
  height = Math.round(height);

  const pages = ratios.map((ratio) => ({ width: Math.round(ratio * height), height }));
  return { pages, width: pages.reduce((sum, page) => sum + page.width, 0), height };
}

// ---------- 확대(줌인), Android 0.2.5 "작품별 확대" (0.2.8부터 100~120%) ----------
/** 화면 고정 모드에서 ▲▼ 한 번에 움직이는 양 — 넘치는 높이의 절반을 이만큼 나눈다 */
export const PAN_STEPS = 2;

export function clampZoom(percent: number): number {
  return Math.min(MAX_ZOOM_PERCENT, Math.max(MIN_ZOOM_PERCENT, Math.round(percent)));
}

/** 크기 맞춤 결과를 펼침째(두 쪽이면 함께) 키운다 */
export function zoomLayout(layout: SpreadLayout, zoomPercent: number): SpreadLayout {
  const zoom = clampZoom(zoomPercent) / 100;
  if (zoom === 1) return layout;
  const pages = layout.pages.map((page) => ({ width: Math.round(page.width * zoom), height: Math.round(page.height * zoom) }));
  return { pages, width: pages.reduce((sum, page) => sum + page.width, 0), height: Math.round(layout.height * zoom) };
}

/**
 * 펼침의 화면 위치.
 * - 확대 전 펼침이 화면 높이 안이면(화면·높이 맞춤): 가운데 기준, ▲▼(panStep −2…2)로 넘친 위아래를 반씩 본다
 * - 확대 전에도 넘치면(너비 맞춤·원본): 위쪽 기준, scrollY로 끝까지 스크롤
 * 좌우로 넘치는 부분은 가운데 기준으로 잘린다.
 */
export function spreadPosition(
  base: SpreadLayout,
  zoomed: SpreadLayout,
  screen: Size,
  panStep: number,
  scrollY: number,
): { left: number; top: number } {
  const left = (screen.width - zoomed.width) / 2;
  if (base.height <= screen.height) {
    const overflow = Math.max(0, zoomed.height - screen.height);
    const top = (screen.height - zoomed.height) / 2 + (-panStep / PAN_STEPS) * (overflow / 2);
    return { left, top };
  }
  return { left, top: -scrollY };
}
