# HANDOFF — 다른 세션·다른 PC에서 이어 하기

마지막 갱신: 2026-11-19 · 기준 버전 **1.0.41 (b56)** — main 에 병합·push 끝

이 파일만 읽고 이어서 일할 수 있게 쓴다. 자세한 규칙은 `TASKS.md`(공통 규칙), 릴리즈는 `docs/RELEASE.md`, 설계는 `docs/ARCHITECTURE.md`.

---

## 0. 꼭 지킬 규칙 (사용자 지시)

| 규칙 | 내용 |
|---|---|
| 한국어 | 사용자에게 보이는 답변·진행 보고·질문은 **모두 한국어**. 서브에이전트·안티그래비티(agy) 지시문에도 "답변·보고서는 한국어로"를 넣는다 (2026-11-15). 코드·커밋 제목은 영어여도 된다. |
| 비밀 정보 | 사용자 Komga 계정·API 키를 파일·메모리에 저장하지 않는다. 키스토어 비밀번호는 채팅에 쓰지 않고 환경 변수로만. GitHub PAT 저장 금지. 공개 데모 계정(`demo.komga.org`, `docs/RELEASE.md`)만 적어 둬도 된다. |
| 읽기 진행 | 테스트로 사용자 서버의 읽기 진행·작품 설정을 바꾸면 **원래대로 되돌린다**. "읽는 중에서 빼기 [제거]"는 실제 서버에서 누르지 않는다. 앱 데이터 `pm clear` 금지. |
| 스포일러 | **킹덤은 사용자가 지금 읽는 권(홈 "읽는 중" — 11-15 59권, 11-16 64권, **11-19 71권**)과 그 뒤 권은 열지도 풀지도 않는다.** 그 앞 권은 써도 된다. 보고서에 줄거리·대사를 쓰지 않는다. |
| 에뮬레이터 | adb 는 늘 `-s emulator-5556` (다른 프로젝트가 5554를 쓴다). 다른 세션이 에뮬레이터를 쓰고 있으면(다른 앱이 앞에 있으면) 조작하지 않는다. 다른 세션의 프로세스를 끄지 않는다. |
| 스토어 그림 | 사용자의 개인 책으로 스토어 스크린샷을 만들지 않는다 (데모 서버로). |
| 작업 흐름 | 기능 브랜치 → `main` 에 `--no-ff` 병합 → push. 빌드마다 `app/build.gradle.kts` 위 `appVersionCode`(+1)·`appVersionName`(1.0.x) 올리고, 빌드 뒤 `app\build\outputs\apk\debug` 를 탐색기로 연다. |
| GitHub Actions | 2026-11-17 사용자 "github actions 에서 돌아가는게 없게 정리해줘" — RR-korea 의 워크플로가 있는 저장소 6개(Autopilot, KomgaTV, kimchicraft, KimchiTower, XboxKit, iso2god-rs-GUI) 모두 설정에서 Actions 를 껐다. 다시 켜지 않는다 (개인정보처리방침 페이지 갱신이 필요하면 사용자에게 먼저 묻는다 — `docs/RELEASE.md`). |
| 잡일 | 할 수 있는 잡일(조사·대량 확인·단순 수정)은 안티그래비티(agy)에게 맡기고, 리드(Claude)는 지시·검토·병합을 한다. agy 결과는 반드시 직접 확인한다 — 숫자 판정을 믿지 말고 그림을 본다. |

---

## 1. 환경

- 저장소: `D:\Project\KomgaTV` → github.com/RR-korea/KomgaTV (main 이 배포 기준). Tizen 판은 `D:\Project\KomgaTV-tizen`(별도 세션, `tizen/` 만).
- 빌드:
  ```sh
  export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-21.0.13.9-hotspot"
  ./gradlew assembleDebug lint testDebugUnitTest -q
  ```
  APK: `app\build\outputs\apk\debug\TVReaderForKomga-v<이름>-b<코드>-debug.apk`. lint 는 `*/build/reports/lint-results-debug.txt` 를 직접 확인 (지금 0 errors; 남은 경고는 AGP 새 버전 알림, ReaderSettingsPanel Modifier 순서, lifecycle lint.jar 경고 — 모두 예전부터).
