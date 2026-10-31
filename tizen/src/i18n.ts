// UI 문자열. Android(values/strings.xml, values-ko)와 같은 문구를 쓴다. 기본 영어, TV 언어가 한국어면 한국어.

const ko = {
  appName: 'KomgaTV',
  loading: '불러오는 중…',
  loadingMore: '더 불러오는 중…',
  errorTitle: '문제가 생겼습니다',
  errorNotConnected: 'Komga 서버에 연결되어 있지 않습니다. 서버 설정을 확인하세요.',
  errorUnreachable: '서버에 연결할 수 없습니다. 주소와 네트워크를 확인하세요.',
  errorUnauthorized: '서버가 로그인을 거부했습니다. 이메일/비밀번호 또는 API 키를 확인하세요.',
  errorUnknown: '불러오는 중 문제가 생겼습니다.',
  retry: '다시 시도',

  settingsTitle: '서버 설정',
  settingsSectionServer: '서버 연결',
  settingsServerUrl: '서버 주소',
  settingsServerUrlPlaceholder: 'http://192.168.1.100:25600',
  settingsAuthMethod: '로그인 방식',
  settingsAuthBasic: '이메일 / 비밀번호',
  settingsAuthApiKey: 'API 키',
  settingsEmail: '이메일 (Komga 로그인 계정)',
  settingsEmailPlaceholder: 'admin@example.com',
  settingsPassword: '비밀번호',
  settingsApiKey: 'API 키',
  settingsTest: '연결 테스트',
  settingsSave: '저장하고 연결',
  settingsTesting: '서버를 확인하는 중…',
  settingsSuccess: '서버에 연결되었습니다.',
  settingsErrorInvalidUrl: '서버 주소가 올바르지 않습니다. (예: http://192.168.1.100:25600)',
  settingsErrorUnreachable: '서버에 연결할 수 없습니다. 주소, 포트, 네트워크를 확인하세요.',
  settingsErrorUnauthorized: '로그인에 실패했습니다. 이메일/비밀번호 또는 API 키를 확인하세요. (401)',
  settingsErrorOther: '서버에 연결하지 못했습니다: {0}',
  settingsErrorMissing: '서버 주소와 로그인 정보를 입력하세요.',
  settingsSectionCache: '캐시',
  settingsClearCache: '이미지 캐시 비우기',
  settingsCacheCleared: '이미지 캐시를 비웠습니다.',
  settingsSectionApp: '앱',
  settingsVersion: '버전 {0}',
  settingsLanguage: '언어',
  settingsLanguageSystem: 'TV 설정 따름',

  homeContinueReading: '이어 읽기',
  homeLibraries: '라이브러리',
  homeBookProgress: '{0}/{1}쪽',
  homeBookCompleted: '다 읽음',
  homeServerSettings: '서버 설정',
  homeEmpty: '읽는 중인 책이 없습니다.',

  seriesBooksCount: '{0}권',
  seriesBooksHeader: '책',
  seriesStatus: '상태: {0}',
  seriesPublisher: '출판사: {0}',
  seriesProgress: '{0}/{1}쪽',
  seriesCompleted: '다 읽음',
  seriesUnread: '안 읽음',
  seriesStatusEnded: '완결',
  seriesStatusOngoing: '연재 중',
  seriesStatusAbandoned: '중단',
  seriesStatusHiatus: '휴재',
  libraryEmpty: '시리즈가 없습니다.',

  readerLoading: '페이지 불러오는 중…',
  readerPageOf: '{0} / {1}쪽',
  readerSpreadOf: '{0} / {1} 펼침 ({2} / {3}쪽)',
  readerSettings: '보기 설정',
  readerSettingsTitle: '리더 보기 설정',
  readerType: '리더 종류',
  readerTypePaged: '페이지 넘김',
  readerTypeContinuous: '세로 스크롤',
  readerPadding: '좌우 여백',
  readerSpacing: '쪽 간격',
  readerLayout: '쪽 배치',
  readerLayoutSingle: '한 쪽',
  readerLayoutDouble: '두 쪽 (표지 단독)',
  readerLayoutDoubleNoCover: '두 쪽 (표지 포함)',
  readerOffset: '쪽 맞춤 밀기',
  readerDirection: '읽기 방향',
  readerDirectionLtr: '왼쪽에서 오른쪽 (LTR)',
  readerDirectionRtl: '오른쪽에서 왼쪽 (RTL)',
  readerScale: '크기 맞춤',
  readerScaleScreen: '화면에 맞춤',
  readerScaleFitWidth: '너비 맞춤',
  readerScaleFitHeight: '높이 맞춤',
  readerScaleOriginal: '원본 크기',
  readerStretch: '작은 이미지 키우기',
  on: '켜짐',
  off: '꺼짐',
  close: '닫기',
  readerHelp: 'OK: 정보  ·  위/아래: 10쪽 이동  ·  Back: 나가기',
  readerHelpContinuous: '정보를 닫으면 위/아래: 한 화면씩  ·  좌/우: 쪽 이동  ·  위/아래(정보): 10쪽 이동',
};

export type Strings = typeof ko;

