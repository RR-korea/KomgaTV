// 페이지 넘김 화면 전환 효과 (Android 0.2.9). 이미 그린 화면을 옮기거나(밀기) 겹치기만(페이드) 한다 — CSS 변환·투명도라 GPU 합성만 쓴다.
import type { PageTransition } from '../state/readerPrefs';

/** 이보다 빨리 연달아 넘기면 효과 없이 바로 바꾼다 (키를 누르고 있을 때 오히려 느려지지 않게) */
export const RAPID_FLIP_MS = 300;
export const SLIDE_MS = 180;
export const FADE_MS = 150;

/**
 * 이번 넘김에 쓸 효과. 효과는 한 칸 앞·뒤(step ±1)로 넘길 때만 —
 * 점프·책 바꿈·설정 변경(step 0), 빠른 연타, 아직 받지 않은 쪽(불러오는 중 표시)은 바로 바꾼다.
 */
export function chooseTransition(kind: PageTransition, step: number, rapid: boolean, ready: boolean): PageTransition {
  return kind !== 'OFF' && step !== 0 && !rapid && ready ? kind : 'OFF';
}

/** 밀기에서 새 펼침이 들어오는 쪽: 1 = 오른쪽에서, −1 = 왼쪽에서. 다음 펼침은 읽는 쪽에서 들어온다(오른쪽→왼쪽이면 왼쪽에서) */
export function slideFrom(step: number, rightToLeft: boolean): 1 | -1 {
  return step > 0 !== rightToLeft ? 1 : -1;
}

export function transitionMs(kind: PageTransition): number {
  return kind === 'SLIDE' ? SLIDE_MS : kind === 'FADE' ? FADE_MS : 0;
}
