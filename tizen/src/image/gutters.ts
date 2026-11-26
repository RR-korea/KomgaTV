// 칸 간격 없애기 (Android Gutters.kt, cacheKey gutters-24 를 그대로 옮김 — 1.0.12~1.0.43).
// 칸(패널) 사이 위아래 여백을 없애 칸끼리 붙이고, 칸들을 쪽 가운데(세로)로 모은다. 좌우 여백은 그대로.
// 쪽 크기는 바꾸지 않는다 — 비는 곳은 원래 여백의 색(흰색·검정·망점의 대표 색)으로 채운다.
//
// 1. 쪽 전체 폭을 가로지르는 여백으로 쪽을 가로 띠로 나누고, 띠끼리 붙여 쪽 가운데로 모은다.
// 2. 띠 안이 세로 칸 묶음 여럿이면(옆에 세로로 긴 칸이 있는 작은 칸들) 묶음마다 위아래 여백을 붙이고
//    쪽 가운데 쪽으로 민다 — 위쪽 띠는 아래로, 아래쪽 띠는 위로. 비는 곳은 쪽 바깥쪽에 생긴다.
// 3. 그래도 붙일 줄이 없는 띠·묶음은 비스듬하거나 계단처럼 엇갈린 여백을 찾는다 (findRibbons) —
//    열마다 빼는 자리는 달라도 빼는 줄 수는 같아서 아래 칸이 일그러지지 않고 통째로 붙는다.
//
// 원칙(사용자): 덜 줄어드는 것은 참을 수 있지만 그림이 잘리는 것은 안 된다. 특정 작품·쪽에 맞춘 예외 규칙은 두지 않는다.
//
// Kotlin 과 한 픽셀도 다르지 않게 하려고 Float 계산(Math.fround), 정수 나눗셈, 안정 정렬, java.util.HashMap 의
// 돌아보는 순서(JHashMap)까지 맞췄다 — dev/ref 로 Kotlin 결과와 실제 쪽을 비교한다 (docs/PLAN.md).
// 픽셀은 ARGB 정수(부호 있는 32비트) Int32Array.

const F = Math.fround;
/** Kotlin 의 Int × Float (결과 Float) */
const fmul = (a: number, b: number): number => Math.fround(Math.fround(a) * b);
/** Kotlin Float.roundToInt() */
const roundF = (value: number): number => Math.round(value);
/** Kotlin 의 Int 나눗셈 (0 쪽으로 버림) — 값은 늘 32비트 안 */
const idiv = (a: number, b: number): number => (a / b) | 0;

const MAX_INT = 2147483647;

/** 왼쪽·오른쪽 끝에서 이만큼은 보지 않는다 (스캔 테두리) */
const EDGE_RATIO = F(0.02);
/** 쪽 끝 열에서 쪽 높이의 이만큼보다 길게 이어지는 잉크는 스캔 테두리 */
const SCAN_EDGE_RUN_RATIO = F(0.6);
/** 바탕과 이만큼 다르면 잉크 */
const INK_DIFF = 80;
/** 쪽 높이(너비)의 이만큼 이어지는 잉크라야 칸 — 여백의 글자는 이보다 짧다 */
const LONG_INK_RATIO = F(0.04);
/** 여백을 가로지르는 것이 이 폭(비율)보다 좁으면 글 상자·말풍선으로 본다 */
const OVERLAY_SPAN_RATIO = F(0.3);
/** 여백 후보의 획이 이만큼 좁은 곳(OVERLAY_SPAN_RATIO 미만)에 몰려 있으면 걸친 효과음·말풍선 */
const OVERLAY_STROKE_SHARE = 0.9;
/** 글 상자 안 줄에 이만큼까지의 짧은 잉크는 잡티로 본다 */
const OVERLAY_NOISE = 2;
/** 쪽 번호·짧은 역자 주를 넘는 잉크 가로 폭 비율 (쪽 너비의 8%) */
const SIGNIFICANT_SPAN_RATIO = F(0.08);
/** 쪽 번호·짧은 역자 주를 넘는 줄 내 획 비율 (4%) */
const SIGNIFICANT_STROKE_RATIO = F(0.04);
/** 고른 색 줄을 볼 때 흐리게 하는 반경 (5칸) — 망점이 고른 회색이 된다 */
const BLUR_RADIUS = 2;
/** 가운데 밝기와 이만큼 넘게 다르면 튀는 칸 */
const FLAT_DIFF = 30;
/** 튀는 칸이 이 비율 이하면 고른 줄 (검은 틈·망점 틈) */
const FLAT_OUTLIER_RATIO = F(0.03);
/** 고른 색 빈 곳이 쪽 높이의 이만큼보다 얇으면 틈이 아니라 칸 테두리 */
const MIN_FLAT_GAP_RATIO = F(0.003);
/** 고른 색 빈 곳이 쪽 높이의 이만큼보다 길면 틈이 아니라 그림 속 고른 곳 */
const MAX_FLAT_GAP_RATIO = F(0.06);
/** 가로로 긴 잉크가 줄의 이만큼을 덮으면 칸 테두리가 지나가는 줄 */
const BORDER_ROW_RATIO = F(0.1);
/** 그림 획이 줄의 이만큼을 덮으면 칸 내용이 지나가는 줄 */
const STROKE_ROW_RATIO = F(0.1);
/** 여백 후보의 줄 중 이만큼은 빈 줄이어야 여백 — 글 쪽·한 장 그림은 빈 줄이 드물다 */
const CLEAN_GAP_RATIO = F(0.6);
/** 빈 종이 줄에서 종이 색보다 이만큼 어두우면 그림 톤·텍스처 */
const PAPER_ROW_DIFF = 18;
/** 빈 종이 줄에서 그림 톤 픽셀이 이 비율 이하이어야 진짜 빈 줄 */
const PAPER_ROW_MAX_NONWHITE = F(0.08);
/** 획이 이 비율 이하인 줄은 빈 줄 (잡티·망점 틈) */
const CLEAN_ROW_RATIO = F(0.01);
/** 여백 후보 전체에서 획이 평균 이 비율을 넘으면 여백이 아니다 */
const GAP_STROKE_RATIO = F(0.1);
/** 칸 묶음 안에서 여백으로 볼 가장 짧은 빈 곳 — 그림 속 흰 줄은 이보다 짧다 */
const MIN_STACK_GAP_RATIO = F(0.003);
/** 띠 높이의 이만큼 넘게 잉크인 열은 칸 세로 테두리 */
const BORDER_COLUMN_INK = F(0.9);
/** 맞닿는 칸 테두리를 이만큼(쪽 높이 비율)까지 겹친다 — 칸 테두리 굵기 */
const FRAME_OVERLAP_RATIO = F(0.003);
/** 맞닿는 칸은 이만큼(쪽 높이 비율)까지 겹친다 — 남은 여백 끝자락 + 테두리 */
const MAX_OVERLAP_RATIO = F(0.012);
/** 위아래 둘 다 MAX_OVERLAP_RATIO 안에 테두리(잉크)가 있는 열이 이만큼은 돼야 겹친다 */
const MIN_FRAMED_RATIO = F(0.5);
/** 열마다 겹쳐도 되는 줄 수 중 이 자리(아래에서부터 비율) 값만큼 겹친다 */
const OVERLAP_PERCENTILE = F(0.1);
/** 칸 덩어리는 쪽 너비의 이만큼, 높이의 MIN_CELL_HEIGHT_RATIO 만큼은 된다 — 더 작으면 글자·티끌 */
const MIN_CELL_WIDTH_RATIO = F(0.1);
const MIN_CELL_HEIGHT_RATIO = F(0.05);
/** 칸은 둘레 상자를 이만큼은 채운다 (네모, 기울어진 사다리꼴) */
const MIN_CELL_FILL = F(0.65);
/** 칸들이 띠 넓이를 이만큼은 덮어야 칸 짜임새 */
const MIN_CELLS_COVER = F(0.7);
/** 칸 위 테두리는 이웃 열끼리 이만큼 매끈하게 이어진다 */
const CELL_FRAME_SMOOTH_RATIO = F(0.8);
/** 칸 좌우 끝 이만큼(쪽 너비 비율)에서 여백에 맞닿은 글자는 쪽 끝으로 열린 말풍선 속 글자로 본다 */
const CELL_SIDE_RATIO = F(0.06);
/** 칸 위 테두리 가운데 패인 열이 칸 너비의 이만큼 넘으면 테두리가 끊긴 칸 */
const CELL_NOTCH_RATIO = F(0.02);
/** 칸 위 테두리는 칸 너비의 이만큼은 된다 */
const CELL_FRAME_WIDTH_RATIO = F(0.3);
/** 칸 묶음 안 칸 조각은 쪽 높이의 이만큼은 된다 */
const MIN_STACK_PART_RATIO = F(0.02);
/** 칸 묶음 사이 세로 여백은 잉크가 이 비율 이하 */
const MAX_COLUMN_GAP_INK = F(0.05);
/** 칸 묶음은 쪽 너비의 이만큼은 돼야 한다 */
const MIN_STACK_WIDTH_RATIO = F(0.1);
/** 칸 묶음 안 줄에 잉크가 이 비율을 넘으면 칸 */
const STACK_INK_RATIO = F(0.01);
/** 이보다 짧은 빈 곳은 이미 붙어 있는 것으로 보고 둔다 */
const MIN_GAP_RATIO = F(0.002);
/** 열의 종이색은 쪽 바탕색보다 이만큼까지만 어둡다 (가운데 접힌 곳 그림자) */
const MAX_SHADE_DIFF = 100;
/** 바탕이 이만큼 밝은 쪽만 열마다 종이색을 따로 본다 (검은 바탕 쪽은 쪽 바탕색 그대로) */
const MIN_COLUMN_PAPER_BG = 128;
/** 열의 종이색은 밝은 쪽에서 이 비율 자리의 밝기 */
const COLUMN_PAPER_SHARE = F(0.95);
const ONE_MINUS_PAPER_SHARE = F(1 - COLUMN_PAPER_SHARE);
/** 비스듬한 여백 띠는 양 끝 이 폭(칸 묶음 폭 비율) 안에서 늦게 시작하거나 일찍 끝나도 된다 (접힌 곳 그림자, 쪽 여백) */
const RIBBON_EDGE_RATIO = F(0.08);
/** 띠가 양 끝에서 덜 지난 열마다의 값 — 긴 구간을 지나서라도 끝까지 이어진 길을 고른다 */
const RIBBON_EDGE_COST = 2000;
/** 비스듬한 여백 띠는 칸 사이를 이만큼 남기고 뺀다 */
const RIBBON_KEEP = 2;
/** 비스듬한 여백 띠의 양 끝은 짧은 구간이 매끈하게 이만큼 이어지는 곳 */
const RIBBON_ANCHOR = 8;
/** 띠가 지나지 못한 양 끝 열에서 뺄 자리는 밝기 차이가 이 이하로 밋밋해야 한다 */
const RIBBON_FLAT_DIFF = 48;
/** 비스듬한 여백 띠는 열의 이만큼 이상이 칸 사이처럼 짧고 막힌 빈 구간이어야 한다 */
const RIBBON_SHORT_RATIO = F(0.6);
/** 비스듬한 여백 띠가 지나는 긴 구간(엇갈린 칸 사이 세로 여백) 열은 이 비율까지 */
const RIBBON_MAX_LONG_RATIO = F(0.05);
/** 비스듬한 여백 띠는 왼쪽 끝에서 오른쪽 끝까지 이 기울기보다 덜 오르내린다 (칸 테두리의 기울기) */
const RIBBON_MAX_SLOPE = F(0.2);
/** 비스듬한 여백 띠의 위아래 끝은 이웃 열끼리 이만큼까지만 달라진다 (칸 테두리의 기울기) */
const RIBBON_STEP = 2;
/** 이웃 열 끝이 매끈하게(RIBBON_STEP) 이어지는 비율 — 그림 속 빈 곳은 들쭉날쭉하다 */
const RIBBON_SMOOTH_RATIO = F(0.8);
/** 비스듬한 여백 띠가 긴 빈 구간(엇갈린 칸의 세로 여백)을 지날 때의 값 — 짧은 구간으로 이어지는 길을 고른다 */
const RIBBON_LONG_COST = 1000;
/** 칸 테두리 사이 여백에서 획이 지나는 열이 이 비율보다 적으면 쪽 번호 같은 작은 덩어리들 */
const STRAY_COLUMN_RATIO = F(0.15);
/** 칸 묶음 안 여백 양 끝에서 떼어 낼 기울어진 테두리 끝자락 줄 수 */
const STRAY_SLACK = 4;
/** 너비가 높이의 이만큼을 넘으면 좌우 두 쪽을 한 장에 스캔한 그림일 수 있다 */
const MIN_SPREAD_ASPECT = F(1.15);
/** 두 쪽 사이 접힌 곳은 가운데에서 너비의 이만큼 안에서 찾는다 */
const SPINE_SEARCH_RATIO = F(0.08);
/** 접힌 곳을 지나는 가로로 긴 잉크가 쪽 높이의 이만큼 넘으면 두 쪽에 걸친 한 장 그림 */
const MAX_SPINE_CROSS_RATIO = F(0.02);
/** 지울 여백이 이보다 적으면 그대로 */
const MIN_SAVED_RATIO = F(0.005);

