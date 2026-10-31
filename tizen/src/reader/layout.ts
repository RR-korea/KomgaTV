// 펼침 크기 계산 (Komelia ScaleType·stretchToFit 규칙). 한 펼침의 쪽들은 같은 높이로 나란히 놓는다.
import type { ScaleType } from '../state/readerPrefs';

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

export function layoutSpread(sizes: Size[], scale: ScaleType, stretchToFit: boolean, screen: Size): SpreadLayout {
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
  // 작은 이미지 키우기가 꺼져 있으면 원본보다 크게 그리지 않는다
  if (!stretchToFit) height = Math.min(height, naturalHeight);
  height = Math.round(height);

  const pages = ratios.map((ratio) => ({ width: Math.round(ratio * height), height }));
  return { pages, width: pages.reduce((sum, page) => sum + page.width, 0), height };
}