- 번역 확인: `PYTHONIOENCODING=utf-8 python tools/check_translations.py` → "문제 없음" (UI 15개 언어, 새 문자열은 모든 `values-*` 에).
- 출시 AAB: `powershell -ExecutionPolicy Bypass -File tools\build-release.ps1` (키 `D:\Keys\komgatv-upload.jks`, 비밀번호는 묻는 창에만).
- 에뮬레이터 (Android TV API 36, 한국어):
  ```sh
  cd /d/Project/kimchi/tools/android-sdk && ./emulator/emulator.exe -avd KomgaTV_API36 -port 5556 -no-snapshot-save -gpu swiftshader_indirect
  ADB=/d/Project/kimchi/tools/android-sdk/platform-tools/adb.exe   # 늘 -s emulator-5556
  ```
  화면 캡처 `adb exec-out screencap -p`, 키 `adb shell input keyevent KEYCODE_DPAD_*`. 에뮬레이터 앱은 사용자 서버에 붙어 있다 — 진행·설정 되돌리기 규칙을 지킨다.
- 안티그래비티: `C:\Users\sokch\AppData\Local\agy\bin\agy -p "<지시문>" --add-dir <폴더> --dangerously-skip-permissions --print-timeout 0`
  (명령은 agy 경로로 시작해야 허용 규칙에 맞는다. agy 안의 백그라운드 작업은 차례가 끝나면 죽으니 "모든 명령은 포그라운드로"를 지시문에 넣는다.)
  작업마다 git worktree(`D:\Project\KomgaTV-<이름>`, 브랜치 `task/<이름>`)를 따로 만들어 준다.

## 2. 배포 상태

- **Google Play:** 1.0.6 (b21) 프로덕션 출시됨 (11-03 제출, 사용자 11-15 "시간이 걸렸지만 성공"). 그 뒤 버전은 아직 Play 에 안 올렸다 — 올릴 때 `docs/RELEASE.md`.
- **사용자 TV:** 사용자가 디버그 APK 를 직접 깔아 쓴다. 최신 전달: **1.0.41 (b56)**.
- Samsung Tizen: 별도 세션 (`D:\Project\KomgaTV-tizen`, 메모리 komgatv-tizen-session).

## 3. 최근 바뀐 것 (1.0.31 → 1.0.41)

