# 📺 KomgaTV

> **[Komga](https://komga.org/) 서버를 위한 Android TV 전용 클라이언트**

거실 TV에서 만화, 웹툰, e북 라이브러리를 리모컨 하나로 탐색하고 읽을 수 있는 앱입니다.

![Android TV](https://img.shields.io/badge/Android_TV-11+-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.4.21-7F52FF?logo=kotlin&logoColor=white)
![Compose](https://img.shields.io/badge/Jetpack_Compose_for_TV-1.1.0-4285F4?logo=jetpackcompose&logoColor=white)
![License](https://img.shields.io/badge/License-MIT-blue)

---

## ✨ 주요 기능

| 기능 | 설명 |
|---|---|
| 📚 **라이브러리 탐색** | 시리즈 그리드, 필터, 정렬 — 리모컨 D-pad로 자연스럽게 |
| 🖼️ **만화/웹툰 리더** | 전체화면 페이지 뷰어, D-pad 좌/우로 페이지 넘기기 |
| 🔖 **읽기 진행 동기화** | Komga 서버와 실시간 읽기 위치 동기화 |
| 🏠 **TV 최적화 홈** | 계속 읽기, 최근 추가, 라이브러리별 카테고리 행 |
| 🔍 **검색** | 시리즈/책 제목 검색, 음성 검색 지원 |
| ⚡ **저사양 최적화** | 2GB RAM TV 박스에서도 원활하게 동작 |

---

## 🏗️ 기술 스택

| 영역 | 기술 |
|---|---|
| **언어** | Kotlin 2.4.21 (JVM) |
| **UI** | Jetpack Compose for TV (`tv-material`) + 표준 Lazy 레이아웃 |
| **이미지** | Coil 3 (RGB_565 모드, TV 최적화) |
| **네트워크** | [komga-client](https://github.com/Snd-R/komga-client) (Ktor, OkHttp 엔진 — Coil과 OkHttpClient 공유) |
| **아키텍처** | Clean Architecture + MVI |
| **DI** | Hilt |
| **네비게이션** | Compose Navigation |
| **비동기** | Kotlin Coroutines + Flow |
| **로컬 저장** | DataStore (서버 설정) |
| **로그** | Timber |
| **빌드** | Gradle 9.8.2, AGP 9.5.0 (내장 Kotlin), Version Catalog, `build-logic` 컨벤션 플러그인 |

자세한 기술 사양은 [TECH_SPEC.md](docs/TECH_SPEC.md)를 참조하세요.

---

## 📐 프로젝트 구조

```
KomgaTV/
├── app/                          # 앱 모듈 (진입점)
│   └── src/main/
│       ├── AndroidManifest.xml
│       └── java/com/komgatv/app/
│           ├── KomgaTVApp.kt     # Application (Hilt, Coil 초기화)
│           ├── MainActivity.kt   # TV 런처 Activity
│           ├── navigation/       # NavHost, 라우팅
│           └── ui/theme/         # TV 다크 테마
│
├── core/                         # 공유 핵심 모듈
│   ├── model/                    # 도메인 모델 (Library, Series, Book)
│   ├── network/                  # Komga REST API 클라이언트
│   ├── datastore/                # 서버 설정 저장 (DataStore)
│   ├── data/                     # Repository (komga-client → 도메인 모델)
│   └── ui/                       # 공유 TV 컴포넌트 (PosterCard, TVRow)
│
├── feature/                      # 화면별 기능 모듈
│   ├── home/                     # 홈 화면 (히어로 배너, 카테고리 행)
│   ├── library/                  # 라이브러리/시리즈 그리드
│   ├── reader/                   # 만화 페이지 리더
│   ├── search/                   # 검색
│   └── settings/                 # 서버 연결, 캐시 관리
│
└── docs/                         # 프로젝트 문서
    ├── TECH_SPEC.md              # 기술 사양서
    ├── ARCHITECTURE.md           # 아키텍처 상세
    └── TV_OPTIMIZATION.md        # TV 성능 최적화 가이드
```

---

## 🔧 빌드 및 실행

### 요구 사항

- **JDK** 17+ (21 권장)
- **Android SDK** — compileSdk 37 (platform `android-37.2`), targetSdk 36, minSdk 30
- 실행 대상: Android TV 에뮬레이터 또는 실제 TV 디바이스

### 빌드

```bash
git clone https://github.com/RR-korea/KomgaTV.git
cd KomgaTV
./gradlew assembleDebug
```

### 실행

1. Android Studio에서 프로젝트 열기
2. TV 에뮬레이터 생성 (API 30+, TV 프로필)
3. `app` 모듈 선택 후 실행

---

## 🔌 Komga 서버 연동

KomgaTV는 Komga 서버의 REST API를 사용합니다.

### 지원 API

- **라이브러리** — 라이브러리 목록 조회
- **시리즈** — 시리즈 목록, 검색, 필터, 메타데이터
- **책/이슈** — 볼륨 목록, 페이지 스트리밍
- **읽기 진행** — 페이지 위치 저장/동기화
- **썸네일** — 시리즈/책 커버 이미지

### 인증

| 방식 | 설명 |
|---|---|
| **Basic Auth** | ID/비밀번호 (HTTP Basic) |
| **API Key** | `X-API-Key` 헤더 |

> Komga 서버 0.100+ 필요. Swagger UI: `http://<서버>:<포트>/swagger-ui.html`

---

## 🗺️ 로드맵

### Phase 1 — MVP (현재)
- [x] 프로젝트 구조 수립
- [ ] 서버 연결 설정 화면
- [ ] 라이브러리 / 시리즈 탐색
- [ ] 기본 만화 리더 (페이지 넘기기)
- [ ] 읽기 진행 동기화

### Phase 2 — 핵심 기능
- [ ] 홈 화면 (계속 읽기, 최근 추가)
- [ ] 검색 (텍스트 + 음성)
- [ ] 시리즈 상세 화면 (메타데이터)
- [ ] 리더 향상 (줌, 더블 페이지)

### Phase 3 — 고급 기능
- [ ] EPUB 리더 지원
- [ ] 컬렉션 / 읽기 목록
- [ ] 오프라인 다운로드 (선별적)
- [ ] OPDS 피드 지원
- [ ] 다중 서버 관리

---

## 🤝 기여하기

기여를 환영합니다! [CONTRIBUTING.md](CONTRIBUTING.md)를 참조해 주세요.

1. Fork
2. Feature 브랜치 생성 (`git checkout -b feature/amazing-feature`)
3. 커밋 (`git commit -m 'feat: Add amazing feature'`)
4. Push (`git push origin feature/amazing-feature`)
5. Pull Request 생성

---

## 📄 라이선스

MIT License — [LICENSE](LICENSE) 참조

---

## 🙏 참고 및 감사

- [Komga](https://github.com/gotson/komga) — 셀프호스팅 만화/e북 미디어 서버
- [Komelia](https://github.com/Snd-R/Komelia) — Komga 크로스플랫폼 클라이언트 (아키텍처 참조)
- [KMReader](https://github.com/kmworks/kmreader) — Apple TV용 Komga 리더 (TV UX 참조)
- [Jetpack Compose for TV](https://developer.android.com/training/tv/playback/compose) — Google 공식 TV UI 프레임워크
