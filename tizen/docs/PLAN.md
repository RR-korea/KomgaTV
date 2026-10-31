# KomgaTV for Tizen — 계획과 결정 (2026-10-31)

삼성 Tizen TV용 Komga 클라이언트. Android TV 버전(저장소 루트)과 **기능·UX는 같고 코드는 따로**다.
Tizen 스토어에는 Komga 클라이언트가 없다 — 이 앱의 목표는 스토어 출시다.

## 역할과 저장소 규칙
- Tizen 쪽 리드: Claude Code 세션(xbox360-kor-b9 [6263bf]). Android 쪽 리드는 다른 세션이며 main 소유자다.
- 브랜치 `tizen/main`, worktree `D:\Project\KomgaTV-tizen`. **모든 파일은 `tizen/` 안에만** 둔다.
  루트 README·TASKS·.gitignore 등 Android 쪽 파일은 고치지 않는다 — 필요하면 Android 리드에게 요청한다.
- main 병합: 사이드로드로 쓸 만해지면 Android 리드에게 병합을 요청한다. 그 전에는 main을 주기적으로 `tizen/main`에 merge해 문서 변경을 따라간다.
- 사양 원본: 루트 `docs/` (LEAD_REVIEW, TASKS의 T2~T5, reports/E2E-1). Tizen 문서는 차이점만 적는다.

## 대상 기기
| 연식 | Tizen | 웹 엔진 |
|---|---|---|
| 2020 (사용자 TV, KQ75QT…AFXKR) | 5.5 | Chromium M69 |
| 2021 / 2022 / 2023 / 2024 / 2025 / 2026 | 6.0 / 6.5 / 7.0 / 8.0 / 9.0 / 10.0 | M76 / M85 / M94 / M108 / M120 / M130 |

출처: Samsung Developer "Web Engine Specifications". **최저 지원 = 2020년형(M69)**. 여기서 돌면 이후 연식 전부 돈다.

## 결정
### Z1. TypeScript + Preact, esbuild로 classic 번들 하나
- Preact: 작고(TV CPU·메모리) 컴포넌트 모델은 Compose와 비슷해 Android 화면을 옮기기 쉽다.
- 번들은 **ES module이 아닌 IIFE 한 파일**. 패키지 앱은 `file://`에서 열리고, Chrome은 `file://`의 `<script type="module">`을 CORS로 막는다.
- 문법은 esbuild `target: chrome69`로 내린다 (`?.`, `??` 등은 써도 됨).
- Web API·CSS는 내려가지 않는다 → API는 `eslint-plugin-compat`(browserslist `chrome 69`)로 검사.
  CSS는 쓰지 않는다: flex `gap`(M84), `aspect-ratio`(M88), `inset`(M87), `loading="lazy"`(M76), `:is()/:where()`(M88), `ResizeObserver`는 M64라 가능.
- Kotlin/Compose Web(Wasm)은 WasmGC(M119+)가 필요해 제외.

### Z2. Komga API 클라이언트는 fetch로 직접 작성
Android가 쓰는 komga-client 0.11.0과 같은 엔드포인트·JSON 형식을 쓴다 (소스에서 확인):
- `GET api/v2/users/me` (연결 확인), `GET api/v1/libraries`
- `POST api/v1/series/list`, `POST api/v1/books/list` — 본문 `{condition, fullTextSearch}`, 쿼리 `page,size,sort`
  조건 예: `{"allOf":[{"libraryId":{"operator":"is","value":"<id>"}}]}`, `{"readStatus":{"operator":"is","value":"IN_PROGRESS"}}`
- `GET api/v1/books/{id}`, `GET api/v1/books/{id}/pages`, `PATCH api/v1/books/{id}/read-progress {page, completed}` → 204
- 이미지: `api/v1/series/{id}/thumbnail`, `api/v1/books/{id}/thumbnail`, `api/v1/books/{id}/pages/{n}` (n은 1부터). 표지 없으면 404 → 자리표시.
- 서버 주소는 하위 경로일 수 있다 (`https://host/komga` → `https://host/komga/api/...`).
- 인증: Basic(로그인 ID는 **이메일**) 또는 `X-API-Key`. 연결 실패는 Android와 같은 5가지로 나눈다(주소 오류/연결 불가/401/SSL/기타).

### Z3. 이미지는 fetch → Blob → object URL
`<img>`에는 인증 헤더를 붙일 수 없다. 모든 이미지를 인증 fetch로 받아 object URL로 보여 주고,
LRU 캐시에서 밀려나면 `URL.revokeObjectURL`로 즉시 해제한다 (TV 메모리).

