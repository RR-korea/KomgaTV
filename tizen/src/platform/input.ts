// 키 입력 분배. 화면이 등록한 처리기를 나중 것부터 부르고, 아무도 안 받으면 기본 처리(포커스 이동·OK·뒤로).
// 입력칸에서 글자를 치는 중이면 손대지 않는다 — TextField가 직접 처리한다.
//
// OK 길게 누르기 (Android T13 책·라이브러리 메뉴): 기본 처리의 OK는 키를 뗄 때 누르기로 친다.
// LONG_PRESS_MS 넘게 누르고 있으면 포커스된 요소에 'longpress' 이벤트를 보내고, 받은 쪽이 preventDefault 하면 누르기는 하지 않는다.
// 화면 처리기(리더 등)가 OK를 받으면 지금처럼 키를 누를 때 바로 처리한다.
import { useEffect, useRef } from 'preact/hooks';
import type { RefObject } from 'preact';
import { activate, current } from './focus';
import { type AppKey, toAppKey } from './keys';

export type KeyHandler = (key: AppKey) => boolean;

export const LONG_PRESS_MS = 600;

const handlers: { handle: KeyHandler }[] = [];

/** active인 동안 키를 먼저 받는다. true를 돌려주면 기본 처리를 막는다. */
export function useKeys(handler: KeyHandler, active = true): void {
  const ref = useRef(handler);
  ref.current = handler;
  useEffect(() => {
    if (!active) return undefined;
    const item = { handle: (key: AppKey) => ref.current(key) };
    handlers.push(item);
    return () => {
      const index = handlers.indexOf(item);
      if (index >= 0) handlers.splice(index, 1);
    };
  }, [active]);
}

/** 요소에 OK 길게 누르기 처리기를 단다 */
export function useLongPress(ref: RefObject<HTMLElement | null>, onLongPress: (() => void) | undefined): void {
  const callback = useRef(onLongPress);
  callback.current = onLongPress;
  const enabled = !!onLongPress;
  useEffect(() => {
    const element = ref.current;
    if (!element || !enabled) return undefined;
    const listener = (event: Event) => {
      if (!callback.current) return;
      event.preventDefault();
      callback.current();
    };
    element.addEventListener('longpress', listener);
    return () => element.removeEventListener('longpress', listener);
  }, [ref, enabled]);
}

export function installKeyDispatcher(fallback: KeyHandler): () => void {
  /** 기본 처리로 넘어간 OK가 눌려 있는 동안의 상태 */
  let enter: { timer: ReturnType<typeof setTimeout>; claimed: boolean } | null = null;

  const offer = (key: AppKey): boolean => {
    for (let i = handlers.length - 1; i >= 0; i--) {
      if (handlers[i].handle(key)) return true;
    }
    return false;
  };

  const onKeyDown = (event: KeyboardEvent) => {
    if (event.target instanceof HTMLInputElement) return;
    const key = toAppKey(event);
    if (!key) return;
    if (key === 'enter') {
      event.preventDefault();
      if (event.repeat || enter) return; // 길게 누르는 동안 반복되는 키
      if (offer('enter')) return;
      const target = current();
      const state = {
        claimed: false,
        timer: setTimeout(() => {
          if (!target) return;
          const long = new CustomEvent('longpress', { bubbles: true, cancelable: true });
          state.claimed = !target.dispatchEvent(long);
        }, LONG_PRESS_MS),
      };
      enter = state;
      return;
    }
    if (offer(key) || fallback(key)) event.preventDefault();
  };

  const onKeyUp = (event: KeyboardEvent) => {
    if (toAppKey(event) !== 'enter' || !enter) return;
    clearTimeout(enter.timer);
    const claimed = enter.claimed;
    enter = null;
    event.preventDefault();
    if (!claimed) activate();
  };

  document.addEventListener('keydown', onKeyDown);
  document.addEventListener('keyup', onKeyUp);
  return () => {
    document.removeEventListener('keydown', onKeyDown);
    document.removeEventListener('keyup', onKeyUp);
  };
}
