# 🤝 기여 가이드

KomgaTV에 기여해 주셔서 감사합니다!

## 개발 환경 설정

### 요구 사항
- **Android Studio** Ladybug (2024.2.1) 이상
- **JDK** 17+
- **Android SDK** 35

### 시작하기
```bash
git clone https://github.com/RR-korea/KomgaTV.git
cd KomgaTV
```
Android Studio에서 프로젝트를 열면 자동으로 Gradle sync가 실행됩니다.

### 테스트 실행
```bash
./gradlew test          # 유닛 테스트
./gradlew lint          # 린트 체크
```

---

## 커밋 컨벤션

[Conventional Commits](https://www.conventionalcommits.org/) 형식을 따릅니다:

| 접두사 | 용도 |
|---|---|
| `feat:` | 새로운 기능 |
| `fix:` | 버그 수정 |
| `docs:` | 문서 변경 |
| `refactor:` | 리팩토링 (동작 변경 없음) |
| `style:` | 코드 포맷팅 |
| `test:` | 테스트 추가/수정 |
| `chore:` | 빌드, 의존성 등 |

**예시:**
```
feat: 홈 화면에 계속 읽기 행 추가
fix: 리더에서 마지막 페이지 넘김 시 크래시 수정
docs: TV 최적화 가이드 업데이트
```

---

## 브랜치 전략

- `main` — 안정 버전
- `feature/<이름>` — 새 기능 개발
- `fix/<이름>` — 버그 수정

## Pull Request

1. `main`에서 feature 브랜치 생성
2. 작업 완료 후 PR 생성
3. 제목에 커밋 컨벤션 접두사 사용
4. 변경 내용 설명 작성

---

## 코드 스타일

- [Kotlin 공식 코딩 컨벤션](https://kotlinlang.org/docs/coding-conventions.html) 준수
- 주요 로직에 한국어 주석 권장
- Compose 컴포넌트는 `@Preview` 추가 권장

## 모듈 구조

새 기능은 해당 feature 모듈에 추가합니다. 공유 컴포넌트는 `core:ui`에, 데이터 로직은 `core:data`에 배치합니다.
