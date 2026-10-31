// 서버 연결 화면 (Android T2와 같은 동작). 첫 실행이면 시작 화면이고 Back은 앱 종료.
import { useRef, useState } from 'preact/hooks';
import { type Auth, connect } from '../api/client';
import { ConnectionError } from '../api/errors';
import { normalizeServerUrl } from '../api/url';
import { fmt, getLanguageChoice, setLanguageChoice, t } from '../i18n';
import { type RouteEntry, reset } from '../platform/router';
import {
  loadSavedServer,
  loadServerList,
  openSession,
  removeSavedServer,
  type SavedServer,
  saveServer,
  serverId,
  session,
  switchServer,
} from '../state/session';
import { focusKeyOrFirst } from '../platform/focus';
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
  const language = useRef(getLanguageChoice()).current;
  const [servers, setServers] = useState<SavedServer[]>(() => loadServerList());
  const currentId = saved ? serverId(saved) : null;

  // 첫 실행이면 주소 칸, 아니면 화면 맨 위(저장된 서버 목록)부터
  useEntryFocus(root, entry, active, 0, entry.route.name === 'settings' && entry.route.first ? 'url' : undefined);

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
        saveServer({ url: normalizeServerUrl(url) ?? url.trim(), auth });
        reset({ name: 'home' });
      } else {
        await connect(url, auth);
        setStatus({ kind: 'success', text: t.settingsSuccess });
      }
    } catch (error) {
      setStatus({ kind: 'error', text: errorText(error) });
    }
  };

  // 저장된 다른 서버로 바꾸면 쌓인 화면을 모두 버리고 홈부터 (옛 서버 내용이 남지 않게, Android T11)
  const switchTo = (server: SavedServer) => {
    if (switchServer(server)) reset({ name: 'home' });
  };

  const removeServer = (server: SavedServer) => {
    setServers(removeSavedServer(serverId(server)));
    if (root.current) focusKeyOrFirst(root.current, 'server-add');
  };

  // 다른 서버 추가: 입력칸을 비우고 주소 칸으로
  const addServer = () => {
    setUrl('');
    setKind('basic');
    setEmail('');
    setPassword('');
    setApiKey('');
    setStatus({ kind: 'idle' });
    if (root.current) focusKeyOrFirst(root.current, 'url');
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

      {servers.length > 0 && !(entry.route.name === 'settings' && entry.route.first) && (
        <>
          <h2 class="section-title">{t.settingsSavedServers}</h2>
          {servers.map((server, i) => {
            const id = serverId(server);
            const account = server.auth.kind === 'basic' ? server.auth.email : t.settingsAuthApiKey;
            return (
              <div key={id} class="saved-server" data-section={`saved-${i}`}>
                <div class="saved-server-text">
                  <div class="saved-server-url">{normalizeServerUrl(server.url) ?? server.url}</div>
                  <div class="saved-server-account">{account}</div>
                </div>
                {id === currentId ? (
                  <div class="saved-server-active">{t.settingsServerActive}</div>
                ) : (
                  <div class="button-row saved-server-actions">
                    <Button focusKey={`server-switch-${i}`} onClick={() => switchTo(server)}>
                      {t.settingsServerSwitch}
                    </Button>
                    <Button focusKey={`server-remove-${i}`} onClick={() => removeServer(server)}>
                      {t.settingsServerRemove}
                    </Button>
                  </div>
                )}
              </div>
            );
          })}
          <div class="button-row">
            <Button focusKey="server-add" onClick={addServer}>
              {t.settingsServerAdd}
            </Button>
          </div>
        </>
      )}

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
      <div class="field-label standalone">{t.settingsLanguage}</div>
      <div class="choice-row" data-section="language" data-last-key={`lang-${language}`}>
        <Button focusKey="lang-system" selected={language === 'system'} onClick={() => setLanguageChoice('system')}>
          {t.settingsLanguageSystem}
        </Button>
        {/* 언어 이름은 그 언어로 쓴다 */}
        <Button focusKey="lang-ko" selected={language === 'ko'} onClick={() => setLanguageChoice('ko')}>
          한국어
        </Button>
        <Button focusKey="lang-en" selected={language === 'en'} onClick={() => setLanguageChoice('en')}>
          English
        </Button>
      </div>
      <div class="about">{fmt(t.settingsVersion, APP_VERSION)}</div>
      <div class="about small">{navigator.userAgent}</div>
    </div>
  );
}
