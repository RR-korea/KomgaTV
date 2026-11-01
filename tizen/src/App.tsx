// 앱 틀: 화면 스택을 그리고(아래 화면은 숨긴 채 유지), 키 분배기를 단다.
import { useEffect } from 'preact/hooks';
import { move } from './platform/focus';
import { installKeyDispatcher } from './platform/input';
import { exitApp } from './platform/keys';
import type { AppKey } from './platform/keys';
import { pop, type RouteEntry, useRouter } from './platform/router';
import { HomeScreen } from './screens/HomeScreen';
import { LibraryScreen } from './screens/LibraryScreen';
import { ReaderScreen } from './screens/ReaderScreen';
import { SearchScreen } from './screens/SearchScreen';
import { SeriesScreen } from './screens/SeriesScreen';
import { SettingsScreen } from './screens/SettingsScreen';
import { ToastView } from './ui/overlays';

/** 화면이 키를 안 받았을 때: 방향키 = 포커스 이동, Back = 뒤로(맨 처음 화면이면 앱 종료). OK는 input.ts가 키를 뗄 때 누른다 */
function fallback(key: AppKey): boolean {
  switch (key) {
    case 'left':
    case 'right':
    case 'up':
    case 'down':
      move(key);
      return true;
    case 'back':
      if (!pop()) exitApp();
      return true;
    default:
      return false;
  }
}

function Screen({ entry, active }: { entry: RouteEntry; active: boolean }) {
  switch (entry.route.name) {
    case 'settings':
      return <SettingsScreen entry={entry} active={active} />;
    case 'home':
      return <HomeScreen entry={entry} active={active} />;
    case 'library':
      return <LibraryScreen entry={entry} active={active} />;
    case 'series':
      return <SeriesScreen entry={entry} active={active} />;
    case 'reader':
      return <ReaderScreen entry={entry} active={active} />;
    case 'search':
      return <SearchScreen entry={entry} active={active} />;
  }
}

export function App() {
  const { stack } = useRouter();
  useEffect(() => installKeyDispatcher(fallback), []);
  return (
    <div class="app">
      {stack.map((entry, i) => {
        const active = i === stack.length - 1;
        return (
          <div key={entry.id} class={active ? 'screen' : 'screen hidden'}>
            <Screen entry={entry} active={active} />
          </div>
        );
      })}
      <ToastView />
      {/* 화질 개선 "선명하게"(0.6)·"더 선명하게"(1.2): 라플라시안 언샤프 마스크 (Android T14와 같은 세기) */}
      <svg class="svg-defs" width="0" height="0" aria-hidden="true">
        <defs>
          <filter id="ktv-sharpen" color-interpolation-filters="sRGB">
            <feConvolveMatrix order="3" kernelMatrix="0 -0.6 0 -0.6 3.4 -0.6 0 -0.6 0" preserveAlpha="true" edgeMode="duplicate" />
          </filter>
          <filter id="ktv-sharpen-strong" color-interpolation-filters="sRGB">
            <feConvolveMatrix order="3" kernelMatrix="0 -1.2 0 -1.2 5.8 -1.2 0 -1.2 0" preserveAlpha="true" edgeMode="duplicate" />
          </filter>
        </defs>
      </svg>
    </div>
  );
}
