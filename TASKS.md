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

---

## T0. 개발 환경 (이 PC)
이 PC에는 JDK·Android SDK가 없다.
- JDK 17 이상 설치 (winget `EclipseAdoptium.Temurin.21.JDK` 등). `JAVA_HOME` 설정.
- Android SDK command-line tools 설치 → `platform-tools`, `platforms;android-<compileSdk>`, `build-tools;<최신>` 설치, 라이선스 동의. `ANDROID_HOME` 설정.
- `local.properties`에 `sdk.dir` 작성 (gitignore 확인).
- **완료 조건:** `java -version`, `sdkmanager --list_installed` 출력이 보고서에 있음.

## T1. 빌드 초록불 — `task/T1-build`
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

## T2. 서버 연결 화면 — `task/T2-connect`
- 첫 실행(세션 없음) → 설정 화면이 시작 화면. URL, 인증 방식(ID/PW | API Key), 연결 테스트, 저장.
- 실패 메시지 구분: 주소 오류 / 연결 불가 / 401 / 기타. 자체서명 인증서는 지금은 미지원(문구로 안내).
- 자격 증명 Keystore 암호화 (D6). `allowBackup=false` 또는 백업 규칙에서 제외.
- 모든 입력·버튼이 D-pad만으로 도달 가능, 화면 진입 시 첫 입력칸에 포커스.
- **완료 조건:** `MockWebServer` 단위 테스트 — 성공(200 /api/v2/users/me), 401, 연결 거부 3가지. `./gradlew testDebugUnitTest` 통과.

## T3. 탐색 — `task/T3-browse`
- 홈(단순 버전): 라이브러리 목록 행 + "읽는 중" 행(`read_status=IN_PROGRESS` 책). 캐러셀은 아직 없음.
- 라이브러리 → 시리즈 그리드(무한 스크롤, 페이지 크기 50) → 시리즈 상세(책 목록, 진행률) → 리더.
- 뒤로 가기 시 이전 화면의 **포커스 위치 복원**.
- **완료 조건:** 빌드 + 리모컨 조작 시나리오를 `docs/reports/T3.md`에 표로 (키 → 기대 포커스).

## T4. 리더 — `task/T4-reader`
- 좌/우 = 이전/다음 페이지, OK = 오버레이, 위/아래(오버레이) = 10페이지 점프, Back = 종료+진행 저장.
- 책의 `readingDirection`이 RIGHT_TO_LEFT면 좌/우를 뒤집는다.
- 이웃 페이지 프리로드(±2), 화면 크기로 디코딩.
- 진행 저장: 페이지 바뀐 뒤 2초 디바운스 + 종료 시 즉시. 마지막 페이지면 `completed=true`.
- 이어 읽기: 진입 시 서버 진행도 페이지로 시작.
- **완료 조건:** `ReaderViewModel` 단위 테스트(방향 반전, 디바운스, 완독). 테스트 통과.

## 이후 (리드가 T1~T4 검토 후 지시)
홈 캐러셀, 검색(온스크린 키보드 + 음성), 웹툰 세로 스크롤 모드, 두 쪽 보기, 다중 서버, EPUB.
