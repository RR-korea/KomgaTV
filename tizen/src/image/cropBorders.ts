// 여백 잘라내기 (Android CropBorders.kt, cacheKey crop-borders-9 를 그대로 옮김 — 1.0.33~1.0.45).
// 쪽 둘레의 빈 테두리(스캔 여백) 두께 — 종이가 흰색이든 검은색이든 둘레의 색으로 본다.
//
// 변마다 맨 바깥 줄에서 가장 흔한 밝기를 여백 색으로 삼고(밝으면 MIN_LIGHT_BORDER 이상, 어두우면 MAX_DARK_BORDER 이하만),
// 열(좌우 변이면 줄)마다 쪽 끝에서 안쪽으로 여백 색(BORDER_TOLERANCE 안)이 몇 픽셀 이어지는지 잰다.
// 위아래: 칸 테두리 밖으로 그림이 나온 열(쪽 끝까지 그린 칸, 칸 밖 머리·옷자락)이나 쪽 번호가 있는 열은 여백이 얕지만,
// 테두리가 있는 열들에 맞춘다 — 열 중 COVER_RATIO 가 그만큼 이상 여백이고 그 깊이에 칸 테두리 선이 있으면 거기까지.
// 그래서 칸 밖으로 나온 그림 끝과 쪽 번호는 테두리와 함께 잘린다. 그림이 쪽 끝까지 꽉 찬 변은 여백이 없어 그대로다.
// 네 변 모두: 칸 밖 쪽 번호·광고·(세로) 글이 여백을 넓게 가려도, 그 너머에 쪽을 길게 가로지르는 칸 테두리 선이 있으면 거기까지.
// 그 밖에는 모든 열이 여백인 곳까지만 (1줄 먼지는 봐준다, 가는 펜선은 지킨다) — 좌우는 칸 밖으로 나온 그림을 자르지 않는다.
//
// 검은 테두리는 네 변 중 셋 이상의 바깥 줄이 검을 때만 본다 (검은 바탕에 스캔한 쪽) — 흰 종이 쪽 아래 끝까지
// 이어진 검은 밤하늘 같은 그림은 테두리가 아니다. 흰 종이 쪽 끝의 얇은 검은 스캔 선(THIN_EDGE_RATIO 안)은 건너
// 그 안쪽 흰 여백을 잰다. 한 변에서 쪽의 MAX_CROP_RATIO 까지만 잰다.
//
// 규칙은 모든 만화책에 적용할 수 있어야 한다 — 특정 쪽만을 위한 예외 규칙은 두지 않는다 (사용자 11-21).
// Kotlin 과 같은 결과가 나오게 Float 계산(Math.fround)까지 맞췄다. 픽셀은 ARGB 정수 Int32Array.

const F = Math.fround;
/** Kotlin 의 Int × Float (결과 Float) */
const fmul = (a: number, b: number): number => Math.fround(Math.fround(a) * b);

