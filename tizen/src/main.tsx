import { render } from 'preact';
import { App } from './App';
import { registerRemoteKeys } from './platform/keys';
import { reset } from './platform/router';
import { loadSavedServer, restoreSession } from './state/session';
import './styles.css';

registerRemoteKeys();

// 저장된 서버가 있으면 네트워크 확인 없이 홈으로 (서버가 꺼져 있으면 홈에서 오류 + 다시 시도)
const saved = loadSavedServer();
if (saved && restoreSession(saved)) reset({ name: 'home' });
else reset({ name: 'settings', first: true });

render(<App />, document.getElementById('app')!);