/** 닫힌 정수 구간 [first, last] (Kotlin IntRange) */
interface Range {
  first: number;
  last: number;
}

const luminance = (c: number): number =>
  idiv(((c >> 16) & 0xff) * 299 + ((c >> 8) & 0xff) * 587 + (c & 0xff) * 114, 1000);

function luminanceMap(pixels: Int32Array): Uint8Array {
  const lum = new Uint8Array(pixels.length);
  for (let i = 0; i < pixels.length; i++) lum[i] = luminance(pixels[i]);
  return lum;
}

/** 같은 값은 처음 순서 그대로 (Chrome 69 의 Array.sort 는 안정 정렬이 아니다) */
function stableSortBy<T>(items: T[], key: (item: T) => number, descending = false): T[] {
  return items
    .map((item, index) => ({ item, index, value: key(item) }))
    .sort((a, b) => (a.value === b.value ? a.index - b.index : descending ? b.value - a.value : a.value - b.value))
    .map((entry) => entry.item);
}

/**
 * 좌우 두 쪽을 한 장에 스캔한 그림은 가운데에서 나눠 쪽마다 따로 붙이고 다시 합친다.
 * @returns 바꾼 픽셀, 바꿀 것이 거의 없으면 null
 */
export function collapseGutters(pixels: Int32Array, width: number, height: number): Int32Array | null {
  if (width < 8 || height < 32) return null;
  const spine = spreadSpine(pixels, width, height);
  if (spine === null) return collapsePage(pixels, width, height);
  const w0 = spine;
  const w1 = width - spine;
  const half0 = new Int32Array(w0 * height);
  const half1 = new Int32Array(w1 * height);
  for (let y = 0; y < height; y++) {
    half0.set(pixels.subarray(y * width, y * width + w0), y * w0);
    half1.set(pixels.subarray(y * width + spine, y * width + width), y * w1);
  }
  const done0 = collapsePage(half0, w0, height);
  const done1 = collapsePage(half1, w1, height);
  // 원칙: 덜 줄어드는 것은 참을 수 있지만, 그림·글자가 잘리거나 칸이 망가지는 것은 절대 안 된다.
  // 좌우 펼침면에서 한쪽만 줄어들면 양쪽 칸 높이가 심각하게 어긋나므로 둘 다 줄어들 때만 합친다.
  if (!done0 || !done1) return null;
  const dst = pixels.slice();
  for (let y = 0; y < height; y++) {
    dst.set(done0.subarray(y * w0, y * w0 + w0), y * width);
    dst.set(done1.subarray(y * w1, y * w1 + w1), y * width + spine);
  }
  return dst;
}

/**
 * 좌우 두 쪽을 한 장에 스캔한 그림의 가운데(접힌 곳) 열 — 가로로 넓은 그림의 가운데 SPINE_SEARCH_RATIO 안에서
 * 가로로 긴 잉크(칸 테두리, 두 쪽에 걸친 그림)가 가장 적게 지나는 열. 그것도 쪽 높이의 MAX_SPINE_CROSS_RATIO 보다
 * 많이 지나면(두 쪽에 걸친 한 장 그림) null — 나누면 그림이 어긋난다.
 */
function spreadSpine(pixels: Int32Array, width: number, height: number): number | null {
  if (width < fmul(height, MIN_SPREAD_ASPECT)) return null;
  const lum = luminanceMap(pixels);
  const bg = backgroundLuminance(lum);
  const paper = bg >= MIN_COLUMN_PAPER_BG ? columnPaper(lum, width, height, bg) : null;
  const from = roundF(fmul(width, F(F(0.5) - SPINE_SEARCH_RATIO)));
  const to = roundF(fmul(width, F(F(0.5) + SPINE_SEARCH_RATIO)));
  const minRun = Math.max(8, roundF(fmul(width, LONG_INK_RATIO)));
  // 줄마다 가운데 둘레의 긴 가로 잉크가 지나는 열을 센다
  const cross = new Int32Array(width);
  const xFrom = Math.max(0, from - minRun);
  const xTo = Math.min(width, to + minRun);
  for (let y = 0; y < height; y++) {
    let start = -1;
    for (let x = xFrom; x <= xTo; x++) {
      let ink = false;
      if (x < width) {
        const v = lum[y * width + x];
        ink = paper ? v < paper[x] - INK_DIFF : Math.abs(v - bg) > INK_DIFF;
      }
      if (ink) {
        if (start < 0) start = x;
      } else if (start >= 0) {
        if (x - start >= minRun) {
          const k1 = Math.min(x, to);
          for (let k = Math.max(start, from); k < k1; k++) cross[k]++;
        }
        start = -1;
      }
    }
  }
  const middle = idiv(width, 2);
  let spine = from;
  for (let x = from + 1; x < to; x++) {
    if (cross[x] < cross[spine] || (cross[x] === cross[spine] && Math.abs(x - middle) < Math.abs(spine - middle))) spine = x;
  }
  return cross[spine] <= fmul(height, MAX_SPINE_CROSS_RATIO) ? spine : null;
}

