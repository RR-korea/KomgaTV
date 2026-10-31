# 📋 KomgaTV 기술 사양서 (Tech Spec)

> **문서 버전:** 1.0  
> **최종 수정:** 2026-10-31  
> **상태:** 초기 설계

---

## 1. 프로젝트 개요

### 1.1 목표

Komga 셀프호스팅 미디어 서버의 **Android TV 전용 네이티브 클라이언트**를 개발한다.
TV 리모컨(D-pad)만으로 만화/웹툰/e북 라이브러리를 탐색·열람할 수 있는 10-foot UI를 제공한다.

### 1.2 비(非)목표

- 모바일/태블릿 지원 (별도 프로젝트 영역)
- Komga 서버 관리 기능 (사용자용 뷰어에 집중)
- 스트리밍 비디오 재생
- Kotlin Multiplatform / Kotlin Native 사용

### 1.3 타겟 사용자

- Komga 서버를 운영 중인 셀프호스터
- 거실 TV에서 만화/웹툰을 편하게 읽고 싶은 사용자
- Android TV 박스 (Chromecast with Google TV, NVIDIA Shield, 샤오미 등) 사용자

---

## 2. 시스템 요구 사항

### 2.1 Android TV 디바이스 사양

| 항목 | 최소 요구 | 권장 |
|---|---|---|
| **OS** | Android TV 11 (API 30) | Android TV 13+ (API 33+) |
| **RAM** | 2 GB | 3 GB+ |
| **스토리지** | 앱 설치 50MB + 캐시 200MB | 앱 50MB + 캐시 500MB |
| **네트워크** | Wi-Fi 2.4GHz (10Mbps+) | Wi-Fi 5GHz / 유선 |
| **해상도** | 1080p | 4K |
| **입력** | D-pad 리모컨 | D-pad + 음성 검색 리모컨 |

### 2.2 Komga 서버 요구

| 항목 | 요구 |
|---|---|
| **Komga 버전** | 0.100.0 이상 |
| **API** | REST API v1 활성화 |
| **네트워크** | 클라이언트 → 서버 HTTP/HTTPS 접근 가능 |
| **인증** | Basic Auth 또는 API Key |

---

## 3. 기술 스택 상세

### 3.1 언어 및 런타임

```
Kotlin 2.4.21 / JVM → ART (Android Runtime)
```

**선택 근거:**
- Android TV는 JVM 기반 플랫폼 → Kotlin/JVM이 네이티브 환경
- Kotlin/Native는 iOS/임베디드용으로, Android에서는 불필요
- KMP는 멀티플랫폼 확장 시에만 의미 → TV 전용이므로 복잡도 배제

### 3.2 UI 프레임워크

| 라이브러리 | 버전 | 용도 |
|---|---|---|
| `androidx.tv:tv-material` | 1.1.0 | TV Material Design 컴포넌트 |
| Compose BOM | 2026.09.00 | Compose 버전 통합 관리 |

**선택 근거:**
- Google이 기존 Leanback 라이브러리를 **공식 deprecated** 처리
- Jetpack Compose for TV가 유일한 공식 권장 프레임워크
- 선언적 UI로 포커스 관리, 상태 처리가 간결

### 3.3 네트워크

| 라이브러리 | 버전 | 용도 |
|---|---|---|
| komga-client | 0.11.0 | Komga REST API 클라이언트 (MIT, Komelia가 쓰는 라이브러리) |
| Ktor | 3.6.1 | komga-client의 HTTP 계층, OkHttp 엔진 사용 |
| OkHttp | 5.5.1 | 인증 인터셉터. Coil 이미지 요청과 같은 클라이언트를 쓴다 |

**선택 근거:**
- API 계층을 직접 쓰지 않는다 (LEAD_REVIEW D1). 헤더(`X-API-Key`)·페이지네이션·검색 조건을 komga-client가 이미 정확히 구현한다.

### 3.4 이미지 로딩

| 라이브러리 | 버전 | 용도 |
|---|---|---|
| Coil 3 | 3.6.3 | Compose 네이티브 이미지 로딩 |