/** 이보다 작은 그림은 보지 않는다 */
const MIN_SIDE = 16;
/** 여백 색의 밝기 폭 — 종이 결, 스캔 잡티, JPEG 얼룩 */
const BORDER_TOLERANCE = 40;
/** 위아래 변에서 칸 테두리 선으로 칠 가로 잉크의 길이 — 변 길이의 이만큼 (글자 획·머리카락은 짧다) */
const BORDER_LINE_RATIO = F(0.08);
/** 같은 깊이에서 칸 테두리 선에 닿은 열이 이만큼은 돼야 그 선에 맞춰 자른다 */
const LINE_SUPPORT_RATIO = F(0.2);
/** 위아래 변의 열 중 이만큼이 그 깊이까지 여백이면 거기까지가 테두리 — 나머지 열은 칸 밖으로 나온 그림·쪽 번호 */
const COVER_RATIO = F(0.3);
/** 위아래 변에서 칸 밖 쪽 번호·광고 너머의 테두리 선은 쪽의 이만큼 안쪽까지만 찾는다 */
const MAX_OUTSIDE_TEXT_RATIO = F(0.1);
/** 칸 테두리 선에서 쪽 끝 쪽으로 이만큼(변 길이 비율) 곧게 이어진 잉크는 칸 옆 테두리 */
const SIDE_CHECK_RATIO = F(0.02);
/** 좌우 변에서 줄의 이만큼이 쪽 끝에 닿으면 쪽 끝까지 그린 그림 — 칸 테두리에 맞춰 자르지 않는다 */
const BLEED_RATIO = F(0.02);
/** 쪽 번호·광고에 가려 선에 닿은 열이 적어도 이만큼은 있고 — */
const MIN_LINE_SUPPORT_RATIO = F(0.05);
/** — 그 선(테두리 선 길이 이상 이어진 잉크)이 변 길이의 이만큼을 가로지르면 칸 테두리 */
const WIDE_LINE_RATIO = F(0.5);
/** 같은 테두리 선에 닿은 것으로 볼 깊이 차이 — 변 길이의 이만큼 (기울어진 스캔) */
const LINE_DEPTH_TOLERANCE = F(0.005);
/** 이만큼 밝으면 종이 테두리 */
const MIN_LIGHT_BORDER = 180;
/** 이만큼 어두우면 검은 테두리 */
const MAX_DARK_BORDER = 85;
/** 흰 종이 쪽에서도 쪽 끝에서 이만큼 안의 검은 줄은 스캔 선으로 보고 뺀다 */
const THIN_EDGE_RATIO = F(0.01);
/** 한 변에서 쪽의 이만큼까지만 뺀다 */
const MAX_CROP_RATIO = F(0.2);
/** 한 변의 테두리가 쪽의 이만큼보다 얇으면 테두리가 아니다 */
const MIN_BORDER_RATIO = F(0.01);
/** 위아래·좌우 모두 이만큼도 안 빠지면 그대로 둔다 */
const MIN_CROP_RATIO = F(0.005);

/**
 * @param topBottom 위아래를 볼지
 * @param leftRight 좌우를 볼지 (위아래도 보면 위아래 테두리를 뺀 줄 사이에서만)
 * @param blankOnly 모든 줄(열)이 여백인 곳까지만 — 칸 테두리에 맞추지 않는다 (칸 간격 없애기가 남긴 빈 줄만 뺄 때)
 * @returns 변마다 테두리 줄 수 [위, 아래, 왼쪽, 오른쪽], 모두 거의 없으면 null
 */
