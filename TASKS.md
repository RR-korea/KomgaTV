# KomgaTV 작업 지시서

리드: Claude Code — 방향·검토·승인. 구현: Antigravity.
배경과 결정 근거는 [`docs/LEAD_REVIEW.md`](docs/LEAD_REVIEW.md). 결정(D1~D6)을 바꿔야 할 것 같으면 구현하지 말고 `docs/QUESTIONS.md`에 적고 멈춘다.

## 공통 규칙
- 작업 하나 = 브랜치 하나 (`task/T1-build` 등). 작업이 끝나면 그 브랜치에 커밋한다. **push·main 병합은 하지 않는다** (리드가 검토 후 병합).
- 커밋 메시지는 Conventional Commits, 한국어 본문 가능.
- "완료 조건"에 적힌 명령을 실제로 실행해 통과시킨 뒤에만 완료로 보고한다. 못 했으면 못 했다고 쓴다.
- 작업 끝에 `docs/reports/T<n>.md`에 기록: 바꾼 것, 실행한 명령과 결과(마지막 30줄), 남은 문제.
- 코드 주석은 한국어, 기존 스타일 유지. 쓰지 않는 코드·의존성은 남기지 않는다.
- 비밀번호·API Key·서버 주소를 저장소에 커밋하지 않는다.
- 테스트 때문에 운영 코드를 바꾸지 않는다 (테스트 전용 생성자, nullable 의존성 금지). 인터페이스를 주입받고 테스트에서는 가짜 구현을 쓴다.
- 이미지 URL은 `core:data`의 `ImageUrls`를 주입받아 만든다. 화면·ViewModel이 `ServerSessionManager`나 baseUrl을 직접 다루지 않는다.
- 보고서의 "경고 0"은 lint 결과 파일(`*/build/reports/lint-results-debug.txt`)을 실제로 확인한 뒤에만 쓴다.
- **UI 문자열은 전부 리소스로** — 화면에 보이는 글자를 코드에 쓰지 않는다. `values/strings.xml`(영어, 기본) + `values-ko/strings.xml`(한국어)에 둘 다 넣는다. ViewModel은 오류 문구 대신 오류 종류를 내보내고 화면이 문자열로 바꾼다.
- **Back 키는 `BackHandler`로** — targetSdk 36은 예측형 뒤로 가기가 기본이라 Back이 `onKeyEvent`/`onPreviewKeyEvent`로 오지 않는다.
- **화면 포커스** — 들어올 때 첫 항목, 돌아오면 연 항목에 포커스: `core:ui`의 `rememberEntryFocusState`/`EntryFocusEffect`/`OnReturnEffect`를 쓴다.

---

## T0. 개발 환경 (이 PC) — ✅ 완료 (docs/reports/T0.md)
이 PC에는 JDK·Android SDK가 없다.
- JDK 17 이상 설치 (winget `EclipseAdoptium.Temurin.21.JDK` 등). `JAVA_HOME` 설정.
- Android SDK command-line tools 설치 → `platform-tools`, `platforms;android-<compileSdk>`, `build-tools;<최신>` 설치, 라이선스 동의. `ANDROID_HOME` 설정.
- `local.properties`에 `sdk.dir` 작성 (gitignore 확인).
- **완료 조건:** `java -version`, `sdkmanager --list_installed` 출력이 보고서에 있음.

