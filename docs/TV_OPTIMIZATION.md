# ⚡ KomgaTV TV 성능 최적화 가이드

> Android TV 디바이스, 특히 **2GB RAM 저사양 박스**에서 원활하게 동작하기 위한 최적화 전략

---

## 1. TV 하드웨어 제약 이해

### 1.1 메모리 예산

```
2GB RAM 디바이스 기준:

┌────────────────────────────────────────────────┐
│ Android OS + System Services      ~800MB       │
│ Google TV Launcher / Home          ~200MB       │
│ ───────────────────────────────────────────     │
│ KomgaTV 앱 사용 가능               ~700MB       │
│   ├─ Coil 메모리 캐시 (20%)         ~140MB       │
│   ├─ 만화 페이지 (3장 × ~15MB)       ~45MB       │
│   ├─ UI 렌더링 + Compose              ~50MB       │
│   ├─ ViewModel + Repository 상태      ~20MB       │
│   └─ 여유 (GC, 버스트)              ~445MB       │
└────────────────────────────────────────────────┘
```

> **중요:** Android Low Memory Killer(LMK)는 메모리 부족 시 백그라운드 앱부터 강제 종료합니다. TV에서는 앱 전환이 적으므로, 자체 메모리 관리가 곧 사용자 경험입니다.

### 1.2 CPU 제약

| 특성 | 영향 |
|---|---|
| Quad-core A53/A55 @ 1.5-2GHz | 무거운 이미지 디코딩 시 UI 프레임 드롭 |
| GPU Mali-G31 (저사양) | 복잡한 애니메이션 동시 실행 시 버벅임 |
| Single-task 환경 | 앱이 포그라운드 대부분 → 풀 리소스 활용 가능 |

---

## 2. 이미지 메모리 최적화

### 2.1 Bitmap Config 선택

```kotlin
// ❌ 기본값 (ARGB_8888) — 4 bytes/pixel
// 1920×1080 이미지 = 1920 × 1080 × 4 = 8.3MB

// ✅ RGB_565 — 2 bytes/pixel
// 1920×1080 이미지 = 1920 × 1080 × 2 = 4.1MB (50% 절감!)
.bitmapConfig(Bitmap.Config.RGB_565)
```

| Config | Bytes/Pixel | 1080p 페이지 | 용도 |
|---|---|---|---|
| ARGB_8888 | 4 | ~8.3 MB | 투명도 필요 시 |
| RGB_565 | 2 | ~4.1 MB | **만화 페이지 (추천)** |
| HARDWARE | GPU 메모리 | N/A | 썸네일 (GPU 직접 렌더) |

### 2.2 다운샘플링

```kotlin
// 화면 해상도에 맞춰 이미지를 축소 로딩
val request = ImageRequest.Builder(context)
    .data(pageUrl)
    .size(
        width = screenWidth,    // TV 실제 표시 크기
        height = screenHeight
    )
    .precision(Precision.EXACT)  // 정확한 크기로 디코딩
    .build()
```

> 4000×6000 원본 만화 페이지를 1080p TV에 표시할 때, 다운샘플링 없이 로딩하면 ~96MB. 화면 크기로 다운샘플하면 ~4MB. **24배 차이!**

### 2.3 3페이지 메모리 관리

```
페이지 이동 시 메모리 상태:

현재 위치: 5페이지

메모리에 유지:
  ┌─────┐  ┌─────┐  ┌─────┐
  │ p.4 │  │ p.5 │  │ p.6 │
  │ 이전 │  │현재 │  │ 다음 │
  └─────┘  └─────┘  └─────┘

6페이지로 이동 시:
  p.4 해제
  ┌─────┐  ┌─────┐  ┌─────┐
  │ p.5 │  │ p.6 │  │ p.7 │  ← p.7 프리로드
  │ 이전 │  │현재 │  │ 다음 │
  └─────┘  └─────┘  └─────┘
```

---

## 3. UI 렌더링 최적화

### 3.1 포커스 변경 시 리컴포지션 최소화

```kotlin
// ❌ 나쁜 예: 포커스마다 ImageRequest 재생성
@Composable
fun BadCard(url: String) {
    var focused by remember { mutableStateOf(false) }
    Box(Modifier.onFocusChanged { focused = it.hasFocus }) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(url)  // 매 recomposition마다 새 객체 생성!
                .build(),
            ...
        )
    }
}

// ✅ 좋은 예: ImageRequest를 remember로 캐싱
@Composable
fun GoodCard(url: String) {
    var focused by remember { mutableStateOf(false) }
    val request = remember(url) {  // url이 바뀔 때만 재생성
        ImageRequest.Builder(context)
            .data(url)
            .size(300, 450)
            .build()
    }
    Box(Modifier.onFocusChanged { focused = it.hasFocus }) {
        AsyncImage(model = request, ...)
    }
}
```

### 3.2 LazyGrid 최적화

```kotlin
// TV 그리드에서 빠른 D-pad 스크롤 시 최적화
TvLazyVerticalGrid(
    columns = TvGridCells.Fixed(6),  // 고정 컬럼 (계산 부하 제거)
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(40.dp),
) {
    items(
        count = series.size,
        key = { series[it].id },  // 안정적 key → 불필요한 recomposition 방지
    ) { index ->
        PosterCard(
            series = series[index],
            modifier = Modifier.animateItem()  // 부드러운 아이템 전환
        )
    }
}
```

### 3.3 애니메이션 절제

| 상황 | 권장 | 비권장 |
|---|---|---|
| 포커스 변경 | `scale(1.0 → 1.05)` + border | 복잡한 3D 회전, 그림자 |
| 이미지 로딩 | 즉시 표시 (crossfade 끔) | 긴 crossfade |
| 화면 전환 | 심플 fade | 복잡한 shared element |
| 카드 하이라이트 | 단색 border glow | 실시간 blur/glassmorphism |