const en: Strings = {
  appName: 'KomgaTV',
  loading: 'Loading…',
  loadingMore: 'Loading more…',
  errorTitle: 'Something went wrong',
  errorNotConnected: 'Not connected to a Komga server. Check the server settings.',
  errorUnreachable: "Can't reach the server. Check the address and your network.",
  errorUnauthorized: 'The server rejected the login. Check your email/password or API key.',
  errorUnknown: 'Something went wrong while loading.',
  retry: 'Retry',

  settingsTitle: 'Server settings',
  settingsSectionServer: 'Server connection',
  settingsServerUrl: 'Server URL',
  settingsServerUrlPlaceholder: 'http://192.168.1.100:25600',
  settingsAuthMethod: 'Sign-in method',
  settingsAuthBasic: 'Email / password',
  settingsAuthApiKey: 'API key',
  settingsEmail: 'Email (your Komga login)',
  settingsEmailPlaceholder: 'admin@example.com',
  settingsPassword: 'Password',
  settingsApiKey: 'API key',
  settingsTest: 'Test connection',
  settingsSave: 'Save and connect',
  settingsTesting: 'Checking the server…',
  settingsSuccess: 'Connected to the server.',
  settingsErrorInvalidUrl: 'The server address is not valid. (e.g. http://192.168.1.100:25600)',
  settingsErrorUnreachable: "Can't reach the server. Check the address, port and network.",
  settingsErrorUnauthorized: 'Sign-in failed. Check your email/password or API key. (401)',
  settingsErrorOther: 'Could not connect to the server: {0}',
  settingsErrorMissing: 'Enter the server URL and your sign-in details.',
  settingsSectionCache: 'Cache',
  settingsClearCache: 'Clear image cache',
  settingsCacheCleared: 'Image cache cleared.',
  settingsSectionApp: 'App',
  settingsVersion: 'Version {0}',
  settingsLanguage: 'Language',
  settingsLanguageSystem: 'Same as TV',

  homeContinueReading: 'Continue reading',
  homeLibraries: 'Libraries',
  homeBookProgress: '{0}/{1} pages',
  homeBookCompleted: 'Finished',
  homeServerSettings: 'Server settings',
  homeEmpty: 'Nothing in progress.',

  seriesBooksCount: '{0} books',
  seriesBooksHeader: 'Books',
  seriesStatus: 'Status: {0}',
  seriesPublisher: 'Publisher: {0}',
  seriesProgress: '{0}/{1} pages',
  seriesCompleted: 'Finished',
  seriesUnread: 'Unread',
  seriesStatusEnded: 'Ended',
  seriesStatusOngoing: 'Ongoing',
  seriesStatusAbandoned: 'Abandoned',
  seriesStatusHiatus: 'On hiatus',
  libraryEmpty: 'No series.',

  readerLoading: 'Loading page…',
  readerPageOf: 'Page {0} / {1}',
  readerSpreadOf: 'Spread {0} / {1} (page {2} / {3})',
  readerSettings: 'View settings',
  readerSettingsTitle: 'Reader view settings',
  readerType: 'Reader type',
  readerTypePaged: 'Paged',
  readerTypeContinuous: 'Continuous',
  readerPadding: 'Side padding',
  readerSpacing: 'Page spacing',
  readerLayout: 'Page layout',
  readerLayoutSingle: 'Single page',
  readerLayoutDouble: 'Double pages (cover alone)',
  readerLayoutDoubleNoCover: 'Double pages (with cover)',
  readerOffset: 'Offset pages',
  readerDirection: 'Reading direction',
  readerDirectionLtr: 'Left to right (LTR)',
  readerDirectionRtl: 'Right to left (RTL)',
  readerScale: 'Scale',
  readerScaleScreen: 'Fit screen',
  readerScaleFitWidth: 'Fit width',
  readerScaleFitHeight: 'Fit height',
  readerScaleOriginal: 'Original size',
  readerStretch: 'Stretch small images',
  on: 'On',
  off: 'Off',
  close: 'Close',
  readerHelp: 'OK: info  ·  Up/Down: jump 10 pages  ·  Back: exit',
  readerHelpContinuous: 'Info closed: Up/Down scroll a screen  ·  Left/Right: page  ·  Up/Down (info): jump 10 pages',
};

const tables: Record<string, Strings> = { ko, en };

// 앱 언어: 기본은 TV 언어를 따르고, 설정에서 고를 수 있다 (TV 언어를 바꿀 수 없는 에뮬레이터·호텔 TV 등).
export type LanguageChoice = 'system' | 'ko' | 'en';
const LANGUAGE_KEY = 'komgatv.language';

export function getLanguageChoice(): LanguageChoice {
  try {
    const value = localStorage.getItem(LANGUAGE_KEY);
    return value === 'ko' || value === 'en' ? value : 'system';
  } catch {
    return 'system';
  }
}

/** 저장하고 앱을 다시 읽는다 (문자열은 시작할 때 한 번 정해진다). */
export function setLanguageChoice(choice: LanguageChoice): void {
  try {
    if (choice === 'system') localStorage.removeItem(LANGUAGE_KEY);
    else localStorage.setItem(LANGUAGE_KEY, choice);
  } catch {
    return;
  }
  location.reload();
}

function pickLanguage(): Strings {
  const choice = getLanguageChoice();
  if (choice !== 'system') return tables[choice];
  const lang = (navigator.language || 'en').toLowerCase();
  return tables[lang.split('-')[0]] ?? en;
}

export const t: Strings = pickLanguage();

/** '{0}/{1}' 자리에 값을 넣는다. */
export function fmt(template: string, ...values: (string | number)[]): string {
  return template.replace(/\{(\d+)\}/g, (_match, index: string) => String(values[Number(index)] ?? ''));
}