/** 한 쪽의 칸 사이 여백을 붙인다 (collapseGutters) */
function collapsePage(pixels: Int32Array, width: number, height: number): Int32Array | null {
  const page = new PageMaps(pixels, width, height);
  const frames = new Frames(width, height, page.paper);
  const edgeX = roundF(fmul(width, EDGE_RATIO));
  const minGap = Math.max(2, roundF(fmul(height, MIN_GAP_RATIO)));
  const minGapX = Math.max(2, roundF(fmul(width, MIN_GAP_RATIO)));
  const minStackGap = Math.max(minGap, roundF(fmul(height, MIN_STACK_GAP_RATIO)));
  const minFlatGap = Math.max(3, roundF(fmul(height, MIN_FLAT_GAP_RATIO)));
  const maxFlatGap = roundF(fmul(height, MAX_FLAT_GAP_RATIO));

  // 쪽 전체 띠들 — 아래에서 나눈 뒤 채운다
  let bandList: Range[] = [];
  const sameBand = (a: Range, b: Range) => a.first === b.first && a.last === b.last;

  /**
   * 띠(band) 안 내용을 붙일 때 첫 줄 — 이웃 띠 쪽으로 붙여 띠 사이 이음매에 빈 곳이 다시 생기지 않게 한다:
   * 첫 띠는 아래로, 끝 띠는 위로. 띠가 하나거나 가운데 띠면 쪽 가운데 쪽으로 (가운데에 걸치면 가운데)
   */
  const towardCentre = (band: Range, removed: number): number => {
    if (bandList.length > 1 && sameBand(band, bandList[0])) return removed;
    if (bandList.length > 1 && sameBand(band, bandList[bandList.length - 1])) return 0;
    if (band.last < idiv(height, 2)) return removed;
    if (band.first > idiv(height, 2)) return 0;
    return idiv(removed, 2);
  };

  /**
   * band 의 [x0, x1) 열에서 비스듬한·엇갈린 여백 띠를 빼고 쪽 가운데 쪽으로 붙인다 — block 에 쓴다.
   * 열마다 같은 줄 수를 빼므로 아래 칸은 일그러지지 않는다. 비는 곳은 뺀 여백의 평균 색.
   */
  const collapseRibbons = (block: Int32Array, band: Range, x0: number, x1: number): boolean => {
    const ribbons = findRibbons(page, x0, x1, band.first, band.last + 1, minStackGap, maxFlatGap, width, edgeX);
    if (!ribbons.length) return false;
    const bandH = band.last - band.first + 1;
    let total = 0;
    for (const ribbon of ribbons) total += ribbon.rows;
    const top = towardCentre(band, total);
    let r = 0;
    let g = 0;
    let b = 0;
    let n = 0;
    for (let x = x0; x < x1; x++) {
      for (const ribbon of ribbons) {
        const from = ribbon.start[x - x0];
        for (let y = from; y < from + ribbon.rows; y++) {
          const c = pixels[y * width + x];
          r += (c >> 16) & 0xff;
          g += (c >> 8) & 0xff;
          b += c & 0xff;
          n++;
        }
      }
    }
    let pad: number;
    if (n === 0) {
      const bg = page.paper;
      pad = (0xff << 24) | (bg << 16) | (bg << 8) | bg;
    } else {
      pad = (0xff << 24) | (Math.floor(r / n) << 16) | (Math.floor(g / n) << 8) | Math.floor(b / n);
    }
    for (let x = x0; x < x1; x++) {
      let out = top;
      let k = 0;
      let y = band.first;
      for (let i = 0; i < bandH; i++) block[i * width + x] = pad;
      while (y <= band.last) {
        if (k < ribbons.length && y === ribbons[k].start[x - x0]) {
          y += ribbons[k].rows;
          k++;
          continue;
        }
        block[out++ * width + x] = pixels[y * width + x];
        y++;
      }
    }
    return true;
  };

  // 기울어진 칸 테두리 끝자락 줄은 테두리 쪽(칸)에 남긴다
  const tail = page.borderTails(0, height, edgeX, width - edgeX, (y) => !page.bandInk(y, edgeX));
  const border = (y: number) => page.borderRow(y, edgeX, width - edgeX);
  // 칸 묶음 안 여백보다 짧은 빈 줄로는 띠를 나누지 않는다 — 비스듬한 여백 가운데 몇 줄만 쪽을 가로질러 비면
  // 띠가 둘로 나뉘어 나머지 비스듬한 부분이 양쪽 띠에 남는다. 한 띠로 두면 비스듬한 여백으로 통째로 뺀다
  const bands = segments(
    height,
    minStackGap,
    minFlatGap,
    maxFlatGap,
    (y) => (page.bandInk(y, edgeX) && !page.flatGutter(y, edgeX, width - edgeX)) || tail[y] === 1,
    (y) => page.bandInk(y, edgeX) || tail[y] === 1,
    (gap) => {
      // 떼어 낸 테두리 끝자락 줄만큼 건너 테두리를 찾는다
      let above = 0;
      for (let y = gap.first - 1; y >= 0 && tail[y] === 1; y--) above++;
      let below = 0;
      for (let y = gap.last + 1; y < height && tail[y] === 1; y++) below++;
      let borderAbove = false;
      for (let y = gap.first - 1; y >= Math.max(0, gap.first - 4 - above); y--) {
        if (border(y)) {
          borderAbove = true;
          break;
        }
      }
      let borderBelow = false;
      for (let y = gap.last + 1; y <= Math.min(height - 1, gap.last + 4 + below); y++) {
        if (border(y)) {
          borderBelow = true;
          break;
        }
      }
      const clean = page.cleanGap(gap, edgeX, width - edgeX, above, below);
      if (borderAbove && borderBelow) return !clean;
      if (!clean) return true;
      for (let y = gap.first; y <= gap.last; y++) {
        if (
          !page.flatGutter(y, edgeX, width - edgeX) &&
          (page.hasSignificantInk(y, edgeX, width - edgeX) || !page.isPaperRow(y, edgeX, width - edgeX))
        ) {
          return true;
        }
      }
      return false;
    },
  );
  if (!bands.length) return null;
  bandList = bands;

  /** 칸마다 밀 때(packCells) 위로 밀지 — 이웃 띠 쪽으로 (towardCentre 와 같은 기준), 아니면 띠 가운데가 쪽 아래 절반이면 위로 */
  const towardUp = (band: Range): boolean => {
    if (bands.length > 1 && sameBand(band, bands[0])) return false;
    if (bands.length > 1 && sameBand(band, bands[bands.length - 1])) return true;
    return band.first + band.last >= height;
  };

  let columnsMoved = false;
  const blocks = bands.map((band) => {
    const bandH = band.last - band.first + 1;
    const block = pixels.slice(band.first * width, (band.last + 1) * width);
    const wideInk = new Uint8Array(width);
    for (let x = 0; x < width; x++) {
      for (let y = band.first; y <= band.last; y++) {
        if (page.wide[y * width + x]) {
          wideInk[x] = 1;
          break;
        }
      }
    }
    const columns = segments(
      width,
      minGapX,
      Math.max(3, roundF(fmul(width, MIN_FLAT_GAP_RATIO))),
      MAX_INT,
      (x) => wideInk[x] === 1 && !page.flatColumn(x, band.first, band.last + 1),
      (x) => wideInk[x] === 1,
      // 칸 묶음 사이 세로 여백은 대부분 비었다 — 큰 그림 속 가로선 없는 세로 줄기는 여백이 아니다.
      // 띠 높이 내내 이어지는 칸 세로 테두리 열은 빼고 본다 — 모두 그런 열이면 여백이 아니라 그림 속 검은 줄기
      (gap) => {
        let count = 0;
        let sum = 0;
        const limit = fmul(bandH, BORDER_COLUMN_INK);
        for (let x = gap.first; x <= gap.last; x++) {
          const ink = page.columnInk(x, band.first, band.last + 1);
          if (ink < limit) {
            count++;
            sum += ink;
          }
        }
        return count === 0 || sum > fmul(count * bandH, MAX_COLUMN_GAP_INK);
      },
    );
    // 좁은 조각으로 나뉘면 칸 묶음이 아니라 그림이 갈라진 것 — 띠를 그대로 둔다
    const minStackWidth = fmul(width, MIN_STACK_WIDTH_RATIO);
    if (!columns.length || columns.some((col) => col.last - col.first + 1 < minStackWidth)) return block;
    if (columns.length === 1) {
      if (collapseRibbons(block, band, columns[0].first, columns[0].last + 1)) {
        columnsMoved = true;
      } else if (packCells(pixels, block, page, band, width, height, towardUp(band))) {
        columnsMoved = true;
      }
      return block;
    }
    for (const col of columns) {
      const x0 = col.first;
      const x1 = col.last + 1;
      // 묶음 안에서는 잉크가 조금이라도 있는 줄은 칸 — 위 테두리 없이 쪽 끝까지 이어진 그림·말풍선을 자르지 않게
      const looseInk = Math.max(2, roundF(fmul(x1 - x0, STACK_INK_RATIO)));
      // 다만 위아래가 칸 테두리로 막힌 여백에 쪽 번호 같은 작은 글자만 있으면 여백 — 글자째 뺀다
      const stray = page.strayInkRows(band.first, band.last + 1, x0, x1);
      const inked = new Uint8Array(bandH);
      for (let i = 0; i < bandH; i++) {
        inked[i] =
          !stray[i] && (page.inkContent(band.first + i, x0, x1) || page.inkCount(band.first + i, x0, x1) > looseInk) ? 1 : 0;
      }
      const parts = segments(
        bandH,
        minStackGap,
        minFlatGap,
        maxFlatGap,
        (i) => inked[i] === 1 && !page.flatGutter(band.first + i, x0, x1),
        (i) => inked[i] === 1,
      );
      if (parts.length <= 1) continue;
      // 칸은 어느 정도 높다 — 아주 얇은 조각(테두리 없는 그림 속 하늘의 점들)이 있으면 칸 묶음이 아니다
      const minPart = fmul(height, MIN_STACK_PART_RATIO);
      if (parts.some((part) => part.last - part.first + 1 < minPart)) continue;
      let rowCount = 0;
      const kept = new Uint8Array(bandH);
      for (const part of parts) {
        for (let r = part.first; r <= part.last; r++) {
          kept[r] = 1;
          rowCount++;
        }
      }
      if (rowCount >= bandH) {
        if (collapseRibbons(block, band, x0, x1)) columnsMoved = true;
        continue;
      }
      // 맞닿는 위 칸 아래 테두리와 아래 칸 위 테두리는 겹친다
      const overlaps = new Int32Array(parts.length);
      let overlapSum = 0;
      for (let k = 1; k < parts.length; k++) {
        overlaps[k] = frames.overlap(
          pixels,
          band.first + parts[k - 1].first,
          band.first + parts[k - 1].last + 1,
          pixels,
          band.first + parts[k].first,
          band.first + parts[k].last + 1,
          x0,
          x1,
        );
        overlapSum += overlaps[k];
      }
      const top = towardCentre(band, bandH - rowCount + overlapSum);
      // 비는 곳은 이 묶음의 원래 여백 중 가장 고른 줄의 대표 색으로 채운다 (검은 틈이면 검정)
      const gapRows: number[] = [];
      for (let i = 0; i < bandH; i++) if (!kept[i]) gapRows.push(band.first + i);
      const pad = page.evenest(gapRows, x0, x1) - band.first;
      const padColor = page.typicalColor(pixels, band.first + pad, x0, x1);
      for (let i = 0; i < bandH; i++) block.fill(padColor, i * width + x0, i * width + x1);
      let o = top;
      parts.forEach((part, k) => {
        for (let r = part.first; r <= part.last; r++) {
          const skip = r - part.first;
          if (skip < overlaps[k]) {
            frames.merge(block, o - overlaps[k] + skip, pixels, band.first + r, x0, x1);
          } else {
            const from = (band.first + r) * width;
            block.set(pixels.subarray(from + x0, from + x1), o++ * width + x0);
          }
        }
      });
      columnsMoved = true;
    }
    return block;
  });

  // 띠끼리 맞닿는 테두리도 겹친다
  const joins = new Int32Array(blocks.length);
  let joinSum = 0;
  let total = 0;
  for (let i = 0; i < blocks.length; i++) {
    if (i > 0) {
      joins[i] = frames.overlap(
        blocks[i - 1],
        0,
        idiv(blocks[i - 1].length, width),
        blocks[i],
        0,
        idiv(blocks[i].length, width),
        0,
        width,
      );
    }
    joinSum += joins[i];
    total += idiv(blocks[i].length, width);
  }
  total -= joinSum;
  const inBand = new Uint8Array(height);
  for (const band of bands) for (let y = band.first; y <= band.last; y++) inBand[y] = 1;
  const removed: number[] = [];
  for (let y = 0; y < height; y++) if (!inBand[y]) removed.push(y);
  if (!columnsMoved && removed.length < fmul(height, MIN_SAVED_RATIO)) return null;
  // 위아래 빈 곳은 쪽 위·아래 절반의 원래 여백 중 가장 고른 줄의 대표 색 (위는 검정 틈, 아래는 흰 종이인 쪽도 있다)
  const padColor = (rows: number[]): number => {
    const sample = rows.length ? rows : removed;
    return page.typicalColor(pixels, sample.length ? page.evenest(sample, 0, width) : 0, 0, width);
  };
  const start = idiv(height - total, 2);
  const out = new Int32Array(pixels.length);
  if (!removed.length) {
    // 쪽 전체 여백은 없고 칸 묶음만 움직였다
    blocks.forEach((block, i) => out.set(block, bands[i].first * width));
    return out;
  }
  const half = idiv(height, 2);
  out.fill(
    padColor(removed.filter((y) => y < half)),
    0,
    start * width,
  );
  // 띠마다 놓일 첫 줄 — 앞 띠 끝에서 겹칠 줄 수만큼 올라간다
  let y = start;
  const tops = new Int32Array(blocks.length);
  for (let i = 0; i < blocks.length; i++) {
    y -= joins[i];
    tops[i] = y;
    y += idiv(blocks[i].length, width);
  }
  blocks.forEach((block, i) => {
    const join = joins[i];
    for (let r = 0; r < join; r++) frames.merge(out, tops[i] + r, block, r, 0, width);
    out.set(block.subarray(join * width), (tops[i] + join) * width);
  });
  out.fill(
    padColor(removed.filter((row) => row >= half)),
    y * width,
    out.length,
  );
  return out;
}

/** 쪽을 보는 표들 — 잉크(바탕과 다른 곳), 세로·가로로 길게 이어진 잉크, 살짝 흐리게 한 밝기 */
class PageMaps {
  private readonly bg: number;
  private readonly colPaper: Int32Array | null;
  private readonly ink: Uint8Array;
  private readonly tall: Uint8Array;
  /** 세로로 긴 잉크가 아닌 잉크 중 위나 아래도 잉크인 곳 — 글자 획·그림. 망점 하나하나는 빠진다 */
  private readonly stroke: Uint8Array;
  readonly wide: Uint8Array;
  /** 가로로 흐리게 (줄이 고른지 볼 때), 세로로 흐리게 (열이 고른지 볼 때) — 망점이 고른 회색이 된다 */
  private readonly blurH: Uint8Array;
  private readonly blurV: Uint8Array;
  private readonly lum: Uint8Array;
  private readonly edgeX: number;
  private readonly firstBorder: number;
  private readonly lastBorder: number;
  private readonly hist = new Int32Array(256);
  /** bandInk(y, edgeX) 결과 (0 모름, 1 참, 2 거짓) — 늘 같은 edgeX 로만 부른다 */
  private readonly bandInkMemo: Uint8Array;
  /** flatGutter 결과: "x0,x1" → 줄마다 (0 모름, 1 참, 2 거짓) */
  private readonly flatGutterMemo = new Map<number, Uint8Array>();

  constructor(
    pixels: Int32Array,
    private readonly width: number,
    private readonly height: number,
  ) {
    const size = pixels.length;
    const lum = luminanceMap(pixels);
    this.lum = lum;
    this.edgeX = roundF(fmul(width, EDGE_RATIO));
    const bg = backgroundLuminance(lum);
    this.bg = bg;
    // 밝은 종이 쪽은 열마다 그 열의 종이색보다 어두운 곳이 잉크 — 가운데 접힌 곳 그림자는 잉크가 아니다
    const colPaper = bg >= MIN_COLUMN_PAPER_BG ? columnPaper(lum, width, height, bg) : null;
    this.colPaper = colPaper;
    const ink = new Uint8Array(size);
    if (colPaper) {
      for (let y = 0, i = 0; y < height; y++) {
        for (let x = 0; x < width; x++, i++) ink[i] = lum[i] < colPaper[x] - INK_DIFF ? 1 : 0;
      }
    } else {
      for (let i = 0; i < size; i++) ink[i] = Math.abs(lum[i] - bg) > INK_DIFF ? 1 : 0;
    }
    this.ink = ink;
    const minRunV = Math.max(8, roundF(fmul(height, LONG_INK_RATIO)));
    const minRunH = Math.max(8, roundF(fmul(width, LONG_INK_RATIO)));
    const tall = longRuns(ink, width, height, true, minRunV, this.edgeX, roundF(fmul(height, SCAN_EDGE_RUN_RATIO)));
    this.tall = tall;
    this.wide = longRuns(ink, width, height, false, minRunH);
    let firstBorder = -1;
    let lastBorder = -1;
    for (let y = 0; y < height; y++) {
      if (this.borderRow(y, this.edgeX, width - this.edgeX)) {
        if (firstBorder < 0) firstBorder = y;
        lastBorder = y;
      }
    }
    this.firstBorder = firstBorder;
    this.lastBorder = lastBorder;
    const stroke = new Uint8Array(size);
    for (let i = 0; i < size; i++) {
      if (!ink[i] || tall[i]) continue;
      const up = i >= width && ink[i - width] === 1 && tall[i - width] === 0;
      const down = i + width < size && ink[i + width] === 1 && tall[i + width] === 0;
      if (up || down) stroke[i] = 1;
    }
    this.stroke = stroke;
    this.blurH = boxBlur(lum, width, height, BLUR_RADIUS, true);
    this.blurV = boxBlur(lum, width, height, BLUR_RADIUS, false);
    this.bandInkMemo = new Uint8Array(height);
  }

  /** 칸 테두리 바깥(첫 테두리 위 또는 끝 테두리 아래)에 있는 줄 — 제목 줄, 글 문단, 오픈 컷 */
  outsideBorders(y: number): boolean {
    return this.firstBorder < 0 || y < this.firstBorder || y > this.lastBorder;
  }

  /**
   * 세로로 긴 잉크가 지나가면 칸. 다만 그것이 좁은 곳(OVERLAY_SPAN_RATIO 미만)에만 있고
   * 그 사이에 짧은 잉크(글자·그림)가 없으면 여백을 가로지르는 글 상자·말풍선 테두리로 보고 여백으로 친다.
   */
  inkContent(y: number, x0: number, x1: number): boolean {
    const base = y * this.width;
    const { tall, ink } = this;
    let first = -1;
    let last = -1;
    for (let x = x0; x < x1; x++) {
      if (tall[base + x]) {
        if (first < 0) first = x;
        last = x;
      }
    }
    if (first < 0) return false;
    if (last - first + 1 >= fmul(x1 - x0, OVERLAY_SPAN_RATIO)) return true;
    let loose = 0;
    for (let x = first; x <= last; x++) if (ink[base + x] && !tall[base + x]) loose++;
    return loose > OVERLAY_NOISE;
  }

