import { describe, expect, it } from 'vitest';
import { boundaryLayout } from '../src/reader/boundary';

describe('책 경계 화면 배치 (Android BoundaryLayout)', () => {
  it('왼쪽→오른쪽: 다음 권은 오른쪽(▶), 이전 권은 왼쪽(◀)', () => {
    expect(boundaryLayout(true, false, false)).toEqual({ destinationOnLeft: false, goKey: '▶', backKey: '◀', arrow: '›' });
    expect(boundaryLayout(false, false, false)).toEqual({ destinationOnLeft: true, goKey: '◀', backKey: '▶', arrow: '‹' });
  });

  it('오른쪽→왼쪽(만화): 다음 권은 왼쪽(◀), 이전 권은 오른쪽(▶)', () => {
    expect(boundaryLayout(true, true, false)).toEqual({ destinationOnLeft: true, goKey: '◀', backKey: '▶', arrow: '‹' });
    expect(boundaryLayout(false, true, false)).toEqual({ destinationOnLeft: false, goKey: '▶', backKey: '◀', arrow: '›' });
  });

  it('세로 스크롤: 다음은 ▼, 이전은 ▲ (읽기 방향과 상관없음)', () => {
    expect(boundaryLayout(true, true, true)).toMatchObject({ destinationOnLeft: false, goKey: '▼', backKey: '▲' });
    expect(boundaryLayout(false, false, true)).toMatchObject({ destinationOnLeft: true, goKey: '▲', backKey: '▼' });
  });
});
