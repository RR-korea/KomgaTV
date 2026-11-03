// 서버 연결 화면 (Android T2와 같은 동작). 첫 실행이면 시작 화면이고 Back은 앱 종료.
import { useRef, useState } from 'preact/hooks';
import { type Auth, connect } from '../api/client';
import { ConnectionError } from '../api/errors';
import { normalizeServerUrl } from '../api/url';
import { fmt, getLanguageChoice, LANGUAGES, setLanguageChoice, t } from '../i18n';
import { type RouteEntry, reset } from '../platform/router';
import {
  DEMO_SERVER,
  isDemoServer,
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
import { current, focusKeyOrFirst } from '../platform/focus';
import { useKeys } from '../platform/input';
import { readerPrefs, saveReaderPrefs } from '../state/readerPrefs';
import { useStore } from '../state/store';
import { applyViewOption, cycledValue, type ViewKey, viewOptions, ViewRows } from '../ui/viewSettings';
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
  const viewDefaults = useStore(readerPrefs);
  const viewRows = viewOptions(viewDefaults, 'defaults');
  const cycleDefault = (key: ViewKey, delta: number) => {
    const option = viewRows.find((row) => row.key === key);
    const value = option ? cycledValue(option, delta) : null;
    if (value !== null) saveReaderPrefs(applyViewOption(viewDefaults, key, value));
  };
  // 보기 기본값 줄에서 좌/우 = 값 바꾸기 (Android T14)
  useKeys((key) => {
    if (key !== 'left' && key !== 'right') return false;
    const focused = current();
    if (!focused?.dataset.viewRow) return false;
    cycleDefault(focused.dataset.key as ViewKey, key === 'left' ? -1 : 1);
    return true;
  }, active);
  const currentId = saved ? serverId(saved) : null;

  // 첫 실행이면 주소 칸, 아니면 화면 맨 위(저장된 서버 목록)부터
  useEntryFocus(root, entry, active, 0, entry.route.name === 'settings' && entry.route.first ? 'url' : undefined);

  const buildAuth = (): Auth | null => {
    if (!url.trim()) return null;
    if (kind === 'basic') return email.trim() && password ? { kind, email: email.trim(), password } : null;
    return apiKey.trim() ? { kind, apiKey: apiKey.trim() } : null;
  };

  const run = async (save: boolean, demo = false) => {
    if (status.kind === 'busy') return;
    const target = demo ? DEMO_SERVER.url : url;
    const auth = demo ? DEMO_SERVER.auth : buildAuth();
    if (!auth) {
      setStatus({ kind: 'error', text: t.settingsErrorMissing });
      return;
    }
    setStatus({ kind: 'busy', text: t.settingsTesting });
    try {
      if (save) {
        await openSession({ url: target, auth });
        saveServer({ url: normalizeServerUrl(target) ?? target.trim(), auth });
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

  // 내장 데모 서버로 바꾸기: 공개 계정으로 연결을 확인하고 지금 서버로 (목록에는 저장하지 않는다)
  const useDemo = () => {
    if (DEMO_SERVER.auth.kind !== 'basic') return;
    setUrl(DEMO_SERVER.url);
    setKind('basic');
    setEmail(DEMO_SERVER.auth.email);
    setPassword(DEMO_SERVER.auth.password);
    void run(true, true);
  };
  const firstRun = entry.route.name === 'settings' && entry.route.first;
  const demoActive = !!saved && isDemoServer(saved);

  const clearCache = () => {
    const current = session.get();
    current?.thumbnails.clear();
    current?.pages.clear();
    setStatus({ kind: 'success', text: t.settingsCacheCleared });
  };

  return (
    <div ref={root} class="settings" data-scroll="y" data-scroll-margin="120">
      <h1 class="screen-title">{t.settingsTitle}</h1>

      {/* 저장된 서버 + 내장 데모 서버 (첫 실행에도 데모로 둘러볼 수 있게) */}
      <h2 class="section-title">{t.settingsSavedServers}</h2>
      {!firstRun &&
        servers.map((server, i) => {
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
      {!firstRun && (
        <div class="button-row">
          <Button focusKey="server-add" onClick={addServer}>
            {t.settingsServerAdd}
          </Button>
        </div>
      )}
      {(firstRun || servers.length === 0) && <div class="field-label standalone">{t.settingsDemoHint}</div>}
      <div class="saved-server builtin" data-section="saved-demo">
        <div class="saved-server-text">
          <div class="saved-server-url">{DEMO_SERVER.url}</div>
          <div class="saved-server-account">{t.settingsDemoBuiltIn}</div>
        </div>
        {demoActive ? (
          <div class="saved-server-active">{t.settingsServerActive}</div>
        ) : (
          <div class="button-row saved-server-actions">
            <Button focusKey="server-demo" onClick={useDemo}>
              {t.settingsDemoServer}
            </Button>
          </div>
        )}
      </div>

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
          <h2 class="section-title">{t.settingsSectionViewDefaults}</h2>
          <div class="field-label standalone">{t.settingsViewDefaultsHint}</div>
          <ViewRows options={viewRows} onCycle={cycleDefault} />

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
      <div class="choice-row wrap" data-section="language" data-last-key={`lang-${language}`}>
        <Button focusKey="lang-system" selected={language === 'system'} onClick={() => setLanguageChoice('system')}>
          {t.settingsLanguageSystem}
        </Button>
        {/* 언어 이름은 그 언어로 쓴다 (Android와 같은 15개) */}
        {LANGUAGES.map((option) => (
          <Button
            key={option.code}
            focusKey={`lang-${option.code}`}
            selected={language === option.code}
            onClick={() => setLanguageChoice(option.code)}
          >
            {option.name}
          </Button>
        ))}
      </div>
      {/* 앱 정보 (Android와 같은 내용). 글뿐이지만 리모컨으로 내려와 볼 수 있게 상자가 포커스를 받는다 */}
      <h2 class="section-title">{t.aboutTitle}</h2>
      <div class="focusable about-box" tabIndex={-1} data-key="about">
        <div class="about-line strong">{fmt(t.aboutAppVersion, APP_VERSION)}</div>
        <div class="about-line">{fmt(t.aboutDeveloper, 'David Hojin Kim', 'sinabro@gmail.com')}</div>
        <div class="about-line">{t.aboutDeveloperBio}</div>
        <div class="about-line">{t.aboutDeveloperWish}</div>
        <div class="about-line">{t.aboutDeveloperJoke}</div>
        <div class="about-line">github.com/RR-korea/KomgaTV</div>
        <div class="about-line">{t.aboutTribute}</div>
        <div class="about-line">Komga — github.com/gotson/komga · Komelia — github.com/Snd-R/Komelia</div>
        <div class="about-line small">{t.aboutLicenses}</div>
        <div class="about-line small">{navigator.userAgent}</div>
      </div>
    </div>
  );
}
