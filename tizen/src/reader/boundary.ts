// 책 경계 화면(책 끝·첫 화면)에서 갈 권을 어느 쪽에 둘지와 키 안내 (Android BoundaryLayout과 같음).
// 원칙: 갈 권은 그리로 가는 키가 있는 쪽에. 왼쪽→오른쪽(LTR)으로 읽으면 다음 권은 ▶ 쪽(오른쪽),
// 오른쪽→왼쪽(RTL, 만화)이면 다음 권은 ◀ 쪽(왼쪽). 이전 권은 그 반대. 세로 스크롤은 아래가 다음이라 LTR 배치에 ▼▲.

export interface BoundaryLayout {
  /** 갈 권(다음·이전 권)을 왼쪽에 둔다 */
  destinationOnLeft: boolean;
  /** 갈 권으로 가는 키 */
  goKey: string;
  /** 지금 권으로 돌아가는 키 */
  backKey: string;
  /** 지금 권 → 갈 권을 가리키는 화살표 */
  arrow: string;
}

const LEFT = '◀';
const RIGHT = '▶';
const UP = '▲';
const DOWN = '▼';

const layout = (destinationOnLeft: boolean, goKey: string, backKey: string): BoundaryLayout => ({
  destinationOnLeft,
  goKey,
  backKey,
  arrow: destinationOnLeft ? '‹' : '›',
});

/** @param toNext true면 책 끝 화면(다음 권), false면 책 첫 화면(이전 권) */
export function boundaryLayout(toNext: boolean, rightToLeft: boolean, continuous: boolean): BoundaryLayout {
  if (continuous) return toNext ? layout(false, DOWN, UP) : layout(true, UP, DOWN);
  if (rightToLeft) return toNext ? layout(true, LEFT, RIGHT) : layout(false, RIGHT, LEFT);
  return toNext ? layout(false, RIGHT, LEFT) : layout(true, LEFT, RIGHT);
}
