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
| Z1 | 기본 틀: 빌드(IIFE, chrome69), 타입 검사, lint(compat), 테스트, `config.xml` | 진행 |
| Z2 | 코어: Komga API 클라이언트, 세션·인증, 오류 분류, 이미지 로더 | 진행 |
| Z3 | 포커스 엔진 + UI 부품(포스터 카드, 행, 그리드, 입력칸) | |
| Z4 | 화면: 서버 연결 → 홈 → 라이브러리 → 시리즈 | |
| Z5 | 리더 (Android T4·T5와 같은 규칙과 옵션) | |
| Z6 | 스토어 준비: 아이콘·스크린샷·개인정보처리방침·심사용 데모 서버, Seller Office | |

## 확인해야 할 위험
- **교차 출처**: 패키지 앱(`file://`)에서 Komga로의 fetch가 `config.xml`의 `<access origin="*">`만으로 되는지, Komga CORS 설정이 필요한지 — Z0에서 실기로 확인.
- 2020년형 메모리 한계에서 큰 페이지 이미지 디코딩 속도.
- 스토어: Public Seller는 미국만 출시 가능, 한국 출시는 삼성 파트너 계약 필요로 보임 — 문의 필요.
