import { describe, expect, it } from 'vitest';
import { findBorders } from '../src/image/cropBorders';

// Android feature/reader/src/test/java/com/komgatv/feature/reader/enhance/CropBordersTest.kt 를 그대로 옮겼다.

const white = -1;
const black = 0xff000000 | 0;
const gray = 0xff808080 | 0;
const w = 200;
const h = 300;

const filled = (n: number, v: number): Int32Array => new Int32Array(n).fill(v);

/** [x0, x1) × [y0, y1) 안에 빗금 그림 (흰·검 섞임) */
function art(px: Int32Array, x0: number, x1: number, y0: number, y1: number): void {
  for (let y = y0; y < y1; y++) for (let x = x0; x < x1; x++) px[y * w + x] = (x + y) % 5 < 2 ? black : white;
}

/** 테두리 2px짜리 칸 — 안은 빗금 그림 */
function panel(px: Int32Array, x0: number, x1: number, y0: number, y1: number, frame: number = black): void {
  art(px, x0, x1, y0, y1);
  for (let y = y0; y < y1; y++)
    for (let x = x0; x < x1; x++) {
      if (y < y0 + 2 || y >= y1 - 2 || x < x0 + 2 || x >= x1 - 2) px[y * w + x] = frame;
    }
}

function borders(px: Int32Array, topBottom = true, leftRight = true): number[] | null {
  return findBorders(px, w, h, topBottom, leftRight);
}

/** assertArrayEquals 비교용 — 반환이 형식 배열이어도 보통 배열로 맞춘다 */
function arr(v: ArrayLike<number> | null): number[] | null {
  return v === null ? null : Array.from(v);
}

