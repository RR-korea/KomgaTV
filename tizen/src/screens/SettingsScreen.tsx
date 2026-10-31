// 서버 연결 화면 (Android T2와 같은 동작). 첫 실행이면 시작 화면이고 Back은 앱 종료.
import { useRef, useState } from 'preact/hooks';
import { type Auth, connect } from '../api/client';
import { ConnectionError } from '../api/errors';
import { fmt, t } from '../i18n';
import { type RouteEntry, reset } from '../platform/router';
import { loadSavedServer, openSession, saveServer, session } from '../state/session';
import { Button, TextField } from '../ui/components';
import { useEntryFocus } from '../ui/entryFocus';

type AuthKind = Auth['kind'];

type Status =
  | { kind: 'idle' }
  | { kind: 'busy'; text: string }
  | { kind: 'success'; text: string }
  | { kind: 'error'; text: string };

function errorText(error: unknown): string {
  if (!(error instanceof ConnectionError)) return fmt(t.settingsErrorOther, String(error));
  switch (error.kind) {
    case 'invalidUrl':
      return t.settingsErrorInvalidUrl;
    case 'unreachable':
      return t.settingsErrorUnreachable;
    case 'unauthorized':
      return t.settingsErrorUnauthorized;
    default:
      return fmt(t.settingsErrorOther, error.message);
  }
}

export function SettingsScreen({ entry, active }: { entry: RouteEntry; active: boolean }) {
  const root = useRef<HTMLDivElement>(null);
  const saved = useRef(loadSavedServer()).current;
  const [url, setUrl] = useState(saved?.url ?? '');
  const [kind, setKind] = useState<AuthKind>(saved?.auth.kind ?? 'basic');
  const [email, setEmail] = useState(saved?.auth.kind === 'basic' ? saved.auth.email : '');
  const [password, setPassword] = useState(saved?.auth.kind === 'basic' ? saved.auth.password : '');
  const [apiKey, setApiKey] = useState(saved?.auth.kind === 'apiKey' ? saved.auth.apiKey : '');
  const [status, setStatus] = useState<Status>({ kind: 'idle' });

  useEntryFocus(root, entry, active, 0, 'url');

  const buildAuth = (): Auth | null => {
    if (!url.trim()) return null;
    if (kind === 'basic') return email.trim() && password ? { kind, email: email.trim(), password } : null;
    return apiKey.trim() ? { kind, apiKey: apiKey.trim() } : null;
  };

  const run = async (save: boolean) => {
    if (status.kind === 'busy') return;
    const auth = buildAuth();
    if (!auth) {
      setStatus({ kind: 'error', text: t.settingsErrorMissing });
      return;
    }
    setStatus({ kind: 'busy', text: t.settingsTesting });
    try {
      if (save) {
        await openSession({ url, auth });
        saveServer({ url: url.trim(), auth });
        reset({ name: 'home' });
      } else {
        await connect(url, auth);
        setStatus({ kind: 'success', text: t.settingsSuccess });
      }
    } catch (error) {
      setStatus({ kind: 'error', text: errorText(error) });
    }
  };

  const clearCache = () => {
    const current = session.get();
    current?.thumbnails.clear();
    current?.pages.clear();
    setStatus({ kind: 'success', text: t.settingsCacheCleared });
  };

  return (
    <div ref={root} class="settings" data-scroll="y" data-scroll-margin="120">
      <h1 class="screen-title">{t.settingsTitle}</h1>

      <h2 class="section-title">{t.settingsSectionServer}</h2>
      <TextField
        focusKey="url"
        label={t.settingsServerUrl}
        value={url}
        onInput={setUrl}
        type="url"
        placeholder={t.settingsServerUrlPlaceholder}
      />

      <div class="field-label standalone">{t.settingsAuthMethod}</div>
      {/* 아래로 내려오면 가까운 항목이 아니라 선택된 항목에 포커스 (E2E-1 남은 문제) */}
      <div class="choice-row" data-section="auth" data-last-key={`auth-${kind}`}>
        <Button focusKey="auth-basic" selected={kind === 'basic'} onClick={() => setKind('basic')}>
          {t.settingsAuthBasic}
        </Button>
        <Button focusKey="auth-apiKey" selected={kind === 'apiKey'} onClick={() => setKind('apiKey')}>
          {t.settingsAuthApiKey}
        </Button>
      </div>

      {kind === 'basic' ? (
        <>
          <TextField
            focusKey="email"
            label={t.settingsEmail}
            value={email}
            onInput={setEmail}
            type="email"
            placeholder={t.settingsEmailPlaceholder}
          />
          <TextField focusKey="password" label={t.settingsPassword} value={password} onInput={setPassword} type="password" />
        </>
      ) : (
        <TextField focusKey="apiKey" label={t.settingsApiKey} value={apiKey} onInput={setApiKey} type="password" />
      )}

      <div class="button-row">
        <Button focusKey="test" onClick={() => void run(false)}>
          {t.settingsTest}
        </Button>
        <Button focusKey="save" class="primary" onClick={() => void run(true)}>
          {t.settingsSave}
        </Button>
      </div>

      <div class={`status status-${status.kind}`}>{status.kind === 'idle' ? '' : status.text}</div>

      {!(entry.route.name === 'settings' && entry.route.first) && (
        <>
          <h2 class="section-title">{t.settingsSectionCache}</h2>
          <div class="button-row">
            <Button focusKey="clear-cache" onClick={clearCache}>
              {t.settingsClearCache}
            </Button>
          </div>
        </>
      )}

      <h2 class="section-title">{t.settingsSectionApp}</h2>
      <div class="about">{fmt(t.settingsVersion, APP_VERSION)}</div>
      <div class="about small">{navigator.userAgent}</div>
    </div>
  );
}
