# Google Play 스토어 자료

Play Console → **스토어 등록정보**에 올릴 문구와 그림. 스크린샷은 모두 Komga 공식 데모 서버(`https://demo.komga.org`, 공개 계정 `demo@komga.org` / `komga-demo`)의 공개 예시 책으로 찍었다 — 개인 서버의 책은 쓰지 않는다.

## 문구 — `listing/<언어>.txt`

| 파일 칸 | Play Console 칸 | 한도 |
|---|---|---|
| `[title]` | 앱 이름 | 30자 (모든 언어 "TV Reader for Komga") |
| `[short_description]` | 간단한 설명 | 80자 |
| `[full_description]` | 자세한 설명 | 4000자 |
| `[release_notes]` | 출시 노트 (버전마다) | 500자 |

- 원본은 `en-US.txt`(기본 언어)와 `ko-KR.txt`. 다른 언어는 이 둘을 옮긴 것이다.
- 파일 이름은 Play Console 언어 코드: `zh-CN`, `zh-TW`, `ja-JP`, `fr-FR`, `de-DE`, `es-ES`, `it-IT`, `ru-RU`, `pt-BR`, `pl-PL`, `tr-TR`, `id`, `vi`.
- 앱 화면 이름("Komga 데모 서버로 둘러보기", "읽는 중" 등)은 앱 번역과 같은 말을 쓴다. 앱 문구를 바꾸면 여기도 고친다.

## 그림 — `graphics/`

| 파일 | Play Console 칸 | 규격 |
|---|---|---|
| `app_icon_512.png` | 앱 아이콘 | 512×512 PNG |
| `feature_graphic_1024x500.png` | 그래픽 이미지 | 1024×500 |
| `tv_banner_1280x720.png` | TV 배너 | 1280×720 |

`python store/make_store_graphics.py <NotoSans-Bold.ttf> <NotoSans-Regular.ttf>`로 다시 만든다 (Windows: `C:/Windows/Fonts/NotoSans-*.ttf`). 표시·배너는 `docs/branding/make_banner.py`와 같은 그림이다.

## 스크린샷 — `screenshots/ko`, `screenshots/en`

Android TV 스크린샷 칸에 올린다 (1920×1080 JPEG, 투명 없음, 최대 8장).

| 파일 | 화면 |
|---|---|
| `01_home` | 홈 — 읽는 중, 최근 추가된 책 |
| `02_series` | 시리즈 — 도서 목록과 읽은 상태 |
| `03_reader` | 만화 한 쪽 보기 |
| `04_overlay` | 읽는 중 메뉴 — 쪽 번호, 진행 막대 |
| `05_view_settings` | 작품별 보기 설정 — 두 쪽, 대비, Lanczos3 |
| `06_two_page` | 두 쪽 보기 |
| `07_epub` | EPUB 책 |
| `08_search` | 검색 |

- 한국어 등록정보에는 `ko`, 나머지 언어에는 `en`을 쓴다 (`03_reader`, `07_epub`는 앱 글자가 없어 두 폴더가 같다).
- 다시 찍을 때: 에뮬레이터 앱 언어를 `adb shell cmd locale set-app-locales com.komgatv.app --locales en-US`로 바꾸고, 서버 설정의 [Komga 데모 서버로 둘러보기]로 데모에 연결한다. 끝나면 언어와 서버를 되돌린다.
