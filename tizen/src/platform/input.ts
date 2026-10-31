// 키 입력 분배. 화면이 등록한 처리기를 나중 것부터 부르고, 아무도 안 받으면 기본 처리(포커스 이동·OK·뒤로).
// 입력칸에서 글자를 치는 중이면 손대지 않는다 — TextField가 직접 처리한다.
import { useEffect, useRef } from 'preact/hooks';
import { type AppKey, toAppKey } from './keys';

export type KeyHandler = (key: AppKey) => boolean;

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

export function installKeyDispatcher(fallback: KeyHandler): () => void {
  const onKeyDown = (event: KeyboardEvent) => {
    if (event.target instanceof HTMLInputElement) return;
    const key = toAppKey(event);
    if (!key) return;
    for (let i = handlers.length - 1; i >= 0; i--) {
      if (handlers[i].handle(key)) {
        event.preventDefault();
        return;
      }
    }
    if (fallback(key)) event.preventDefault();
  };
  document.addEventListener('keydown', onKeyDown);
  return () => document.removeEventListener('keydown', onKeyDown);
}
