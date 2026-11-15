# 리더 그림 처리 조사 도구 (칸 간격 없애기 · 테두리 잘라내기)

실제 만화 쪽으로 `Gutters.kt`(칸 간격 없애기)와 `CropBorders.kt`(테두리 잘라내기)를 돌려 보고, 옛 코드와 새 코드 결과를 비교한다.
JVM 단위 테스트로 돌린다 — 환경 변수가 없으면 건너뛰므로 평소 `testDebugUnitTest`에는 영향이 없다.

만화 원본(사용자 NAS `Z:\Drive\Rock_Comics`, 읽기 전용)과 풀어 둔 쪽·결과는 저장소 밖 `D:\Project\KomgaTV-gutters-data\`에 둔다.
**사용자가 지금 읽는 권과 그 뒤 권은 열지 않는다** (킹덤 — 홈 "읽는 중"의 권 미만만). 보고서에 줄거리·대사를 쓰지 않는다.

## 1. 쪽 풀기
- zip: 권마다 `D:\Project\KomgaTV-gutters-data\<묶음>\<책 이름>\` 에 푼다 (하위 폴더 하나 = 한 권).
- PDF: PyMuPDF(`import pymupdf`)로 쪽마다 높이 2200 PNG로 그린다 (앱은 PDF 쪽을 화면 크기로 그려 받는다).
- 지금 풀어 둔 묶음: `a`(원피스·나루토·마사루·겁쟁이 페달), `b`(단다단·감옥학원·가이버·열혈강호·Monster X Monster),
  `c`(탁구부·시마사원·도라에몽 플러스·소라의 날개 41 — GIF라 덤프 도구는 건너뜀), `kingdom`(01·10·20·30·34·40·42), `crop`(테두리 조사용 14종).

## 2. 칸 간격 없애기 — 옛/새 비교
```sh
export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-21.0.13.9-hotspot"
# 비교할 코드마다 (예: main 의 worktree 와 작업 브랜치 worktree 에서 각각)
GUT_DUMP_SRC="D:/Project/KomgaTV-gutters-data/a" GUT_DUMP_OUT="D:/Project/KomgaTV-gutters-data/dump-old/a" \
  ./gradlew :feature:reader:testDebugUnitTest --tests '*GutterDumpTest*' --rerun -q
# 같은 쪽끼리 비교 — 새 코드가 잉크를 1% 넘게 더 잃은 쪽을 원본|옛|새 그림으로 cmp-<NEWDIR>\ 에 쓴다
cd D:/Project/KomgaTV-gutters-data
OLDDIR=dump-old NEWDIR=dump-new python <저장소>/tools/reader-survey/compare_dumps.py a b c kingdom
```
- `GutterDumpTest` 는 결과 PNG가 이미 있으면 그 쪽을 건너뛴다 — 코드를 바꿨으면 출력 폴더를 새로 잡는다.
- **잉크 손실 숫자는 볼 쪽을 고르는 데만 쓴다.** 맞닿은 칸 테두리를 한 줄로 겹치는 것(의도된 동작)도 잉크가 줄어든다.
  목록의 쪽은 전부 눈으로 본다. 그림이 잘린 쪽이 하나라도 있으면 그 수정은 고치거나 되돌린다.
- 그림 하나로 볼 때: 원본 | 옛 코드 | 새 코드.

## 3. 테두리 잘라내기 — 쪽 확인
```sh
# 목록 파일: 한 줄에 쪽 그림 경로 하나 (D:/... 형식)
CROP_LIST='C:\...\crop_list.txt' CROP_OUT='D:\Project\KomgaTV-gutters-data\crop-probe' \
  ./gradlew :feature:reader:testDebugUnitTest --tests '*CropProbeTest*' --rerun -i | grep "CROP "
```
- 쪽마다 `CROP <번호> 위,아래,왼쪽,오른쪽 <경로>` 를 찍고, `<번호>.jpg` 에 원본(빨간 선 = 자를 곳) | 잘라낸 쪽 을 그린다 (상하좌우).
- 사용자 기준 예: 열혈강호 01권 `030.jpg`(쪽 번호 31) — 위는 오른쪽 위 칸 테두리, 아래는 칸 아래 테두리에서 잘리고
  탑 꼭대기·쪽 번호·옷자락은 함께 잘린다. 좌우는 칸 밖으로 나온 그림을 자르지 않는다.
