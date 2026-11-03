# 삼성 스토어(Samsung Apps TV) 자료 — TV Reader for Komga (Tizen)

Samsung Seller Office(seller.samsungapps.com)에 앱을 등록할 때 쓰는 패키지·문구·그림. Android(Google Play) 자료(`/store`)를 타이젠에 맞게 고친 것이다.

## 패키지

| 항목 | 값 |
|---|---|
| 앱 이름 | TV Reader for Komga (모든 언어 같음) |
| 버전 | `package.json` `version`·`build` = `static/config.xml` `version` (지금 1.0.6 / b21, Android와 같은 번호) |
| Tizen 앱 ID / 패키지 ID | `KomgaTVtz0.KomgaTV` / `KomgaTVtz0` — **스토어에 올린 뒤에는 바꾸지 않는다** (바꾸면 다른 앱이 된다) |
| 최소 플랫폼 | Tizen 5.5 (2020년형 TV, Chromium 69) |
| 파일 | `dist/TVReaderForKomga-v<버전>-b<빌드>.wgt` |

만들기:

```
npm run build
node dev/package-wgt.mjs KomgaTV-samsung   # 삼성 인증서 보안 프로필로 서명
```

- 지금 `KomgaTV-samsung` 프로필의 배포자 인증서에는 **에뮬레이터 DUID만** 들어 있다. 실제 TV에 직접 설치해 시험하려면 그 TV의 DUID를 넣어 배포자 인증서를 다시 만들어야 한다 (`dev/samsung-cert/`, TV 개발자 모드).
- 스토어 제출용 서명 조건(배포자 인증서 권한 등급 Public/Partner 등)은 Seller Office에서 확인한다.

## 문구 — `listing/<언어>.txt`

`[title]`, `[short_description]`, `[full_description]`, `[release_notes]` — Android 문구와 같고 타이젠과 다른 곳만 고쳤다.
- Android TV → 삼성 스마트 TV(타이젠, 2020년형 이후)
- 로그인 정보 "암호화" 문구 삭제 — 타이젠은 앱 전용 저장 공간(localStorage)에 둔다 (Android처럼 Keystore로 암호화하지 않음)
- 음성 검색 삭제 — 타이젠 앱에는 음성 버튼이 없다 (TV 화면 키보드의 음성 입력은 TV 기능)
- "삼성 TV용 버전 출시 예정" → "구글 TV·Android TV용 버전도 있습니다"

지금은 `en-US`(기본)와 `ko-KR`만 있다. 다른 13개 언어는 Android `store/listing/`을 같은 규칙으로 고치면 된다.

## 개인정보처리방침 — `privacy/index.html`

- **URL: https://rr-korea.github.io/KomgaTV/privacy/tizen/** (한·영, GitHub Pages — main 브랜치 `docs/privacy/tizen/index.html`, 2026-11-03 게시)
- Android 방침(`/privacy/`)에서 저장 방식(앱 전용 저장 공간, 암호화 아님)·검색(음성 버튼 없음)만 타이젠에 맞게 바꿨다.
- Seller Office의 개인정보처리방침 칸에 이 주소를 넣는다. 고칠 때는 이 파일을 고치고 main의 `docs/privacy/tizen/index.html`에도 같은 내용을 넣는다.

## 그림

| 파일 | 용도 | 규격 |
|---|---|---|
| `../static/icon.png` | 앱 아이콘 | 512×423 PNG (`dev/make-icon.py`, Android 배너와 같은 그림) |
| `screenshots/en/*.jpg`, `screenshots/ko/*.jpg` | 스크린샷 | 1920×1080 JPEG 8장 |

스크린샷은 Komga 공식 데모 서버(`https://demo.komga.org`, 공개 계정 `demo@komga.org` / `komga-demo`)의 공개 예시 책으로 찍었다 — 개인 서버의 책은 쓰지 않는다.
다시 찍기: `npm run build && node dev/store-shots.mjs en` (또는 `ko`). 빌드한 앱을 PC Edge(1920×1080)로 열어 리모컨 키로 돈다. 쪽을 넘기지 않아 데모 서버의 읽은 위치를 바꾸지 않는다.

| 파일 | 화면 |
|---|---|
| `01_home` | 홈 — 읽는 중, 최근 추가된 책 |
| `02_series` | 시리즈 — 책 목록과 읽은 상태 |
| `03_reader` | 만화 한 쪽 |
| `04_overlay` | 읽는 중 정보 — 쪽 번호, 진행 막대 |
| `05_view_settings` | 작품별 보기 설정 — 두 쪽, 대비, Lanczos3 |
| `06_two_page` | 두 쪽 보기 |
| `07_epub` | EPUB 책 |
| `08_search` | 검색 |

## 심사용 계정

심사자에게는 위 데모 서버 계정을 알려 준다. 앱 첫 화면(서버 설정)의 **[Komga 데모 서버로 둘러보기]** 한 번으로 연결된다. 개인 서버 정보는 절대 쓰지 않는다.

## 올리기 전에

- **실제 2020년형 TV에서 확인하지 않았다.** 에뮬레이터(Tizen 10, Chromium 130)와 PC Edge에서만 시험했다. Chromium 69에서 되는 기능만 썼지만(eslint compat, CSS 규칙) 실기에서 홈·리더·PDF·EPUB·Lanczos(Web Worker)·데모 서버 연결을 한 번 확인한다.
- 판매 국가: 개인(Public Seller)은 미국 등 일부 국가만 가능하고 한국 출시는 삼성 파트너 계약이 필요한 것으로 보인다 — Seller Office에서 확인.