describe('여백 자르기 (Android CropBordersTest.kt)', () => {
  it('흰 여백을 칸 테두리까지 잰다 - 여백의 먼지 몇 점은 봐준다', () => {
    const px = filled(w * h, white);
    panel(px, 20, 180, 30, 270);
    // 위 여백의 먼지 한 점
    px[10 * w + 100] = black;

    expect(arr(borders(px))).toEqual([30, 30, 20, 20]);
  });

  it('위아래만, 좌우만 고르면 그 변만 잰다', () => {
    const px = filled(w * h, white);
    panel(px, 20, 180, 30, 270);

    expect(arr(borders(px, true, false))).toEqual([30, 30, 0, 0]);
    expect(arr(borders(px, false, true))).toEqual([0, 0, 20, 20]);
  });

  it('위아래는 테두리가 있는 곳에 맞춘다 - 칸 밖으로 나온 그림과 쪽 번호도 함께 자른다', () => {
    const px = filled(w * h, white);
    // 1단: 왼쪽 칸은 쪽 위 끝까지 그렸고(탑), 오른쪽 칸은 테두리 위가 여백 — 열혈강호 1권 31쪽
    art(px, 0, 90, 0, 140);
    panel(px, 100, 190, 30, 140);
    // 2단: 아래 여백에 쪽 번호, 칸 밖 아래로 옷자락이 나왔다
    panel(px, 10, 190, 150, 270);
    art(px, 120, 160, 270, h);
    for (let y = 280; y < 286; y++) for (let x = 95; x < 105; x++) px[y * w + x] = black;

    // 위·아래는 테두리 선에서 자른다. 왼쪽은 칸이 쪽 끝까지 그려져 안 자르고, 오른쪽은 모든 줄이 여백인 10만
    expect(arr(borders(px))).toEqual([30, 30, 0, 10]);
  });

  it('칸 밖 아래 여백의 쪽 번호와 넓은 광고 글은 여백으로 보고 칸 테두리 선에서 자른다', () => {
    const px = filled(w * h, white);
    panel(px, 10, 190, 20, 130);
    panel(px, 10, 100, 140, 275);
    panel(px, 110, 190, 140, 275);
    // 아래 여백: 왼쪽 작은 글, 쪽 번호, 쪽 너비 절반을 넘는 광고 글 (킹덤 권 중간 쪽처럼)
    art(px, 12, 40, 281, 292);
    for (let y = 280; y < 287; y++) for (let x = 44; x < 52; x++) px[y * w + x] = black;
    art(px, 60, 170, 279, 297);

    // 위는 칸 테두리 20, 아래는 칸 테두리 선(274)에서 — 쪽 번호와 광고는 함께 잘린다
    expect(arr(borders(px))).toEqual([20, 25, 10, 10]);
  });

  it('빈 줄만 빼기 - 칸 간격 없애기가 남긴 빈 줄은 빼고 칸 밖으로 나온 말풍선은 남긴다', () => {
    const px = filled(w * h, white);
    panel(px, 10, 190, 10, 250);
    // 아래 칸 테두리 밖으로 나온 말풍선 (270줄까지), 그 아래 칸을 붙이고 남은 빈 줄
    art(px, 95, 110, 240, 270);

    // 보통 자르기는 칸 테두리(249)에서 자르지만, 빈 줄만 빼면 말풍선 아래까지만
    expect(arr(borders(px))).toEqual([10, 50, 10, 10]);
    expect(arr(findBorders(px, w, h, true, true, true))).toEqual([10, 30, 10, 10]);
  });

  it('칸 밖 위 여백의 제목 글도 칸 테두리 선에서 함께 자른다', () => {
    const px = filled(w * h, white);
    panel(px, 10, 190, 40, 290);
    // 위 여백에 쪽 너비 60%의 머리글
    art(px, 30, 150, 10, 30);

    expect(arr(borders(px))).toEqual([40, 10, 10, 10]);
  });

  it('쪽 끝까지 그린 맨 아래 칸의 하얀 칸 안은 여백이 아니다 - 그 칸의 위 테두리에서 자르지 않는다', () => {
    const px = filled(w * h, white);
    panel(px, 10, 190, 20, 200);
    // 맨 아래 칸: 위·옆 테두리만 있고 쪽 아래 끝까지 열렸다. 안은 거의 하얗고 얼굴 하나 (킹덤 1권 185쪽처럼)
    for (let x = 10; x < 190; x++) for (let y = 210; y < 212; y++) px[y * w + x] = black;
    for (let y = 210; y < h; y++) for (let x = 10; x < 12; x++) px[y * w + x] = black;
    for (let y = 210; y < h; y++) for (let x = 188; x < 190; x++) px[y * w + x] = black;
    art(px, 70, 130, 240, h);

    expect(arr(borders(px))).toEqual([20, 0, 10, 10]);
  });

  it('위아래에 칸 테두리 선이 없으면(열린 칸, 칸 밖 글) 모든 열이 여백인 곳까지만 자른다', () => {
    const px = filled(w * h, white);
    // 테두리 없이 위아래로 열린 칸 — 가는 세로 획(머리카락·빗줄기)만 y=20..259, 가로로 긴 선은 없다 (나루토 1권 58쪽)
    for (let x = 20; x <= 180; x += 20)
      for (let y = 20; y < 260; y++) {
        px[y * w + x] = black;
        px[y * w + x + 1] = black;
      }

    // 30% 열은 더 깊이 비었지만 테두리 선이 아니라서 따라가지 않는다
    expect(arr(borders(px))).toEqual([20, 40, 20, 18]);
  });

  it('좌우는 칸 밖으로 나온 그림이 있으면 그만큼 덜 자른다', () => {
    const px = filled(w * h, white);
    panel(px, 20, 180, 30, 270);
    // 오른쪽 여백으로 나온 칼끝 (줄 20개)
    for (let y = 100; y < 120; y++) for (let x = 180; x < 190; x++) px[y * w + x] = black;

    expect(arr(borders(px))).toEqual([30, 30, 20, 10]);
  });

  it('검은 바탕에 스캔한 쪽은 검은 테두리를 잰다', () => {
    const px = filled(w * h, black);
    panel(px, 15, 190, 25, 280, white);

    expect(arr(borders(px))).toEqual([25, 20, 15, 10]);
  });

  it('쪽 끝의 얇은 검은 스캔 선은 건너 그 안쪽 흰 여백을 잰다', () => {
    const px = filled(w * h, white);
    // 왼쪽 끝 2열 검은 스캔 선
    for (let y = 0; y < h; y++) for (let x = 0; x < 2; x++) px[y * w + x] = black;
    panel(px, 20, 180, 30, 270);

    expect(arr(borders(px))).toEqual([30, 30, 20, 20]);
  });

  it('흰 종이 쪽 아래 끝까지 이어진 검은 그림은 테두리가 아니다', () => {
    const px = filled(w * h, white);
    panel(px, 20, 180, 30, 200);
    // 아래 100줄은 쪽 끝까지 검은 밤하늘
    for (let y = 200; y < h; y++) for (let x = 0; x < w; x++) px[y * w + x] = black;

    expect(arr(borders(px))).toEqual([30, 0, 0, 0]);
  });

  it('중간 밝기 여백(회색 망점)은 테두리로 치지 않는다', () => {
    const px = filled(w * h, gray);
    panel(px, 20, 180, 30, 270);

    expect(borders(px)).toBeNull();
  });

  it('쪽 끝까지 그린 쪽은 테두리가 없다', () => {
    const px = new Int32Array(w * h);
    art(px, 0, w, 0, h);

    expect(borders(px)).toBeNull();
  });

  it('한 변에서 쪽의 20퍼센트까지만 잰다 - 빈 쪽도 다 지우지 않는다', () => {
    const px = filled(w * h, white);

    expect(arr(borders(px))).toEqual([60, 60, 40, 40]);
  });

  it('검은 바탕에 스캔한 쪽의 외곽 줄에 센서 빛샘이나 노이즈가 있어도 검은 테두리를 잰다', () => {
    const px = filled(w * h, black);
    panel(px, 15, 190, 25, 280, white);
    // 오른쪽 맨 바깥 줄(x=w-1)에 스캐너 반사광/센서 빛샘 (밝기 110)
    const bleed = 0xff6e6e6e | 0; // lum ~110
    for (let y = 0; y < h; y++) px[y * w + w - 1] = bleed;

    // 오른쪽은 빛샘 1줄을 건너뛰고도 검은 테두리 10을 잰다
    expect(arr(borders(px))).toEqual([25, 20, 15, 10]);
  });

  it('좌우 변에서 컷 밖으로 튀어나온 그림은 몇 줄 되지 않아도 자르지 않는다', () => {
    const px = filled(w * h, white);
    panel(px, 20, 180, 30, 270);
    // 오른쪽 여백으로 나온 칼끝이 단 2줄 (전체 높이의 1% 미만이어도 자르지 않아야 함)
    for (let y = 100; y < 102; y++) for (let x = 180; x < 190; x++) px[y * w + x] = black;

    expect(arr(borders(px))).toEqual([30, 30, 20, 10]);
  });

  it('좌우 여백의 세로 광고 글은 여백으로 보고 칸 테두리 선에서 자른다', () => {
    const px = filled(w * h, white);
    panel(px, 30, 180, 20, 280);
    // 왼쪽 여백에 쪽 높이 거의 전부를 따라 내려가는 세로 광고 글 (킹덤 권 끝 쪽처럼) — 글자 사이 틈 몇 줄만 테두리까지 빈다
    for (let c = 0; c < 13; c++) art(px, 8, 24, 24 + c * 20, 42 + c * 20);

    expect(arr(borders(px))).toEqual([20, 20, 30, 20]);
  });

  it('좌우 변에서 1줄짜리 고립된 먼지 점은 봐주고 테두리까지 잰다', () => {
    const px = filled(w * h, white);
    panel(px, 20, 180, 30, 270);
    // 오른쪽 여백 한가운데에 1줄짜리 먼지 점 1개 (x=190, y=100)
    px[100 * w + 190] = black;

    expect(arr(borders(px))).toEqual([30, 30, 20, 20]);
  });

  it('좌우 변에서 1열짜리 가는 펜선이 안쪽으로 이어져 있으면 먼지로 오인하지 않고 보존한다', () => {
    const px = filled(w * h, white);
    panel(px, 20, 180, 30, 270);
    // 칸 테두리에서 오른쪽 여백(x=180..199)으로 튀어나온 가는 머리카락 펜선 (x=180..185, 2줄) — 칸에 이어졌으니 칸 밖 글이 아니다
    for (let y = 100; y <= 101; y++) for (let x = 180; x <= 185; x++) px[y * w + x] = black;

    // x=185의 머리카락 펜선을 지우지 않고 15에서 안전하게 멈춘다
    expect(arr(borders(px))).toEqual([30, 30, 20, 14]);
  });

  it('칸 테두리 없는 열린 컷에서 짧은 가로 스피드선은 칸 테두리로 오인하지 않고 멈춘다', () => {
    const px = filled(w * h, white);
    panel(px, 20, 180, 80, 270);
    // 위쪽으로 열린 머리카락이 y=45까지 올라와 있고
    for (let x = 100; x <= 120; x++) for (let y = 45; y <= 50; y++) px[y * w + x] = black;
    // 그 사이에 짧은 스피드선(너비의 5.5% = 11px)이 있다
    for (let x = 30; x <= 40; x++) px[60 * w + x] = black; // 11px 스피드선 (BORDER_LINE_RATIO 8% 미만)

    // 패널 테두리 y=80이나 스피드선 y=60까지 자르지 않고 머리카락 시작 위치 45에서 안전하게 멈춘다
    expect(arr(borders(px))).toEqual([45, 30, 20, 20]);
  });

  it('검은 바탕에 스캔한 쪽의 상단에 센서 조명 빛샘이 있어도 검은 테두리를 잰다', () => {
    const px = filled(w * h, black);
    panel(px, 15, 190, 25, 280, white);
    // 상단 바깥 줄(y=0)에 스캐너 조명 빛샘 (밝기 82)
    const bleed = 0xff525252 | 0; // lum ~82 (<= MAX_DARK_BORDER 85)
    for (let x = 0; x < w; x++) px[x] = bleed;

    // 상단 빛샘을 인식하고 검은 테두리 25를 정상적으로 잰다
    expect(arr(borders(px))).toEqual([25, 20, 15, 10]);
  });
});
