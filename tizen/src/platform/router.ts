// 화면 스택. 아래 화면은 숨긴 채 남겨 두어(목록·스크롤 유지) 돌아오면 연 항목에 포커스한다 (PLAN Z4).
import { Store, useStore } from '../state/store';

export type Route =
  | { name: 'settings'; first: boolean }
  | { name: 'home' }
  | { name: 'library'; libraryId: string; title: string }
  | { name: 'series'; seriesId: string }
  | { name: 'reader'; bookId: string };

export interface RouteEntry {
  id: number;
  route: Route;
  /** 다른 화면을 열 때 포커스돼 있던 항목의 data-key (돌아오면 그 항목에 포커스) */
  focusKey?: string;
  /** 자식 화면에서 돌아온 횟수 — 바뀌면 조용히 새로고침 */
  returns: number;
}

export interface RouterState {
  stack: RouteEntry[];
}

export const router = new Store<RouterState>({ stack: [] });

let nextId = 1;
const entry = (route: Route): RouteEntry => ({ id: nextId++, route, returns: 0 });

export function reset(route: Route): void {
  router.set({ stack: [entry(route)] });
}

export function push(route: Route): void {
  const { stack } = router.get();
  const focused = document.activeElement as HTMLElement | null;
  const top = stack[stack.length - 1];
  const updatedTop = top && { ...top, focusKey: focused?.dataset?.key ?? top.focusKey };
  router.set({ stack: [...stack.slice(0, -1), ...(updatedTop ? [updatedTop] : []), entry(route)] });
}

/** 한 단계 뒤로. 최상위면 false(앱 종료는 호출한 쪽이). */
export function pop(): boolean {
  const { stack } = router.get();
  if (stack.length <= 1) return false;
  const next = stack.slice(0, -1);
  const top = next[next.length - 1];
  next[next.length - 1] = { ...top, returns: top.returns + 1 };
  router.set({ stack: next });
  return true;
}

export function useRouter(): RouterState {
  return useStore(router);
}
