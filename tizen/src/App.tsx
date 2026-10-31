// Z0 실기 확인용 임시 화면: 리모컨 키 표시 + 서버 연결 시험(교차 출처 fetch 확인).
// Z4에서 실제 화면으로 바꾼다.
import { useEffect, useState } from 'preact/hooks';
import { connect } from './api/client';
import { ConnectionError } from './api/errors';
import { exitApp, isTizen, toAppKey } from './platform/keys';

const userAgent = navigator.userAgent;

export function App() {
  const [lastKey, setLastKey] = useState('(없음)');
  const [result, setResult] = useState('');

  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      const key = toAppKey(event);
      setLastKey(`${key ?? '알 수 없음'} (keyCode ${event.keyCode}, key "${event.key}")`);
      if (key === 'back' && isTizen()) exitApp();
    };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, []);

  // 주소에 ?server=...&apiKey=... 를 주면 연결을 시험한다 (Z0 확인용, 저장하지 않음)
  useEffect(() => {
    const params = new URLSearchParams(location.search);
    const server = params.get('server');
    const apiKey = params.get('apiKey');
    if (!server || !apiKey) return;
    setResult('연결 중…');
    connect(server, { kind: 'apiKey', apiKey }).then(
      ({ user }) => setResult(`연결 성공: ${user.email}`),
      (error: unknown) => setResult(error instanceof ConnectionError ? `${error.kind}: ${error.message}` : String(error)),
    );
  }, []);

  return (
    <main class="diag">
      <h1>KomgaTV</h1>
      <p>Tizen 개발 확인 화면</p>
      <dl>
        <dt>마지막 키</dt>
        <dd>{lastKey}</dd>
        <dt>Tizen API</dt>
        <dd>{isTizen() ? '있음' : '없음 (PC 브라우저)'}</dd>
        <dt>User-Agent</dt>
        <dd>{userAgent}</dd>
        {result && (
          <>
            <dt>서버 연결</dt>
            <dd>{result}</dd>
          </>
        )}
      </dl>
    </main>
  );
}
