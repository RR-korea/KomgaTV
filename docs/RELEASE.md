# 릴리즈 준비 메모

정식 릴리즈(스토어 배포) 때 할 일. 개발 중에는 바꾸지 않는다.

## 패키지 이름 (applicationId)
- 릴리즈: **`com.rock.komga`** (사용자 결정, 2026-11-01)
- 지금(개발): `com.komgatv.app` — TV·에뮬레이터에 깔린 시험판과 저장된 서버 설정을 그대로 쓰려고 유지한다.
- 바꿀 곳: `app/build.gradle.kts`의 `applicationId` 한 줄. 코드 패키지(`namespace`, `com.komgatv.*`)는 그대로 둬도 된다.
- 바꾸면 TV에서는 다른 앱으로 깔린다 — 예전 시험판은 지우고, 서버 연결은 다시 해야 한다.

## 그 밖에
- 버전: `app/build.gradle.kts` 위쪽 `appVersionCode`·`appVersionName` (APK 이름에도 들어간다).
- 서명: 릴리즈 키 만들기, `release` 빌드(`isMinifyEnabled = true`) 실기 확인.
- 개발자 정보: David Hojin Kim · sinabro@gmail.com · github.com/RR-korea/KomgaTV (앱 정보 화면에 있음).
- 오픈소스 고지: `THIRD_PARTY_NOTICES.md` (Komga 로고 MIT, Noto Sans OFL, komga-client MIT).