**TV 최적화 설정:**
```kotlin
ImageLoader.Builder(context)
    .memoryCache {
        MemoryCache.Builder(context)
            .maxSizePercent(0.20)      // 앱 메모리의 20%
            .build()
    }
    .diskCache {
        DiskCache.Builder()
            .maxSizeBytes(100 * 1024 * 1024)  // 100MB
            .build()
    }
    .bitmapConfig(Bitmap.Config.RGB_565)      // 메모리 50% 절감
    .crossfade(false)                          // D-pad 스크롤 시 부하 제거
    .build()
```

### 3.5 아키텍처 & DI

| 항목 | 선택 | 비고 |
|---|---|---|
| **아키텍처** | Clean Architecture + MVI | 단방향 데이터 흐름 |
| **DI** | Hilt 2.60.1 | Android 표준 DI |
| **네비게이션** | Compose Navigation 2.10.2 | Type-safe routes |
| **Lifecycle** | 2.11.0 | ViewModel, compose-lifecycle |
| **로컬 저장** | DataStore 1.3.0 | 서버 설정 (Room은 MVP 이후) |

### 3.6 빌드 도구

| 도구 | 버전 |
|---|---|
| Gradle | 9.8.2 |
| Android Gradle Plugin | 9.5.0 (내장 Kotlin) |
| KSP | 2.3.13 |
| Java Target | 17 |
| Min SDK | 30 |
| Target SDK | 36 |
| Compile SDK | 37 (platform `android-37.2`, komga-client가 minCompileSdk 37 요구) |

---

## 4. 아키텍처 설계

### 4.1 모듈 의존성 그래프

```
app
├── feature:home
│   ├── core:ui
│   ├── core:data
│   └── core:model
├── feature:library
│   ├── core:ui
│   ├── core:data
│   └── core:model
├── feature:reader
│   ├── core:ui
│   ├── core:data
│   └── core:model
├── feature:search
│   ├── core:ui
│   ├── core:data
│   └── core:model
└── feature:settings
    ├── core:ui
    ├── core:data
    └── core:model

core:data
├── core:network
└── core:model

core:ui
└── core:model

core:network
└── core:model
```

### 4.2 레이어 구조

```
┌─────────────────────────────────────────────┐
│                  UI Layer                    │
│  Compose for TV Screen + ViewModel (MVI)    │
├─────────────────────────────────────────────┤
│               Domain Layer                   │
│         Repository Interface + UseCase       │
├─────────────────────────────────────────────┤
│                Data Layer                    │
│  Repository Impl + Network + Local Cache    │
└─────────────────────────────────────────────┘
```

### 4.3 MVI 패턴

```kotlin
// 각 화면은 이 패턴을 따른다
data class ScreenState(...)           // 불변 UI 상태
sealed interface ScreenIntent { ... } // 사용자 액션
sealed interface ScreenEffect { ... } // 일회성 이벤트 (토스트, 네비게이션)

class ScreenViewModel : ViewModel() {
    private val _state = MutableStateFlow(ScreenState())
    val state: StateFlow<ScreenState> = _state.asStateFlow()

    fun onIntent(intent: ScreenIntent) { ... }
}
```

---

## 5. Komga API 연동 명세

### 5.1 핵심 API 엔드포인트

| HTTP | 엔드포인트 | 설명 | 우선순위 |
|---|---|---|---|
| GET | `/api/v1/libraries` | 라이브러리 목록 | P0 |
| GET | `/api/v1/series?page=&size=&libraryId=` | 시리즈 목록 (페이지네이션) | P0 |
| GET | `/api/v1/series/{id}` | 시리즈 상세 | P0 |
| GET | `/api/v1/series/{id}/books?page=&size=` | 시리즈의 책 목록 | P0 |
| GET | `/api/v1/books/{id}` | 책 상세 | P0 |
| GET | `/api/v1/books/{id}/pages/{page}` | **페이지 이미지 스트리밍** | **P0 (핵심)** |
| GET | `/api/v1/books/{id}/pages/{page}/thumbnail` | 페이지 썸네일 | P1 |
| PATCH | `/api/v1/books/{id}/read-progress` | 읽기 진행 업데이트 | P0 |
| GET | `/api/v1/series/{id}/thumbnail` | 시리즈 커버 이미지 | P0 |
| GET | `/api/v1/books/{id}/thumbnail` | 책 커버 이미지 | P0 |
| GET | `/api/v1/books?search=` | 검색 | P1 |
| GET | `/api/v1/collections` | 컬렉션 | P2 |
| GET | `/api/v1/readlists` | 읽기 목록 | P2 |

