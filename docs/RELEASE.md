# 릴리즈 준비 메모

정식 릴리즈(스토어 배포) 때 할 일. 개발 중에는 바꾸지 않는다.

## 패키지 이름 (applicationId)
- **`com.komgatv.app` 그대로 쓴다** (사용자 결정, 2026-11-01 — 한때 `com.rock.komga`로 바꾸기로 했다가 취소).
- 개발판·릴리즈판이 같은 이름이라 TV에 깔린 앱을 그대로 업데이트할 수 있다(서명 키가 같아야 함).

## 그 밖에
- 버전: `app/build.gradle.kts` 위쪽 `appVersionCode`·`appVersionName` (APK 이름에도 들어간다).
- 서명: 릴리즈 키 만들기, `release` 빌드(`isMinifyEnabled = true`) 실기 확인.
- 개발자 정보: David Hojin Kim · sinabro@gmail.com · github.com/RR-korea/KomgaTV (앱 정보 화면에 있음).
- 오픈소스 고지: `THIRD_PARTY_NOTICES.md` (Komga 로고 MIT, Noto Sans OFL, komga-client MIT).
