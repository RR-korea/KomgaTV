# 🏛️ KomgaTV 아키텍처 문서

---

## 1. 설계 원칙

| 원칙 | 설명 |
|---|---|
| **TV First** | 모든 UI/UX 결정은 10-foot 거리에서 리모컨으로 조작하는 환경을 전제 |
| **단방향 데이터 흐름** | MVI 패턴으로 상태 예측 가능성 확보 |
| **모듈 분리** | 기능(feature) 단위 모듈로 빌드 시간 최적화, 관심사 분리 |
| **메모리 우선** | 2GB RAM 디바이스를 기준으로 모든 이미지·캐시 전략 수립 |
| **오프라인 관용** | 네트워크 불안정 시 캐시된 데이터로 탐색 가능 |

---

## 2. 모듈 구조 상세

### 2.1 Core 모듈

#### `core:model`
```
도메인 모델 (순수 Kotlin data class)
의존성: kotlinx.serialization만
```
- `Library`, `Series`, `SeriesMetadata`
- `Book`, `BookMetadata`, `ReadProgress`
- `Page`, `PagedResponse<T>`

#### `core:network`
```
Komga REST API 클라이언트
의존성: Retrofit, OkHttp, kotlinx.serialization, Hilt
```
- `KomgaApi` — Retrofit 인터페이스
- `dto/` — API 응답 DTO (서버 JSON 매핑)
- `di/NetworkModule` — OkHttp, Retrofit, API 인스턴스 제공
- `AuthInterceptor` — Basic Auth / API Key 주입

#### `core:data`
```
Repository 패턴 + 로컬 캐시
의존성: core:network, core:model, Room, DataStore, Hilt
```
- `repository/` — LibraryRepository, SeriesRepository, BookRepository
- `mapper/` — DTO ↔ Domain 모델 변환
- `preferences/ServerPreferences` — 서버 URL, 인증 정보
- `cache/` — Room DB (시리즈·책 메타데이터 오프라인 캐시)

#### `core:ui`
```
공유 TV UI 컴포넌트
의존성: Compose for TV, Coil, core:model
```
- `PosterCard` — 포커스 애니메이션 포함 포스터 카드
- `TVRow` — 타이틀 + 가로 스크롤 행 (TvLazyRow)
- `LoadingIndicator` — TV에 적합한 로딩 표시
- `ErrorScreen` — 에러 화면 (재시도 버튼, 포커스)
- `theme/` — TV 다크 테마, 타이포그래피, 색상 팔레트

### 2.2 Feature 모듈

각 feature 모듈은 동일한 구조를 따른다:

```
feature:xxx/
├── XxxScreen.kt          # Composable 화면
├── XxxViewModel.kt       # MVI ViewModel
├── XxxUiState.kt         # 상태 정의 (선택적, ViewModel에 내장 가능)
└── navigation/
    └── XxxNavigation.kt  # NavGraphBuilder 확장 함수
```

#### `feature:home` — 홈 화면
- ImmersiveList / Carousel (히어로 배너)
- "계속 읽기" 행 (최근 읽은 책 + 진행률 표시)
- "최근 추가" 행
- 라이브러리별 행

#### `feature:library` — 라이브러리 탐색
- 시리즈 포스터 그리드 (TvLazyVerticalGrid)
- 무한 스크롤 페이지네이션
- 필터/정렬 옵션 (사이드 패널)

#### `feature:reader` — 만화 리더
- 전체화면 페이지 표시 (Coil AsyncImage)
- D-pad 페이지 넘기기
- 3페이지 메모리 관리 (PageManager)
- 컨트롤 오버레이 (페이지 번호, 슬라이더, 나가기)
- 읽기 진행 자동 저장 (debounce 2초)

#### `feature:search` — 검색
- 온스크린 키보드 입력
- 음성 검색 인텐트 지원
- 검색 결과 그리드

#### `feature:settings` — 설정
- 서버 URL, 인증 방식 설정
- 연결 테스트
- 캐시 용량 표시 및 삭제
- 이미지 품질 설정

---

## 3. MVI 상태 관리