### Z4. 리모컨 포커스는 자체 공간 내비게이션
방향키 → 화면 좌표상 가장 가까운 포커스 대상. 영역(행·그리드)마다 마지막 포커스를 기억한다.
Android E2E에서 얻은 규칙을 그대로 따른다: 화면 진입 시 첫 항목 포커스, 돌아오면 **연 항목**에 포커스,
돌아오면 진행도 조용히 새로고침, 입력칸은 지나가기만 해서는 키보드가 뜨지 않고 OK로 진입.

### Z5. 자격 증명 저장
MVP는 앱 전용 `localStorage`(Tizen 앱마다 격리). 스토어 전에 Tizen KeyManager 사용 가능 여부를 확인해 바꾼다.

## 작업
| # | 내용 | 상태 |
|---|---|---|
| Z0 | TV 환경: Tizen Studio CLI, TV 개발자 모드, 삼성 인증서, 사이드로드 hello 앱, 리모컨 키·교차 출처 fetch 확인 | 사용자 TV 필요 |
| Z1 | 기본 틀: 빌드(IIFE, chrome69), 타입 검사, lint(compat), 테스트, `config.xml` | 완료 (PC) |
| Z2 | 코어: Komga API 클라이언트, 세션·인증, 오류 분류, 이미지 로더 | 완료 (PC) |
| Z3 | 포커스 엔진 + UI 부품(포스터 카드, 행, 그리드, 입력칸) | 완료 (PC) |
| Z4 | 화면: 서버 연결 → 홈 → 라이브러리 → 시리즈 | 완료 (PC·에뮬레이터) |
| Z5 | 리더 (Android T4·T5와 같은 규칙과 옵션) | 완료 (PC·에뮬레이터) |
| Z5b | 세로 스크롤(웹툰) 리더, 다 읽은 책 1쪽부터 (Android T6) | 완료 (PC·에뮬레이터) |
| Z4b | 홈 줄 확장(다음 권·최근 추가/업데이트, Android T9), 시리즈·책 검색(Android T8) | 완료 (PC·에뮬레이터) |
| Z6 | 스토어 준비: 아이콘·스크린샷·개인정보처리방침·심사용 데모 서버, Seller Office | |

"완료 (PC·에뮬레이터)" = PC Edge 스모크(46항목) + 삼성 TV 10.0 에뮬레이터에서 실제 Komga 서버로 확인.
**실기 TV(Z0)는 아직** — 2020년형(Chromium 69)에서 키·메모리·속도 확인 전에는 사이드로드 완료로 보지 않는다.
실기에 올리려면 TV DUID를 넣어 삼성 배포자 인증서를 다시 받는다 (`dev/samsung-cert/create-cert.mjs`, 작성자 인증서는 그대로).

## 확인 방법
- `npm run check` — 타입 검사 + lint(chrome 69 compat) + 단위 테스트 + 빌드
- `npm run mock` — 가짜 Komga 서버(하위 경로 `/komga`, test@example.com / pw 또는 API 키 test) + 앱 → http://localhost:8790/
- `npm run smoke [폴더]` — Edge(headless, 1920×1080)로 리모컨 키만 써서 설정 → 홈 → 리더(두 쪽·RTL·진행 저장) → 라이브러리 → 시리즈 → 뒤로를 돌고 PASS/FAIL과 스크린샷을 남긴다

## TV 에뮬레이터 (2026-10-31)
- Tizen Studio 6.1 CLI를 `D:\tizen-studio`(데이터 `D:\tizen-studio-data`)에 관리자 권한 없이 설치: 설치 파일 안의 zip을 풀고 `sdk.info`를 직접 쓴 뒤,
  패키지 관리자는 `__COMPAT_LAYER=RunAsInvoker`로 실행. **`NoDefaultCurrentDirectoryInExePath` 환경 변수를 지워야** 패키지 설치 스크립트가 돈다.
- 받을 수 있는 TV 확장은 **10.0(2026년형)뿐** — 웹 엔진이 2020년형(M69)보다 훨씬 새로워 호환성은 실기에서만 확인된다.
- 에뮬레이터는 WHPX(Hyper-V)로 돌아 Android 에뮬레이터와 함께 쓸 수 있다. `em-cli launch -n T-samsung-10.0-x86_64`, 리모컨 키는 `ecp-cli --target emulator-26101 keycode <코드>` (VM 이름을 target으로 주면 조용히 실패).
- **설치는 삼성 인증서가 있어야 한다.** Tizen 작성자 인증서(배포자 new 인증서 포함)로 서명하면 `Invalid certificate chain`으로 거절.
  삼성 인증서 발급: `dev/samsung-cert/login.mjs`(삼성 계정 로그인, 2차 인증은 사람이) → `create-cert.mjs`(작성자·배포자 p12, 기기 DUID 포함).
  DUID는 `sdb shell 0 getduid`. 인증서·비밀번호는 `D:\tizen-studio-data\keystore\samsung`(저장소 밖), 보안 프로필 `KomgaTV-samsung`.
