# KomgaTV 리드 검토 (2026-10-31)

초기 커밋 `53a2348` 검토 결과와 방향 결정. 작업 지시는 [`TASKS.md`](../TASKS.md).

## 1. 방향 평가

**유지한다.** Android TV 전용, Kotlin + Jetpack Compose for TV, 기능 모듈 분리, 2GB RAM 기준 이미지 전략은 맞는 선택이다.
현재 공개된 Komga 클라이언트 중 D-pad를 제대로 지원하는 Android TV 앱은 없다
(Komelia 이슈 #40·#66이 바로 이 불만). 이 앱의 존재 이유는 **리모컨 조작 품질**이다.

## 2. 현재 상태: 빌드 불가

| # | 문제 | 위치 | 결과 |
|---|---|---|---|
| B1 | `dependencyResolution` → `dependencyResolutionManagement` 오타 | `settings.gradle.kts` | Gradle 설정 단계 실패 |
| B2 | `gradlew`, `gradlew.bat`, `gradle-wrapper.jar` 없음 | 루트 | `./gradlew` 실행 불가 |
| B3 | 순환 의존: `core:network`의 `NetworkModule`이 `core:data`의 `ServerPreferences`를 import, `core:data`는 `core:network`에 의존 | `core/network/.../NetworkModule.kt` | 컴파일 실패 |
| B4 | Coil 3 의존성인데 코드는 Coil 2 API (`coil.compose.*`, `ImageLoaderFactory`) | `KomgaTVApp.kt`, `PosterCard.kt`, `ReaderScreen.kt` 등 | 컴파일 실패 |
| B5 | `TvLazyRow`/`TvLazyColumn`/`rememberTvLazyListState` — tv-foundation 1.0 안정판에서 제거됨 | `TVRow.kt`, `HomeScreen.kt` | 컴파일 실패 |
| B6 | Manifest가 `@drawable/tv_banner`, `@mipmap/ic_launcher` 참조, 리소스 없음 | `app/src/main/res` | AAPT 실패 |
| B7 | 툴체인 구버전 (Kotlin 2.1.0 / AGP 8.7.3 / Compose BOM 2025.05) | `libs.versions.toml` | komga-client(최신 Kotlin으로 빌드됨)와 메타데이터 불일치 가능 |

## 3. 동작 결함 (빌드 후에도 안 되는 것)

| # | 문제 | 결과 |
|---|---|---|
| F1 | API Key 헤더가 `X-Auth-Token` — Komga는 **`X-API-Key`** | API Key 로그인 항상 401 |
| F2 | 이미지 URL이 상대경로(`"api/v1/series/$id/thumbnail"`), Coil에 서버 주소·인증 없음 | 커버·페이지 이미지 전부 안 뜸 |
| F3 | Retrofit `baseUrl`을 싱글턴 생성 시 `runBlocking`으로 한 번만 읽음 | 첫 실행 시 localhost 고정, 서버 변경이 재시작 전까지 반영 안 됨 |
| F4 | 인터셉터에서 요청마다 `runBlocking { datastore.first() }` | 이미지 요청마다 I/O 블로킹 |
| F5 | 비밀번호를 DataStore에 평문 저장 (문서는 "암호화"라고 기술) | 문서와 구현 불일치 |

## 4. 결정 사항

### D1. Komga API 계층은 직접 쓰지 않고 `io.github.snd-r:komga-client`를 쓴다
- MIT, Maven Central, Android 아티팩트 있음(`komga-client-android`), Komelia가 실제로 쓰는 라이브러리.
- `X-API-Key`, Basic Auth, 쿠키 세션, 동적 `baseUrl: () -> URLBuilder`, 페이지네이션, 검색 조건 빌더, read-progress까지 이미 있다. F1·F3을 구조적으로 없앤다.
- Ktor 엔진은 **OkHttp**로 하고, 같은 `OkHttpClient`를 Coil(`coil-network-okhttp`)에도 넘긴다 → 인증·연결 풀 공유(F2 해결).
- 결과: Retrofit·직접 작성한 DTO 삭제. `core:network`는 "서버 세션(OkHttpClient + KomgaClientFactory) 제공" 모듈로 축소.

### D2. 서버 세션은 `ServerSessionManager` 하나가 소유한다
- 위치: `core:network`. 설정 저장(`ServerPreferences`)은 `core:datastore`(신규, 의존 없음)로 옮겨 순환을 끊는다.
- `StateFlow<KomgaSession?>` — 설정 저장 시 클라이언트를 새로 만든다. 요청 경로에 `runBlocking` 없음(F3·F4).
- 의존 방향: `core:datastore` ← `core:network` ← `core:data` ← `feature:*`.

### D3. 버전은 "지금 시점 최신 안정판"으로 올린다
Kotlin, AGP, Gradle, Compose BOM, tv-material, Coil 3, Hilt, KSP를 Maven/Google 저장소에서 확인한 최신 **안정판**으로 맞춘다. komga-client 0.11.0이 요구하는 Kotlin 이상이어야 한다.

### D4. 리스트는 표준 Compose `LazyRow`/`LazyColumn`/`LazyVerticalGrid` + tv-material3 `Card`/`Carousel`
tv-foundation의 TvLazy* 대신 표준 lazy 레이아웃을 쓴다(Google 권장). 포커스 복원은 `focusRestorer()`와 `FocusRequester`로 처리한다.

### D5. MVP 범위를 좁힌다
1차 목표: **서버 연결 → 라이브러리 → 시리즈 → 책 → 리더(좌/우 넘김) → 진행도 저장**이 실제 Komga 서버에서 끝까지 동작.
홈 캐러셀, 검색, Room 캐시, EPUB, 음성 검색은 그 다음이다. Room은 MVP에서 의존성까지 뺀다.

### D6. 자격 증명은 Android Keystore로 암호화한다
`EncryptedSharedPreferences`는 deprecated이므로 Keystore AES-GCM으로 비밀번호·API Key를 암호화해 DataStore에 저장한다.

## 5. 문서 수정
- KMReader 링크: `pvalenta/KMReader` → `https://github.com/kmworks/kmreader`
- TECH_SPEC 날짜 `2024-10-31` → `2026-10-31`, 버전 표는 D3 이후 실제 값으로 갱신
- 기술 스택의 Retrofit → komga-client(Ktor + OkHttp 엔진)