  /** 칸 테두리 같은 가로선이 줄의 BORDER_ROW_RATIO 이상을 지나간다 */
  borderRow(y: number, x0: number, x1: number): boolean {
    const base = y * this.width;
    const { wide } = this;
    let n = 0;
    for (let x = x0; x < x1; x++) if (wide[base + x]) n++;
    return n >= fmul(x1 - x0, BORDER_ROW_RATIO);
  }

  /** 화 제목, 글 문단, 테두리 없는 오픈 컷처럼 가로 폭이 OVERLAY_SPAN_RATIO 이상을 차지하는 독립적인 글·그림 줄 */
  broadInk(y: number, x0: number, x1: number): boolean {
    const base = y * this.width;
    const { ink } = this;
    let first = -1;
    let last = -1;
    let inkCount = 0;
    for (let x = x0; x < x1; x++) {
      if (ink[base + x]) {
        if (first < 0) first = x;
        last = x;
        inkCount++;
      }
    }
    if (first < 0) return false;
    const span = last - first + 1;
    const w = x1 - x0;
    return span >= fmul(w, OVERLAY_SPAN_RATIO) && inkCount >= Math.max(4, roundF(fmul(w, F(0.02))));
  }

  /** 그림 획이 줄의 STROKE_ROW_RATIO 이상을 지나간다 — 테두리 없는 그림 */
  private strokeRow(y: number, x0: number, x1: number): boolean {
    const base = y * this.width;
    const { stroke } = this;
    let n = 0;
    for (let x = x0; x < x1; x++) if (stroke[base + x]) n++;
    return n >= fmul(x1 - x0, STROKE_ROW_RATIO);
  }

  /** 쪽 번호나 짧은 역자 주보다 큰 잉크 덩어리 — 글 줄, 제목, 테두리 없는 그림 */
  hasSignificantInk(y: number, x0: number, x1: number): boolean {
    const base = y * this.width;
    const { ink, stroke } = this;
    let minX = -1;
    let maxX = -1;
    let strokeCount = 0;
    let inkCount = 0;
    for (let x = x0; x < x1; x++) {
      if (ink[base + x]) {
        if (minX < 0) minX = x;
        maxX = x;
        inkCount++;
        if (stroke[base + x]) strokeCount++;
      }
    }
    if (minX < 0) return false;
    const span = maxX - minX + 1;
    const w = x1 - x0;
    const wideSpan =
      span >= fmul(w, SIGNIFICANT_SPAN_RATIO) && (strokeCount >= 3 || inkCount >= Math.max(3, roundF(fmul(w, F(0.015)))));
    const denseStroke = strokeCount >= Math.max(4, roundF(fmul(w, SIGNIFICANT_STROKE_RATIO)));
    return wideSpan || denseStroke;
  }

  /** 쪽 전체 띠를 나눌 때의 잉크 줄 — 세로로 긴 잉크가 지나가거나, 칸 테두리 같은 가로선, 또는 글 줄·제목·테두리 없는 그림 */
  bandInk(y: number, edgeX: number): boolean {
    const memo = this.bandInkMemo[y];
    if (memo) return memo === 1;
    const result = this.computeBandInk(y, edgeX);
    this.bandInkMemo[y] = result ? 1 : 2;
    return result;
  }

  private computeBandInk(y: number, edgeX: number): boolean {
    const width = this.width;
    if (this.inkContent(y, 0, width) || this.borderRow(y, edgeX, width - edgeX)) return true;
    if (this.bg < MIN_COLUMN_PAPER_BG && this.strokeRow(y, edgeX, width - edgeX)) return true;
    return this.outsideBorders(y) && this.broadInk(y, edgeX, width - edgeX);
  }

  inkAt(i: number): boolean {
    return this.ink[i] === 1;
  }

  /** 쪽 바탕 밝기 */
  get paper(): number {
    return this.bg;
  }

  lumAt(x: number, y: number): number {
    const row = y < 0 ? 0 : y > this.height - 1 ? this.height - 1 : y;
    return this.lum[row * this.width + x];
  }

  /** x 열의 [y0, y1) 줄들의 가장 밝고 어두운 곳 차이 — 작으면 위아래로 밋밋하다 (그림자, 빈 종이) */
  rowRange(x: number, y0: number, y1: number): number {
    let min = 255;
    let max = 0;
    const to = Math.min(y1, this.height);
    for (let y = Math.max(y0, 0); y < to; y++) {
      const v = this.lum[y * this.width + x];
      if (v < min) min = v;
      if (v > max) max = v;
    }
    return max - min;
  }

  /**
   * x 열의 [y0, y1) 안 빈 구간들, 위에서부터 — 그 열의 밝은 곳(COLUMN_PAPER_SHARE)에 가까우면 빈 곳.
   * 쪽 전체 종이색이 아니라 열마다 보므로 가운데 접힌 곳 그림자 속 여백도 빈 곳이다.
   */
  cleanRuns(x: number, y0: number, y1: number): Range[] {
    const { hist, lum, width } = this;
    hist.fill(0);
    for (let y = y0; y < y1; y++) hist[lum[y * width + x]]++;
    let paper = 255;
    let seen = 0;
    const limit = fmul(y1 - y0, ONE_MINUS_PAPER_SHARE);
    while (paper > 0 && seen + hist[paper] <= limit) seen += hist[paper--];
    // 위아래로 다 어두운 열(칸 세로 테두리, 검은 칸)의 종이색은 쪽 바탕색에서 MAX_SHADE_DIFF 까지만 어둡게 본다
    const min = Math.max(paper, this.bg - MAX_SHADE_DIFF) - INK_DIFF;
    const runs: Range[] = [];
    let start = -1;
    for (let y = y0; y <= y1; y++) {
      if (y < y1 && lum[y * width + x] >= min) {
        if (start < 0) start = y;
      } else if (start >= 0) {
        runs.push({ first: start, last: y - 1 });
        start = -1;
      }
    }
    return runs;
  }

  inkCount(y: number, x0: number, x1: number): number {
    const base = y * this.width;
    const { ink } = this;
    let n = 0;
    for (let x = x0; x < x1; x++) if (ink[base + x]) n++;
    return n;
  }

  /** x 열의 [y0, y1) 안 잉크 칸 수 */
  columnInk(x: number, y0: number, y1: number): number {
    const { ink, width } = this;
    let n = 0;
    for (let y = y0; y < y1; y++) if (ink[y * width + x]) n++;
    return n;
  }

  /** 줄의 흐린 밝기 분포를 hist 에 */
  private rowHist(y: number, x0: number, x1: number): void {
    const { hist, blurH } = this;
    hist.fill(0);
    const base = y * this.width;
    for (let x = x0; x < x1; x++) hist[blurH[base + x]]++;
  }

  private histMedian(n: number): number {
    const { hist } = this;
    let median = 0;
    let seen = 0;
    while (median < 255) {
      seen += hist[median];
      if (seen * 2 >= n) break;
      median++;
    }
    return median;
  }

  private histOutliers(median: number): number {
    const { hist } = this;
    let out = 0;
    for (let v = 0; v <= 255; v++) if (Math.abs(v - median) > FLAT_DIFF) out += hist[v];
    return out;
  }

  /** 줄에서 가운데 밝기(중앙값)와 FLAT_DIFF 넘게 다른 칸 수 — 적을수록 고른 줄 */
  outliers(y: number, x0: number, x1: number): number {
    this.rowHist(y, x0, x1);
    return this.histOutliers(this.histMedian(x1 - x0));
  }

  private rowMedian(y: number, x0: number, x1: number): number {
    this.rowHist(y, x0, x1);
    return this.histMedian(x1 - x0);
  }

  /** 가장 고른 줄 — 바탕색과 너무 다른 줄(스캔 끝 흰색/검은 테두리)은 피하고 종이색에 가까운 가장 고른 줄 */
  evenest(rows: number[], x0: number, x1: number): number {
    const bg = this.bg;
    const medians = rows.map((y) => this.rowMedian(y, x0, x1));
    let candidates: number[] = [];
    for (let k = 0; k < rows.length; k++) {
      const m = medians[k];
      if (bg < 80 ? m < 140 : bg > 180 ? m > 100 : true) candidates.push(k);
    }
    if (!candidates.length) candidates = rows.map((_row, k) => k);
    let best = -1;
    let bestOutliers = 0;
    let bestDistance = 0;
    for (const k of candidates) {
      this.rowHist(rows[k], x0, x1);
      const out = this.histOutliers(medians[k]);
      const distance = Math.abs(medians[k] - bg);
      if (best < 0 || out < bestOutliers || (out === bestOutliers && distance < bestDistance)) {
        best = k;
        bestOutliers = out;
        bestDistance = distance;
      }
    }
    return rows[best];
  }

  /** 줄의 대표 색 — 흐리게 본 밝기가 가운데 값 가까운 칸들의 평균 색 (망점이면 그 회색, 색 틈이면 그 색) */
  typicalColor(pixels: Int32Array, y: number, x0: number, x1: number): number {
    const base = y * this.width;
    const { hist, blurH } = this;
    // 흐린 밝기 순으로 세운 줄의 (칸 수 / 2)번째 값
    this.rowHist(y, x0, x1);
    const rank = idiv(x1 - x0, 2);
    let mid = 0;
    let seen = 0;
    for (let v = 0; v <= 255; v++) {
      seen += hist[v];
      if (seen > rank) {
        mid = v;
        break;
      }
    }
    let r = 0;
    let g = 0;
    let b = 0;
    let n = 0;
    for (let x = x0; x < x1; x++) {
      if (Math.abs(blurH[base + x] - mid) > FLAT_DIFF) continue;
      const c = pixels[base + x];
      r += (c >> 16) & 0xff;
      g += (c >> 8) & 0xff;
      b += c & 0xff;
      n++;
    }
    if (n === 0) return (0xff << 24) | (mid << 16) | (mid << 8) | mid;
    return (0xff << 24) | (Math.floor(r / n) << 16) | (Math.floor(g / n) << 8) | Math.floor(b / n);
  }

  flat(y: number, x0: number, x1: number): boolean {
    return x1 > x0 && this.outliers(y, x0, x1) <= fmul(x1 - x0, FLAT_OUTLIER_RATIO);
  }

  /** 바탕과 다른 한 가지 색으로 고른 줄 — 검은 틈·망점 틈. 바탕색으로 고른 줄(액자 선만 지나가는 흰 줄)은 아니다 */
  flatGutter(y: number, x0: number, x1: number): boolean {
    const key = x0 * 65536 + x1;
    let memo = this.flatGutterMemo.get(key);
    if (!memo) {
      memo = new Uint8Array(this.height);
      this.flatGutterMemo.set(key, memo);
    }
    const known = memo[y];
    if (known) return known === 1;
    let result = false;
    if (x1 > x0) {
      this.rowHist(y, x0, x1);
      const median = this.histMedian(x1 - x0);
      result = this.histOutliers(median) <= fmul(x1 - x0, FLAT_OUTLIER_RATIO) && Math.abs(median - this.bg) > FLAT_DIFF;
    }
    memo[y] = result ? 1 : 2;
    return result;
  }

  /** 줄이 진짜 빈 흰 종이(또는 검은 바탕)인지 — 옅은 회색 톤이나 그림 텍스처가 깔려 있으면 빈 줄이 아니다 */
  isPaperRow(y: number, x0: number, x1: number): boolean {
    let nonWhite = 0;
    const base = y * this.width;
    const { lum, colPaper, bg } = this;
    if (colPaper) {
      for (let x = x0; x < x1; x++) if (colPaper[x] - lum[base + x] > PAPER_ROW_DIFF) nonWhite++;
    } else {
      for (let x = x0; x < x1; x++) if (Math.abs(lum[base + x] - bg) > PAPER_ROW_DIFF) nonWhite++;
    }
    return nonWhite <= fmul(x1 - x0, PAPER_ROW_MAX_NONWHITE);
  }