| 버전 | 내용 |
|---|---|
| 1.0.31 | 리모컨 종류와 상관없이 OK 길게 누르기(시간을 직접 잼, `SelectKeyGuard.selectKeyClicks`). "칸 겹치기 (실험적)" 1~10%. |
| 1.0.32 | 칸 간격 없애기: 킹덤 조사 결과(좁은 여백, 검은 바탕 쪽) + 끝 띠·첫 띠를 이웃 띠 쪽으로 붙임(킹덤 34권 176쪽, 사용자 제보). 다른 만화에서 그림을 자르던 1차 변경 셋은 되돌림. |
| 1.0.33 | **테두리 잘라내기** (꺼짐/상하/좌우/상하좌우, 확대 바로 아래). **자동 채색 제거** (코드는 `archive/colorize` 브랜치에 보관 — 사용자 "나중에 테스트 해볼수 있도록 보관"), ONNX Runtime 빠져 APK 87MB → 45MB, 받아 둔 채색 모델은 앱 시작 때 지움. |
| 1.0.34 | 홈 "다음 권"(On deck) 줄 제거 (사용자 "무쓸모"). |
| 1.0.35 | 칸 간격 2차 (칸 밖 화 제목·글 문단·테두리 없는 그림·옅은 벽 그림 보호, 세로 긴 칸 안 자르기 방지 — cacheKey gutters-23). 테두리 잘라내기 검증 반영 (검은 바탕 스캔 빛샘, 좌우 칸 밖 그림 보호, 위아래는 칸 테두리 선이 있을 때만 따라감 — crop-borders-4). |
| 1.0.36 | **"말풍선까지 옮기기"·"칸 겹치기" 설정 제거** (사용자 11-19 "말풍선까지 옮기기 기능과 칸 겹치기 기능은 제거해줘"). 확대 때 말풍선 안에서 대사를 옮기는 기본 동작은 그대로 (cacheKey balloons-5). 저장된 작품 설정의 두 값은 읽을 때 무시된다. |
| 1.0.37 | **확대 때 쪽 끝 대사 옮기기 제거** (사용자 11-19 "확대해서 쪽 끝에서 잘리는 대사를 말풍선 안에서 옮기는 동작도 제거해"). `Balloons.kt`·테스트 삭제, `pageWorkLock` 은 `enhance/PageWork.kt` 로. 확대는 이제 그릴 때 화면 배율만 바꾼다 (쪽 이미지를 다시 받지 않음). |
| 1.0.38 | **확대 위치(▲▼) 한 번에 절반만** (사용자 11-19 "한번에 움직이는 픽셀을 지금의 절반으로"). 넘치는 높이의 1/8 → 1/16. 값을 ‰ 로 바꿈 (`zoomPanPermille`, ±1000, 125 단위) — 예전 `zoomPanPercent` 저장값은 ×10 으로 읽는다. |
| 1.0.39 | **테두리 잘라내기 두 쪽 보기 오류 수정** (사용자 TV 제보 11-19: 킹덤 권 첫머리 로고 쪽 옆 쪽의 위아래·안쪽이 크게 잘림). 원인: `matchCrop` 이 로고만 있는 빈 쪽의 빈 곳(재는 끝 20%)을 테두리로 알고 옆 쪽에 맞춤. 수정: `spreadShare` — 한 변이라도 재는 끝까지 빈 쪽은 아무것도 맞추게 하지 않고, 맞춰 더 자르는 것은 한 변 12%까지 (crop-borders-5). |
| 1.0.40 | **"테두리 잘라내기" → "여백 잘라내기", 쪽마다 따로** (사용자 11-20 "테두리 잘라내기는, 여백 잘라내기로 바꾸고, 한쪽씩 따로 잘라내는걸로 규칙을 바꾸자 두쪽보기는 각각의 쪽 여백을 제거한 후에 붙이기"). 두 쪽 맞추기(`matchCrop`·`spreadShare`·펼침 공유 맵) 전부 제거 — 쪽마다 자르고 화면 가운데(접히는 곳)에 붙여 놓는 것은 원래 배치(`SpreadPages` CenterEnd/CenterStart) 그대로. crop-borders-6. 설정 이름 15개 언어 "여백"으로. 줄 셋을 뺀 만큼 보기 설정 패널 간격 되돌림 (사용자 "옵션을 제거한 만큼 보기 설정 ui 크기 재배치": 줄 사이 3dp, 줄 안 위아래 3dp, 패널 6dp — 에뮬레이터에서 한 화면에 들어감 확인). |
| 1.0.41 | **여백 잘라내기 3차 (agy 8,800쪽 조사, `task/crop-round3` 281aa48 병합)**: 바깥으로 이어진 1열 펜선은 먼지로 보지 않음(`isDust`), 칸 테두리 선 길이 기준 4%→8%(효과선·짧은 선에 맞춰 그림을 잘라먹던 쪽 — 나루토·마사루 등 344쪽 덜 자름), 검은 바탕 빛샘(MAX_DARK_BORDER 85, 바깥 1~2줄 건넘). 두 쪽 맞추기 검증·`CropSpreadProbeTest` 는 규칙이 바뀌어 빼고 병합. crop-borders-7. 보고서 `docs/reports/CROP-ROUND3.md`, 비교 도구 `tools/reader-survey/compare_crops.py`. |

### 여백 잘라내기 (옛 이름 테두리 잘라내기) — 사용자 요구 (그대로)
- "komelia는 경계선 밖이 흰색인 경우에는 잘동작하는데 검정색인경우에는 안되더라고" → 흰·검은 여백 모두.
- "테두리 잘라내기는 상하, 좌우, 상하좌우 3가지" / "확대기능 바로 아래로" / "설정페이지 스크롤 안되도록 주의".
- ~~"같은 페이지 안에 테두리가 있는 쪽과 없는 쪽이 섞여 있다면 있는쪽에 맞춰서 없는쪽도 잘라냄" → 두 쪽 보기에서 다른 쪽에 맞춤~~ — **대체됨 11-20**: "한쪽씩 따로 잘라내는걸로 규칙을 바꾸자 두쪽보기는 각각의 쪽 여백을 제거한 후에 붙이기". 이름도 "여백 잘라내기".
- "한페이지안에서도 테두리가 있는 쪽에 맞춰 없는 부분을 잘라냄 (이건 상하의 경우에만)" + 그림 예시(열혈강호 1권 31쪽: 탑 꼭대기·쪽 번호까지 잘라 칸 테두리 선에서) →
  위아래는 칸 테두리 선이 있는 열들에 맞춰 자르고(칸 밖으로 나온 그림·쪽 번호 함께), **그 깊이에 칸 테두리 선이 없으면**(테두리 없이 열린 칸, 칸 밖 글) 모든 열이 여백인 곳까지만. 좌우는 칸 밖 그림을 자르지 않음.