export function findBorders(
  pixels: Int32Array,
  width: number,
  height: number,
  topBottom: boolean,
  leftRight: boolean,
  blankOnly = false,
): number[] | null {
  if (width < MIN_SIDE || height < MIN_SIDE) return null;
  const lum = new Uint8Array(pixels.length);
  for (let i = 0; i < pixels.length; i++) {
    const c = pixels[i];
    lum[i] = ((((c >> 16) & 0xff) * 299 + ((c >> 8) & 0xff) * 587 + (c & 0xff) * 114) / 1000) | 0;
  }
  const hist = new Int32Array(256);

  /** 첫 픽셀 start 부터 step 간격 count 개 픽셀 중 가장 흔한 밝기 */
  const commonLevel = (start: number, step: number, count: number): number => {
    hist.fill(0);
    let i = start;
    for (let k = 0; k < count; k++) {
      hist[lum[i]]++;
      i += step;
    }
    let peak = 0;
    for (let v = 1; v < 256; v++) if (hist[v] > hist[peak]) peak = v;
    return peak;
  };

  const isDark = (level: number) => level <= MAX_DARK_BORDER;

  /** 맨 바깥 줄과 그 안쪽 줄들 중 더 어두운 레벨 (가장자리 빛샘·노이즈 방어) */
  const edgeLevel = (start: number, step: number, count: number, inward: number): number => {
    const l0 = commonLevel(start, step, count);
    const l1 = commonLevel(start + inward, step, count);
    const l2 = commonLevel(start + 2 * inward, step, count);
    const minLevel = Math.min(l0, l1, l2);
    return isDark(minLevel) ? minLevel : l0;
  };

  // 네 변 중 셋 이상의 바깥 레벨이 검거나, 둘 이상이 짙은 검은색이면서 흰 종이(MIN_LIGHT_BORDER 이상) 변이 없으면 검은 바탕 쪽
  const edgeLevels = [
    edgeLevel(0, 1, width, width),
    edgeLevel((height - 1) * width, 1, width, -width),
    edgeLevel(0, width, height, 1),
    edgeLevel(width - 1, width, height, -1),
  ];
  const countLevels = (test: (level: number) => boolean) => edgeLevels.filter(test).length;
  const darkPage =
    countLevels(isDark) >= 3 ||
    (countLevels((level) => level <= 50) >= 2 &&
      edgeLevels.every((level) => level < MIN_LIGHT_BORDER) &&
      countLevels((level) => level <= 110) >= 3);

  /**
   * 한 변의 테두리 두께. 변을 따라 count 개 선(열 또는 줄)이 있고, k번째 선의 쪽 끝 픽셀은 first + k×across,
   * 안쪽으로 한 픽셀 갈 때마다 inward 만큼 움직인다. depthLimit 까지, 변에 수직인 길이 length.
   * side: 좌우 변 — 칸 밖으로 나온 그림은 자르지 않고, 쪽 끝까지 그린 그림(줄의 BLEED_RATIO 이상이 쪽 끝에 닿음)이 있으면
   * 칸 테두리에 맞추지 않는다 (사용자 11-15 "테두리가 있는 쪽에 맞춰 없는 부분을 잘라냄 (이건 상하의 경우에만)").
   */
  const sideDepth = (
    first: number,
    across: number,
    count: number,
    inward: number,
    length: number,
    depthLimit: number,
    side = false,
  ): number => {
    if (count <= 0) return 0;
    // 흰 종이 쪽 끝의 얇은 검은 스캔 선 또는 검은 바탕 가장자리 빛샘 건넌다
    let skip = 0;
    let level = commonLevel(first, across, count);
    if (darkPage) {
      const l1 = commonLevel(first + inward, across, count);
      const l2 = commonLevel(first + 2 * inward, across, count);
      if (isDark(l1) && (!isDark(level) || l1 + BORDER_TOLERANCE < level)) {
        skip = 1;
        level = l1;
      } else if (isDark(l2) && (!isDark(level) || l2 + BORDER_TOLERANCE < level)) {
        skip = 2;
        level = l2;
      }
    } else if (isDark(level)) {
      skip = Math.ceil(fmul(length, THIN_EDGE_RATIO));
      level = commonLevel(first + skip * inward, across, count);
    }
    const margin = level >= MIN_LIGHT_BORDER || (isDark(level) && darkPage);
    if (!margin) return 0;
    const off = (i: number): boolean => Math.abs(lum[i] - level) > BORDER_TOLERANCE;
    const depths = new Int32Array(count);
    for (let k = 0; k < count; k++) {
      const edge = first + k * across;
      let d = skip;
      while (d < depthLimit && !off(edge + d * inward)) d++;
      depths[k] = d;
    }

    /** 모든 선이 여백인 깊이 — 1줄짜리 고립된 먼지 점(안쪽 1~2px 가 다시 여백인 점)만 이웃 선으로 완화하고, 안쪽으로 이어지는 펜선(머리카락 끝·칼끝)은 보존 */
    const strictDepth = (): number => {
      const isDust = (i: number, d: number): boolean => {
        if (d + 2 >= depthLimit) return false;
        const edge = first + i * across;
        const step1 = off(edge + (d + 1) * inward);
        const step2 = off(edge + (d + 2) * inward);
        if (step1) return !step2 && (d + 3 >= depthLimit || !off(edge + (d + 3) * inward));
        return !step2;
      };
      let min = -1;
      for (let i = 0; i < count; i++) {
        const prev = i > 0 ? depths[i - 1] : depths[i];
        const next = i < count - 1 ? depths[i + 1] : depths[i];
        const smoothed =
          depths[i] < prev && depths[i] < next && isDust(i, depths[i]) ? Math.max(depths[i], Math.min(prev, next)) : depths[i];
        if (min < 0 || smoothed < min) min = smoothed;
      }
      return min < 0 ? 0 : min;
    };

    /** k번째 선이 d 깊이에서 멈춘 픽셀이 변을 따라 길게 이어진 잉크(칸 테두리 선)에 들어 있는지 */
    const stopsAtLine = (k: number, d: number): boolean => {
      if (d >= depthLimit) return false;
      const inked = (j: number) => off(first + j * across + d * inward);
      let a = k;
      let b = k;
      while (a > 0 && inked(a - 1)) a--;
      while (b < count - 1 && inked(b + 1)) b++;
      return b - a + 1 >= fmul(count, BORDER_LINE_RATIO);
    };

    const tolerance = Math.max(2, Math.trunc(fmul(length, LINE_DEPTH_TOLERANCE)));

    /** d~d+tolerance 줄에서 BORDER_LINE_RATIO 보다 길게 이어진 잉크 [시작, 끝] 목록 (기울어진 테두리 선도 잰다) */
    const longRuns = (d: number): Array<[number, number]> => {
      const inked = new Uint8Array(count);
      const to = Math.min(d + tolerance, length - 1);
      for (let k = 0; k < count; k++) {
        for (let depth = d; depth <= to; depth++) {
          if (off(first + k * across + depth * inward)) {
            inked[k] = 1;
            break;
          }
        }
      }
      const runs: Array<[number, number]> = [];
      const minRun = fmul(count, BORDER_LINE_RATIO);
      let start = -1;
      for (let k = 0; k <= count; k++) {
        if (k < count && inked[k]) {
          if (start < 0) start = k;
        } else {
          if (start >= 0 && k - start >= minRun) runs.push([start, k - 1]);
          start = -1;
        }
      }
      return runs;
    };

    /** d 깊이 선에서 쪽 끝 쪽으로 곧게 이어진 잉크(칸 옆 테두리)가 있는지 — 있으면 그 선은 쪽 끝까지 그린 칸의 안쪽 테두리다 */
    const goesOutward = (d: number): boolean => {
      const h = Math.min(Math.max(3, Math.trunc(fmul(length, SIDE_CHECK_RATIO))), d - skip);
      if (h < 3) return false;
      for (let k = 0; k < count; k++) {
        let all = true;
        for (let step = 1; step <= h; step++) {
          if (!off(first + k * across + (d - step) * inward)) {
            all = false;
            break;
          }
        }
        if (all) return true;
      }
      return false;
    };

    /**
     * 칸 밖 쪽 번호·광고·글에 가려 선에 닿은 열이 적어도(MIN_LINE_SUPPORT_RATIO), 쪽을 길게(WIDE_LINE_RATIO) 가로지르는
     * 칸 테두리 선 — 쪽 끝 쪽으로 칸 옆 테두리가 이어지지 않고 선이 쪽 양 끝까지 닿지 않을 때만 (쪽 끝까지 그린 칸의 안쪽 테두리가 아니다).
     * 쪽 끝에 가장 가까운 것, 없으면 -1
     */
    const outsideTextLine = (): number => {
      const hits: number[] = [];
      for (let k = 0; k < count; k++) if (stopsAtLine(k, depths[k])) hits.push(depths[k]);
      const lineDepths = Int32Array.from(hits).sort();
      let lo = 0;
      for (let hi = 0; hi < lineDepths.length; hi++) {
        while (lineDepths[hi] - lineDepths[lo] > tolerance) lo++;
        // 깊이가 같은 열을 다 모은 뒤에 본다
        if (hi + 1 < lineDepths.length && lineDepths[hi + 1] - lineDepths[lo] <= tolerance) continue;
        if (hi - lo + 1 < fmul(count, MIN_LINE_SUPPORT_RATIO)) continue;
        const d = lineDepths[lo];
        // 위아래의 쪽 번호·광고 띠는 얇다 — 더 깊으면 칸 사이에 떠 있는 그림·대사(웹툰)다
        if (!side && d > fmul(length, MAX_OUTSIDE_TEXT_RATIO)) break;
        const runs = longRuns(d);
        let covered = 0;
        for (const run of runs) covered += run[1] - run[0] + 1;
        if (covered < fmul(count, WIDE_LINE_RATIO)) continue;
        // (좌우는 위아래를 잘라낸 줄 사이만 보므로 세로 테두리가 늘 양 끝에 닿는다)
        if ((!side && runs.some((run) => run[0] <= 2 || run[1] >= count - 3)) || goesOutward(d)) continue;
        return d;
      }
      return -1;
    };

    let depth: number;
    if (blankOnly) {
      depth = strictDepth();
    } else if (!side) {
      // 위아래 변: 선 중 COVER_RATIO 가 이만큼 이상 여백인 깊이 (칸 밖 머리·옷자락·쪽 번호 무시) —
      // 단 그 깊이에서 여러 열(LINE_SUPPORT_RATIO)이 함께 칸 테두리 선에 닿을 때만. 아니면 칸 밖 글·쪽 번호·광고 너머의 테두리 선,
      // 그것도 없으면(테두리 없이 열린 칸, 칸 밖 글 문단) 모든 열이 여백인 곳까지만
      const sorted = Int32Array.from(depths).sort();
      // 내림차순의 n번째 = 오름차순의 (count − 1 − n)번째
      const candidate = sorted[count - 1 - Math.min(count - 1, Math.trunc(fmul(count, COVER_RATIO)))];
      let onLine = 0;
      for (let k = 0; k < count; k++) {
        if (Math.abs(depths[k] - candidate) <= tolerance && stopsAtLine(k, depths[k])) onLine++;
      }
      if (onLine >= fmul(count, LINE_SUPPORT_RATIO)) {
        depth = candidate;
      } else {
        const line = outsideTextLine();
        depth = line >= 0 ? line : strictDepth();
      }
    } else {
      // 좌우 변: 모든 줄이 여백인 곳까지만 (칸 밖으로 나온 그림은 자르지 않는다) — 단 칸 밖 세로 글·쪽 번호·광고 너머에
      // 칸 테두리 선이 길게 있으면 거기까지. 쪽 끝까지 그린 그림이 있는 변이면 맞추지 않는다
      let touching = 0;
      for (let k = 0; k < count; k++) if (depths[k] <= skip) touching++;
      const bleeds = touching >= fmul(count, BLEED_RATIO);
      const line = bleeds ? -1 : outsideTextLine();
      depth = line >= 0 ? line : strictDepth();
    }
    // 아주 얇은 것은 그림 결이다 (쪽 끝까지 그린 그림의 잔 무늬)
    return depth > skip && depth >= fmul(length, MIN_BORDER_RATIO) ? depth : 0;
  };

  const maxY = Math.trunc(fmul(height, MAX_CROP_RATIO));
  const maxX = Math.trunc(fmul(width, MAX_CROP_RATIO));
  let top = 0;
  let bottom = 0;
  if (topBottom) {
    top = sideDepth(0, 1, width, width, height, maxY);
    bottom = sideDepth((height - 1) * width, 1, width, -width, height, maxY);
  }
  let left = 0;
  let right = 0;
  if (leftRight) {
    // 위아래 여백(쪽 번호 등)을 뺀 줄 사이에서만 본다
    const rows = height - top - bottom;
    left = sideDepth(top * width, width, rows, 1, width, maxX, true);
    right = sideDepth(top * width + width - 1, width, rows, -1, width, maxX, true);
  }
  if (top + bottom < fmul(height, MIN_CROP_RATIO) && left + right < fmul(width, MIN_CROP_RATIO)) return null;
  return [top, bottom, left, right];
}
