# REMOVE-ONDECK 보고서: 홈 화면의 "다음 권"(On deck) 줄 없애기

작업: Antigravity — 2026-11-15 (사용자 요청: "데쉬보드의 다음권 기능 제거 무쓸모")
브랜치: `task/remove-ondeck`

## 바꾼 파일

1. **feature/home UI 및 ViewModel**
   - `HomeScreen.kt`:
     - 상단 주석의 홈 화면 줄 순서 설명에서 "다음 권" 제거.
     - `EntryFocusEffect` 인자에서 `state.onDeck` 제거 및 주석 수정.
     - rows 목록에서 `"ondeck"` `HomeRow` 블록 삭제.
   - `HomeViewModel.kt`:
     - `HomeUiState.Success`에서 `onDeck: List<Book>` 프로퍼티 및 KDoc 설명 제거.
     - `loadHomeData()`에서 `onDeck` 비동기 조회(`bookRepository.getBooksOnDeck`) 및 상태 반영 제거.
     - `readingRows()`에서 `onDeck` 매개변수 제거.
     - `hideFromReading()`에서 `onDeck` 필터링 및 "다음 권" 관련 주석 제거.
     - `refreshReadingRows()`에서 `onDeck` 비동기 조회 및 상태 갱신 제거, 주석 정리.
     - 초기화 블록 주석 정리.
   - `ReadingRows.kt`:
     - `ReadingRows` 데이터 클래스에서 `onDeck` 프로퍼티 제거.
     - `filterReadingRows()`에서 `onDeck` 매개변수 및 반환값 처리 제거, 주석 정리.
   - `LibraryActionsDialog.kt`:
     - `ReadingActionsDialog` KDoc 머리 주석에서 "다음 권" 문구 제거 ("읽는 중" 책 메뉴로 수정).

2. **core/data & core/datastore**
   - `core/data/src/main/java/com/komgatv/core/data/repository/BookRepository.kt`:
     - `BookRepository` 인터페이스에서 `getBooksOnDeck(size: Int = 20): List<Book>` 메서드 제거.
     - `DefaultBookRepository` 구현체에서 `override suspend fun getBooksOnDeck(...)` 메서드 구현 삭제.
   - `core/datastore/src/main/java/com/komgatv/core/datastore/HomePreferences.kt`:
     - 22줄 주석의 "다음 권" 언급 정리.

3. **테스트 가짜 저장소 및 단위 테스트**
   - 가짜 BookRepository 구현체들에서 `getBooksOnDeck` 제거:
     - `feature/library/src/test/java/com/komgatv/feature/library/SeriesDetailViewModelTest.kt`
     - `feature/reader/src/test/java/com/komgatv/feature/reader/ReaderViewModelTest.kt`
     - `feature/reader/src/test/java/com/komgatv/feature/reader/epub/EpubViewModelTest.kt`
     - `feature/search/src/test/java/com/komgatv/feature/search/SearchViewModelTest.kt`
     - `feature/home/src/test/java/com/komgatv/feature/home/HomeViewModelTest.kt`
   - `feature/home/src/test/java/com/komgatv/feature/home/HomeViewModelTest.kt`:
     - FakeBookRepository에서 `onDeckToReturn`, `failOnDeck`, `getBooksOnDeck` 제거, `failLatestBooks` 추가.
     - `다음 권과 최근 추가된 시리즈 줄을 함께 읽는다` 테스트를 `최근 추가된 시리즈 줄을 읽는다`로 수정하고 onDeck 검증 제거.
     - `선택 줄이 실패해도 홈은 열리고 그 줄만 비어 있다` 테스트를 `failLatestBooks`를 사용하도록 수정.
     - `읽는 중에서 빼면...` 테스트에서 onDeck(`next` 책) 부분 제거 및 `continueReading`만 검증하도록 수정.
   - `feature/home/src/test/java/com/komgatv/feature/home/ReadingRowsTest.kt`:
     - onDeck 매개변수 및 단언 제거, "읽는 중" 줄 필터링 및 복원 검증만 수행하도록 수정.

4. **다국어 문자열 (15개 언어)**
   - `feature/home/src/main/res/values*/strings.xml` (values, values-ko, values-de, values-es, values-fr, values-in, values-it, values-ja, values-pl, values-pt-rBR, values-ru, values-tr, values-vi, values-zh-rCN, values-zh-rTW):
     - `home_on_deck` 문자열 리소스 삭제.
     - `reading_action_hint` 문자열에서 "다음 권" 관련 문구를 제거하고 "읽는 중"만 자연스럽게 언급하도록 15개 언어 모두 수정.
     - `python tools/check_translations.py` 검사 통과 ("문제 없음").

5. **앱 버전**
   - `app/build.gradle.kts`는 변경하지 않음.

---

## 실행한 명령과 결과

### 1. 번역 검사
명령: `$env:PYTHONIOENCODING="utf-8"; python tools/check_translations.py`
결과:
```
문제 없음
```

### 2. 빌드, 린트 및 단위 테스트 검증
명령:
`$env:JAVA_HOME="C:\Program Files\Eclipse Adoptium\jdk-21.0.13.9-hotspot"; .\gradlew assembleDebug lint testDebugUnitTest --console=plain`