### 5.2 인증 흐름

```
┌──────────┐          ┌──────────────┐          ┌─────────┐
│ KomgaTV  │──Basic──▶│  OkHttp      │──HTTP───▶│ Komga   │
│ App      │  Auth    │  Interceptor │  Request │ Server  │
│          │  or      │              │          │         │
│          │  API Key │              │          │         │
└──────────┘          └──────────────┘          └─────────┘
```

```kotlin
// AuthInterceptor.kt
class AuthInterceptor(private val prefs: ServerPreferences) : Interceptor {
    override fun intercept(chain: Chain): Response {
        val request = chain.request().newBuilder().apply {
            when (val auth = prefs.authMethod) {
                is BasicAuth -> {
                    val cred = Credentials.basic(auth.username, auth.password)
                    header("Authorization", cred)
                }
                is ApiKeyAuth -> header("X-API-Key", auth.key)
            }
        }.build()
        return chain.proceed(request)
    }
}
```

### 5.3 페이지 이미지 로딩 전략

```
Komga Server                    KomgaTV App
     │                               │
     │  GET /books/{id}/pages/3      │  ← 현재 페이지
     │◀──────────────────────────────│
     │──── JPEG/PNG bytes ──────────▶│─▶ 화면 표시
     │                               │
     │  GET /books/{id}/pages/4      │  ← 다음 페이지 프리로드
     │◀──────────────────────────────│
     │──── JPEG/PNG bytes ──────────▶│─▶ 메모리 캐시
     │                               │
     │       (이전 페이지 1은          │
     │        메모리에서 해제)          │
```

**3페이지 룰:**
- 메모리에 동시 보유: 이전(N-1) + 현재(N) + 다음(N+1) 페이지만
- N+1 이전 페이지는 Coil 메모리 캐시에서 자동 퇴출
- 디스크 캐시에는 최근 열람한 책의 페이지가 LRU로 보존

---

## 6. 화면 설계

### 6.1 화면 목록

| 화면 | 라우트 | 설명 |
|---|---|---|
| **서버 설정** | `/settings` | 최초 실행 시 서버 URL, 인증 정보 입력 |
| **홈** | `/home` | 계속 읽기, 최근 추가, 라이브러리 행 |
| **라이브러리** | `/library/{id}` | 시리즈 포스터 그리드 |
| **시리즈 상세** | `/series/{id}` | 시리즈 정보 + 볼륨 목록 |
| **만화 리더** | `/reader/{bookId}` | 전체화면 페이지 뷰어 |
| **검색** | `/search` | 검색 입력 + 결과 그리드 |

### 6.2 홈 화면 레이아웃

```
┌─────────────────────────────────────────────────────┐
│  ┌───────────────────────────────────────────────┐  │
│  │              히어로 캐러셀                      │  │
│  │    (추천 시리즈 / 최근 읽던 책 배경)             │  │
│  └───────────────────────────────────────────────┘  │
│                                                     │
│  📖 계속 읽기                                        │
│  ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐     │
│  │ 커버 │ │ 커버 │ │ 커버 │ │ 커버 │ │ 커버 │ ──▶  │
│  └──────┘ └──────┘ └──────┘ └──────┘ └──────┘     │
│                                                     │
│  🆕 최근 추가                                        │
│  ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐     │
│  │ 커버 │ │ 커버 │ │ 커버 │ │ 커버 │ │ 커버 │ ──▶  │
│  └──────┘ └──────┘ └──────┘ └──────┘ └──────┘     │
│                                                     │
│  📚 라이브러리 1                                     │
│  ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐     │
│  │ 커버 │ │ 커버 │ │ 커버 │ │ 커버 │ │ 커버 │ ──▶  │
│  └──────┘ └──────┘ └──────┘ └──────┘ └──────┘     │
└─────────────────────────────────────────────────────┘
```