### 3.1 패턴 구조

```kotlin
// ── State ──
data class HomeUiState(
    val isLoading: Boolean = true,
    val continueReading: List<Book> = emptyList(),
    val recentlyAdded: List<Series> = emptyList(),
    val libraries: List<Library> = emptyList(),
    val error: String? = null
)

// ── Intent (사용자 액션) ──
sealed interface HomeIntent {
    data object LoadHome : HomeIntent
    data class OpenSeries(val seriesId: String) : HomeIntent
    data class ContinueReading(val bookId: String) : HomeIntent
    data object Refresh : HomeIntent
}

// ── Effect (일회성 이벤트) ──
sealed interface HomeEffect {
    data class NavigateToSeries(val seriesId: String) : HomeEffect
    data class NavigateToReader(val bookId: String) : HomeEffect
    data class ShowError(val message: String) : HomeEffect
}
```

### 3.2 데이터 흐름

```
User Action
    │
    ▼
┌─────────┐     ┌──────────────┐     ┌────────────┐
│  Screen  │────▶│  ViewModel   │────▶│ Repository │
│ (Compose)│     │  (MVI)       │     │  (Data)    │
│          │◀────│              │◀────│            │
└─────────┘     └──────────────┘     └────────────┘
   State            Intent              Flow<Data>
   Effect           reduce()
```

---

## 4. 이미지 파이프라인

### 4.1 이미지 유형별 전략

| 유형 | 소스 | 크기 전략 | 캐시 |
|---|---|---|---|
| **시리즈 커버** | `/series/{id}/thumbnail` | 300×450 리사이즈 | 메모리 + 디스크 |
| **책 커버** | `/books/{id}/thumbnail` | 200×300 리사이즈 | 메모리 + 디스크 |
| **만화 페이지** | `/books/{id}/pages/{n}` | 화면 해상도에 맞춤 | 메모리(3장) + 디스크 |
| **페이지 썸네일** | `/books/{id}/pages/{n}/thumbnail` | 160×240 | 디스크만 |

### 4.2 PageManager (리더 전용)

```kotlin
class PageManager(
    private val bookId: String,
    private val totalPages: Int,
    private val imageLoader: ImageLoader,
) {
    private var currentPage = 0

    // 현재 ± 1 페이지만 메모리에 유지
    fun preload(page: Int) {
        val targets = listOf(page - 1, page, page + 1)
            .filter { it in 0 until totalPages }

        targets.forEach { p ->
            imageLoader.enqueue(
                ImageRequest.Builder(context)
                    .data(buildPageUrl(bookId, p))
                    .size(screenWidth, screenHeight)
                    .build()
            )
        }
    }
}
```

---

## 5. 에러 처리

### 5.1 에러 분류

| 분류 | 예시 | UI 처리 |
|---|---|---|
| **네트워크** | 타임아웃, DNS 실패 | "서버에 연결할 수 없습니다" + 재시도 |
| **인증** | 401 Unauthorized | "로그인 정보를 확인하세요" → 설정 이동 |
| **서버** | 500 Internal Error | "서버 오류" + 재시도 |
| **데이터** | 빈 라이브러리, 404 | 빈 상태 화면 표시 |
| **로컬** | 캐시 손상, 디스크 부족 | 캐시 초기화 안내 |

### 5.2 Result 래퍼

```kotlin
sealed interface Result<out T> {
    data class Success<T>(val data: T) : Result<T>
    data class Error(val exception: Throwable) : Result<Nothing>
    data object Loading : Result<Nothing>
}
```

---

## 6. 향후 확장 고려

| 항목 | 현재 | 향후 |
|---|---|---|
| **EPUB 리더** | 미지원 | WebView 기반 reflowable 리더 추가 |
| **OPDS** | 미지원 | 다른 OPDS 서버 연동 가능성 |
| **다중 서버** | 단일 서버 | 서버 목록 관리, 전환 |
| **오프라인** | 캐시만 | 선별적 다운로드 + Room 저장 |
| **위젯** | 없음 | TV 홈 채널 추천 행 |
