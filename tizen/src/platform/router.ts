// 화면 스택. 각 항목은 화면이 돌아왔을 때 복원할 상태(연 항목 키, 불러온 목록)를 들고 있다.
import { Store, useStore } from '../state/store';

export type Route =
  | { name: 'settings'; first: boolean }
  | { name: 'home' }
  | { name: 'library'; libraryId: string; title: string }
  | { name: 'series'; seriesId: string }
  | { name: 'reader'; bookId: string };

export interface RouteEntry {
  route: Route;
  /** 화면이 돌아왔을 때 쓸 보관함 (연 항목 키, 목록 캐시 등). 화면이 자유롭게 쓴다. */
  state: Record<string, unknown>;
  /** 자식 화면에서 돌아온 횟수 — 바뀌면 조용히 새로고침 */
  returns: number;
}

export interface RouterState {
  stack: RouteEntry[];
}

export const router = new Store<RouterState>({ stack: [] });

export function reset(route: Route): void {
  router.set({ stack: [{ route, state: {}, returns: 0 }] });
}

export function push(route: Route): void {
  router.set({ stack: [...router.get().stack, { route, state: {}, returns: 0 }] });
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

export function replace(route: Route): void {
  const { stack } = router.get();
  router.set({ stack: [...stack.slice(0, -1), { route, state: {}, returns: 0 }] });
}

export function useRouter(): RouterState {
  return useStore(router);
}

export function top(): RouteEntry | undefined {
  const { stack } = router.get();
  return stack[stack.length - 1];
}