  /** 여백 후보가 정말 비었는지 — 빈 줄(고른 색 줄 포함)이 CLEAN_GAP_RATIO 이상이고 획이 평균 GAP_STROKE_RATIO 이하 */
  cleanGap(rows: Range, x0: number, x1: number, slackAbove = 0, slackBelow = slackAbove): boolean {
    const { stroke, width, height } = this;
    const noise = Math.max(OVERLAY_NOISE, roundF(fmul(x1 - x0, CLEAN_ROW_RATIO)));
    let clean = 0;
    let strokes = 0;
    const columns = new Int32Array(x1 - x0);
    for (let y = rows.first; y <= rows.last; y++) {
      if (this.flatGutter(y, x0, x1)) {
        clean++;
        continue;
      }
      let n = 0;
      const base = y * width;
      for (let x = x0; x < x1; x++) {
        if (stroke[base + x]) {
          n++;
          columns[x - x0]++;
        }
      }
      if (n <= noise) clean++;
      strokes += n;
    }
    const count = rows.last - rows.first + 1;
    if (clean >= fmul(count, CLEAN_GAP_RATIO) && F(strokes) <= fmul(count * (x1 - x0), GAP_STROKE_RATIO)) return true;
    // 위아래가 칸 테두리로 막혔고 획이 좁은 곳에만 몰려 있으면 여백에 걸친 효과음·말풍선 — 그만큼 짧아져도 여백으로 친다
    // 테두리는 slackAbove·slackBelow 줄 안에서 찾는다 (기울어진 테두리 끝자락을 여백에서 떼어 냈을 때)
    let above = false;
    for (let y = rows.first - 1; y >= rows.first - 1 - slackAbove; y--) {
      if (y >= 0 && this.borderRow(y, x0, x1)) {
        above = true;
        break;
      }
    }
    let below = false;
    for (let y = rows.last + 1; y <= rows.last + 1 + slackBelow; y++) {
      if (y < height && this.borderRow(y, x0, x1)) {
        below = true;
        break;
      }
    }
    if (!(count <= fmul(height, MAX_FLAT_GAP_RATIO) && above && below)) return false;
    // 획이 한 곳에 몰렸거나(효과음), 작은 덩어리 몇 개뿐이라(쪽 번호와 걸친 말풍선 끝) 지나는 열이 적다
    if (narrowestSpan(columns, strokes * OVERLAY_STROKE_SHARE) < fmul(x1 - x0, OVERLAY_SPAN_RATIO)) return true;
    let used = 0;
    for (let i = 0; i < columns.length; i++) if (columns[i] > 0) used++;
    return used < fmul(x1 - x0, STRAY_COLUMN_RATIO);
  }

  /**
   * [y0, y1) 줄 중 칸 테두리 사이 여백에 걸친 작은 글자(쪽 번호)·효과음 줄 — 세로로 긴 잉크도 칸 테두리 같은 가로선도
   * 지나지 않는 줄들이 이어진 곳마다 cleanGap 이면 (위아래가 테두리로 막혔고 글자가 좁은 곳에 몰렸거나 대부분 빈 줄) 참.
   */
  strayInkRows(y0: number, y1: number, x0: number, x1: number): Uint8Array {
    const out = new Uint8Array(y1 - y0);
    const margin = Math.min(roundF(fmul(this.width, EDGE_RATIO)), idiv(x1 - x0, 4));
    const openMemo = new Uint8Array(y1 - y0);
    const open = (y: number): boolean => {
      const known = openMemo[y - y0];
      if (known) return known === 1;
      const result = !this.inkContent(y, x0, x1) && !this.borderRow(y, x0, x1);
      openMemo[y - y0] = result ? 1 : 2;
      return result;
    };
    const tail = this.borderTails(y0, y1, x0, x1, open);
    let y = y0;
    while (y < y1) {
      if (!open(y) || tail[y - y0]) {
        y++;
        continue;
      }
      const first = y;
      while (y < y1 && open(y) && !tail[y - y0]) y++;
      const last = y - 1;
      // 양 끝 margin 열(가운데 접힌 곳 그림자 얼룩, 스캔 가장자리)은 보지 않는다
      if (first - 1 > y0 && last + 1 < y1 && this.cleanGap({ first, last }, x0 + margin, x1 - margin, STRAY_SLACK)) {
        for (let r = first; r <= last; r++) out[r - y0] = 1;
      }
    }
    return out;
  }

  /**
   * [y0, y1) 줄 중 open 줄(칸이 아닌 줄)들이 이어진 곳 양 끝에서 가로선이 조금 걸친 줄 — 기울어진 칸 테두리 끝자락.
   * STRAY_SLACK 줄까지. 여백으로 빼지 않고 테두리 쪽에 남긴다.
   */
  borderTails(y0: number, y1: number, x0: number, x1: number, open: (y: number) => boolean): Uint8Array {
    const out = new Uint8Array(y1 - y0);
    const { wide, width } = this;
    const hasWide = (y: number): boolean => {
      const base = y * width;
      for (let x = x0; x < x1; x++) if (wide[base + x]) return true;
      return false;
    };
    let y = y0;
    while (y < y1) {
      if (!open(y)) {
        y++;
        continue;
      }
      let first = y;
      while (y < y1 && open(y)) y++;
      let last = y - 1;
      let trim = 0;
      while (trim < STRAY_SLACK && first < last && hasWide(first)) {
        out[first++ - y0] = 1;
        trim++;
      }
      trim = 0;
      while (trim < STRAY_SLACK && last > first && hasWide(last)) {
        out[last-- - y0] = 1;
        trim++;
      }
    }
    return out;
  }

  flatColumn(x: number, y0: number, y1: number): boolean {
    const { hist, blurV, width } = this;
    hist.fill(0);
    for (let y = y0; y < y1; y++) hist[blurV[y * width + x]]++;
    return this.histOutliers(this.histMedian(y1 - y0)) <= fmul(y1 - y0, FLAT_OUTLIER_RATIO);
  }
}

/** counts 의 합이 share 이상인 가장 좁은 연속 구간의 폭 */
function narrowestSpan(counts: Int32Array, share: number): number {
  let best = counts.length;
  let sum = 0;
  let a = 0;
  for (let b = 0; b < counts.length; b++) {
    sum += counts[b];
    while (a <= b && sum - counts[a] >= share) sum -= counts[a++];
    if (sum >= share) best = Math.min(best, b - a + 1);
  }
  return best;
}

/**
 * 세로(또는 가로)로 minRun 이상 이어지는 잉크만 남긴 표.
 * 세로일 때 왼쪽·오른쪽 끝 edge 열 안에서는 maxEdgeRun 보다 긴 것(쪽 높이 대부분을 지나는 스캔 테두리)은 뺀다 —
 * 쪽 끝에 바짝 붙은 칸 테두리는 칸 높이만큼만 이어지므로 남는다.
 */
function longRuns(
  ink: Uint8Array,
  width: number,
  height: number,
  vertical: boolean,
  minRun: number,
  edge = 0,
  maxEdgeRun = MAX_INT,
): Uint8Array {
  const out = new Uint8Array(ink.length);
  if (vertical) {
    // 열마다 이어지는 잉크가 시작한 줄 (-1: 잉크 아님) — 줄 순서로 훑어도 결과는 열마다 훑은 것과 같다
    const start = new Int32Array(width).fill(-1);
    for (let y = 0; y <= height; y++) {
      const base = y * width;
      for (let x = 0; x < width; x++) {
        if (y < height && ink[base + x]) {
          if (start[x] < 0) start[x] = y;
        } else if (start[x] >= 0) {
          const run = y - start[x];
          const maxRun = x < edge || x >= width - edge ? maxEdgeRun : MAX_INT;
          if (run >= minRun && run <= maxRun) for (let k = start[x]; k < y; k++) out[k * width + x] = 1;
          start[x] = -1;
        }
      }
    }
    return out;
  }
  for (let line = 0; line < height; line++) {
    const origin = line * width;
    let start = -1;
    for (let i = 0; i <= width; i++) {
      if (i < width && ink[origin + i]) {
        if (start < 0) start = i;
      } else if (start >= 0) {
        if (i - start >= minRun) for (let k = start; k < i; k++) out[origin + k] = 1;
        start = -1;
      }
    }
  }
  return out;
}

/** 한 방향으로 2r+1칸 평균 — 이웃 줄(열)의 칸 테두리가 섞이지 않게 한 방향으로만. 가장자리는 있는 칸만 평균 */
function boxBlur(lum: Uint8Array, width: number, height: number, r: number, horizontal: boolean): Uint8Array {
  const out = new Uint8Array(lum.length);
  if (!horizontal) {
    // 창(줄 y−r … y+r) 안의 열 합을 줄 순서로 밀어 간다 — 열마다 누적합을 낸 것과 같은 값
    const sum = new Int32Array(width);
    for (let y = 0; y < Math.min(height, r); y++) {
      const base = y * width;
      for (let x = 0; x < width; x++) sum[x] += lum[base + x];
    }
    for (let y = 0; y < height; y++) {
      if (y + r < height) {
        const add = (y + r) * width;
        for (let x = 0; x < width; x++) sum[x] += lum[add + x];
      }
      if (y - r - 1 >= 0) {
        const drop = (y - r - 1) * width;
        for (let x = 0; x < width; x++) sum[x] -= lum[drop + x];
      }
      const count = Math.min(height, y + r + 1) - Math.max(0, y - r);
      const base = y * width;
      for (let x = 0; x < width; x++) out[base + x] = idiv(sum[x], count);
    }
    return out;
  }
  const prefix = new Int32Array(width + 1);
  for (let line = 0; line < height; line++) {
    const origin = line * width;
    for (let i = 0; i < width; i++) prefix[i + 1] = prefix[i] + lum[origin + i];
    for (let i = 0; i < width; i++) {
      const a = Math.max(0, i - r);
      const b = Math.min(width, i + r + 1);
      out[origin + i] = idiv(prefix[b] - prefix[a], b - a);
    }
  }
  return out;
}

/**
 * 내용이 있는 구간들 — minGap 보다 짧은 빈 곳은 구간 안으로 치고, 앞뒤 빈 곳은 뺀다.
 * 고른 색이라서만 빈 곳(flatOnly)이 이어진 구간은 minFlat 보다 짧으면(칸 테두리 굵기) 내용,
 * 칸 사이에서 maxFlat 보다 길면(그림 속 검은 곳) 내용으로 친다. keepGap 이 참인 빈 곳(빽빽한 글·그림)도 내용.
 */
function segments(
  length: number,
  minGap: number,
  minFlat: number,
  maxFlat: number,
  isContent: (i: number) => boolean,
  flatOnly: (i: number) => boolean = () => false,
  keepGap: (gap: Range) => boolean = () => false,
): Range[] {
  const content = new Uint8Array(length);
  let first = -1;
  let last = -1;
  for (let k = 0; k < length; k++) {
    if (isContent(k)) {
      content[k] = 1;
      if (first < 0) first = k;
      last = k;
    }
  }
  let i = 0;
  while (i < length) {
    if (content[i] || !flatOnly(i)) {
      i++;
      continue;
    }
    const start = i;
    while (i < length && !content[i] && flatOnly(i)) i++;
    const inner = start > first && i - 1 < last;
    if (i - start < minFlat || (inner && i - start > maxFlat)) for (let k = start; k < i; k++) content[k] = 1;
  }
  i = 0;
  while (i < length) {
    if (content[i]) {
      i++;
      continue;
    }
    const start = i;
    while (i < length && !content[i]) i++;
    if (keepGap({ first: start, last: i - 1 })) for (let k = start; k < i; k++) content[k] = 1;
  }
  const out: Range[] = [];
  let start = -1;
  let lastContent = -1;
  for (let k = 0; k < length; k++) {
    if (!content[k]) continue;
    if (start < 0) {
      start = k;
    } else if (k - lastContent - 1 >= minGap) {
      out.push({ first: start, last: lastContent });
      start = k;
    }
    lastContent = k;
  }
  if (start >= 0) out.push({ first: start, last: lastContent });
  return out;
}

/**
 * 맞닿는 칸 겹치기 — 위 조각 끝과 아래 조각 처음을 몇 줄 겹쳐, 남은 여백 끝자락(비스듬하거나 흐린 테두리 가장자리)을
 * 없애고 위 칸 아래 테두리와 아래 칸 위 테두리를 한 줄로 만든다. 겹친 줄은 둘 중 어두운 칸을 쓴다.
 *
 * 열마다 끝에 남은 옅은 줄 수와 그 너머 테두리 두께(frame 까지)를 재어, 그 열에서 겹쳐도 되는 줄 수
 * (위·아래 옅은 줄 + 둘 다 테두리면 얇은 쪽 테두리)를 구하고, 열들 중 OVERLAP_PERCENTILE 자리 값만큼 겹친다 —
 * 몇 안 되는 열(글자, 그림이 맞닿은 곳)은 어두운 칸끼리 조금 겹칠 수 있다.
 */