- 코드: `feature/reader/.../enhance/CropBorders.kt`, 테스트 `CropBordersTest.kt`, 설정 `core/model ReaderSettings.CropBorders`.

## 4. 지금 상태 (2026-11-16)

진행 중인 agy 작업 없음. 모두 main 에 병합됐다.
- 칸 간격 2차: agy `fc9106c` + 리드 `4e3e7c0` (흰 종이인데 어두운 그림이 많은 쪽에서 위아래를 회색·검은 띠로 채우던 변경을 되돌림).
  옛 코드(1.0.32~34)와 3,420쪽 비교: 잉크를 1% 넘게 더 잃은 11쪽을 모두 눈으로 봄 — 그림 잘림 없음, 오히려 옛 코드의 망가짐 몇 개가 고쳐짐
  (감옥학원 120 회색 상자, 열혈강호 198 광고 쪽 제목).
- 테두리 잘라내기: agy 34권 4,364쪽 조사 `6893a24` + 리드 `8707146` (나루토 1권 58쪽처럼 테두리 없이 열린 칸은 위아래를 모든 열이 여백인 곳까지만).
  44쪽 표본과 사용자 예시(열혈강호 1권 `030.jpg` = 쪽 번호 31)를 눈으로 확인. 보고서 `docs/reports/CROP-SURVEY.md`.
- 에뮬레이터: 1.0.35 설치, 나루토 1권에서 테두리 잘라내기 상하좌우 + 칸 간격 켜고 확인 후 "앱 기본값으로 되돌리기" 함.

### 칸 간격 없애기 — 아직 못 고친 것 (조사 보고서 `docs/reports/GUTTERS-SURVEY-A/B/C.md`, `GUTTERS-KINGDOM.md`, `GUTTERS-ROUND2.md`)
- 여백을 말풍선·효과음·칸 밖 그림이 가로질러 띠가 안 나뉘는 쪽, 쪽 끝 스캔 그림자, 비스듬한 여백 일부.
- 킹덤 34권 078·120·165쪽(1차에서 붙였다가 다른 만화 그림을 잘라 되돌린 변경 둘의 대상).
- 원칙: **덜 줄어드는 것은 참을 수 있지만 그림이 잘리는 것은 안 된다.** 수정마다 `tools/reader-survey` 로 전체 비교 + 바뀐 쪽 눈 확인.

## 5. 조사 데이터 (저장소 밖, `D:\Project\KomgaTV-gutters-data\`)
- 풀어 둔 쪽: `a`, `b`, `c`, `kingdom`(01·10·20·30·34·40·42권), `crop`(테두리 조사 14종). 원본은 `Z:\Drive\Rock_Comics`(읽기 전용).
- 비교 결과: `dump-new3`(=1.0.32~34 의 칸 간격 코드), `dump-r2b`(=1.0.35 의 칸 간격 코드 — 다음 비교의 옛 쪽 기준), `cmp-*`(비교 그림).
- 사용법: `tools/reader-survey/README.md`.

## 6. 남은 일 (나중)
- 사용자 TV 에서: 1.0.34 의 테두리 잘라내기·칸 간격(킹덤) 확인 결과 받기. TV 에서 칸 간격이 안 붙는다는 쪽은 권·쪽 번호를 받아 조사 도구로 본다 (킹덤 59권 이후는 쪽 번호만 받고 그림은 보지 않음 — 사용자가 직접 확인).
- 에뮬레이터 정리: 킹덤 34권 작품 설정을 "앱 기본값으로 되돌리기"(1.0.31 때 두 쪽·칸 간격 켬으로 바꿔 둠 — 에뮬레이터에만 저장, TV 무관). 킹덤은 사용자가 읽는 중이라 열지 않았다 → 다음에 사용자 확인 후.
- 11-16 홈에서 열혈강호 01권 진행이 3/199 로 보였다 (11-15 에는 30/199) — 사용자가 직접 읽고 테스트한 것 (11-19 확인). 되돌리지 않는다.
- Play 에 새 버전 올리기는 사용자가 원할 때 (`docs/RELEASE.md`, 데모 서버로 심사).
- 자동 채색 다시 보기: `archive/colorize` (모델 라이선스 확인이 먼저).
- 정리할 worktree: `KomgaTV-survey-a/b/c`, `KomgaTV-probe`, `KomgaTV-ondeck`(병합 끝), `KomgaTV-T5/T6`(prunable) — 지우기 전에 병합 여부 확인.