## T1. 빌드 초록불 — `task/T1-build` — ✅ 완료 (docs/reports/T1.md)
LEAD_REVIEW §2의 B1~B7 전부와 D1·D2·D3·D4 구조 변경.
1. Gradle wrapper 생성 (`gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`), 버전은 D3.
2. `settings.gradle.kts` 오타 수정, `:core:datastore` 모듈 추가.
3. `libs.versions.toml`: D3 최신 안정판. Retrofit·converter·Room 제거. 추가: `io.github.snd-r:komga-client:0.11.0`, `io.ktor:ktor-client-okhttp`(komga-client가 쓰는 Ktor 메이저와 맞춤), `coil-network-okhttp`.
4. `ServerPreferences` → `core:datastore`로 이동.
5. `core:network`: `KomgaApi`·DTO 삭제. `ServerSessionManager`(D2) 작성:
   - `val session: StateFlow<KomgaSession?>` — `KomgaSession`은 `OkHttpClient`, `KomgaClientFactory`, `baseUrl` 보유.
   - OkHttp 인터셉터가 Basic 또는 `X-API-Key` 헤더를 붙인다(Coil 이미지 요청에도 적용되도록 OkHttp 레벨에서).
   - `suspend fun connect(url, auth): Result<KomgaUser>` — `userClient().getMe()`로 확인 후 저장.
6. `core:data` 리포지토리는 komga-client 모델 → `core:model` 매핑만 한다.
7. Coil 3로 이전: `SingletonImageLoader.Factory`, `coil3.compose.AsyncImage`, 세션의 `OkHttpClient`로 `OkHttpNetworkFetcherFactory`. 이미지 URL은 세션 `baseUrl` 기준 **절대 URL**.
8. TvLazy* → 표준 Lazy 레이아웃 (D4).
9. 런처 리소스: `ic_launcher`(adaptive), `tv_banner`(320×180 xml/png) 임시 제작.
- **완료 조건:** `./gradlew assembleDebug lint` 성공. 경고 중 deprecated API 사용은 0개.

## T2. 서버 연결 화면 — `task/T2-connect` — ✅ 완료 (docs/reports/T2.md)
- 첫 실행(세션 없음) → 설정 화면이 시작 화면. URL, 인증 방식(ID/PW | API Key), 연결 테스트, 저장.
- 실패 메시지 구분: 주소 오류 / 연결 불가 / 401 / 기타. 자체서명 인증서는 지금은 미지원(문구로 안내).
- 자격 증명 Keystore 암호화 (D6). `allowBackup=false` 또는 백업 규칙에서 제외.
- 모든 입력·버튼이 D-pad만으로 도달 가능, 화면 진입 시 첫 입력칸에 포커스.
- **완료 조건:** `MockWebServer` 단위 테스트 — 성공(200 /api/v2/users/me), 401, 연결 거부 3가지. `./gradlew testDebugUnitTest` 통과.

## T3. 탐색 — `task/T3-browse` — ✅ 완료 (docs/reports/T3.md)
- 홈(단순 버전): 라이브러리 목록 행 + "읽는 중" 행(`read_status=IN_PROGRESS` 책). 캐러셀은 아직 없음.
- 라이브러리 → 시리즈 그리드(무한 스크롤, 페이지 크기 50) → 시리즈 상세(책 목록, 진행률) → 리더.
- 뒤로 가기 시 이전 화면의 **포커스 위치 복원**.
- **완료 조건:** 빌드 + 리모컨 조작 시나리오를 `docs/reports/T3.md`에 표로 (키 → 기대 포커스).

## T4. 리더 — `task/T4-reader` — ✅ 완료 (docs/reports/T4.md)
- 좌/우 = 이전/다음 페이지, OK = 오버레이, 위/아래(오버레이) = 10페이지 점프, Back = 종료+진행 저장.
- 책의 `readingDirection`이 RIGHT_TO_LEFT면 좌/우를 뒤집는다.
- 이웃 페이지 프리로드(±2), 화면 크기로 디코딩.
- 진행 저장: 페이지 바뀐 뒤 2초 디바운스 + 종료 시 즉시. 마지막 페이지면 `completed=true`.
- 이어 읽기: 진입 시 서버 진행도 페이지로 시작.
- **완료 조건:** `ReaderViewModel` 단위 테스트(방향 반전, 디바운스, 완독). 테스트 통과.