class Frames {
  private readonly reach: number;
  private readonly frame: number;
  private edgeLight = 0;
  private edgeInk = 0;

  constructor(
    private readonly width: number,
    height: number,
    private readonly bg: number,
  ) {
    this.reach = Math.max(2, roundF(fmul(height, MAX_OVERLAP_RATIO)));
    this.frame = Math.max(1, roundF(fmul(height, FRAME_OVERLAP_RATIO)));
  }

  private dark(c: number): boolean {
    return luminance(c) < this.bg - INK_DIFF;
  }

  /** rows(끝에서부터의 줄)에서 x 열의 옅은 줄 수와 그 너머 잉크(테두리) 줄 수 → edgeLight, edgeInk */
  private edge(px: Int32Array, rows: Int32Array, x: number): void {
    const { width, frame } = this;
    let light = 0;
    while (light < rows.length && !this.dark(px[rows[light] * width + x])) light++;
    let ink = 0;
    while (ink < frame && light + ink < rows.length && this.dark(px[rows[light + ink] * width + x])) ink++;
    this.edgeLight = light;
    this.edgeInk = ink;
  }

  /**
   * 위 조각 upper 의 [upperStart, upperEnd) 줄 끝과 아래 조각 lower 의 [lowerStart, lowerEnd) 줄 처음을 겹칠 줄 수.
   * 검은 바탕 쪽은 겹치지 않는다.
   */
  overlap(
    upper: Int32Array,
    upperStart: number,
    upperEnd: number,
    lower: Int32Array,
    lowerStart: number,
    lowerEnd: number,
    x0: number,
    x1: number,
  ): number {
    if (this.bg < MIN_COLUMN_PAPER_BG || x1 <= x0) return 0;
    const n = Math.min(this.reach, upperEnd - upperStart, lowerEnd - lowerStart);
    if (n <= 0) return 0;
    const up = new Int32Array(n);
    const down = new Int32Array(n);
    for (let k = 0; k < n; k++) {
      up[k] = upperEnd - 1 - k;
      down[k] = lowerStart + k;
    }
    let framed = 0;
    const allow = new Int32Array(x1 - x0);
    for (let i = 0; i < allow.length; i++) {
      this.edge(upper, up, x0 + i);
      const lu = this.edgeLight;
      const iu = this.edgeInk;
      this.edge(lower, down, x0 + i);
      const ld = this.edgeLight;
      const id = this.edgeInk;
      const both = iu > 0 && id > 0;
      if (both) framed++;
      allow[i] = lu + ld + (both ? Math.min(iu, id) : 0);
    }
    // 위아래 칸 테두리가 맞닿는 곳만 — 글 줄 사이 같은 곳은 겹치지 않는다
    if (framed < fmul(x1 - x0, MIN_FRAMED_RATIO)) return 0;
    allow.sort();
    return Math.min(n, allow[roundF(fmul(allow.length - 1, OVERLAP_PERCENTILE))]);
  }

  /** dst 의 dstRow 줄에 src 의 srcRow 줄을 겹친다 — [x0, x1)에서 어두운 쪽 */
  merge(dst: Int32Array, dstRow: number, src: Int32Array, srcRow: number, x0: number, x1: number): void {
    const { width } = this;
    for (let x = x0; x < x1; x++) {
      const a = dst[dstRow * width + x];
      const b = src[srcRow * width + x];
      if (luminance(b) < luminance(a)) dst[dstRow * width + x] = b;
    }
  }
}

/**
 * 띠 안의 칸을 칸마다 따로 쪽 가운데 쪽으로 붙인다 (up 이면 위로) — 칸 사이 여백이 가로·세로 모두 비스듬해
 * 곧은 줄로도, 띠 전체를 가로지르는 비스듬한 여백으로도 나눌 수 없을 때 (비스듬한 칸이 엇갈린 쪽).
 *
 * 여백: 쪽 좌우 끝에서 이어진 종이 (테두리 틈으로 새지 않게 2칸 넘게 빈 곳으로만 잇는다). 칸: 여백 아닌 곳의 덩어리 중
 * 쪽 너비의 MIN_CELL_WIDTH_RATIO·높이의 MIN_CELL_HEIGHT_RATIO 넘는 것, 더 작은 덩어리(말풍선 글자 등)는 둘러싼 칸에 붙인다.
 * 위쪽 칸부터, 위 테두리가 곧거나 비스듬한 선인 칸만 위 칸(또는 띠 끝)에 닿을 때까지 — MAX_FLAT_GAP_RATIO 까지 — 민다.
 * 빈 곳은 여백 색으로 채운다. 바뀐 것이 있으면 block 에 쓰고 true.
 */
