// 잠깐 알림(토스트)과 동작 메뉴 (Android T13 책·라이브러리 메뉴의 결과 알림·메뉴).
import { useEffect, useRef } from 'preact/hooks';
import { focusKeyOrFirst } from '../platform/focus';
import { useKeys } from '../platform/input';
import { Store, useStore } from '../state/store';
import { Button } from './components';

const toast = new Store<{ text: string; id: number } | null>(null);
let toastId = 0;

export function showToast(text: string): void {
  const id = ++toastId;
  toast.set({ text, id });
  setTimeout(() => {
    if (toast.get()?.id === id) toast.set(null);
  }, 3000);
}

export function ToastView() {
  const value = useStore(toast);
  return value ? <div class="toast">{value.text}</div> : null;
}

export interface MenuAction {
  key: string;
  label: string;
  run: () => void;
}

/** 화면 가운데 메뉴. 열리면 첫 항목에 포커스, 포커스는 메뉴 안에서만, Back = 닫기. */
export function ActionMenu({
  title,
  hint,
  actions,
  onClose,
}: {
  title: string;
  hint?: string;
  actions: MenuAction[];
  onClose: () => void;
}) {
  const panel = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (panel.current) focusKeyOrFirst(panel.current, actions[0]?.key);
  }, []);
  useKeys((key) => {
    if (key !== 'back') return false;
    onClose();
    return true;
  });
  return (
    <div class="menu-backdrop">
      <div ref={panel} class="menu-panel" data-focus-trap>
        <h2 class="menu-title">{title}</h2>
        {actions.map((action) => (
          <Button
            key={action.key}
            focusKey={action.key}
            class="menu-item"
            onClick={() => {
              onClose();
              action.run();
            }}
          >
            {action.label}
          </Button>
        ))}
        {hint && <div class="menu-hint">{hint}</div>}
      </div>
    </div>
  );
}