## T5. 리더 보기 설정 (Komelia 페이지 리더와 같은 옵션) — `task/T5-reader-options` — ✅ 완료 (docs/reports/T5.md, 리모컨 키 표 포함)
사용자 요구: Komelia 등 만화 뷰어에 있는 기본 보기 설정을 그대로 지원한다. 기준은 Komelia `ImageReaderSettings`(Apache-2.0,
`komelia-infra/database/shared/.../db/ImageReaderSettings.kt`, `komelia-ui/.../reader/image/paged/PagedReaderState.kt`).
옵션 이름·기본값은 Komelia와 같게 한다. 코드를 옮겨 오면 해당 파일 주석에 출처를 쓰고 `THIRD_PARTY_NOTICES.md`에 Komelia(Apache-2.0)를 적는다.

1. **설정 저장** — `core:datastore`에 `ReaderPreferences`(인터페이스 + DataStore 구현, `ServerPreferences`와 같은 방식). 전역 기본값:
   - `pageLayout`: `SINGLE_PAGE`(기본) / `DOUBLE_PAGES`(표지는 한 쪽) / `DOUBLE_PAGES_NO_COVER`
   - `scaleType`: `SCREEN`(기본, 화면에 맞춤) / `FIT_WIDTH` / `FIT_HEIGHT` / `ORIGINAL`
   - `stretchToFit`: `true`(기본, 화면보다 작은 이미지를 키움)
   - `readingDirection`: `LEFT_TO_RIGHT`(기본) / `RIGHT_TO_LEFT`
   - 모델 enum은 `core:model`에 둔다.
2. **읽기 방향 우선순위** — 시리즈 메타데이터에 방향이 있으면 그것, 없으면 저장된 기본값. 리더에서 방향을 바꾸면 지금 책에 바로 적용하고 기본값으로 저장한다(Komelia와 같음). `RIGHT_TO_LEFT`면 좌/우 키와 두 쪽 배치(첫 쪽이 오른쪽)가 뒤집힌다.
3. **두 쪽 보기** — 책 페이지 목록의 가로·세로 크기(`bookClient().getBookPages(bookId)`, 크기가 null이면 세로로 본다)로 가로가 긴 페이지(가로 > 세로, 이미 펼침으로 스캔된 쪽)는 혼자 보인다. 나머지는 두 쪽씩. `DOUBLE_PAGES`는 첫 쪽(표지)을 혼자. **쪽 맞춤 밀기(offset)** 토글: 짝이 어긋난 책을 한 쪽 밀어 맞춘다(세션 동안만, Komelia와 같음). 좌/우 = 펼침 단위로 이동.
4. **크기 맞춤** — 화면보다 큰 페이지(`FIT_WIDTH`, `ORIGINAL`)는 오버레이가 닫힌 상태에서 위/아래로 화면 절반씩 스크롤, 끝에 닿은 상태의 위/아래는 아무것도 안 한다. 좌/우는 항상 페이지 넘김. 디코딩 크기는 화면·맞춤 방식에 맞춘다(원본 그대로 디코딩 금지 — 2GB TV).
5. **설정 패널** — 오버레이(OK)에 "보기 설정" 버튼 → D-pad로만 조작하는 패널(쪽 배치, 밀기, 읽기 방향, 크기 맞춤, 작은 이미지 키우기). 바꾸면 즉시 반영. Back = 패널 닫기. 열 때 첫 항목에 포커스.
6. **진행 저장** — 펼침에서 보이는 쪽 중 큰 번호를 저장한다. 마지막 쪽이 보이면 `completed=true`. 진입 시 서버 진행 쪽이 들어 있는 펼침에서 시작.
7. 프리로드는 펼침 기준 앞뒤 2개.
- **이번 범위 아님:** 세로 스크롤(웹툰, `CONTINUOUS`) — T6, 패널 읽기, 여백 자르기, 페이지 전환 깜빡임, 다운샘플링 커널·ONNX 업스케일러(TV 성능).
- **완료 조건:** 단위 테스트 — 펼침 지도(표지 있음/없음/밀기/가로 쪽 단독/홀수 끝), 방향 우선순위(시리즈 > 기본값), 방향 바꾸면 기본값 저장, 펼침에서 저장 쪽·완독, 설정 저장·읽기(가짜 저장소). `./gradlew assembleDebug lint testDebugUnitTest` 통과, 우리 코드 lint 경고 0(결과 파일 확인).