function packCells(
  pixels: Int32Array,
  block: Int32Array,
  page: PageMaps,
  band: Range,
  width: number,
  height: number,
  up: boolean,
): boolean {
  const y0 = band.first;
  const bh = band.last - band.first + 1;
  const n = width * bh;
  const offset = y0 * width;
  // 여백 — 둘레 1칸이 모두 빈 곳을 따라 쪽 좌우 끝에서 채운 뒤 1칸 넓힌다
  const clean = new Uint8Array(n);
  for (let i = 0; i < n; i++) clean[i] = page.inkAt(offset + i) ? 0 : 1;
  const core = new Uint8Array(n);
  for (let y = 0, i = 0; y < bh; y++) {
    for (let x = 0; x < width; x++, i++) {
      if (
        clean[i] &&
        (x === 0 || clean[i - 1]) &&
        (x === width - 1 || clean[i + 1]) &&
        (y === 0 || clean[i - width]) &&
        (y === bh - 1 || clean[i + width])
      ) {
        core[i] = 1;
      }
    }
  }
  const gutter = new Uint8Array(n);
  const queue = new Int32Array(n);
  let tail = 0;
  for (let y = 0; y < bh; y++) {
    for (let side = 0; side < 2; side++) {
      const i = y * width + (side === 0 ? 0 : width - 1);
      if (core[i] && !gutter[i]) {
        gutter[i] = 1;
        queue[tail++] = i;
      }
    }
  }
  let head = 0;
  const NX = [1, -1, 0, 0];
  const NY = [0, 0, 1, -1];
  while (head < tail) {
    const i = queue[head++];
    const x = i % width;
    const y = idiv(i, width);
    for (let d = 0; d < 4; d++) {
      const nx = x + NX[d];
      const ny = y + NY[d];
      if (nx < 0 || ny < 0 || nx >= width || ny >= bh) continue;
      const j = ny * width + nx;
      if (core[j] && !gutter[j]) {
        gutter[j] = 1;
        queue[tail++] = j;
      }
    }
  }
  for (let y = 0, i = 0; y < bh; y++) {
    for (let x = 0; x < width; x++, i++) {
      if (gutter[i] || !clean[i]) continue;
      if (
        (x > 0 && gutter[i - 1] && core[i - 1]) ||
        (x < width - 1 && gutter[i + 1] && core[i + 1]) ||
        (y > 0 && gutter[i - width] && core[i - width]) ||
        (y < bh - 1 && gutter[i + width] && core[i + width])
      ) {
        gutter[i] = 1;
      }
    }
  }
  // 칸 덩어리 (8방향)
  const label = new Int32Array(n).fill(-1);
  let count = 0;
  for (let start = 0; start < n; start++) {
    if (gutter[start] || label[start] >= 0) continue;
    label[start] = count;
    head = 0;
    tail = 0;
    queue[tail++] = start;
    while (head < tail) {
      const i = queue[head++];
      const y = idiv(i, width);
      const x = i - y * width;
      const xFrom = x > 0 ? -1 : 0;
      const xTo = x < width - 1 ? 1 : 0;
      for (let dy = y > 0 ? -1 : 0; dy <= (y < bh - 1 ? 1 : 0); dy++) {
        const row = i + dy * width;
        for (let dx = xFrom; dx <= xTo; dx++) {
          const j = row + dx;
          if (!gutter[j] && label[j] < 0) {
            label[j] = count;
            queue[tail++] = j;
          }
        }
      }
    }
    count++;
  }
  if (count < 2) return false;
  // 덩어리마다 둘레 상자 [위, 아래, 왼쪽, 오른쪽]
  const box = new Int32Array(count * 4);
  for (let c = 0; c < count; c++) {
    box[c * 4] = MAX_INT;
    box[c * 4 + 1] = -1;
    box[c * 4 + 2] = MAX_INT;
    box[c * 4 + 3] = -1;
  }
  const area = new Int32Array(count);
  for (let y = 0, i = 0; y < bh; y++) {
    for (let x = 0; x < width; x++, i++) {
      const l = label[i];
      if (l < 0) continue;
      const b = l * 4;
      if (y < box[b]) box[b] = y;
      if (y > box[b + 1]) box[b + 1] = y;
      if (x < box[b + 2]) box[b + 2] = x;
      if (x > box[b + 3]) box[b + 3] = x;
      area[l]++;
    }
  }
  const top = (c: number) => box[c * 4];
  const bottom = (c: number) => box[c * 4 + 1];
  const left = (c: number) => box[c * 4 + 2];
  const right = (c: number) => box[c * 4 + 3];
  const minCellWidth = fmul(width, MIN_CELL_WIDTH_RATIO);
  const minCellHeight = fmul(height, MIN_CELL_HEIGHT_RATIO);
  const big: number[] = [];
  for (let c = 0; c < count; c++) {
    if (right(c) - left(c) + 1 >= minCellWidth && bottom(c) - top(c) + 1 >= minCellHeight) big.push(c);
  }
  if (big.length < 2) return false;
  // 칸 짜임새일 때만 — 칸마다 둘레 상자를 거의 채우고(네모·사다리꼴), 칸들이 띠 넓이 대부분을 덮는다.
  // 테두리 없는 그림 조각이나 인물 소개 쪽의 사진 상자 + 글은 옮기지 않는다
  const boxArea = (c: number) => (bottom(c) - top(c) + 1) * (right(c) - left(c) + 1);
  let covered = 0;
  for (const c of big) {
    if (area[c] < fmul(boxArea(c), MIN_CELL_FILL)) return false;
    covered += area[c];
  }
  if (F(covered) < fmul(n, MIN_CELLS_COVER)) return false;
  // 작은 덩어리는 가운데가 든 가장 작은 칸에, 없으면 가장 가까운 칸에
  const owner = new Int32Array(count).fill(-1);
  for (const c of big) owner[c] = c;
  for (let c = 0; c < count; c++) {
    if (owner[c] >= 0) continue;
    const cx = idiv(left(c) + right(c), 2);
    const cy = idiv(top(c) + bottom(c), 2);
    let best = -1;
    let bestInside = false;
    let bestValue = 0;
    for (const b of big) {
      const dx = Math.max(left(b) - cx, 0, cx - right(b));
      const dy = Math.max(top(b) - cy, 0, cy - bottom(b));
      const inside = dx === 0 && dy === 0;
      const value = inside ? boxArea(b) : dx + dy;
      // 안에 든 칸이 늘 먼저 (Kotlin: 넓이 − Long.MAX_VALUE / 2), 같은 값이면 앞의 것
      const better = best < 0 || (inside && !bestInside) || (inside === bestInside && value < bestValue);
      if (better) {
        best = b;
        bestInside = inside;
        bestValue = value;
      }
    }
    owner[c] = best;
  }
  // 작은 덩어리는 모두 어느 칸 상자 안에 있어야 한다 — 칸 밖 글·그림이 있으면 칸만 옮길 수 없다
  for (let c = 0; c < count; c++) {
    const o = owner[c];
    if (o === c) continue;
    if (top(c) < top(o) || bottom(c) > bottom(o) || left(c) < left(o) || right(c) > right(o)) return false;
  }
  const cellOf = new Int32Array(n);
  for (let i = 0; i < n; i++) cellOf[i] = label[i] < 0 ? -1 : owner[label[i]];
  // 작은 덩어리(글자)가 맞닿은 여백 칸 — 칸마다
  const touches: number[][] = [];
  for (let c = 0; c < count; c++) touches.push([]);
  const side = roundF(fmul(width, CELL_SIDE_RATIO));
  for (let i = 0; i < n; i++) {
    const l = label[i];
    if (l < 0 || owner[l] === l) continue;
    const x = i % width;
    const y = idiv(i, width);
    const o = owner[l];
    if (x < left(o) + side || x > right(o) - side) continue;
    if (
      (x > 0 && gutter[i - 1]) ||
      (x < width - 1 && gutter[i + 1]) ||
      (y > 0 && gutter[i - width]) ||
      (y < bh - 1 && gutter[i + width])
    ) {
      touches[o].push(i);
    }
  }
  // 칸마다 열별 쪽 가운데 쪽 끝 (위로 밀면 위 끝)
  const frontOf = (c: number): Int32Array => {
    const front = new Int32Array(right(c) - left(c) + 1).fill(-1);
    for (let x = left(c); x <= right(c); x++) {
      if (up) {
        for (let y = top(c); y <= bottom(c); y++) {
          if (cellOf[y * width + x] === c) {
            front[x - left(c)] = y;
            break;
          }
        }
      } else {
        for (let y = bottom(c); y >= top(c); y--) {
          if (cellOf[y * width + x] === c) {
            front[x - left(c)] = y;
            break;
          }
        }
      }
    }
    return front;
  };
  const maxShift = roundF(fmul(height, MAX_FLAT_GAP_RATIO));
  /** 쪽 가운데 쪽 끝 줄 (위로 밀면 가장 위) */
  const frontEdge = (front: Int32Array): number => {
    let edge = -1;
    for (let k = 0; k < front.length; k++) {
      const f = front[k];
      if (f < 0) continue;
      if (edge < 0 || (up ? f < edge : f > edge)) edge = f;
    }
    return edge;
  };
  const breaksOut = (start: number[], c: number, front: Int32Array): boolean => {
    if (!start.length) return false;
    const edge = frontEdge(front);
    const near = (f: number) => f >= 0 && Math.abs(f - edge) <= maxShift;
    let firstNear = -1;
    let lastNear = -1;
    for (let k = 0; k < front.length; k++) {
      if (near(front[k])) {
        if (firstNear < 0) firstNear = k;
        lastNear = k;
      }
    }
    const first = firstNear + left(c);
    const last = lastNear + left(c);
    const seen = new Uint8Array(n);
    let seenCount = 0;
    const stack: number[] = [];
    const neighbours = (i: number) => [i - 1, i + 1, i - width, i + width];
    for (const i of start) {
      for (const j of neighbours(i)) if (j >= 0 && j < n && gutter[j]) stack.push(j);
    }
    const quarter = idiv(n, 4);
    while (stack.length) {
      const i = stack.pop() as number;
      if (seen[i]) continue;
      seen[i] = 1;
      seenCount++;
      const x = i % width;
      const y = idiv(i, width);
      if (x < left(c) || x > right(c) || y < top(c) || y > bottom(c)) continue;
      if (x >= first && x <= last && (up ? y <= edge : y >= edge)) return true;
      if (seenCount > quarter) return true;
      for (const j of neighbours(i)) if (j >= 0 && j < n && gutter[j] && !seen[j]) stack.push(j);
    }
    return false;
  };
  // 위 테두리가 선이다 — 쪽 가운데 쪽 끝에 가까운 열(가파른 옆 테두리는 빼고)이 칸 너비의 CELL_FRAME_WIDTH_RATIO 넘고,
  // 그 열들이 이웃끼리 RIBBON_STEP 줄 안에서 이어진다
  const framed = (front: Int32Array): boolean => {
    const edge = frontEdge(front);
    if (edge < 0) return false;
    const near = (f: number) => f >= 0 && Math.abs(f - edge) <= maxShift;
    let nearCount = 0;
    let first = -1;
    let last = -1;
    for (let k = 0; k < front.length; k++) {
      if (near(front[k])) {
        nearCount++;
        if (first < 0) first = k;
        last = k;
      }
    }
    if (nearCount < fmul(front.length, CELL_FRAME_WIDTH_RATIO)) return false;
    // 테두리는 한 줄로 이어진다 — 가운데가 깊게 패였으면 테두리 밖으로 나온 말풍선 등이 여백과 이어진 것이라 옮기지 않는다
    let notch = 0;
    for (let k = first; k <= last; k++) if (!near(front[k])) notch++;
    if (notch > fmul(front.length, CELL_NOTCH_RATIO)) return false;
    let pairs = 0;
    let smooth = 0;
    for (let k = 1; k < front.length; k++) {
      if (!near(front[k]) || !near(front[k - 1])) continue;
      pairs++;
      if (Math.abs(front[k] - front[k - 1]) <= RIBBON_STEP) smooth++;
    }
    return pairs > 0 && smooth >= fmul(pairs, CELL_FRAME_SMOOTH_RATIO);
  };
  const minShift = Math.max(2, roundF(fmul(height, MIN_GAP_RATIO)));
  const shift = new Int32Array(count);
  const order = up ? stableSortBy(big, (c) => top(c)) : stableSortBy(big, (c) => bottom(c), true);
  // 자리 잡은 칸들 — 옮긴 자리 기준
  const occupied = new Uint8Array(n);
  let moved = false;
  const step = up ? -1 : 1;
  for (const c of order) {
    const front = frontOf(c);
    let d = 0;
    // 글자가 맞닿은 여백이 칸 상자 안에서 위 테두리 쪽(옮기는 쪽) 끝까지 이어지면 — 위 테두리를 뚫고 나온 말풍선 속 글자 —
    // 옮기지 않는다 (말풍선 안쪽은 여백이라 함께 옮겨지지 않는다). 옆 테두리로 열린 말풍선은 괜찮다
    const loose = breaksOut(touches[c], c, front);
    if (!loose && framed(front)) {
      // 열마다 칸 끝에서 쪽 가운데 쪽으로 비어 있는 줄 수 — 그 가장 적은 곳만큼
      d = maxShift;
      for (let x = left(c); x <= right(c); x++) {
        const f = front[x - left(c)];
        if (f < 0) continue;
        let free = 0;
        let y = f + step;
        while (free < d && y >= 0 && y < bh && !occupied[y * width + x]) {
          free++;
          y += step;
        }
        d = Math.min(d, free);
        if (d < minShift) break;
      }
      if (d < minShift) d = 0;
    }
    shift[c] = d;
    if (d > 0) moved = true;
    // 자리 잡기 (작은 덩어리 포함)
    for (let yy = top(c); yy <= bottom(c); yy++) {
      for (let x = 0; x < width; x++) {
        if (cellOf[yy * width + x] === c) {
          const ny = yy + step * d;
          if (ny >= 0 && ny < bh) occupied[ny * width + x] = 1;
        }
      }
    }
    // 칸 상자 밖의 작은 덩어리도
    for (let i = 0; i < n; i++) {
      if (cellOf[i] !== c) continue;
      const row = idiv(i, width);
      if (row < top(c) || row > bottom(c)) {
        const ny = row + step * d;
        if (ny >= 0 && ny < bh) occupied[ny * width + (i % width)] = 1;
      }
    }
  }
  if (!moved) return false;
  // 여백 색 — 여백 칸의 평균
  let r = 0;
  let g = 0;
  let bl = 0;
  let m = 0;
  for (let i = 0; i < n; i++) {
    if (!gutter[i]) continue;
    const c = pixels[offset + i];
    r += (c >> 16) & 0xff;
    g += (c >> 8) & 0xff;
    bl += c & 0xff;
    m++;
  }
  let pad: number;
  if (m === 0) {
    const bg = page.paper;
    pad = (0xff << 24) | (bg << 16) | (bg << 8) | bg;
  } else {
    pad = (0xff << 24) | (Math.floor(r / m) << 16) | (Math.floor(g / m) << 8) | Math.floor(bl / m);
  }
  // 옮길 칸은 열마다 칸의 첫 칸부터 끝 칸까지 통째로 (칸 안의 말풍선 속·글자 둘레 옅은 칸까지) — 다른 칸 것은 빼고.
  // 지운 뒤 새 자리에 그린다
  const moving = new Int32Array(n).fill(-1);
  for (const c of big) {
    if (shift[c] === 0) continue;
    for (let x = left(c); x <= right(c); x++) {
      let first = -1;
      let last = -1;
      for (let y = top(c); y <= bottom(c); y++) {
        if (cellOf[y * width + x] === c) {
          if (first < 0) first = y;
          last = y;
        }
      }
      if (first < 0) continue;
      for (let y = first; y <= last; y++) {
        const i = y * width + x;
        if (cellOf[i] < 0 || cellOf[i] === c) moving[i] = c;
      }
    }
  }
  for (let i = 0; i < n; i++) if (moving[i] >= 0) block[i] = pad;
  for (let i = 0; i < n; i++) {
    const c = moving[i];
    if (c < 0) continue;
    const ny = idiv(i, width) + step * shift[c];
    if (ny >= 0 && ny < bh) block[ny * width + (i % width)] = pixels[offset + i];
  }
  return true;
}

/** 비스듬한·엇갈린 여백 띠 — x0 부터 열마다 뺄 줄의 시작 start (쪽 기준)과 뺄 줄 수 rows */
interface Ribbon {
  x0: number;
  start: Int32Array;
  rows: number;
}

/**
 * 길 위의 이웃한 두 열 구간 — 앞 열 구간 p, 이 열 구간 q, 여기까지의 값, 앞 열 상태 back.
 * fresh 면 이 열에서 시작한 길 (p 도 이 열 구간).
 */
interface Step {
  p: number;
  q: number;
  cost: number;
  back: number;
  fresh: boolean;
}

/**
 * java.util.HashMap<Long, V> 의 값 돌아보는 순서를 그대로 흉내 낸다 — Kotlin 의 길 찾기는 값이 같은 길 가운데
 * 이 순서로 먼저 나온 것을 고른다. 열쇠는 해시(= 통 번호를 정하는 값)와 구분 문자열로 준다.
 * (한 통에 9개 넘게 모여 나무로 바뀌는 경우의 순서까지는 흉내 내지 않는다 — 통이 64개 이상이면서 그만큼 몰리는 일은 사실상 없다)
 */
class JHashMap<V> {
  private table: Array<Array<{ hash: number; key: string; value: V }>> = [];
  private capacity = 0;
  private threshold = 0;
  private size = 0;
  private readonly index = new Map<string, { hash: number; key: string; value: V }>();

  get(key: string): V | undefined {
    return this.index.get(key)?.value;
  }

  put(hash: number, key: string, value: V): void {
    const existing = this.index.get(key);
    if (existing) {
      existing.value = value;
      return;
    }
    if (!this.capacity) this.resize();
    const entry = { hash, key, value };
    this.index.set(key, entry);
    const bucket = this.table[hash & (this.capacity - 1)];
    const before = bucket.length;
    bucket.push(entry);
    // 통에 8개가 있던 곳에 더하면: 표가 64칸보다 작으면 표를 키운다
    if (before >= 8 && this.capacity < 64) this.resize();
    if (++this.size > this.threshold) this.resize();
  }

  private resize(): void {
    const old = this.table;
    const oldCapacity = this.capacity;
    this.capacity = oldCapacity ? oldCapacity * 2 : 16;
    this.threshold = this.capacity * 0.75;
    const table: Array<Array<{ hash: number; key: string; value: V }>> = [];
    for (let i = 0; i < this.capacity; i++) table.push([]);
    for (const bucket of old) for (const entry of bucket) table[entry.hash & (this.capacity - 1)].push(entry);
    this.table = table;
  }

  values(): V[] {
    const out: V[] = [];
    for (const bucket of this.table) for (const entry of bucket) out.push(entry.value);
    return out;
  }
}

/**
 * [x0, x1) 열을 왼쪽 끝부터 오른쪽 끝까지 가로지르는 빈 띠들 — 줄이 아니라 열마다 빈 구간을 하나씩 골라 잇는다.
 * 칸 사이처럼 maxGap 이하로 짧고 위아래가 잉크로 막힌 구간이 대부분(RIBBON_SHORT_RATIO)이며 그 위아래 끝이
 * 매끈해야(칸 테두리) 한다 — 긴 구간(아래 칸 흰 바탕과 이어진 곳, 엇갈린 칸 사이 세로 여백)은 잇는 데만 쓴다.
 * 모든 열에서 같은 줄 수를 빼되, 열마다 앞·이·다음 열이 함께 빈 곳에서 빼므로 빼고 나서 이웃 열끼리 어긋나는 곳은 빈 곳뿐이다.
 * 양 끝 RIBBON_EDGE_RATIO 안에서는 길이 늦게 시작하거나 일찍 끝나도 된다 — 거기(가운데 접힌 곳 그림자, 쪽 여백)서는
 * 길 끝 자리에서 같은 줄 수를 빼되, 그 자리가 위아래로 밋밋해야 한다.
 */
