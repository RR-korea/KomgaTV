# 릴리즈 준비 메모

정식 릴리즈(스토어 배포) 때 할 일. 개발 중에는 바꾸지 않는다.

## 패키지 이름 (applicationId)
- **`com.komgatv.app` 그대로 쓴다** (사용자 결정, 2026-11-01 — 한때 `com.rock.komga`로 바꾸기로 했다가 취소).
- 개발판·릴리즈판이 같은 이름이라 TV에 깔린 앱을 그대로 업데이트할 수 있다(서명 키가 같아야 함).

## 심사·시험용 서버 (Komga 공식 데모, 공개 계정)
- 주소: `https://demo.komga.org`
- 이메일: `demo@komga.org` / 비밀번호: `komga-demo`
- 라이브러리: Comics, eBooks (2026-11-02 로그인 확인)
- Play Console → 앱 콘텐츠 → **앱 액세스**에 이 계정을 적어 심사자가 로그인할 수 있게 한다. 개인 서버 정보는 절대 쓰지 않는다.

## 개인정보처리방침
- URL: https://rr-korea.github.io/KomgaTV/privacy/ (한·영, GitHub Pages — `.github/workflows/pages.yml`이 `docs/privacy`가 바뀔 때만 올림, `docs/privacy/index.html`)
- Play Console → 앱 콘텐츠 → 개인정보처리방침에 이 주소를 넣는다. 앱이 다루는 데이터가 바뀌면 이 페이지도 고친다.

## 서명 (업로드 키)
- 키 파일: `D:\Keys\komgatv-upload.jks` (별칭 `upload`, 2026-11-02 생성). 저장소 밖에 두고 백업한다 — 잃어버리면 Play Console에서 업로드 키 재설정을 요청해야 한다.
- 비밀번호는 어디에도 저장하지 않는다. `app/build.gradle.kts`는 환경 변수(`KOMGATV_UPLOAD_STORE_FILE`, `KOMGATV_UPLOAD_STORE_PASSWORD`, 선택 `KOMGATV_UPLOAD_KEY_ALIAS`/`KOMGATV_UPLOAD_KEY_PASSWORD`)가 있을 때만 release를 서명한다.
- 출시용 AAB: `powershell -ExecutionPolicy Bypass -File toolsuild-release.ps1` → 비밀번호를 가려서 묻고 `dist\TVReaderForKomga-v<버전>-b<빌드>-release.aab`를 만든다.
- Play 앱 서명(Play App Signing)을 켜 둔다 — 배포용 키는 Google이 보관하고, 이 키는 업로드용이다.

## 그 밖에
- 버전: `app/build.gradle.kts` 위쪽 `appVersionCode`·`appVersionName` (APK 이름에도 들어간다).
- `release` 빌드(`isMinifyEnabled = true`) 실기 확인.
- 개발자 정보: David Hojin Kim · sinabro@gmail.com · github.com/RR-korea/KomgaTV (앱 정보 화면에 있음).
- 오픈소스 고지: `THIRD_PARTY_NOTICES.md` (Komga 로고 MIT, Noto Sans OFL, komga-client MIT).