- 에뮬레이터 시스템 언어는 바꿀 수 없다 (`ecp-cli menu language` 무효, `sdb shell` 막힘, 설정 앱 없음) → 앱 설정에 언어 선택(TV 설정 따름/한국어/English)을 넣었다.

### 에뮬레이터 1차 결과 (실제 Komga 서버, 2026-10-31)
- 설치·실행, 첫 실행 설정 화면 → 실제 서버(하위 경로) 로그인 → 홈(이어 읽기·표지) → 리더(쪽 넘김, 정보, 보기 설정 패널) → 진행 저장 ✅
- 패키지 앱(wgt)에서는 Komga CORS 설정 없이 서버 요청이 된다 (TizenBrew 모듈은 다를 수 있음).
- 찾아서 고친 결함:
  1. **화면 키보드(IME)가 떠 있어도 방향키·OK가 페이지에 전달된다.** 위/아래로 입력을 끝내던 규칙 때문에 키보드 안에서 아래로 갈 수 없었다 → Tizen에서는 IME 완료(65376)·취소(65385)·Back으로만 입력 종료.
  2. **리더 쪽 크기가 적용되지 않음** — Preact 11은 숫자 스타일 값에 px를 붙이지 않는다. 화면에 맞춤이 원본 크기로 그려졌다 → 단위 명시, 스모크 테스트에 높이 검사 추가.
- 입력칸 종류마다 키보드 배치가 다르다: url은 소문자+`www.`/`.com`, email·password는 숫자 줄+QWERTY+`@`.
- 웹 엔진: Tizen 10.0 = Chromium 130. 2020년형(M69) 호환은 여전히 실기에서만 확인된다.
- 패키징: `node dev/package-wgt.mjs <보안 프로필>` (TIZEN_STUDIO 환경 변수).

### 에뮬레이터 2차 (2026-11-01, 사용자 부재 중 자율 진행)
- 확인: 라이브러리 그리드 이어 불러오기·실제 표지, 시리즈 상세, 뒤로 ×2 포커스 복원, 세로 스크롤(실제 책), 홈 6줄, 검색("boys" → 시리즈 3·책 55).
- 찾아서 고친 결함:
  1. 홈 위 "검색" 버튼에서 왼쪽 → 아래 첫 책으로 떨어짐 → 좌/우는 같은 줄에서만.
  2. 확대된 포커스 카드 옆 카드를 위 키 후보로 잡음(스모크 간헐 실패) → 후보 중심이 가장자리 너머에 있어야 함.
  3. **검색 결과가 도착할 때 입력칸 포커스를 빼앗아 TV 키보드가 닫힘** → 입력 중에는 포커스 복원을 하지 않음. 스모크에 "결과가 와도 입력칸 유지" 추가.
     이 결함 때문에 시험 키가 앱으로 들어가 실제 서버의 책 하나(크로스본 건담 외전)에 진행이 저장됐다 — 진행 기록이 그 시각에 처음 생긴 것을 확인하고 지웠다(원래 안 읽은 책).
- 실제 서버에 남은 시험 흔적: "20세기 소년 01권" 진행 13 → 15페이지 (리더 시험).

## Android와 다른 점
- 리더: 정보가 열린 상태에서 OK = 보기 설정 열기, 정보 닫기는 Back. (Android는 OK가 정보 토글, 설정은 정보의 버튼) TV 웹앱에서 숨은 버튼으로 포커스를 옮기는 단계를 줄였다.
- 리더: 채널 위/아래 = 10쪽 이동 (정보를 열지 않고).
- 화면 스택: 아래 화면을 숨긴 채 유지해 목록·스크롤을 그대로 두고, 돌아오면 연 항목에 포커스.
- 진행 저장 중이면 홈·시리즈 새로고침이 저장을 기다린다 (Android E2E-1의 경쟁 조건을 여기서는 처음부터 막음).
- 앱 시작 시 저장된 서버로 네트워크 확인 없이 홈 — 서버가 꺼져 있으면 홈에서 오류 + 다시 시도.

## 확인해야 할 위험
- **교차 출처**: 패키지 앱(`file://`)에서 Komga로의 fetch가 `config.xml`의 `<access origin="*">`만으로 되는지, Komga CORS 설정이 필요한지 — Z0에서 실기로 확인.
- 2020년형 메모리 한계에서 큰 페이지 이미지 디코딩 속도.
- 스토어: Public Seller는 미국만 출시 가능, 한국 출시는 삼성 파트너 계약 필요로 보임 — 문의 필요.
