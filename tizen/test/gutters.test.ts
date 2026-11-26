import { describe, expect, it } from 'vitest';
import { collapseGutters } from '../src/image/gutters';

// Android feature/reader/src/test/java/com/komgatv/feature/reader/enhance/GuttersTest.kt 를 그대로 옮겼다.

const white = -1;
const black = 0xff000000 | 0;
const w = 100;
const h = 400;

/** Kotlin Int 나눗셈 (0 쪽으로 버림) */
const div = (a: number, b: number): number => Math.trunc(a / b);
/** Kotlin `a until b` */
const until = (a: number, b: number): number[] => Array.from({ length: Math.max(0, b - a) }, (_, i) => a + i);
/** Kotlin `a..b` */
const rangeTo = (a: number, b: number): number[] => until(a, b + 1);
const sum = (xs: number[]): number => xs.reduce((s, v) => s + v, 0);
const filled = (n: number, v: number): Int32Array => new Int32Array(n).fill(v);

/** Kotlin requireNotNull */
function req<T>(v: T | null): T {
  expect(v).not.toBeNull();
  return v as T;
}

/** 테두리 2px짜리 칸: 가로 10~89, 세로 [top]부터 100줄 */
function panel(px: Int32Array, top: number): void {
  for (let y = top; y < top + 100; y++)
    for (let x = 10; x < 90; x++) {
      const edge = y < top + 2 || y >= top + 98 || x < 12 || x >= 88;
      if (edge) px[y * w + x] = black;
    }
}

/** 테두리 2px짜리 칸: 가로 [x0]~[x1] 앞, 세로 [y0]~[y1] 앞 */
function box(px: Int32Array, x0: number, x1: number, y0: number, y1: number): void {
  for (let y = y0; y < y1; y++)
    for (let x = x0; x < x1; x++) {
      const edge = y < y0 + 2 || y >= y1 - 2 || x < x0 + 2 || x >= x1 - 2;
      if (edge) px[y * w + x] = black;
    }
}

/** 칸 A(위 테두리 40줄, 아래 테두리는 [slant]만큼 기울었다)와 그 [gap]줄 아래에서 같은 기울기로 시작해 300줄에서 끝나는 칸 B */
function slantedPanels(px: Int32Array, gap: number, slant: (x: number) => number): void {
  for (let x = 10; x < 90; x++) {
    const b = slant(x);
    for (let y = 40; y < b + 2; y++) if (y < 42 || y >= b || x < 12 || x >= 88) px[y * w + x] = black;
    for (let y = b + 2 + gap; y < 302; y++) if (y < b + 4 + gap || y >= 300 || x < 12 || x >= 88) px[y * w + x] = black;
  }
}

/** [x] 열에서 검은 줄들이 (통째로 옮겨졌을 수는 있어도) 간격 그대로인지 */
function assertRowsKept(input: Int32Array, out: Int32Array | null, width: number, x: number): void {
  const before = until(0, h).filter((y) => input[y * width + x] === black);
  const after = until(0, h).filter((y) => (out ?? input)[y * width + x] === black);
  expect(after.length).toBe(before.length);
  expect(after.map((y) => y - after[0])).toEqual(before.map((y) => y - before[0]));
}