## T6. 세로 스크롤 리더 (웹툰, Komelia `CONTINUOUS`) — `task/T6-continuous` — ✅ 완료 (docs/reports/T6.md)
사용자 요구: Komelia처럼 웹툰을 세로로 이어서 읽는다. 기준은 Komelia(Apache-2.0)
`komelia-infra/database/shared/.../db/ImageReaderSettings.kt`, `komelia-ui/.../reader/image/ReaderState.kt`,
`komelia-ui/.../reader/image/continuous/ContinuousReaderState.kt`. 이름·기본값은 Komelia와 같게, 옮긴 코드는 출처 주석 + `THIRD_PARTY_NOTICES.md`.
T5 코드(`ReaderViewModel`, `ReaderScreen`, `ReaderSettingsPanel`, `docs/reports/T5.md`의 리모컨 키 표)를 먼저 읽는다.

1. **설정** — `ReaderSettings`/`ReaderPreferences`에 추가 (Komelia 기본값):
   - `readerType`: `PAGED`(기본) / `CONTINUOUS` (`PANELS`는 범위 아님 — enum에 넣지 않는다)
   - `continuousPadding`: `0f` (좌우 여백, 화면 너비 비율. 패널에서 0%~45%, 5% 단위)
   - `continuousPageSpacing`: `0` (쪽 사이 간격 dp. 패널에서 0~64, 8 단위)
   - 세로 스크롤 방향은 위→아래(`TOP_TO_BOTTOM`)만 구현한다. 가로 연속 보기는 범위 아님.
2. **리더 종류 우선순위 (Komelia `ReaderState`와 같음)** — 시리즈 방향 `LEFT_TO_RIGHT`/`RIGHT_TO_LEFT` → `PAGED`, `WEBTOON` → `CONTINUOUS`,
   `VERTICAL`/없음 → 저장된 `readerType`. 리더에서 종류를 바꾸면 지금 책에 바로 적용하고 기본값으로 저장.
3. **이어 읽기 (두 모드 공통, Komelia와 같음)** — 진행이 없거나 `completed`면 1쪽부터, 아니면 저장된 쪽부터. (지금 T5는 완독한 책을 마지막 쪽에서 연다 — 고친다.)
4. **화면** — `LazyColumn`, 쪽 너비 = 화면 너비 × (1 − 2 × padding), 쪽 사이 간격, 작은 이미지 키우기(`stretchToFit`) 적용.
   - **큰 이미지는 타일로** — 웹툰 한 쪽은 세로 1만 px가 넘기도 하고 싼 TV 박스는 텍스처 한도가 4096px다. 한 비트맵으로 그리지 않는다.
     페이지 파일을 받아 `BitmapRegionDecoder`로 세로 조각(그렸을 때 높이 ≤ 화면 높이)만 `inSampleSize`·RGB_565로 디코딩한다.
     `LazyColumn` 항목 = 타일, 보이는 타일 앞뒤만 메모리에 둔다(LruCache, 크기 상한). 타일 계획(쪽 크기·화면·여백 → 타일 목록)은 순수 함수로 분리해 테스트.
   - 쪽 크기는 `getBookPages`의 가로·세로. null이면 파일을 받은 뒤 bounds로 알아내고 그 쪽의 타일을 다시 계획한다(그동안은 화면 높이 자리표시).
   - **페이지 파일 캐시** — `core:data`에 인터페이스 + 구현: 세션 `OkHttpClient`(인증 헤더 포함)로 `ImageUrls.page` URL을 받아 `cacheDir`에 저장, 크기 상한 LRU(200MB). 화면·ViewModel은 OkHttp를 직접 쓰지 않는다.
   - 프리로드: 화면 아래쪽 다음 2쪽 파일을 미리 받는다.
