// 리모컨 키 → 앱 동작. Tizen 키 코드는 Samsung TVInputDevice 문서 기준,
// PC 브라우저 개발용으로 Escape/Backspace도 Back으로 받는다.

export type AppKey =
  | 'left' | 'right' | 'up' | 'down' | 'enter' | 'back'
  | 'playPause' | 'play' | 'pause' | 'rewind' | 'fastForward'
  | 'channelUp' | 'channelDown';

const keyCodes: Record<number, AppKey> = {
  37: 'left',
  38: 'up',
  39: 'right',
  40: 'down',
  13: 'enter',
  10009: 'back', // Tizen Return
  27: 'back', // PC: Escape
  8: 'back', // PC: Backspace (입력칸 밖에서만 — 처리하는 쪽이 판단)
  10252: 'playPause',
  415: 'play',
  19: 'pause',
  412: 'rewind',
  417: 'fastForward',
  427: 'channelUp',
  428: 'channelDown',
};

export function toAppKey(event: KeyboardEvent): AppKey | null {
  return keyCodes[event.keyCode] ?? null;
}

// 방향키·OK·Back 말고는 등록해야 앱으로 온다.
const tizenKeysToRegister = [
  'MediaPlayPause', 'MediaPlay', 'MediaPause', 'MediaRewind', 'MediaFastForward', 'ChannelUp', 'ChannelDown',
];

interface TizenGlobal {
  tvinputdevice?: { registerKey(name: string): void };
  application?: { getCurrentApplication(): { exit(): void } };
}

function tizen(): TizenGlobal | undefined {
  return (window as unknown as { tizen?: TizenGlobal }).tizen;
}

export const isTizen = (): boolean => tizen() !== undefined;

export function registerRemoteKeys(): void {
  const input = tizen()?.tvinputdevice;
  if (!input) return;
  for (const name of tizenKeysToRegister) {
    try {
      input.registerKey(name);
    } catch {
      // 모델에 없는 키는 건너뛴다
    }
  }
}

/** 최상위 화면에서 Back → 앱 종료 (스토어 체크리스트). PC에서는 아무것도 안 한다. */
export function exitApp(): void {
  tizen()?.application?.getCurrentApplication().exit();
}
