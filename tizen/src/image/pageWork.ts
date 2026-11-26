// 쪽 그림 처리 순서 (Android ReaderScreen.pageRequest, 1.0.45): 여백 잘라내기 → 칸 간격 없애기 → 남은 위아래 빈 줄만 한 번 더.
// 칸을 먼저 붙이면 칸 밖 쪽 번호·광고가 칸 바로 밑으로 끌려와 칸 밖 글로 보이지 않아서, 자르기가 먼저다.
// 순수 함수 — Worker(pageWorker.ts)와 테스트가 함께 쓴다. 픽셀은 ARGB 정수 Int32Array.
import { findBorders } from './cropBorders';
import { collapseGutters } from './gutters';

/** 여백 잘라내기: 끄기 / 위아래 / 좌우 / 네 변 (Android CropBorders) */
export type CropBorders = 'OFF' | 'TOP_BOTTOM' | 'LEFT_RIGHT' | 'ALL';
export const CROP_BORDERS: CropBorders[] = ['OFF', 'TOP_BOTTOM', 'LEFT_RIGHT', 'ALL'];

export interface PagePixels {
  pixels: Int32Array;
  width: number;
  height: number;
}

/** [위, 아래, 왼쪽, 오른쪽] 만큼 잘라낸 쪽 */
export function cropPixels(page: PagePixels, borders: number[]): PagePixels {
  const [top, bottom, left, right] = borders;
  const width = page.width - left - right;
  const height = page.height - top - bottom;
  const pixels = new Int32Array(width * height);
  for (let y = 0; y < height; y++) {
    const from = (y + top) * page.width + left;
    pixels.set(page.pixels.subarray(from, from + width), y * width);
  }
  return { pixels, width, height };
}

/** 쪽 하나를 설정대로 처리한다. 바뀐 것이 없으면 받은 것을 그대로 돌려준다 */
export function processPage(page: PagePixels, crop: CropBorders, removeGutters: boolean): PagePixels {
  let current = page;
  const topBottom = crop !== 'LEFT_RIGHT';
  const leftRight = crop !== 'TOP_BOTTOM';
  if (crop !== 'OFF') {
    const borders = findBorders(current.pixels, current.width, current.height, topBottom, leftRight);
    if (borders) current = cropPixels(current, borders);
  }
  if (removeGutters) {
    const collapsed = collapseGutters(current.pixels, current.width, current.height);
    if (collapsed) current = { pixels: collapsed, width: current.width, height: current.height };
    if (crop !== 'OFF') {
      const blank = findBorders(current.pixels, current.width, current.height, topBottom, leftRight, true);
      if (blank) current = cropPixels(current, blank);
    }
  }
  return current;
}

/** 캔버스 RGBA 바이트 → ARGB 정수 (리틀 엔디언 기기) */
export function rgbaToArgb(data: Uint8ClampedArray): Int32Array {
  const source = new Uint32Array(data.buffer, data.byteOffset, data.byteLength >> 2);
  const out = new Int32Array(source.length);
  for (let i = 0; i < source.length; i++) {
    const v = source[i]; // A B G R (높은 바이트부터)
    out[i] = (v & 0xff00ff00) | ((v & 0xff) << 16) | ((v >> 16) & 0xff);
  }
  return out;
}

/** ARGB 정수 → 캔버스 RGBA 바이트 */
export function argbToRgba(pixels: Int32Array): Uint8ClampedArray {
  const out = new Uint8ClampedArray(pixels.length * 4);
  const target = new Uint32Array(out.buffer);
  for (let i = 0; i < pixels.length; i++) {
    const v = pixels[i];
    target[i] = (v & 0xff00ff00) | ((v & 0xff) << 16) | ((v >> 16) & 0xff);
  }
  return out;
}

/** 쪽 그림 처리 설정 — 하나라도 켜져 있어야 처리한다 */
export interface PageWorkOptions {
  crop: CropBorders;
  removeGutters: boolean;
  /** 처리할 크기의 한도 (화면에서 쪽이 차지할 칸) */
  boxWidth: number;
  boxHeight: number;
}

/** 세로로 긴 쪽(너비 맞춤·원본)은 화면 높이의 이만큼까지 (Android MAX_TALL_SCREENS — 메모리) */
const MAX_TALL_SCREENS = 3;

/**
 * 처리할 크기의 한도 (Android pageRequest 의 디코딩 크기와 같음): 화면 맞춤 = 칸 × 화면 높이,
 * 높이 맞춤 = 칸 두 배 × 화면 높이, 너비 맞춤 = 칸 × 화면 높이 세 배, 원본 = 칸 두 배 × 화면 높이 세 배.
 * 칸은 두 쪽 펼침이면 화면 절반.
 */
export function workBox(
  scaleType: 'SCREEN' | 'FIT_WIDTH' | 'FIT_HEIGHT' | 'ORIGINAL',
  pagesInSpread: number,
  screen: { width: number; height: number },
): { width: number; height: number } {
  const slot = pagesInSpread >= 2 ? Math.floor(screen.width / 2) : screen.width;
  switch (scaleType) {
    case 'FIT_HEIGHT':
      return { width: slot * 2, height: screen.height };
    case 'FIT_WIDTH':
      return { width: slot, height: screen.height * MAX_TALL_SCREENS };
    case 'ORIGINAL':
      return { width: slot * 2, height: screen.height * MAX_TALL_SCREENS };
    default:
      return { width: slot, height: screen.height };
  }
}

/** 원본을 한도 안에 맞춘 크기 — 줄이기만 하고 키우지 않는다 (2020년형 TV에서 원본 크기 그대로는 느리고 무겁다) */
export function workSize(
  sourceWidth: number,
  sourceHeight: number,
  boxWidth: number,
  boxHeight: number,
): { width: number; height: number } {
  const scale = Math.min(boxWidth / sourceWidth, boxHeight / sourceHeight, 1);
  return { width: Math.max(1, Math.round(sourceWidth * scale)), height: Math.max(1, Math.round(sourceHeight * scale)) };
}