마지막 30줄 출력:
```
> Task :feature:library:kspDebugUnitTestKotlin UP-TO-DATE
> Task :feature:reader:lintDebug
> Task :feature:reader:lint
> Task :feature:settings:compileDebugUnitTestKotlin UP-TO-DATE
> Task :feature:settings:compressDebugUnitTestJavaRes UP-TO-DATE
> Task :feature:settings:extractDebugUnitTestJavaRes UP-TO-DATE
> Task :feature:settings:compileDebugUnitTestJavaWithJavac NO-SOURCE
> Task :feature:settings:hiltCollectClassesDebugUnitTest UP-TO-DATE
> Task :feature:search:hiltAggregateDepsDebugUnitTest UP-TO-DATE
> Task :feature:reader:kspDebugUnitTestKotlin UP-TO-DATE
> Task :feature:home:compileDebugUnitTestKotlin UP-TO-DATE
> Task :feature:search:hiltJavaCompileDebugUnitTest NO-SOURCE
> Task :feature:home:compressDebugUnitTestJavaRes UP-TO-DATE
> Task :feature:home:extractDebugUnitTestJavaRes UP-TO-DATE
> Task :feature:home:compileDebugUnitTestJavaWithJavac NO-SOURCE
> Task :feature:home:hiltCollectClassesDebugUnitTest UP-TO-DATE
> Task :app:generateDebugUnitTestLintModel UP-TO-DATE
> Task :feature:search:transformDebugUnitTestClassesWithAsm UP-TO-DATE
> Task :feature:settings:hiltAggregateDepsDebugUnitTest UP-TO-DATE
> Task :feature:settings:hiltJavaCompileDebugUnitTest NO-SOURCE
> Task :feature:settings:transformDebugUnitTestClassesWithAsm UP-TO-DATE
> Task :feature:home:hiltAggregateDepsDebugUnitTest UP-TO-DATE
> Task :feature:home:hiltJavaCompileDebugUnitTest NO-SOURCE
> Task :feature:library:compileDebugUnitTestKotlin UP-TO-DATE
> Task :feature:library:compressDebugUnitTestJavaRes UP-TO-DATE
> Task :feature:library:extractDebugUnitTestJavaRes UP-TO-DATE
> Task :feature:search:testDebugUnitTest UP-TO-DATE
> Task :feature:library:compileDebugUnitTestJavaWithJavac NO-SOURCE
> Task :feature:library:hiltCollectClassesDebugUnitTest UP-TO-DATE
> Task :feature:home:transformDebugUnitTestClassesWithAsm UP-TO-DATE
> Task :feature:settings:testDebugUnitTest UP-TO-DATE
> Task :feature:library:hiltAggregateDepsDebugUnitTest UP-TO-DATE
> Task :app:lintAnalyzeDebug UP-TO-DATE
> Task :feature:library:hiltJavaCompileDebugUnitTest NO-SOURCE
> Task :feature:home:testDebugUnitTest UP-TO-DATE
> Task :feature:library:transformDebugUnitTestClassesWithAsm UP-TO-DATE
> Task :app:lintAnalyzeDebugAndroidTest UP-TO-DATE
> Task :feature:library:testDebugUnitTest UP-TO-DATE
> Task :feature:reader:compileDebugUnitTestKotlin UP-TO-DATE
> Task :feature:reader:compressDebugUnitTestJavaRes UP-TO-DATE
> Task :feature:reader:extractDebugUnitTestJavaRes UP-TO-DATE
> Task :app:lintAnalyzeDebugUnitTest UP-TO-DATE
> Task :feature:reader:compileDebugUnitTestJavaWithJavac NO-SOURCE
> Task :feature:reader:hiltCollectClassesDebugUnitTest UP-TO-DATE
> Task :app:lintReportDebug UP-TO-DATE
> Task :app:lintDebug
> Task :app:lint
> Task :app:bundleDebugClassesToRuntimeJar UP-TO-DATE
> Task :feature:reader:hiltAggregateDepsDebugUnitTest UP-TO-DATE
> Task :feature:reader:hiltJavaCompileDebugUnitTest NO-SOURCE
> Task :feature:reader:transformDebugUnitTestClassesWithAsm UP-TO-DATE
> Task :app:kspDebugUnitTestKotlin UP-TO-DATE
> Task :feature:reader:testDebugUnitTest UP-TO-DATE
> Task :app:compileDebugUnitTestKotlin UP-TO-DATE
> Task :app:compressDebugUnitTestJavaRes UP-TO-DATE
> Task :app:extractDebugUnitTestJavaRes UP-TO-DATE
> Task :app:compileDebugUnitTestJavaWithJavac NO-SOURCE
> Task :app:hiltCollectClassesDebugUnitTest UP-TO-DATE
> Task :app:hiltAggregateDepsDebugUnitTest UP-TO-DATE
> Task :app:hiltJavaCompileDebugUnitTest NO-SOURCE
> Task :app:transformDebugUnitTestClassesWithAsm UP-TO-DATE
> Task :app:testDebugUnitTest UP-TO-DATE

BUILD SUCCESSFUL in 7s
597 actionable tasks: 11 executed, 586 up-to-date
```

Lint 결과 확인:
- `feature/home/build/reports/lint-results-debug.txt`: "No issues found." (0 errors, 0 warnings)
- 프로젝트 전체에 새 린트 경고 없음.

---

## 남은 문제

- 새로운 버그 및 미해결 오류 없음.
- `grep -rni "ondeck\|on_deck\|on deck\|다음 권" --include=*.kt --include=*.xml .` 결과에 남은 "다음 권" 항목에 대한 설명:
  - `ondeck`, `on_deck`, `on deck`는 프로젝트 전체 `.kt` 및 `.xml` 파일에서 완전히 제거됨 (0건).
  - 남은 "다음 권" 검색 결과들은 모두 리더 모듈(`feature/reader`)의 끝 페이지(EndPageView) 및 같은 시리즈의 다음 책 이어보기 기능(`BookRepository.getNextBook()`, `end_page_next` 등) 관련 주석, UI 문자열, 테스트명임. 이는 홈 화면의 "다음 권(on deck)" 줄 기능과는 무관한 리더의 필수 기능이므로 정상 유지됨.