```kotlin
// 포커스 애니메이션: 가벼운 scale + border만 사용
val scale by animateFloatAsState(
    targetValue = if (focused) 1.05f else 1.0f,
    animationSpec = tween(durationMillis = 150)  // 빠르게
)

val borderColor by animateColorAsState(
    targetValue = if (focused) Color.White else Color.Transparent,
    animationSpec = tween(durationMillis = 150)
)
```

---

## 4. 네트워크 최적화

### 4.1 페이지네이션

```kotlin
// 시리즈 목록: 페이지당 20개씩 로딩
interface KomgaApi {
    @GET("api/v1/series")
    suspend fun getSeries(
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20,  // TV 그리드 한 화면분
        @Query("library_id") libraryId: String? = null,
    ): PagedResponse<SeriesDto>
}
```

### 4.2 썸네일 크기 요청

```kotlin
// Komga는 썸네일 크기 파라미터를 지원
// 서버에서 리사이즈된 이미지를 받아 대역폭 + 메모리 절감
@GET("api/v1/series/{seriesId}/thumbnail")
suspend fun getSeriesThumbnail(
    @Path("seriesId") seriesId: String,
    @Query("convert") convert: String = "jpeg",
    @Query("quality") quality: Int = 75,
): ResponseBody
```

### 4.3 OkHttp 캐시

```kotlin
// HTTP 캐시로 반복 요청 절감
val okHttpClient = OkHttpClient.Builder()
    .cache(Cache(
        directory = context.cacheDir.resolve("http_cache"),
        maxSize = 50L * 1024 * 1024  // 50MB HTTP 캐시
    ))
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)  // 큰 이미지 전송 대비
    .build()
```

---

## 5. 메모리 누수 방지

### 5.1 TV 특수 상황

TV 앱은 일반 모바일 앱과 달리 **수 시간 연속 실행**되는 경우가 많습니다:
- 만화 연속 읽기 세션 (1~3시간)
- 백그라운드 대기 후 재진입
- Sleep 후 Wake-up

### 5.2 체크리스트

| 항목 | 대응 |
|---|---|
| ViewModel Scope | `viewModelScope`만 사용, 수동 Scope 금지 |
| Flow 수집 | `collectAsStateWithLifecycle()` 사용 |
| Context 참조 | Activity Context 직접 보유 금지 → Application Context |
| 이미지 리스너 | Coil의 lifecycle-aware 로딩 활용 |
| Room DB | 쿼리 완료 후 Cursor 자동 해제 확인 |
| WebView (향후) | EPUB 리더 시 `destroy()` 명시 호출 |

### 5.3 메모리 모니터링

```kotlin
// 디버그 빌드에서 메모리 상태 로깅
if (BuildConfig.DEBUG) {
    val runtime = Runtime.getRuntime()
    val usedMB = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024
    val maxMB = runtime.maxMemory() / 1024 / 1024
    Timber.d("Memory: ${usedMB}MB / ${maxMB}MB (${usedMB * 100 / maxMB}%)")
}
```

---

## 6. I/O 스레딩

### 6.1 스레드 할당

| 작업 | Dispatcher | 이유 |
|---|---|---|
| API 호출 | `Dispatchers.IO` | 네트워크 블로킹 |
| 이미지 디코딩 | `Dispatchers.IO` → Coil 내부 | CPU 집약적 |
| UI 렌더링 | `Dispatchers.Main` | Compose 렌더 루프 |
| 캐시 쓰기 | `Dispatchers.IO` | 디스크 I/O |
| 읽기 진행 저장 | `Dispatchers.IO` + debounce | 빈번한 업데이트 배치 |

### 6.2 UI 스레드 보호

```kotlin
// ❌ 메인 스레드에서 이미지 디코딩
val bitmap = BitmapFactory.decodeStream(inputStream) // ANR 위험!

// ✅ IO 디스패처에서 처리
val bitmap = withContext(Dispatchers.IO) {
    BitmapFactory.decodeStream(inputStream)
}
```

---

## 7. 성능 목표

| 지표 | 목표 | 측정 방법 |
|---|---|---|
| **앱 시작** | 2초 이내 (Cold Start) | `adb shell am start -W` |
| **홈 화면 로딩** | 1초 이내 | 네트워크 + 렌더 합산 |
| **페이지 전환** | 200ms 이내 | D-pad 입력 → 화면 갱신 |
| **그리드 스크롤** | 60fps 유지 | GPU 프로파일러 |
| **메모리 사용** | 200MB 이하 (정상 상태) | Android Studio Profiler |
| **메모리 피크** | 400MB 이하 (리더 사용 중) | Android Studio Profiler |
| **네트워크** | 썸네일 50KB, 페이지 500KB 이하 | OkHttp 로깅 |
| **디스크 캐시** | 200MB 이하 (기본 설정) | 설정 화면 표시 |

---

## 8. 디바이스별 테스트 매트릭스

| 디바이스 | RAM | SoC | 해상도 | 우선순위 |
|---|---|---|---|---|
| **Android TV 에뮬레이터** | 2GB | x86_64 | 1080p | P0 (개발용) |
| **Chromecast with Google TV (4K)** | 2GB | S905X4 | 4K | P0 (기준 디바이스) |
| **Chromecast with Google TV (HD)** | 1.5GB | S805X2 | 1080p | P0 (저사양 기준) |
| **NVIDIA Shield TV** | 3GB | Tegra X1+ | 4K | P1 (고사양 검증) |
| **Xiaomi Mi Box S** | 2GB | S905X | 4K | P1 (보급형) |
| **Samsung TV (Tizen→ATV)** | 다양 | 다양 | 4K | P2 (향후) |