5. **리모컨 키** — 위/아래 = 화면 높이만큼 부드럽게 스크롤(Komelia `scrollScreenForward/Backward`), 좌/우 = 이전/다음 쪽 첫 타일로 이동,
   OK·Back·오버레이·보기 설정은 T5와 같다(Back은 `BackHandler`). 오버레이의 위/아래 10쪽 점프도 같다.
6. **보기 설정 패널** — 첫 줄에 "리더 종류"(Paged/Continuous). 종류에 따라 줄이 바뀐다: Paged = T5 줄들, Continuous = 좌우 여백, 쪽 간격, 작은 이미지 키우기.
   이름은 Komelia 영어 이름(Reader type, Paged, Continuous, Side padding, Page spacing) + `values-ko` 한국어.
7. **진행 저장** — 화면에 보이는 쪽 중 가장 큰 번호, 2초 디바운스 + 종료 시 즉시. 마지막 쪽의 마지막 타일이 보이면 `completed=true`.
- **이번 범위 아님:** 가로 연속 보기, 패널 읽기, 여백 자르기, 확대/축소(핀치), 다운샘플링 커널.
- **완료 조건:** 단위 테스트 — 리더 종류 우선순위(4가지), 완독 책은 1쪽부터, 타일 계획(작은 쪽 / 화면보다 긴 쪽 / 크기 null / 여백·간격), 보이는 타일 → 저장 쪽·완독,
  페이지 파일 캐시(MockWebServer: 인증 헤더, 캐시 적중 시 재요청 없음, 상한 넘으면 오래된 파일 삭제). `./gradlew assembleDebug lint testDebugUnitTest` 통과, 우리 코드 lint 경고 0(결과 파일 확인).
  **에뮬레이터 확인:** TV 에뮬레이터(`emulator-5556`, `adb -s emulator-5556`)에 설치해 보기 설정에서 Continuous로 바꾸고 위/아래 스크롤·좌우 쪽 이동·Back 순서를
  `adb exec-out screencap -p`로 캡처해 `docs/reports/T6/`에 넣는다. 서버 연결은 이미 저장돼 있다 — 앱 데이터를 지우지 않는다(`pm clear` 금지).

## T7. 다국어 (영어 기본 + 한국어) — 문자열 이전 완료(홈·라이브러리·설정·리더·core:ui), 화면별 두 언어 캡처 검사 남음
사용자 요구: UI가 한국어도 지원해야 한다. 지금은 한국어가 `values/`(기본)에만 있다.
- 모든 모듈 `values/strings.xml` → 영어, 지금 한국어는 `values-ko/strings.xml`로. 앱 이름·TV 배너 포함.
- 코드에 박힌 오류 문구(ViewModel `e.localizedMessage ?: "…"`, `ServerConnectionException` 기본 메시지, `NotConnectedException`)를 오류 종류로 바꾸고 화면에서 리소스로 표시.
- **완료 조건:** 에뮬레이터를 영어·한국어로 바꿔 각각 설정→홈→라이브러리→시리즈→리더 화면 캡처, 하드코딩 한글 검사(`grep`) 0.

## T8. 시리즈 검색 — ✅ 완료 (docs/reports/T8.md)
홈의 검색 버튼, 입력(디바운스 400ms) + 음성 인식, 결과 그리드·이어 불러오기, 포커스 복원.

## T9. 홈 줄 확장 — ✅ 완료 (docs/reports/T9.md)
다음 권, 최근 추가된 책·시리즈, 최근 업데이트된 시리즈.

## T10. 책 제목 검색 — ✅ 완료 (docs/reports/T10.md)

## 이후 (리드가 지시)
다중 서버, EPUB.