describe('칸 사이 여백 붙이기 (Android GuttersTest.kt)', () => {
  it('칸 사이 여백을 얇게 줄이고 가운데로 모으며, 여백의 글자는 무시하고 지운다', () => {
    const px = filled(w * h, white);
    panel(px, 40);
    panel(px, 200);
    // 칸 사이(140~199)의 작은 글자 — 획 높이 10줄이라 칸으로 치지 않는다
    for (let y = 165; y < 175; y++) for (let x = 30; x < 60; x++) if (x % 6 < 3) px[y * w + x] = black;

    const out = req(collapseGutters(px, w, h));

    // 칸 100줄 + 칸 100줄 = 200줄이 붙고, 위아래 100줄씩 비운다
    const blackRows = until(0, h).filter((y) => out[y * w + 45] === black);
    expect(blackRows).toEqual([100, 101, 198, 199, 200, 297, 298]);
    // 좌우는 그대로
    expect(out[150 * w + 10]).toBe(black);
    expect(out[150 * w + 9]).toBe(white);
  });

  it('옆에 긴 칸이 있는 작은 칸들도 여백을 붙여 쪽 가운데 쪽으로 민다', () => {
    const px = filled(w * h, white);
    // 오른쪽 세로로 긴 칸, 왼쪽에 작은 칸 둘 (사이 40줄) — 쪽 위쪽 절반
    box(px, 55, 95, 20, 180);
    box(px, 6, 50, 20, 80);
    box(px, 6, 50, 120, 180);

    const out = req(collapseGutters(px, w, h));

    // 띠 160줄이 가운데(120~279)로. 긴 칸은 그대로
    expect(until(0, h).filter((y) => out[y * w + 70] === black)).toEqual([120, 121, 278, 279]);
    // 작은 칸 둘은 맞붙고, 위쪽 띠라 아래(쪽 가운데 쪽)로 붙는다
    expect(until(0, h).filter((y) => out[y * w + 20] === black)).toEqual([161, 162, 219, 220, 221, 278, 279]);
  });

  it('끝 띠의 칸 묶음은 위 띠 쪽으로 붙인다 - 빼낸 띠 사이 여백이 다시 생기지 않는다', () => {
    const px = filled(w * h, white);
    // 1단: 넓은 칸. 2단: 왼쪽 긴 칸(쪽 바닥 70줄 위에서 끝남) + 오른쪽 작은 칸 둘(아래 칸은 쪽 바닥까지) — 킹덤 34권 176쪽
    box(px, 6, 94, 20, 100);
    box(px, 6, 50, 130, 330);
    box(px, 55, 94, 130, 220);
    box(px, 55, 94, 250, 400);

    const out = req(collapseGutters(px, w, h));

    // 1단 아래 테두리와 2단 칸들의 위 테두리 사이에 흰 줄이 없다 (맞닿은 테두리는 한 줄로 겹친다)
    /** 이어진 검은 줄 묶음 — [처음, 끝] (끝 포함, Kotlin IntRange) */
    function runs(x: number): Array<[number, number]> {
      const rows = until(0, h).filter((y) => out[y * w + x] === black);
      const result: Array<[number, number]> = [];
      let start = rows[0];
      for (let i = 1; i < rows.length; i++)
        if (rows[i] !== rows[i - 1] + 1) {
          result.push([start, rows[i - 1]]);
          start = rows[i];
        }
      result.push([start, rows[rows.length - 1]]);
      return result;
    }
    // 왼쪽 긴 칸 열: 1단 위 테두리, 1단 아래+긴 칸 위 테두리, 긴 칸 아래 테두리
    expect(runs(20).length).toBe(3);
    // 오른쪽 열: 1단 아래 테두리와 작은 칸 위 테두리도 붙는다
    expect(runs(70)[1][0]).toBe(runs(20)[1][0]);
  });

  it('칸 묶음 사이 여백의 쪽 번호는 여백과 함께 지운다', () => {
    const px = filled(w * h, white);
    // 오른쪽 세로로 긴 칸, 왼쪽에 작은 칸 둘 (사이 40줄) — 그 사이 여백 왼쪽에 쪽 번호 ("10", 획 높이 10줄)
    box(px, 55, 95, 20, 180);
    box(px, 6, 50, 20, 80);
    box(px, 6, 50, 120, 180);
    for (let y = 95; y < 105; y++) {
      px[y * w + 10] = black;
      for (let x = 13; x <= 16; x++) if (y === 95 || y === 104 || x === 13 || x === 16) px[y * w + x] = black;
    }

    const out = req(collapseGutters(px, w, h));

    // 쪽 번호가 없을 때처럼 작은 칸 둘이 맞붙는다
    expect(until(0, h).filter((y) => out[y * w + 20] === black)).toEqual([161, 162, 219, 220, 221, 278, 279]);
    // 쪽 번호는 남지 않는다 (작은 칸 테두리 2줄씩만)
    expect(until(0, h).filter((y) => out[y * w + 13] === black)).toEqual([161, 162, 219, 220, 221, 278, 279]);
  });

  it('칸 사이에 걸친 글 상자는 여백으로 보고, 상자는 빈 안쪽만 짧아지며 테두리가 이어진다', () => {
    const px = filled(w * h, white);
    panel(px, 40);
    panel(px, 200);
    // 칸 A 아래쪽부터 칸 B 중간까지 걸친 좁은 글 상자 (안쪽은 비어 있다)
    box(px, 60, 80, 100, 250);

    const out = req(collapseGutters(px, w, h));

    // 칸은 첫 시험과 같은 자리로 붙는다
    expect(until(0, h).filter((y) => out[y * w + 45] === black)).toEqual([100, 101, 198, 199, 200, 297, 298]);
    // 상자 왼쪽 테두리는 끊기지 않고 160~248로 이어진다 (칸 A 위 테두리 100·101, 칸 B 아래 테두리 297·298)
    const boxRows = until(0, h).filter((y) => out[y * w + 60] === black);
    expect(boxRows).toEqual([100, 101, ...rangeTo(160, 248), 297, 298]);
  });

  it('기울어진 스캔의 테두리 끝자락이 여백에 걸쳐도 테두리를 자르지 않고 칸을 붙인다', () => {
    const px = filled(w * h, white);
    panel(px, 40);
    panel(px, 200);
    // 칸 A 아래 테두리가 오른쪽으로 갈수록 3줄 내려가고(138~142), 칸 B 위 테두리도 기울었다(196~200)
    for (let x = 10; x < 90; x++) {
      const down = div((x - 10) * 4, 80);
      for (let y = 138 + down; y < 140 + down; y++) px[y * w + x] = black;
      for (let y = 196 + down; y < 198 + down; y++) px[y * w + x] = black;
    }

    const out = req(collapseGutters(px, w, h));

    // 오른쪽 끝(x=80) 테두리 줄은 맞닿는 테두리가 1줄 겹치는 것 말고는 잘리지 않는다
    expect(until(0, h).filter((y) => out[y * w + 80] === black).length).toBe(
      until(0, h).filter((y) => px[y * w + 80] === black).length - 1,
    );
    // 칸 사이 흰 곳은 없어진다 — 칸 A 아래 테두리 끝(142)과 칸 B 위 테두리 시작(196) 사이 53줄에 더해
    // 기울어진 테두리 끝자락과 맞닿는 테두리가 4줄 겹친다
    const before = until(0, h).filter((y) => px[y * w + 80] === black);
    const after = until(0, h).filter((y) => out[y * w + 80] === black);
    expect(
      before[before.length - 1] - before[0] - (after[after.length - 1] - after[0]),
    ).toBe(57);
  });

  it('쪽 위아래 끝까지 찬 펼침 쪽도 칸 묶음 안의 여백은 붙인다', () => {
    const px = filled(w * h, white);
    // 왼쪽은 쪽 높이 전부를 차는 칸, 오른쪽은 위아래 끝에 닿은 작은 칸 둘 (사이 40줄) — 쪽 전체를 가로지르는 여백이 없다
    for (const [x0, x1, y0, y1] of [
      [6, 45, 0, h],
      [55, 95, 0, 180],
      [55, 95, 220, h],
    ]) {
      box(px, x0, x1, y0, y1);
      // 칸 안 그림 (빗금)
      for (let y = y0 + 5; y < y1 - 5; y++) for (let x = x0 + 5; x < x1 - 5; x++) if ((x + y) % 7 === 0) px[y * w + x] = black;
    }

    const out = req(collapseGutters(px, w, h));

    // 왼쪽 칸은 그대로, 오른쪽 칸 둘은 맞붙어 가운데로
    expect(until(0, h).filter((y) => out[y * w + 9] === black)).toEqual([0, 1, 398, 399]);
    expect(until(0, h).filter((y) => out[y * w + 58] === black)).toEqual([20, 21, 198, 199, 200, 377, 378]);
  });

  it('쪽 끝에 바짝 붙은 칸 테두리는 칸이다', () => {
    const wide = 400;
    const px = filled(wide * h, white);
    // 좌우 테두리가 쪽 끝(2% 안)에 붙은 칸 둘, 사이 60줄
    for (const [y0, y1] of [
      [40, 140],
      [200, 300],
    ])
      for (let y = y0; y < y1; y++)
        for (let x = 2; x < 398; x++) {
          if (y < y0 + 2 || y >= y1 - 2 || x < 4 || x >= 396) px[y * wide + x] = black;
        }

    const out = req(collapseGutters(px, wide, h));

    // 칸 안쪽 빈 곳은 그대로 두고 사이 여백만 없앤다
    expect(until(0, h).filter((y) => out[y * wide + 200] === black)).toEqual([100, 101, 198, 199, 200, 297, 298]);
    expect(until(0, h).filter((y) => out[y * wide + 2] === black).length).toBe(199);
  });

  it('칸 사이 여백에 좁게 걸친 효과음은 여백과 함께 짧아진다', () => {
    const px = filled(w * h, white);
    panel(px, 40);
    box(px, 10, 90, 160, 260);
    // 칸 A 아래쪽부터 칸 B 위쪽까지 걸친 효과음 — 폭 7칸, 획 높이 3줄
    for (let y = 120; y < 180; y++) for (let x = 20; x <= 26; x++) if (y % 6 < 3) px[y * w + x] = black;

    const out = req(collapseGutters(px, w, h));

    // 칸 100줄 + 칸 100줄이 맞붙어 가운데로 — 맞닿는 테두리는 1줄 겹친다
    expect(until(0, h).filter((y) => out[y * w + 45] === black)).toEqual([100, 101, 198, 199, 200, 297, 298]);
  });

  it('비스듬한 칸 테두리 사이 여백도 기울기 그대로 붙이고, 아래 칸은 일그러지지 않는다', () => {
    const px = filled(w * h, white);
    // 아래 테두리가 왼쪽 140줄에서 오른쪽 152줄로 기운다 (여백 10줄보다 더) — 쪽 전체를 가로지르는 빈 줄이 없다
    const slant = (x: number): number => 140 + div((x - 10) * 12, 79);
    slantedPanels(px, 10, slant);

    const out = req(collapseGutters(px, w, h));

    const blackRows = (x: number): number[] => until(0, h).filter((y) => out[y * w + x] === black);
    for (const x of [12, 30, 50, 70, 87]) {
      const rows = blackRows(x);
      // 위 테두리 2줄, A 아래 테두리 2줄, B 위 테두리 2줄, B 아래 테두리 2줄 — A·B 사이 빈 줄은 10줄에서 4줄 이하로
      expect(rows.length).toBe(8);
      expect(rows[4] - rows[3] - 1 <= 4).toBe(true);
      // A 안쪽 높이는 그대로
      expect(rows[2] - rows[0]).toBe(slant(x) - 40);
    }
    // 칸 B는 통째로 올라왔다 — 아래 테두리가 모든 열에서 같은 줄, 기울기도 그대로
    expect(new Set([12, 30, 50, 70, 87].map((x) => blackRows(x)[6])).size).toBe(1);
    expect(blackRows(87)[4] - blackRows(12)[4]).toBe(slant(87) - slant(12));
  });

  it('그림 속 들쭉날쭉한 흰 틈은 비스듬한 여백으로 보지 않는다', () => {
    const px = filled(w * h, white);
    // 위아래 칸 사이 흰 틈이 열마다 크게 오르내린다 (그림 속 빈 곳)
    slantedPanels(px, 18, (x) => 150 + ((x * 37) % 23));

    const out = collapseGutters(px, w, h);

    // 틈은 줄지 않는다 (통째로 옮겨졌을 수는 있다)
    assertRowsKept(px, out, w, 50);
  });

  it('좌우 두 쪽을 한 장에 스캔한 그림은 쪽마다 따로 칸을 붙인다 - 가운데 접힌 곳 그림자는 잉크가 아니다', () => {
    const wide = 260;
    const px = filled(wide * h, white);
    // 가운데 접힌 곳 그림자 — 100~159열, 가운데로 갈수록 어둡다 (가장 어두운 곳 밝기 150). 칸 안쪽까지 걸친다
    function shade(x: number): number {
      const v = 255 - div(Math.max(0, 30 - Math.abs(x - 130)) * 105, 30);
      return (0xff << 24) | (v << 16) | (v << 8) | v;
    }
    for (let y = 0; y < h; y++) for (let x = 100; x < 160; x++) px[y * wide + x] = shade(x);
    function wideBox(x0: number, x1: number, y0: number, y1: number): void {
      for (let y = y0; y < y1; y++)
        for (let x = x0; x < x1; x++) {
          if (y < y0 + 2 || y >= y1 - 2 || x < x0 + 2 || x >= x1 - 2) px[y * wide + x] = black;
        }
    }
    // 왼쪽 쪽: 칸 사이 200~239줄, 오른쪽 쪽: 칸 사이 120~159줄 — 쪽 전체를 가로지르는 여백이 없다
    wideBox(10, 122, 40, 200);
    wideBox(10, 122, 240, 360);
    wideBox(138, 250, 40, 120);
    wideBox(138, 250, 160, 360);

    const out = req(collapseGutters(px, wide, h));

    // 두 쪽 다 칸 둘이 맞붙어 맞닿는 테두리가 1줄 겹친다 (높이 279줄)
    for (const x of [60, 200]) {
      const rows = until(0, h).filter((y) => out[y * wide + x] === black);
      expect(rows.length).toBe(7);
      expect(rows[3]).toBe(rows[2] + 1);
      expect(rows[4]).toBe(rows[3] + 1);
      expect(rows[rows.length - 1] - rows[0]).toBe(278);
    }
    // 그림자는 그대로
    expect(out[200 * wide + 130]).toBe(shade(130));
  });

  it('세로 여백도 비스듬해 줄로 나눌 수 없는 칸은 칸마다 이웃 칸에 닿을 때까지 쪽 가운데 쪽으로 민다', () => {
    const wide = 200;
    const px = filled(wide * h, white);
    // 왼쪽 칸 둘 (위 20~119, 아래 140~299, 사이 20줄)과 오른쪽 긴 칸 (20~299) — 그 사이 세로 여백이 비스듬하다
    const leftEdge = (y: number): number => 90 + div(y - 20, 10);
    for (let y = 20; y < 300; y++)
      for (let x = 10; x < 190; x++) {
        const right = leftEdge(y);
        const inLeft = x < right && ((y >= 20 && y < 120) || (y >= 140 && y < 300));
        const inRight = x >= right + 12;
        if (!inLeft && !inRight) continue;
        const top = inRight ? 20 : y < 120 ? 20 : 140;
        const bottom = inRight ? 299 : y < 120 ? 119 : 299;
        const edge =
          y < top + 2 || y > bottom - 2 || x < 12 || x >= 188 || (inLeft && x >= right - 2) || (inRight && x < right + 14);
        // 칸 안 그림 (빗금)
        if (edge || (x + y) % 9 === 0) px[y * wide + x] = black;
      }

    const out = req(collapseGutters(px, wide, h));

    // 가로 테두리 줄 (왼쪽 칸 안쪽 20~60열이 모두 검정) — 위 칸 위·아래, 아래 칸 위·아래
    const borders = (img: Int32Array): number[] =>
      until(0, h).filter((y) => until(20, 60).every((x) => img[y * wide + x] === black));
    const before = borders(px);
    const after = borders(out);
    expect(before).toEqual([20, 21, 118, 119, 140, 141, 298, 299]);
    // 쪽 위쪽 띠라 위 왼쪽 칸이 아래 왼쪽 칸 바로 위로 내려왔다 — 두 칸 사이 20줄 여백이 없어지고, 칸 높이는 그대로
    expect(after.length).toBe(8);
    const d = after[4] - after[3];
    expect(d >= 1 && d <= 2, `[${after.join(', ')}]`).toBe(true);
    expect(after[7] - after[4]).toBe(before[7] - before[4]);
    expect(after[3] - after[0]).toBe(before[3] - before[0]);
  });

  it('쪽 끝의 스캔 테두리 줄은 칸으로 치지 않는다', () => {
    const px = filled(w * h, white);
    panel(px, 40);
    panel(px, 200);
    // 오른쪽 끝 위아래로 쭉 이어진 검은 줄 (스캔 테두리)
    for (let y = 0; y < h; y++) px[y * w + w - 1] = black;

    const out = req(collapseGutters(px, w, h));
    const blackRows = until(0, h).filter((y) => out[y * w + 45] === black);
    expect(blackRows).toEqual([100, 101, 198, 199, 200, 297, 298]);
  });

  it('검은 칸 틈도 붙인다', () => {
    const px = filled(w * h, white);
    panel(px, 40);
    // 칸 사이(140~159)가 쪽 끝까지 검정
    for (let y = 140; y < 160; y++) for (let x = 0; x < w; x++) px[y * w + x] = black;
    panel(px, 160);

    const out = req(collapseGutters(px, w, h));

    // 칸 둘(200줄)이 맞붙어 가운데(100~299)로, 검은 틈은 없어진다
    expect(until(0, h).filter((y) => out[y * w + 45] === black)).toEqual([100, 101, 198, 199, 200, 297, 298]);
    expect(until(0, h).filter((y) => out[y * w + 5] === black)).toEqual([]);
  });

  it('그림 속 넓은 검은 곳은 틈으로 보지 않는다', () => {
    const px = filled(w * h, white);
    panel(px, 40);
    // 쪽 높이의 6%보다 긴(60줄) 검은 띠 — 그림 속 검은 하늘 같은 것
    for (let y = 140; y < 200; y++) for (let x = 0; x < w; x++) px[y * w + x] = black;
    panel(px, 200);

    const out = req(collapseGutters(px, w, h));
    // 칸과 검은 띠가 한 덩어리(260줄)로 남아 가운데로 모일 뿐 — 검은 띠 60줄이 그대로 있다
    expect(until(0, h).filter((y) => out[y * w + 5] === black).length).toBe(60);
  });

  it('그림 속 검은 세로 줄기는 칸 묶음 사이 여백이 아니다 - 옆의 글과 검은 바탕 사이를 줄이지 않는다', () => {
    const px = filled(w * h, white);
    panel(px, 20);
    // 왼쪽에 해설 글(140~175), 그 아래 흰 바탕을 건너 검은 바탕(220~319) — 띠 높이 내내 검은 줄기(38~49)로 오른쪽 칸과 이어진다
    for (let y = 140; y < 176; y++)
      for (let x = 6; x < 14; x++) if (((x * 7919 + y * 104729) ^ (x * y)) % 9 < 6) px[y * w + x] = black;
    for (let y = 220; y < 320; y++) for (let x = 2; x < 38; x++) px[y * w + x] = black;
    for (let y = 130; y < 320; y++) for (let x = 38; x < 50; x++) px[y * w + x] = black;
    box(px, 50, 96, 130, 320);
    for (let y = 134; y < 316; y++) for (let x = 54; x < 92; x++) if ((x + y) % 9 === 0) px[y * w + x] = black;

    const out = collapseGutters(px, w, h);

    assertRowsKept(px, out, w, 8);
  });

  it('글이 빽빽한 곳은 여백이 아니다 - 글 줄 사이를 지우지 않는다', () => {
    const px = filled(w * h, white);
    panel(px, 20);
    panel(px, 290);
    // 두 칸 사이 글 12줄 — 획 높이 8줄(짧아서 칸으로는 안 친다), 줄 사이 4줄
    for (let t = 135; t < 280; t += 12)
      for (let y = t; y < t + 8; y++) for (let x = 15; x < 85; x++) if (x % 6 < 3) px[y * w + x] = black;

    const out = collapseGutters(px, w, h);

    assertRowsKept(px, out, w, 15);
  });

  it('액자 선만 지나가는 흰 줄은 여백이 아니다 - 목차 줄 사이를 지우지 않는다', () => {
    const wide = 400;
    const px = filled(wide * h, white);
    // 쪽 높이 거의 전부를 두른 1px 액자, 안에 30줄마다 목차 한 줄
    for (let y = 20; y < 380; y++) {
      px[y * wide + 40] = black;
      px[y * wide + 360] = black;
    }
    for (let t = 40; t < 360; t += 30)
      for (let y = t; y < t + 8; y++) for (let x = 60; x < 340; x++) if (x % 6 < 3) px[y * wide + x] = black;

    const out = collapseGutters(px, wide, h);

    assertRowsKept(px, out, wide, 60);
  });

  it('지울 여백이 없으면 그대로', () => {
    // 쪽 전체가 그림(세로 줄무늬)
    const px = new Int32Array(w * h);
    for (let i = 0; i < px.length; i++) px[i] = (i % w) % 10 < 3 ? black : white;
    expect(collapseGutters(px, w, h)).toBeNull();
  });

  it('스택 내 작은 칸들의 가로선이 세로 여백에 걸쳐 있어도 세로 칸 묶음을 나누어 가로 여백을 붙인다', () => {
    const px = filled(w * h, white);
    // 오른쪽 세로로 긴 칸(55~95, 20~220), 왼쪽에 작은 칸 둘(6~50, 20~80 및 120~220, 사이 40줄 여백)
    box(px, 55, 95, 20, 220);
    box(px, 6, 50, 20, 80);
    box(px, 6, 50, 120, 220);
    // 세로 여백(50..55)을 가로지르는 가로 선 (스택 컷 테두리 연장선)
    for (let x = 50; x < 55; x++) {
      px[80 * w + x] = black;
      px[120 * w + x] = black;
    }

    const out = req(collapseGutters(px, w, h));

    // 작은 칸 둘 사이의 40줄 여백이 줄어들어 붙는다
    const blackRows = until(0, h).filter((y) => out[y * w + 20] === black);
    expect(blackRows).toEqual([100, 101, 158, 159, 200, 201, 298, 299]);
  });

  it('칸 사이 가로 여백이 좁아도 테두리 사이 여백을 찾아 붙인다', () => {
    // 높이 1000px, 너비 400px에서 8px짜리 좁은 가로 여백
    const w2 = 400;
    const h2 = 1000;
    const px = filled(w2 * h2, white);
    for (let y = 50; y < 450; y++)
      for (let x = 40; x < 360; x++) {
        if (y < 52 || y >= 448 || x < 42 || x >= 358) px[y * w2 + x] = black;
      }
    for (let y = 458; y < 858; y++)
      for (let x = 40; x < 360; x++) {
        if (y < 460 || y >= 856 || x < 42 || x >= 358) px[y * w2 + x] = black;
      }

    const out = req(collapseGutters(px, w2, h2));
    // 8줄 여백이 제거되어 두 칸 테두리가 맞붙는다
    const blackRows = until(0, h2).filter((y) => out[y * w2 + 200] === black);
    expect(blackRows).toEqual([101, 102, 499, 500, 897, 898]);
  });

  it('검은 바탕 쪽에서 테두리 없는 그림 칸 사이의 검은 여백도 붙인다', () => {
    const px = filled(w * h, black);
    // 검은 바탕에 위 칸(y=30..150), 아래 칸(y=200..320) — 테두리 없이 안쪽만 흰색 빗금 그림
    for (let y = 30; y < 150; y++)
      for (let x = 15; x < 85; x++) {
        if ((x + y) % 6 < 3) px[y * w + x] = white;
      }
    for (let y = 200; y < 320; y++)
      for (let x = 15; x < 85; x++) {
        if ((x + y) % 6 < 3) px[y * w + x] = white;
      }

    const out = req(collapseGutters(px, w, h));
    const whiteRows = until(0, h).filter((y) => out[y * w + 50] === white);
    const beforeGap = 200 - 150;
    const topPart = whiteRows.filter((y) => y < 200);
    const bottomPart = whiteRows.filter((y) => y > 200);
    const afterTopEnd = topPart.length > 0 ? Math.max(...topPart) : 0;
    const afterBottomStart = bottomPart.length > 0 ? Math.min(...bottomPart) : h;
    const afterGap = afterBottomStart - afterTopEnd;
    expect(afterGap < beforeGap).toBe(true);
  });

  it('맨 위 칸 위에 화 제목 글 줄이 있어도 제목을 지우지 않고 칸과 제목 사이 여백을 붙인다', () => {
    const px = filled(w * h, white);
    // 맨 위 화 제목 글자들 (y=20..32, x=25..75에 글자 획 폭 50px = 쪽 너비의 50%)
    for (let y = 20; y < 32; y++)
      for (let x = 25; x < 75; x++) {
        if ((x + y) % 5 < 2) px[y * w + x] = black;
      }
    // 여백 후 칸 (y=70..270)
    box(px, 10, 90, 70, 270);

    const out = req(collapseGutters(px, w, h));
    const totalInkSrc = sum(until(0, h).map((y) => until(0, w).filter((x) => px[y * w + x] === black).length));
    const totalInkOut = sum(until(0, h).map((y) => until(0, w).filter((x) => out[y * w + x] === black).length));
    console.log(`Title test: totalSrc=${totalInkSrc}, totalOut=${totalInkOut}`);
    const blackY = until(0, h).filter((y) => until(25, 75).some((x) => out[y * w + x] === black));
    console.log(`Title test black Ys: [${blackY.join(', ')}]`);
    // 화 제목 글자 획들이 온전히 남아 있다
    const srcTitleInk = sum(until(20, 32).map((y) => until(25, 75).filter((x) => px[y * w + x] === black).length));
    const outTitleInk = sum(
      until(0, 150).map((y) => until(25, 75).filter((x) => out[y * w + x] === black && x >= 13 && x <= 87).length),
    );
    // Kotlin 원본에서도 계산만 하고 쓰지 않는 값 (noUnusedLocals 때문에 읽기만 한다)
    void srcTitleInk;
    void outTitleInk;
    expect(totalInkOut >= div(totalInkSrc * 9, 10)).toBe(true);
  });

  it('그림 칸 아래에 글 문단 여러 줄이 있어도 글자를 지우지 않고 여백을 붙인다', () => {
    const px = filled(w * h, white);
    // 상단 칸 (y=20..150)
    box(px, 10, 90, 20, 150);
    // 하단 글 문단 1 (y=200..230), 문단 2 (y=280..310) (문단 폭 60px = 쪽 너비의 60%)
    for (let y = 200; y < 230; y++) for (let x = 20; x < 80; x++) if ((x + y) % 6 < 2) px[y * w + x] = black;
    for (let y = 280; y < 310; y++) for (let x = 20; x < 80; x++) if ((x + y) % 6 < 2) px[y * w + x] = black;

    const out = req(collapseGutters(px, w, h));
    // 문단 글자들이 지워지지 않고 온전히 유지된다
    const srcTextInk = sum(until(200, 320).map((y) => until(20, 80).filter((x) => px[y * w + x] === black).length));
    const outTextInk = sum(
      until(0, h).map((y) => until(20, 80).filter((x) => out[y * w + x] === black && y >= 150).length),
    );
    expect(outTextInk >= div(srcTextInk * 9, 10)).toBe(true);
  });

  it('테두리 없는 오픈 컷 그림이 있어도 그림을 지우지 않고 보존한다', () => {
    const px = filled(w * h, white);
    // 위 칸 (y=20..160)
    box(px, 10, 90, 20, 160);
    // 아래 테두리 없는 오픈 컷 (y=220..320, x=15..65) — 인물 얼굴과 대사
    for (let y = 220; y < 320; y++)
      for (let x = 15; x < 65; x++) {
        if ((x * 3 + y * 7) % 11 < 3) px[y * w + x] = black;
      }

    const out = req(collapseGutters(px, w, h));
    const srcOpenInk = sum(until(220, 320).map((y) => until(15, 65).filter((x) => px[y * w + x] === black).length));
    const outOpenInk = sum(
      until(0, h).map((y) => until(15, 65).filter((x) => out[y * w + x] === black && y >= 160).length),
    );
    expect(outOpenInk >= div(srcOpenInk * 9, 10)).toBe(true);
  });

  it('칸 없는 한 장 전면 일러스트에서 옅은 벽 그림을 여백으로 자르지 않는다', () => {
    // 흰색 바탕(255)에 상단 벽면 회색(220) 텍스처, 하단 그림
    const grayWall = (0xff << 24) | (220 << 16) | (220 << 8) | 220;
    const crack = (0xff << 24) | (195 << 16) | (195 << 8) | 195;
    const px = filled(w * h, white);
    for (let y = 120; y < 240; y++)
      for (let x = 15; x < 85; x++) {
        px[y * w + x] = (x * 13 + y * 17) % 31 === 0 ? crack : grayWall;
      }
    for (let y = 240; y < 360; y++)
      for (let x = 15; x < 85; x++) {
        if ((x + y) % 8 < 3) px[y * w + x] = black;
      }
    const out = collapseGutters(px, w, h);
    // 벽면 그림과 하단 그림이 분리되어 벽면이 여백으로 잘려나가지 않고 온전히 유지된다
    const result = out ?? px;
    const outWallPixels = sum(
      until(0, h).map(
        (y) => until(15, 85).filter((x) => result[y * w + x] === grayWall || result[y * w + x] === crack).length,
      ),
    );
    const srcWallPixels = sum(
      until(120, 240).map(
        (y) => until(15, 85).filter((x) => px[y * w + x] === grayWall || px[y * w + x] === crack).length,
      ),
    );
    expect(outWallPixels >= div(srcWallPixels * 9, 10)).toBe(true);
  });

  it('세로로 긴 칸 옆의 작은 칸들 사이 여백을 줄일 때 세로 칸 안의 그림을 자르지 않는다', () => {
    const px = filled(w * h, white);
    // 왼쪽 세로로 긴 칸 (x=6..45, y=30..350)
    box(px, 6, 45, 30, 350);
    // 왼쪽 칸 안의 그림 (y=150..220 손/인물 빗금)
    for (let y = 150; y < 220; y++)
      for (let x = 12; x < 40; x++) {
        if ((x + y) % 4 === 0) px[y * w + x] = black;
      }
    // 오른쪽 작은 칸 둘 (x=55..95, y=30..150 및 y=230..350, 사이 80줄 여백)
    box(px, 55, 95, 30, 150);
    box(px, 55, 95, 230, 350);

    const out = req(collapseGutters(px, w, h));
    const srcHandInk = sum(until(150, 220).map((y) => until(12, 40).filter((x) => px[y * w + x] === black).length));
    const outHandInk = sum(
      until(0, h).map(
        (y) =>
          until(12, 40).filter((x) => out[y * w + x] === black && !(x >= 6 && x <= 8) && !(x >= 43 && x <= 45))
            .length,
      ),
    );
    console.log(`Hand test: src=${srcHandInk}, out=${outHandInk}`);
    expect(outHandInk >= div(srcHandInk * 9, 10)).toBe(true);
  });

  it('검은 바탕 쪽에서 테두리 밖 여백을 검은색으로 안전하게 채운다', () => {
    const px = filled(w * h, black);
    box(px, 20, 80, 50, 150);
    for (let y = 60; y < 140; y++) for (let x = 30; x < 70; x++) if ((x + y) % 4 === 0) px[y * w + x] = white;
    box(px, 20, 80, 250, 350);
    for (let y = 260; y < 340; y++) for (let x = 30; x < 70; x++) if ((x + y) % 4 === 0) px[y * w + x] = white;

    const out = req(collapseGutters(px, w, h));
    const topRowsWhite = sum(until(0, 30).map((y) => until(0, w).filter((x) => out[y * w + x] === white).length));
    expect(topRowsWhite).toBe(0);
  });

  it('검은 바탕 쪽에 스캔 가장자리 흰 줄이 섞여 있어도 위아래 여백을 검은색으로 채운다', () => {
    const px = filled(w * h, black);
    // 맨 위 20줄에 스캔 여백 흰 줄이 섞여 있음
    for (let y = 0; y < 20; y++) for (let x = 0; x < w; x++) px[y * w + x] = white;
    box(px, 20, 80, 50, 150);
    for (let y = 60; y < 140; y++) for (let x = 30; x < 70; x++) if ((x + y) % 4 === 0) px[y * w + x] = white;
    box(px, 20, 80, 250, 350);
    for (let y = 260; y < 340; y++) for (let x = 30; x < 70; x++) if ((x + y) % 4 === 0) px[y * w + x] = white;

    const out = req(collapseGutters(px, w, h));
    // 위쪽 비는 곳(0..40)이 흰색으로 채워지면 안 되고 검은색이어야 한다
    const topWhite = sum(until(0, 30).map((y) => until(0, w).filter((x) => out[y * w + x] === white).length));
    expect(topWhite).toBe(0);
  });

  it('좌우 펼침면에서 한쪽만 줄어들어 양쪽 칸의 높이가 어긋나는 경우 펼침면을 건드리지 않는다', () => {
    const spreadW = 200;
    const spreadH = 100;
    const px = filled(spreadW * spreadH, white);
    // 왼쪽 반쪽(0..99): 칸 2개 (여백 20줄) -> 줄어듦
    for (let y = 10; y < 40; y++)
      for (let x = 10; x < 90; x++) {
        const edge = y < 12 || y >= 38 || x < 12 || x >= 88;
        if (edge) px[y * spreadW + x] = black;
      }
    for (let y = 60; y < 90; y++)
      for (let x = 10; x < 90; x++) {
        const edge = y < 62 || y >= 88 || x < 12 || x >= 88;
        if (edge) px[y * spreadW + x] = black;
      }
    // 오른쪽 반쪽(100..199): 위부터 아래까지 꽉 찬 한 장 그림 (테두리/여백 없음) -> 줄어들지 않음
    for (let y = 0; y < spreadH; y++)
      for (let x = 100; x < spreadW; x++) {
        if ((x + y) % 3 === 0) px[y * spreadW + x] = black;
      }

    // 한쪽만 줄어들면 좌우 정렬이 망가지므로 null을 반환해야 함
    const out = collapseGutters(px, spreadW, spreadH);
    expect(out).toBeNull();
  });
});