### 6.3 만화 리더 조작

| 입력 | 동작 |
|---|---|
| D-pad → | 다음 페이지 |
| D-pad ← | 이전 페이지 |
| D-pad 중앙 (OK) | 컨트롤 오버레이 토글 |
| D-pad ↑/↓ (오버레이) | 페이지 슬라이더 이동 |
| Back | 리더 종료 + 진행 저장 |
| 길게 누르기 | (향후) 북마크 추가 |

### 6.4 네비게이션 흐름

```
설정 ──▶ 홈 ──┬──▶ 라이브러리 ──▶ 시리즈 상세 ──▶ 리더
              ├──▶ 검색 ──▶ 시리즈 상세 ──▶ 리더
              └──▶ 계속 읽기 카드 ──▶ 리더 (바로 연결)
```

---

## 7. 데이터 흐름

### 7.1 시리즈 목록 로딩

```
사용자 액션                   ViewModel                  Repository               API
    │                           │                           │                      │
    │ 라이브러리 선택            │                           │                      │
    │──────────────────────────▶│                           │                      │
    │                           │ LoadSeries Intent         │                      │
    │                           │──────────────────────────▶│                      │
    │                           │                           │ GET /series          │
    │                           │                           │─────────────────────▶│
    │                           │                           │◀──PagedResponse──────│
    │                           │◀──Flow<List<Series>>──────│                      │
    │                           │                           │                      │
    │◀──State(series=list)──────│                           │                      │
    │  UI 렌더링 (그리드)        │                           │                      │
```

### 7.2 읽기 진행 동기화

```
리더 화면                    ViewModel                  Repository
    │                           │                           │
    │ 페이지 변경 (p.5→p.6)     │                           │
    │──────────────────────────▶│                           │
    │                           │ debounce(2초)             │
    │                           │──────────────────────────▶│
    │                           │                           │ PATCH /books/{id}/read-progress
    │                           │                           │ { "page": 6, "completed": false }
    │                           │                           │
    │ Back 버튼 (리더 종료)      │                           │
    │──────────────────────────▶│                           │
    │                           │ 즉시 동기화                │
    │                           │──────────────────────────▶│ PATCH (최종 페이지)
```

---

## 8. 보안 고려 사항

| 항목 | 구현 |
|---|---|
| **자격 증명 저장** | EncryptedSharedPreferences (AndroidX Security) |
| **네트워크** | HTTPS 권장, HTTP 허용 (로컬 네트워크 고려) |
| **API Key 보호** | 메모리 내 평문 최소화, 프로세스 종료 시 소거 |
| **인증서** | 사설 인증서(Self-signed) 허용 옵션 제공 |

---

## 9. 테스트 전략

| 레벨 | 도구 | 대상 |
|---|---|---|
| **Unit** | JUnit 5 + MockK | ViewModel, Repository, UseCase |
| **UI** | Compose Testing | TV 컴포넌트, 포커스 동작 |
| **Integration** | Hilt Testing + MockWebServer | API 연동 검증 |
| **E2E** | UiAutomator + TV 에뮬레이터 | 전체 사용자 흐름 |

---

## 10. 버전 관리 및 릴리스

| 항목 | 정책 |
|---|---|
| **브랜치 전략** | GitHub Flow (main + feature branches) |
| **커밋 컨벤션** | Conventional Commits (`feat:`, `fix:`, `docs:`, `refactor:`) |
| **버전 체계** | Semantic Versioning (0.x.y 개발기, 1.0.0 안정 릴리스) |
| **배포** | GitHub Releases → APK / AAB |
| **CI/CD** | GitHub Actions (빌드, 린트, 테스트) |
