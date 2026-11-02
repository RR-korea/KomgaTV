// Lanczos 리샘플링 (Android 1.0.1 Lanczos.kt와 같은 식). 순수 계산이라 Web Worker와 테스트가 같이 쓴다.
// 창 크기 a — 양쪽 a픽셀씩(2a탭). 2는 빠르고 부드럽고, 3은 흔한 절충, 4는 더 또렷하지만 느리다.
// 가로 → 세로 두 단계. 출력 줄 범위 [y0, y1)만 계산할 수 있어 여러 Worker가 줄을 나눠 한다.

function sinc(x: number): number {
  if (x === 0) return 1;
  const px = Math.PI * x;
  return Math.sin(px) / px;
}

function lanczosWeight(x: number, a: number): number {
  return Math.abs(x) < a ? sinc(x) * sinc(x / a) : 0;
}

/** 한 방향으로 다시 맞출 때 출력 픽셀마다 쓰는 원본 픽셀 범위와 가중치 (가중치는 한 배열에 이어 둔다) */
export interface Kernel {
  start: Int32Array;
  count: Int32Array;
  offset: Int32Array;
  weights: Float32Array;
}

export function kernel(src: number, dst: number, a: number): Kernel {
  const scale = dst / src;
  // 줄일 때는 창을 넓혀 원본을 고르게 섞는다 (안 그러면 계단·물결이 생긴다)
  const support = scale < 1 ? a / scale : a;
  const stretch = scale < 1 ? scale : 1;
  const start = new Int32Array(dst);
  const count = new Int32Array(dst);
  const offset = new Int32Array(dst);
  const all: number[] = [];
  for (let i = 0; i < dst; i++) {
    const center = (i + 0.5) / scale - 0.5;
    const from = Math.max(0, Math.floor(center - support + 1));
    const to = Math.min(src - 1, Math.ceil(center + support - 1));
    start[i] = from;
    offset[i] = all.length;
    let sum = 0;
    const first = all.length;
    for (let k = from; k <= to; k++) {
      const w = lanczosWeight((k - center) * stretch, a);
      all.push(w);
      sum += w;
    }
    count[i] = to - from + 1;
    if (sum !== 0) for (let k = first; k < all.length; k++) all[k] /= sum;
  }
  return { start, count, offset, weights: new Float32Array(all) };
}

/** 출력 줄 [y0, y1)이 쓰는 원본 줄 범위 [from, to] — Worker에 그 줄만 넘긴다 */
export function sourceRows(ky: Kernel, y0: number, y1: number): { from: number; to: number } {
  let from = Number.MAX_SAFE_INTEGER;
  let to = -1;
  for (let y = y0; y < y1; y++) {
    from = Math.min(from, ky.start[y]);
    to = Math.max(to, ky.start[y] + ky.count[y] - 1);
  }
  return { from: to < 0 ? 0 : from, to };
}

/**
 * RGBA 픽셀(width×height)을 newWidth×newHeight로 Lanczos(a) 리샘플링해 출력 줄 [y0, y1)만 돌려준다.
 * pixels는 원본 firstRow 줄부터의 일부여도 된다(그 줄들에 필요한 원본 줄만 넘길 때). 필요한 원본 줄만 가로로 먼저 계산한다.
 */
export function lanczosRows(
  pixels: Uint8ClampedArray,
  width: number,
  height: number,
  newWidth: number,
  newHeight: number,
  a: number,
  y0: number,
  y1: number,
  firstRow = 0,
): Uint8ClampedArray {
  const kx = kernel(width, newWidth, a);
  const ky = kernel(height, newHeight, a);
  const { from: srcFrom, to: srcTo } = sourceRows(ky, y0, y1);
  const rows = Math.max(0, srcTo - srcFrom + 1);
  // 1단계: 가로 (채널별 실수)
  const tmp = new Float32Array(rows * newWidth * 4);
  for (let r = 0; r < rows; r++) {
    const rowBase = (srcFrom + r - firstRow) * width * 4;
    const outBase = r * newWidth * 4;
    for (let x = 0; x < newWidth; x++) {
      const s = kx.start[x];
      const n = kx.count[x];
      const o = kx.offset[x];
      let cr = 0;
      let cg = 0;
      let cb = 0;
      let ca = 0;
      for (let k = 0; k < n; k++) {
        const w = kx.weights[o + k];
        const p = rowBase + (s + k) * 4;
        cr += pixels[p] * w;
        cg += pixels[p + 1] * w;
        cb += pixels[p + 2] * w;
        ca += pixels[p + 3] * w;
      }
      const q = outBase + x * 4;
      tmp[q] = cr;
      tmp[q + 1] = cg;
      tmp[q + 2] = cb;
      tmp[q + 3] = ca;
    }
  }
  // 2단계: 세로 (Uint8ClampedArray가 0~255로 자르고 반올림한다)
  const out = new Uint8ClampedArray((y1 - y0) * newWidth * 4);
  for (let y = y0; y < y1; y++) {
    const s = ky.start[y] - srcFrom;
    const n = ky.count[y];
    const o = ky.offset[y];
    const outBase = (y - y0) * newWidth * 4;
    for (let x = 0; x < newWidth; x++) {
      let cr = 0;
      let cg = 0;
      let cb = 0;
      let ca = 0;
      for (let k = 0; k < n; k++) {
        const w = ky.weights[o + k];
        const p = ((s + k) * newWidth + x) * 4;
        cr += tmp[p] * w;
        cg += tmp[p + 1] * w;
        cb += tmp[p + 2] * w;
        ca += tmp[p + 3] * w;
      }
      const q = outBase + x * 4;
      out[q] = cr;
      out[q + 1] = cg;
      out[q + 2] = cb;
      out[q + 3] = ca;
    }
  }
  return out;
}

/** 한 번에 전부 (테스트·Worker가 없을 때) */
export function lanczosResize(
  pixels: Uint8ClampedArray,
  width: number,
  height: number,
  newWidth: number,
  newHeight: number,
  a: number,
): Uint8ClampedArray {
  return lanczosRows(pixels, width, height, newWidth, newHeight, a, 0, newHeight);
}