function findRibbons(
  page: PageMaps,
  x0: number,
  x1: number,
  y0: number,
  y1: number,
  minGap: number,
  maxGap: number,
  pageWidth: number,
  scanEdge: number,
): Ribbon[] {
  const n = x1 - x0;
  if (n < 3) return [];
  const short = (r: Range): boolean => {
    const length = r.last - r.first + 1;
    return length >= minGap && length <= maxGap && r.first > y0 && r.last < y1 - 1;
  };
  const edge = roundF(fmul(n, RIBBON_EDGE_RATIO));
  const runs: Range[][] = [];
  for (let i = 0; i < n; i++) runs.push(page.cleanRuns(x0 + i, y0, y1).filter((r) => r.last - r.first + 1 >= minGap));
  // 긴 구간을 지나는 값 — 양 끝 edge 안(쪽 여백)에서는 따지지 않는다. 거기선 덜 오르내리는 길이 낫다 (끝의 잡티 사이로 가지 않게)
  const stepCost = (i: number, run: Range): number => (short(run) || i < edge || i >= n - edge ? 0 : RIBBON_LONG_COST);
  const shiftCost = (a: Range, b: Range): number => Math.abs(a.first - b.first) + Math.abs(a.last - b.last);

  // 열마다 구간까지 오는 가장 싼 길 — 긴 구간을 덜 지나고, 덜 오르내리는 길. 앞·이·다음 열 구간이 minGap 이상 함께 비어야 잇는다.
  // 왼쪽 끝 열의 모든 빈 구간(아래 칸 흰 바탕과 이어져 긴 구간이 된 여백도)과, 왼쪽 끝 가까이의 짧은 구간에서 떠난다
  const steps: Step[][] = [];
  for (let i = 0; i < n; i++) {
    const best = new JHashMap<Step>();
    const offer = (hash: number, key: string, s: Step) => {
      const known = best.get(key);
      if (!known || known.cost > s.cost) best.put(hash, key, s);
    };
    runs[i].forEach((run, q) => {
      if (i === 0 || (i <= edge && short(run))) {
        offer(q, `f${q}`, { p: q, q, cost: i * RIBBON_EDGE_COST + stepCost(i, run), back: -1, fresh: true });
      }
    });
    if (i > 0) {
      steps[i - 1].forEach((before, s) => {
        const b = runs[i - 1][before.q];
        const a = before.fresh ? b : runs[i - 2][before.p];
        runs[i].forEach((c, r) => {
          const together = Math.min(a.last, b.last, c.last) - Math.max(a.first, b.first, c.first) + 1;
          if (together < minGap) return;
          offer(before.q ^ r, `${before.q},${r}`, {
            p: before.q,
            q: r,
            cost: before.cost + stepCost(i, c) + shiftCost(b, c),
            back: s,
            fresh: false,
          });
        });
      });
    }
    steps.push(best.values());
  }
  const found: Array<{ cost: number; ribbon: Ribbon }> = [];
  // 오른쪽 끝 가까이의 짧은 구간마다 거기까지 온 가장 싼 길 하나
  for (let end = n - 1; end >= Math.max(1, n - 1 - edge); end--) {
    const groups = new Map<number, Step>();
    for (const s of steps[end]) {
      if (s.fresh || !short(runs[end][s.q])) continue;
      const known = groups.get(s.q);
      if (!known || s.cost < known.cost) groups.set(s.q, s);
    }
    // (Map 은 열쇠가 처음 들어온 순서로 돈다 — Kotlin groupBy 와 같음)
    groups.forEach((lastStep) => {
      const chosen: Range[] = [];
      let at = lastStep;
      let col = end;
      chosen.push(runs[col][at.q]);
      while (!at.fresh) {
        col--;
        at = steps[col][at.back];
        chosen.push(runs[col][at.q]);
      }
      chosen.reverse();
      // 길은 짧은 구간이 매끈하게 RIBBON_ANCHOR 열 넘게 이어지는 첫 곳부터 마지막 곳까지 —
      // 그 바깥(쪽 여백, 쪽 끝 잡티 사이)은 길로 치지 않고 아래처럼 길 끝 자리를 그대로 잇는다
      const anchored = (m: number): boolean => {
        if (m + RIBBON_ANCHOR > chosen.length) return false;
        for (let k = m; k < m + RIBBON_ANCHOR; k++) {
          if (!short(chosen[k])) return false;
          if (
            k !== m &&
            (Math.abs(chosen[k].first - chosen[k - 1].first) > RIBBON_STEP ||
              Math.abs(chosen[k].last - chosen[k - 1].last) > RIBBON_STEP)
          ) {
            return false;
          }
        }
        return true;
      };
      let head = -1;
      let lastAnchor = -1;
      for (let m = 0; m < chosen.length; m++) {
        if (anchored(m)) {
          if (head < 0) head = m;
          lastAnchor = m;
        }
      }
      if (head < 0) return;
      const tailLength = chosen.length - 1 - (lastAnchor + RIBBON_ANCHOR - 1);
      const start = col + head;
      const stop = end - tailLength;
      const path = chosen.slice(head, chosen.length - tailLength);
      const len = path.length;
      if (start > edge || stop < n - 1 - edge || len < 3) return;
      // 칸 사이 여백답게 — 짧은 구간이 대부분이고(긴 구간은 엇갈린 칸 사이 세로 여백 몇 곳뿐),
      // 그 위아래 끝(칸 테두리)이 매끈하며, 크게 오르내리지 않는다 (칸 안 빈 곳을 타고 내려가는 길이 아니다)
      let shortCount = 0;
      for (const run of path) if (short(run)) shortCount++;
      if (shortCount < fmul(len, RIBBON_SHORT_RATIO)) return;
      if (len - shortCount > fmul(len, RIBBON_MAX_LONG_RATIO)) return;
      if (Math.abs(path[len - 1].first - path[0].first) > fmul(len, RIBBON_MAX_SLOPE)) return;
      let pairs = 0;
      let smooth = 0;
      for (let m = 1; m < len; m++) {
        const a = path[m - 1];
        const b = path[m];
        if (!short(a) || !short(b)) continue;
        pairs++;
        if (Math.abs(a.first - b.first) <= RIBBON_STEP && Math.abs(a.last - b.last) <= RIBBON_STEP) smooth++;
      }
      if (smooth < fmul(pairs, RIBBON_SMOOTH_RATIO)) return;
      // 열마다 앞·이 열·다음 열이 함께 빈 곳 안에서 뺀다 — 그러면 빼고 나서 이웃 열끼리 어긋나는 곳(두 자리 사이)은
      // 두 열 모두 빈 곳뿐이다. 뺄 줄 수는 그 함께 빈 곳의 가장 좁은 곳
      const low = new Int32Array(len);
      const high = new Int32Array(len);
      let rows = MAX_INT;
      for (let m = 0; m < len; m++) {
        let a = path[m].first;
        let b = path[m].last;
        if (m > 0) {
          a = Math.max(a, path[m - 1].first);
          b = Math.min(b, path[m - 1].last);
        }
        if (m < len - 1) {
          a = Math.max(a, path[m + 1].first);
          b = Math.min(b, path[m + 1].last);
        }
        low[m] = a;
        high[m] = b;
        rows = Math.min(rows, b - a + 1);
      }
      // 여백을 RIBBON_KEEP 줄 남긴다 — 길 밖 끝 열에서 자리를 옮겨도 이웃 열과 빈 곳끼리 맞닿는다
      rows -= RIBBON_KEEP;
      if (rows < minGap) return;
      // 자리는 앞 열 자리에 가장 가깝게(덜 오르내린다), 남기는 줄은 위아래로 나눠 테두리에서 한 줄씩 띄운다
      const lo = new Int32Array(n);
      const keep = idiv(RIBBON_KEEP, 2);
      for (let m = 0; m < len; m++) {
        const prev = m > 0 ? lo[start + m - 1] : low[0] + keep;
        const min = low[m] + keep;
        const max = high[m] - rows + 1 - (RIBBON_KEEP - keep);
        lo[start + m] = prev < min ? min : prev > max ? max : prev;
      }
      // 길 밖 양 끝 열은 길 끝 자리에서 한 열씩 바깥으로, 밋밋하고 이음새가 안 보이는 자리를 RIBBON_STEP 줄 안에서
      // 가까운 곳부터 골라 따라간다. 쪽 좌우 끝 scanEdge 열(스캔 가장자리)은 보지 않고 그대로 잇는다
      const follow = (from: number, until: number, dir: number): boolean => {
        let m = from + dir;
        while (m !== until) {
          const prev = lo[m - dir];
          const x = x0 + m;
          if (x < scanEdge || x >= pageWidth - scanEdge) {
            lo[m] = prev;
            m += dir;
            continue;
          }
          const runsInCol = runs[m];
          if (!runsInCol.length) return false;
          // 뺄 줄의 위아래 끝 1줄(테두리 가장자리의 옅은 칸)은 보지 않는다
          const range = (at: number) => page.rowRange(x, at + 1, at + rows - 1);
          // 빼고 난 두 열이 자리가 달라 어긋나는 줄에서 밝기가 비슷해야 한다
          const out = (column: number, at: number, y: number) => page.lumAt(column, y < at ? y : y + rows);
          const seamless = (at: number): boolean => {
            for (let y = Math.min(prev, at); y < Math.max(prev, at); y++) {
              if (Math.abs(out(x, at, y) - out(x - dir, prev, y)) > RIBBON_FLAT_DIFF) return false;
            }
            return true;
          };
          // 가까운 자리부터 — 그 열의 빈 구간 안에서만 뺀다 (세로로 긴 칸 안은 절대 빼지 않는다)
          let best = -1;
          let chosenAt = false;
          for (let d = 0; d <= RIBBON_STEP && !chosenAt; d++) {
            const candidates = d === 0 ? [prev] : [prev - d, prev + d];
            for (const at of candidates) {
              if (
                at >= y0 &&
                at + rows <= y1 &&
                range(at) <= RIBBON_FLAT_DIFF &&
                seamless(at) &&
                runsInCol.some((r) => at >= r.first && at + rows <= r.last + 1)
              ) {
                best = at;
                chosenAt = true;
                break;
              }
            }
          }
          if (!chosenAt) return false;
          lo[m] = best;
          m += dir;
        }
        return true;
      };
      const flat = follow(start, -1, -1) && follow(stop, n, 1);
      if (!flat) return;
      found.push({ cost: lastStep.cost + (n - 1 - stop) * RIBBON_EDGE_COST, ribbon: { x0, start: lo, rows } });
    });
  }
  // 싼 길부터 — 이미 고른 띠와 어느 열에서든 겹치거나 위아래가 엇갈리면 같은 여백을 다르게 지나는 길이라 버린다
  const out: Ribbon[] = [];
  for (const { ribbon } of stableSortBy(found, (entry) => entry.cost)) {
    const clear = out.every((other) => {
      let above = true;
      let below = true;
      for (let k = 0; k < n; k++) {
        if (ribbon.start[k] + ribbon.rows > other.start[k]) above = false;
        if (other.start[k] + other.rows > ribbon.start[k]) below = false;
        if (!above && !below) return false;
      }
      return true;
    });
    if (clear) out.push(ribbon);
  }
  return stableSortBy(out, (ribbon) => ribbon.start[0]);
}

/**
 * 열마다 종이색 — 그 열의 밝은 쪽 COLUMN_PAPER_SHARE 자리 밝기. 쪽 바탕색보다 밝지 않고, MAX_SHADE_DIFF 까지만 어둡다
 * (가운데 접힌 곳 그림자 속 종이; 위아래로 다 어두운 열은 쪽 바탕색에 가깝게).
 */
function columnPaper(lum: Uint8Array, width: number, height: number, bg: number): Int32Array {
  const hist = new Int32Array(256);
  const out = new Int32Array(width);
  const limit = fmul(height, ONE_MINUS_PAPER_SHARE);
  for (let x = 0; x < width; x++) {
    hist.fill(0);
    for (let y = 0; y < height; y++) hist[lum[y * width + x]]++;
    let paper = 255;
    let seen = 0;
    while (paper > 0 && seen + hist[paper] <= limit) seen += hist[paper--];
    out[x] = paper < bg - MAX_SHADE_DIFF ? bg - MAX_SHADE_DIFF : paper > bg ? bg : paper;
  }
  return out;
}

/** 바탕 밝기 — 가장 흔한 밝기 (흰 종이면 255 근처, 검은 바탕 쪽이면 0 근처) */
function backgroundLuminance(lum: Uint8Array): number {
  const hist = new Int32Array(256);
  for (let i = 0; i < lum.length; i += 3) hist[lum[i]]++;
  let best = 0;
  for (let v = 1; v < 256; v++) if (hist[v] > hist[best]) best = v;
  return best;
}
