// 화면 진입·복귀 포커스 (Android core:ui EntryFocus와 같은 규칙, PLAN Z4).
// - 처음 들어오면 기본 항목(없으면 첫 항목)에 포커스
// - 돌아오면 연 항목(focusKey)에 포커스
// - 새로고침·더 불러오기로 내용이 바뀌면, 포커스가 남아 있으면 보이게만 하고 사라졌으면 다시 찾는다
import type { RefObject } from 'preact';
import { useEffect } from 'preact/hooks';
import { current, focusKeyOrFirst, reveal } from '../platform/focus';
import type { RouteEntry } from '../platform/router';

export function useEntryFocus(
  root: RefObject<HTMLElement | null>,
  entry: RouteEntry,
  active: boolean,
  /** 내용이 바뀔 때마다 바뀌는 값. null이면 아직 준비 안 됨 */
  contentVersion: unknown,
  defaultKey?: string,
): void {
  const ready = contentVersion !== null;

  // 들어올 때, 돌아올 때
  useEffect(() => {
    const element = root.current;
    if (!active || !ready || !element) return;
    focusKeyOrFirst(element, entry.focusKey ?? defaultKey);
  }, [active, ready, entry.returns]);

  // 내용이 바뀔 때
  useEffect(() => {
    const element = root.current;
    if (!active || !ready || !element) return;
    // 입력 중이면 건드리지 않는다 — 검색 결과가 도착할 때 포커스를 옮겨 TV 화면 키보드가 닫히던 문제 (에뮬레이터)
    const typing = document.activeElement;
    if (typing instanceof HTMLInputElement && element.contains(typing)) return;
    const focused = current();
    if (focused && element.contains(focused)) reveal(focused);
    else focusKeyOrFirst(element, entry.focusKey ?? defaultKey);
  }, [contentVersion]);
}
