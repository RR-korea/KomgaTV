package com.komgatv.feature.reader

import com.komgatv.core.model.MediaProfile
import com.komgatv.core.model.EpubPosition
import com.komgatv.core.model.EpubManifest
import com.komgatv.core.data.image.ImageUrls
import com.komgatv.core.data.repository.BookPageResult
import com.komgatv.core.data.repository.BookRepository
import com.komgatv.core.data.repository.SeriesPageResult
import com.komgatv.core.data.repository.SeriesRepository
import com.komgatv.core.datastore.ReaderPreferences
import com.komgatv.core.model.Book
import com.komgatv.core.model.BookMetadata
import com.komgatv.core.model.BookPage
import com.komgatv.core.model.ImageEnhancement
import com.komgatv.core.model.LoadError
import com.komgatv.core.model.PageLayout
import com.komgatv.core.model.ReadProgress
import com.komgatv.core.model.ReaderSettings
import com.komgatv.core.model.ReaderType
import com.komgatv.core.model.ReadingDirection
import com.komgatv.core.model.ScaleType
import com.komgatv.core.model.Series
import com.komgatv.core.model.SeriesMetadata
import com.komgatv.feature.reader.continuous.PageTile
import com.komgatv.feature.reader.continuous.TileSource
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * ReaderViewModel 단위 테스트
 *
 * 검증 항목:
 * 1. 방향 우선순위: 시리즈 메타데이터 > 저장된 기본값
 * 2. 방향 변경 시 즉시 반영 및 전역 기본값 저장
 * 3. D-pad 좌/우 이동 및 읽기 방향에 따른 반전
 * 4. 두 쪽 펼침에서 진행 저장 (보이는 쪽 중 큰 번호 저장)
 * 5. 마지막 페이지 펼침 도달 시 완독(completed=true) 저장
 * 6. 서버 진행도 페이지가 포함된 펼침에서 시작 (이어 읽기)
 * 7. 2초 디바운스 및 종료 시 즉시 저장 (saveProgressOnExit)
 * 8. 펼침 기준 앞뒤 2개 프리로드 URL 생성
 * 9. 설정 변경 (쪽 배치, 밀기, 크기 맞춤, 작은 이미지 키우기) 및 저장
 * 10. 10페이지 점프 조작
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var bookRepository: FakeBookRepository
    private lateinit var seriesRepository: FakeSeriesRepository
    private lateinit var readerPreferences: FakeReaderPreferences
    private lateinit var tileSource: FakeTileSource
    private lateinit var viewModel: ReaderViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        bookRepository = FakeBookRepository()
        seriesRepository = FakeSeriesRepository()
        readerPreferences = FakeReaderPreferences()
        tileSource = FakeTileSource()
        viewModel = ReaderViewModel(bookRepository, seriesRepository, FakeImageUrls, readerPreferences, tileSource)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `이어 읽기 - 서버에 저장된 진행도 페이지가 속한 펼침에서 시작한다`() = runTest(testDispatcher) {
        val series = createTestSeries(id = "series1", direction = ReadingDirection.LEFT_TO_RIGHT)
        val book = createTestBook(
            id = "book1",
            seriesId = "series1",
            pageCount = 20,
            progress = ReadProgress(page = 7, completed = false),
        )
        seriesRepository.seriesMap[series.id] = series
        bookRepository.books[book.id] = book
        bookRepository.pagesMap[book.id] = (1..20).map { BookPage(number = it) }

        viewModel.loadBook(book.id)
        advanceUntilIdle()

        val state = viewModel.uiState.value as ReaderUiState.Reading
        assertTrue(state.currentSpread.containsPage(7))
        assertEquals(20, state.totalPages)
    }

    @Test
    fun `방향 우선순위 - 시리즈에 방향이 있으면 기본값보다 시리즈 방향을 우선한다`() = runTest(testDispatcher) {
        // 기본 설정은 LEFT_TO_RIGHT
        readerPreferences.saveReadingDirection(ReadingDirection.LEFT_TO_RIGHT)

        // 시리즈는 RIGHT_TO_LEFT
        val series = createTestSeries(id = "s_rtl", direction = ReadingDirection.RIGHT_TO_LEFT)
        val book = createTestBook(id = "b1", seriesId = "s_rtl", pageCount = 10)
        seriesRepository.seriesMap[series.id] = series
        bookRepository.books[book.id] = book

        viewModel.loadBook(book.id)
        advanceUntilIdle()

        val state = viewModel.uiState.value as ReaderUiState.Reading
        assertEquals(ReadingDirection.RIGHT_TO_LEFT, state.readingDirection)
    }

    @Test
    fun `방향 우선순위 - 시리즈에 방향이 없으면 저장된 기본값을 사용한다`() = runTest(testDispatcher) {
        readerPreferences.saveReadingDirection(ReadingDirection.RIGHT_TO_LEFT)

        // 시리즈에 방향 지정 안 됨 (null)
        val series = createTestSeries(id = "s_null", direction = null)
        val book = createTestBook(id = "b1", seriesId = "s_null", pageCount = 10)
        seriesRepository.seriesMap[series.id] = series
        bookRepository.books[book.id] = book

        viewModel.loadBook(book.id)
        advanceUntilIdle()

        val state = viewModel.uiState.value as ReaderUiState.Reading
        assertEquals(ReadingDirection.RIGHT_TO_LEFT, state.readingDirection)
    }

    @Test
    fun `방향 우선순위 - 시리즈가 WEBTOON이면 페이지 리더는 저장된 기본값을 쓴다`() = runTest(testDispatcher) {
        readerPreferences.saveReadingDirection(ReadingDirection.RIGHT_TO_LEFT)
        val series = createTestSeries(id = "s_webtoon", direction = ReadingDirection.WEBTOON)
        val book = createTestBook(id = "b1", seriesId = "s_webtoon", pageCount = 10)
        seriesRepository.seriesMap[series.id] = series
        bookRepository.books[book.id] = book

        viewModel.loadBook(book.id)
        advanceUntilIdle()

        val state = viewModel.uiState.value as ReaderUiState.Reading
        assertEquals(ReadingDirection.RIGHT_TO_LEFT, state.readingDirection)
    }

    @Test
    fun `로드 실패 - 책이 없으면 오류 종류를 내보낸다`() = runTest(testDispatcher) {
        viewModel.loadBook("missing")
        advanceUntilIdle()

        assertEquals(ReaderUiState.Error(LoadError.UNKNOWN), viewModel.uiState.value)
    }

    @Test
    fun `방향 변경 - 리더에서 방향을 바꾸면 지금 책에 바로 적용되고 기본값으로 저장된다`() = runTest(testDispatcher) {
        val series = createTestSeries(id = "s1", direction = ReadingDirection.LEFT_TO_RIGHT)
        val book = createTestBook(id = "b1", seriesId = "s1", pageCount = 10)
        seriesRepository.seriesMap[series.id] = series
        bookRepository.books[book.id] = book

        viewModel.loadBook(book.id)
        advanceUntilIdle()

        var state = viewModel.uiState.value as ReaderUiState.Reading
        assertEquals(ReadingDirection.LEFT_TO_RIGHT, state.readingDirection)

        // 방향 변경
        viewModel.setReadingDirection(ReadingDirection.RIGHT_TO_LEFT)
        advanceUntilIdle()

        // 지금 책에 즉시 반영
        state = viewModel.uiState.value as ReaderUiState.Reading
        assertEquals(ReadingDirection.RIGHT_TO_LEFT, state.readingDirection)

        // 기본값으로도 저장됨
        assertEquals(ReadingDirection.RIGHT_TO_LEFT, readerPreferences.series.value.values.single().readingDirection)
    }

    @Test
    fun `방향 반전 - RIGHT_TO_LEFT 시리즈는 D-pad 좌측이 다음 펼침, 우측이 이전 펼침`() = runTest(testDispatcher) {
        val series = createTestSeries(id = "series_rtl", direction = ReadingDirection.RIGHT_TO_LEFT)
        val book = createTestBook(id = "book_rtl", seriesId = "series_rtl", pageCount = 10)
        seriesRepository.seriesMap[series.id] = series
        bookRepository.books[book.id] = book

        viewModel.loadBook(book.id)
        advanceUntilIdle()

        var state = viewModel.uiState.value as ReaderUiState.Reading
        assertEquals(0, state.currentSpreadIndex)

        // D-pad 좌측 누름 -> 다음 펼침(index 1)으로 이동
        viewModel.onDpadLeft()
        state = viewModel.uiState.value as ReaderUiState.Reading
        assertEquals(1, state.currentSpreadIndex)

        // D-pad 우측 누름 -> 이전 펼침(index 0)으로 이동
        viewModel.onDpadRight()
        state = viewModel.uiState.value as ReaderUiState.Reading
        assertEquals(0, state.currentSpreadIndex)
    }

    @Test
    fun `기본 방향 - LEFT_TO_RIGHT 시리즈는 D-pad 우측이 다음 펼침, 좌측이 이전 펼침`() = runTest(testDispatcher) {
        val series = createTestSeries(id = "series_ltr", direction = ReadingDirection.LEFT_TO_RIGHT)
        val book = createTestBook(id = "book_ltr", seriesId = "series_ltr", pageCount = 10)
        seriesRepository.seriesMap[series.id] = series
        bookRepository.books[book.id] = book

        viewModel.loadBook(book.id)
        advanceUntilIdle()

        var state = viewModel.uiState.value as ReaderUiState.Reading
        assertEquals(0, state.currentSpreadIndex)

        // D-pad 우측 누름 -> 다음 펼침(index 1)으로 이동
        viewModel.onDpadRight()
        state = viewModel.uiState.value as ReaderUiState.Reading
        assertEquals(1, state.currentSpreadIndex)

        // D-pad 좌측 누름 -> 이전 펼침(index 0)으로 이동
        viewModel.onDpadLeft()
        state = viewModel.uiState.value as ReaderUiState.Reading
        assertEquals(0, state.currentSpreadIndex)
    }

    @Test
    fun `두 쪽 보기 진행 저장 - 펼침에서 보이는 쪽 중 큰 번호를 저장한다`() = runTest(testDispatcher) {
        readerPreferences.savePageLayout(PageLayout.DOUBLE_PAGES)
        val series = createTestSeries(id = "s1")
        val book = createTestBook(id = "b1", seriesId = "s1", pageCount = 10)
        seriesRepository.seriesMap[series.id] = series
        bookRepository.books[book.id] = book
        bookRepository.pagesMap[book.id] = (1..10).map { BookPage(number = it) }

        viewModel.loadBook(book.id)
        advanceUntilIdle()

        // DOUBLE_PAGES: 펼침 0은 [1], 펼침 1은 [2, 3]
        viewModel.nextSpread() // 펼침 1 [2, 3]으로 이동
        advanceTimeBy(2000L)
        advanceUntilIdle()

        assertEquals(1, bookRepository.progressUpdates.size)
        val update = bookRepository.progressUpdates.first()
        assertEquals("b1", update.bookId)
        // [2, 3] 중 큰 번호인 3이 저장되어야 함
        assertEquals(3, update.page)
        assertFalse(update.completed)
    }

    @Test
    fun `완독 - 마지막 쪽이 보이는 펼침에 도달하면 completed=true로 저장된다`() = runTest(testDispatcher) {
        readerPreferences.savePageLayout(PageLayout.DOUBLE_PAGES_NO_COVER)
        val series = createTestSeries(id = "s1")
        // 4페이지 책: 펼침 0=[1, 2], 펼침 1=[3, 4]
        val book = createTestBook(id = "b1", seriesId = "s1", pageCount = 4)
        seriesRepository.seriesMap[series.id] = series
        bookRepository.books[book.id] = book
        bookRepository.pagesMap[book.id] = (1..4).map { BookPage(number = it) }

        viewModel.loadBook(book.id)
        advanceUntilIdle()

        viewModel.nextSpread() // [3, 4] 펼침으로 이동 (마지막 쪽 4 포함)
        advanceTimeBy(2000L)
        advanceUntilIdle()

        assertEquals(1, bookRepository.progressUpdates.size)
        val update = bookRepository.progressUpdates.first()
        assertEquals(4, update.page)
        assertTrue(update.completed)
    }

    @Test
    fun `디바운스 - 연속으로 빠르게 넘기면 마지막 페이지만 1회 저장된다`() = runTest(testDispatcher) {
        val series = createTestSeries(id = "s1")
        val book = createTestBook(id = "b1", seriesId = "s1", pageCount = 10)
        seriesRepository.seriesMap[series.id] = series
        bookRepository.books[book.id] = book

        viewModel.loadBook(book.id)
        advanceUntilIdle()

        viewModel.nextSpread()
        advanceTimeBy(500L)
        runCurrent()
        viewModel.nextSpread()
        advanceTimeBy(500L)
        runCurrent()
        viewModel.nextSpread()
        runCurrent()

        // 아직 2초 미경과
        advanceTimeBy(1500L)
        runCurrent()
        assertTrue(bookRepository.progressUpdates.isEmpty())

        // 2초 경과 (추가 500ms)
        advanceTimeBy(500L)
        runCurrent()
        assertEquals(1, bookRepository.progressUpdates.size)
        assertEquals(4, bookRepository.progressUpdates.first().page)
    }

    @Test
    fun `종료 시 즉시 저장 - 2초 미경과 상태에서 saveProgressOnExit 호출 시 즉시 저장된다`() = runTest(testDispatcher) {
        val series = createTestSeries(id = "s1")
        val book = createTestBook(id = "b1", seriesId = "s1", pageCount = 10)
        seriesRepository.seriesMap[series.id] = series
        bookRepository.books[book.id] = book

        viewModel.loadBook(book.id)
        advanceUntilIdle()

        viewModel.nextSpread()
        advanceTimeBy(300L)
        assertTrue(bookRepository.progressUpdates.isEmpty())

        viewModel.saveProgressOnExit()
        advanceUntilIdle()

        assertEquals(1, bookRepository.progressUpdates.size)
        assertEquals(2, bookRepository.progressUpdates.first().page)
    }

    @Test
    fun `프리로드 - 펼침 기준 앞뒤 2개 펼침의 모든 페이지가 프리로드 URL로 생성된다`() = runTest(testDispatcher) {
        // DOUBLE_PAGES_NO_COVER: [1, 2], [3, 4], [5, 6], [7, 8], [9, 10]
        readerPreferences.savePageLayout(PageLayout.DOUBLE_PAGES_NO_COVER)
        val series = createTestSeries(id = "s1")
        val book = createTestBook(id = "b1", seriesId = "s1", pageCount = 10, progress = ReadProgress(page = 5, completed = false))
        seriesRepository.seriesMap[series.id] = series
        bookRepository.books[book.id] = book
        bookRepository.pagesMap[book.id] = (1..10).map { BookPage(number = it) }

        viewModel.loadBook(book.id)
        advanceUntilIdle()

        val state = viewModel.uiState.value as ReaderUiState.Reading
        // 현재 펼침: index 2 ([5, 6])
        assertEquals(2, state.currentSpreadIndex)
        // 가까운 것부터, 앞쪽을 먼저: 3([7, 8]), 4([9, 10]), 1([3, 4]), 0([1, 2])
        val url = { n: Int -> "http://localhost:8080/api/v1/books/b1/pages/$n" }
        val expected = listOf(listOf(url(7), url(8)), listOf(url(9), url(10)), listOf(url(3), url(4)), listOf(url(1), url(2)))
        assertEquals(expected, state.preloadSpreads)
    }

    @Test
    fun `설정 변경 - 쪽 배치 변경 시 보고 있던 페이지를 유지하면서 펼침 재계산 및 저장된다`() = runTest(testDispatcher) {
        val series = createTestSeries(id = "s1")
        val book = createTestBook(id = "b1", seriesId = "s1", pageCount = 10, progress = ReadProgress(page = 5, completed = false))
        seriesRepository.seriesMap[series.id] = series
        bookRepository.books[book.id] = book
        bookRepository.pagesMap[book.id] = (1..10).map { BookPage(number = it) }

        viewModel.loadBook(book.id)
        advanceUntilIdle()

        // 현재 SINGLE_PAGE 상태에서 5페이지 보고 있음
        var state = viewModel.uiState.value as ReaderUiState.Reading
        assertEquals(PageLayout.SINGLE_PAGE, state.pageLayout)
        assertTrue(state.currentSpread.containsPage(5))

        // 두 쪽 보기로 변경
        viewModel.setPageLayout(PageLayout.DOUBLE_PAGES)
        advanceUntilIdle()

        state = viewModel.uiState.value as ReaderUiState.Reading
        assertEquals(PageLayout.DOUBLE_PAGES, state.pageLayout)
        // 여전히 5페이지가 포함된 펼침이어야 함
        assertTrue(state.currentSpread.containsPage(5))
        assertEquals(PageLayout.DOUBLE_PAGES, readerPreferences.series.value.values.single().pageLayout)
    }

    @Test
    fun `설정 변경 - 밀기(offset) 토글 시 펼침이 재계산된다`() = runTest(testDispatcher) {
        readerPreferences.savePageLayout(PageLayout.DOUBLE_PAGES)
        val series = createTestSeries(id = "s1")
        val book = createTestBook(id = "b1", seriesId = "s1", pageCount = 6)
        seriesRepository.seriesMap[series.id] = series
        bookRepository.books[book.id] = book
        bookRepository.pagesMap[book.id] = (1..6).map { BookPage(number = it) }

        viewModel.loadBook(book.id)
        advanceUntilIdle()

        var state = viewModel.uiState.value as ReaderUiState.Reading
        assertFalse(state.layoutOffset)
        assertEquals(4, state.totalSpreads) // [1], [2, 3], [4, 5], [6]

        // 밀기 켜기
        viewModel.setLayoutOffset(true)
        state = viewModel.uiState.value as ReaderUiState.Reading
        assertTrue(state.layoutOffset)
        // [1], [2], [3, 4], [5, 6] -> 4개 펼침
        assertEquals(4, state.totalSpreads)
    }

    @Test
    fun `설정 변경 - 크기 맞춤 및 작은 이미지 키우기 설정 저장`() = runTest(testDispatcher) {
        val series = createTestSeries(id = "s1")
        val book = createTestBook(id = "b1", seriesId = "s1", pageCount = 6)
        seriesRepository.seriesMap[series.id] = series
        bookRepository.books[book.id] = book

        viewModel.loadBook(book.id)
        advanceUntilIdle()

        viewModel.setScaleType(ScaleType.FIT_WIDTH)
        viewModel.setStretchToFit(false)
        advanceUntilIdle()

        val state = viewModel.uiState.value as ReaderUiState.Reading
        assertEquals(ScaleType.FIT_WIDTH, state.scaleType)
        assertFalse(state.stretchToFit)
        assertEquals(ScaleType.FIT_WIDTH, readerPreferences.series.value.values.single().scaleType)
        assertFalse(readerPreferences.series.value.values.single().stretchToFit)
    }

    @Test
    fun `점프 - 10페이지 점프 조작 시 범위 내에서 정상 이동한다`() = runTest(testDispatcher) {
        val series = createTestSeries(id = "s1")
        val book = createTestBook(id = "b1", seriesId = "s1", pageCount = 25, progress = ReadProgress(page = 5, completed = false))
        seriesRepository.seriesMap[series.id] = series
        bookRepository.books[book.id] = book
        bookRepository.pagesMap[book.id] = (1..25).map { BookPage(number = it) }

        viewModel.loadBook(book.id)
        advanceUntilIdle()

        // 10페이지 앞으로 (+10)
        viewModel.jumpPages(10)
        var state = viewModel.uiState.value as ReaderUiState.Reading
        assertEquals(15, state.currentSpread.pages.first().number)

        // 10페이지 뒤로 (-10)
        viewModel.jumpPages(-10)
        state = viewModel.uiState.value as ReaderUiState.Reading
        assertEquals(5, state.currentSpread.pages.first().number)

        // 최소값 1로 제한
        viewModel.jumpPages(-10)
        state = viewModel.uiState.value as ReaderUiState.Reading
        assertEquals(1, state.currentSpread.pages.first().number)
    }

    // ---- T6: 리더 종류, 이어 읽기, 세로 스크롤 ----

    private fun openBook(
        direction: ReadingDirection? = null,
        pages: List<BookPage> = (1..10).map { BookPage(number = it, width = 1000, height = 1000) },
        progress: ReadProgress? = null,
    ) {
        val series = createTestSeries(id = "s1", direction = direction)
        val book = createTestBook(id = "b1", seriesId = "s1", pageCount = pages.size, progress = progress)
        seriesRepository.seriesMap[series.id] = series
        bookRepository.books[book.id] = book
        bookRepository.pagesMap[book.id] = pages
        viewModel.setScreenSize(1000, 1000)
        viewModel.loadBook(book.id)
    }

    private val reading get() = viewModel.uiState.value as ReaderUiState.Reading

    @Test
    fun `리더 종류 - WEBTOON 시리즈는 저장된 값이 PAGED여도 세로 스크롤로 연다`() = runTest(testDispatcher) {
        openBook(direction = ReadingDirection.WEBTOON)
        advanceUntilIdle()

        assertEquals(ReaderType.CONTINUOUS, reading.readerType)
    }

    @Test
    fun `리더 종류 - RTL 시리즈는 저장된 값이 CONTINUOUS여도 페이지 넘김으로 연다`() = runTest(testDispatcher) {
        readerPreferences.saveReaderType(ReaderType.CONTINUOUS)
        openBook(direction = ReadingDirection.RIGHT_TO_LEFT)
        advanceUntilIdle()

        assertEquals(ReaderType.PAGED, reading.readerType)
    }

    @Test
    fun `리더 종류 - 방향이 없으면 저장된 값을 따른다`() = runTest(testDispatcher) {
        readerPreferences.saveReaderType(ReaderType.CONTINUOUS)
        openBook(direction = null)
        advanceUntilIdle()

        assertEquals(ReaderType.CONTINUOUS, reading.readerType)
    }

    @Test
    fun `리더 종류 변경 - 보던 쪽에서 이어지고 기본값으로 저장된다`() = runTest(testDispatcher) {
        openBook(progress = ReadProgress(page = 4, completed = false))
        advanceUntilIdle()

        viewModel.setReaderType(ReaderType.CONTINUOUS)
        advanceUntilIdle()

        assertEquals(ReaderType.CONTINUOUS, reading.readerType)
        assertEquals("4_0", reading.continuousTiles[reading.scrollToTileIndex!!].key)
        assertEquals(ReaderType.CONTINUOUS, readerPreferences.series.value.values.single().readerType)
    }

    @Test
    fun `이어 읽기 - 완독한 책은 1쪽부터 연다`() = runTest(testDispatcher) {
        openBook(progress = ReadProgress(page = 10, completed = true))
        advanceUntilIdle()

        assertEquals(1, reading.currentSpread.pages.first().number)
    }

    @Test
    fun `세로 스크롤 - 이어 읽기 쪽의 첫 타일로 스크롤을 요청하고 처리하면 지운다`() = runTest(testDispatcher) {
        readerPreferences.saveReaderType(ReaderType.CONTINUOUS)
        openBook(progress = ReadProgress(page = 3, completed = false))
        advanceUntilIdle()

        assertEquals("3_0", reading.continuousTiles[reading.scrollToTileIndex!!].key)
        viewModel.onTileScrolled()
        assertEquals(null, reading.scrollToTileIndex)
    }

    @Test
    fun `세로 스크롤 - 보이는 쪽 중 큰 번호를 2초 뒤 저장하고 마지막 타일이 보이면 완독`() = runTest(testDispatcher) {
        readerPreferences.saveReaderType(ReaderType.CONTINUOUS)
        openBook()
        advanceUntilIdle()

        viewModel.onContinuousVisibleTilesChanged(listOf("4_0", "5_0"))
        advanceTimeBy(1_999L)
        runCurrent()
        assertTrue(bookRepository.progressUpdates.isEmpty())
        advanceTimeBy(1L)
        runCurrent()
        assertEquals(ProgressUpdate("b1", 5, false), bookRepository.progressUpdates.last())

        viewModel.onContinuousVisibleTilesChanged(listOf("9_0", "10_0"))
        viewModel.saveProgressOnExit()
        advanceUntilIdle()
        assertEquals(ProgressUpdate("b1", 10, true), bookRepository.progressUpdates.last())
    }

    @Test
    fun `세로 스크롤 - 좌우는 쪽 처음으로, 맨 위가 쪽 중간이면 왼쪽은 그 쪽 처음`() = runTest(testDispatcher) {
        readerPreferences.saveReaderType(ReaderType.CONTINUOUS)
        // 2쪽은 화면(1000)보다 3배 긴 쪽 → 타일 3개
        val pages = listOf(
            BookPage(number = 1, width = 1000, height = 1000),
            BookPage(number = 2, width = 1000, height = 3000),
            BookPage(number = 3, width = 1000, height = 1000),
        )
        openBook(pages = pages)
        advanceUntilIdle()
        val keyOf = { reading.continuousTiles[reading.scrollToTileIndex!!].key }

        viewModel.onContinuousVisibleTilesChanged(listOf("2_1"))
        viewModel.onDpadLeft()
        assertEquals("2_0", keyOf())

        viewModel.onContinuousVisibleTilesChanged(listOf("2_0"))
        viewModel.onDpadLeft()
        assertEquals("1_0", keyOf())

        viewModel.onContinuousVisibleTilesChanged(listOf("2_2", "3_0"))
        viewModel.onDpadRight()
        assertEquals("3_0", keyOf())
    }

    @Test
    fun `세로 스크롤 - 크기를 모르는 쪽이 보이면 크기를 알아내 타일을 다시 만든다`() = runTest(testDispatcher) {
        readerPreferences.saveReaderType(ReaderType.CONTINUOUS)
        openBook(pages = listOf(BookPage(number = 1), BookPage(number = 2, width = 1000, height = 1000)))
        advanceUntilIdle()
        assertTrue(reading.continuousTiles.first().isPlaceholder)
        tileSource.sizes[1] = 1000 to 2500

        viewModel.onContinuousVisibleTilesChanged(listOf("1_0"))
        advanceUntilIdle()

        assertEquals(listOf("1_0", "1_1", "1_2", "2_0"), reading.continuousTiles.map { it.key })
    }

    @Test
    fun `세로 스크롤 - 여백을 바꿔 타일이 다시 만들어져도 보던 자리로 돌아간다`() = runTest(testDispatcher) {
        readerPreferences.saveReaderType(ReaderType.CONTINUOUS)
        // 각 쪽 1000x3000 → 여백 0이면 쪽마다 타일 3개
        openBook(pages = (1..5).map { BookPage(number = it, width = 1000, height = 3000) })
        advanceUntilIdle()
        viewModel.onContinuousVisibleTilesChanged(listOf("4_2"))

        viewModel.setContinuousPadding(0.25f) // 너비 500 → 쪽마다 타일 2개, 4쪽 아래 절반이 2번째 타일
        advanceUntilIdle()

        assertEquals("4_1", reading.continuousTiles[reading.scrollToTileIndex!!].key)
    }

    @Test
    fun `로드 실패 - 쪽 목록을 못 받고 쪽 수도 0이면 오류를 보여 준다`() = runTest(testDispatcher) {
        seriesRepository.seriesMap["s1"] = createTestSeries(id = "s1")
        bookRepository.books["b1"] = createTestBook(id = "b1", seriesId = "s1", pageCount = 0)
        bookRepository.pagesError = java.io.IOException("Book analysis failed")

        viewModel.loadBook("b1")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is ReaderUiState.Error)
    }

    @Test
    fun `쪽 목록을 못 받아도 쪽 수를 알면 크기 없이 연다`() = runTest(testDispatcher) {
        seriesRepository.seriesMap["s1"] = createTestSeries(id = "s1")
        bookRepository.books["b1"] = createTestBook(id = "b1", seriesId = "s1", pageCount = 5)
        bookRepository.pagesError = RuntimeException("timeout")

        viewModel.loadBook("b1")
        advanceUntilIdle()

        assertEquals(5, reading.totalPages)
    }

    @Test
    fun `작품 설정 - 패널에서 바꾸면 이 작품에만 저장되고 앱 기본값은 그대로`() = runTest(testDispatcher) {
        openBook()
        advanceUntilIdle()
        assertFalse(reading.seriesCustomized)

        viewModel.applySettings(reading.settings.copy(pageLayout = PageLayout.DOUBLE_PAGES, imageEnhancement = ImageEnhancement.SHARPEN))
        advanceUntilIdle()

        assertTrue(reading.seriesCustomized)
        assertEquals(PageLayout.DOUBLE_PAGES, readerPreferences.series.value["s1"]!!.pageLayout)
        assertEquals(ImageEnhancement.SHARPEN, readerPreferences.series.value["s1"]!!.imageEnhancement)
        assertEquals(PageLayout.SINGLE_PAGE, readerPreferences.readerSettings.first().pageLayout)
    }

    @Test
    fun `작품 설정 - 저장된 작품 설정이 앱 기본값과 시리즈 방향보다 먼저다`() = runTest(testDispatcher) {
        readerPreferences.saveReaderSettings(ReaderSettings(pageLayout = PageLayout.SINGLE_PAGE))
        readerPreferences.series.value = mapOf(
            "s1" to ReaderSettings(pageLayout = PageLayout.DOUBLE_PAGES, readingDirection = ReadingDirection.LEFT_TO_RIGHT),
        )
        // 시리즈 메타데이터는 RTL이지만 사용자가 이 작품에 LTR을 골라 두었다
        openBook(direction = ReadingDirection.RIGHT_TO_LEFT)
        advanceUntilIdle()

        assertEquals(PageLayout.DOUBLE_PAGES, reading.pageLayout)
        assertEquals(ReadingDirection.LEFT_TO_RIGHT, reading.readingDirection)
        assertTrue(reading.seriesCustomized)
    }

    @Test
    fun `작품 설정 - 기본값으로 되돌리면 작품 설정을 지우고 앱 기본값을 쓴다`() = runTest(testDispatcher) {
        readerPreferences.saveReaderSettings(ReaderSettings(scaleType = ScaleType.FIT_WIDTH))
        readerPreferences.series.value = mapOf("s1" to ReaderSettings(scaleType = ScaleType.ORIGINAL))
        openBook()
        advanceUntilIdle()
        assertEquals(ScaleType.ORIGINAL, reading.scaleType)

        viewModel.resetSeriesSettings()
        advanceUntilIdle()

        assertEquals(ScaleType.FIT_WIDTH, reading.scaleType)
        assertFalse(reading.seriesCustomized)
        assertTrue(readerPreferences.series.value.isEmpty())
    }

    private fun addNextBook(id: String = "b2", pages: Int = 6) {
        bookRepository.books[id] = createTestBook(id = id, seriesId = "s1", pageCount = pages)
        bookRepository.pagesMap[id] = (1..pages).map { BookPage(number = it, width = 1000, height = 1000) }
        bookRepository.nextOf["b1"] = id
    }

    @Test
    fun `다음 권 - 마지막 쪽에서 앞으로 가면 책 끝 화면, 한 번 더 가면 다음 권 1쪽 (로딩 화면 없이)`() = runTest(testDispatcher) {
        openBook(progress = ReadProgress(page = 10, completed = false))
        addNextBook()
        advanceUntilIdle()
        assertEquals(10, reading.currentSpread.pages.first().number)

        viewModel.nextSpread()
        advanceUntilIdle()
        val end = reading.endPage!!
        assertTrue(end.nextResolved)
        assertEquals("b2", end.nextBook?.id)

        viewModel.nextSpread()
        // 다음 권을 불러오는 동안에도 로딩 화면으로 바뀌지 않는다
        assertTrue(viewModel.uiState.value is ReaderUiState.Reading)
        advanceUntilIdle()

        assertEquals(1, reading.currentSpread.pages.first().number)
        assertEquals(6, reading.totalPages)
        assertEquals(null, reading.endPage)
    }

    @Test
    fun `다음 권 - 책 끝 근처에서 미리 찾고 다음 권 첫 쪽을 미리 받는다`() = runTest(testDispatcher) {
        openBook(progress = ReadProgress(page = 5, completed = false))
        addNextBook()
        advanceUntilIdle()
        assertTrue(bookRepository.nextRequests.isEmpty())

        repeat(4) { viewModel.nextSpread() } // 9쪽 — 끝까지 펼침 1개
        advanceUntilIdle()

        assertEquals(listOf("b1"), bookRepository.nextRequests)
        assertTrue(reading.preloadSpreads.flatten().any { it.endsWith("/books/b2/pages/1") })
    }

    @Test
    fun `다음 권 - 넘어가기 전에 다 읽은 책을 완독으로 저장한다`() = runTest(testDispatcher) {
        openBook(progress = ReadProgress(page = 9, completed = false))
        addNextBook()
        advanceUntilIdle()

        viewModel.nextSpread() // 10쪽 (마지막)
        viewModel.nextSpread() // 책 끝
        viewModel.nextSpread() // 다음 권
        advanceUntilIdle()

        assertTrue(bookRepository.progressUpdates.contains(ProgressUpdate("b1", 10, true)))
    }

    @Test
    fun `다음 권 - 마지막 권이면 책 끝 화면에 머물고, 뒤로 가면 마지막 쪽`() = runTest(testDispatcher) {
        openBook(progress = ReadProgress(page = 10, completed = false))
        advanceUntilIdle()

        viewModel.nextSpread()
        advanceUntilIdle()
        assertEquals(null, reading.endPage!!.nextBook)
        assertTrue(reading.endPage!!.nextResolved)

        viewModel.nextSpread()
        advanceUntilIdle()
        assertTrue(reading.endPage != null)

        viewModel.previousSpread()
        assertEquals(null, reading.endPage)
        assertEquals(10, reading.currentSpread.pages.first().number)
    }

    @Test
    fun `다음 권 - 세로 스크롤은 마지막 쪽에서 다음이면 책 끝 카드로, 그다음 다음 권`() = runTest(testDispatcher) {
        readerPreferences.saveReaderType(ReaderType.CONTINUOUS)
        openBook()
        addNextBook()
        advanceUntilIdle()
        viewModel.onContinuousVisibleTilesChanged(listOf("10_0"))

        viewModel.onDpadRight()
        advanceUntilIdle()
        assertEquals(reading.continuousTiles.size, reading.scrollToTileIndex)
        assertEquals("b2", reading.endPage?.nextBook?.id)

        viewModel.onDpadRight()
        advanceUntilIdle()
        assertEquals(6, reading.totalPages)
    }

    private fun addPreviousBook(id: String = "b0", pages: Int = 8) {
        bookRepository.books[id] = createTestBook(id = id, seriesId = "s1", pageCount = pages)
        bookRepository.pagesMap[id] = (1..pages).map { BookPage(number = it, width = 1000, height = 1000) }
        bookRepository.previousOf["b1"] = id
    }

    @Test
    fun `이전 권 - 열자마자 찾고 마지막 쪽을 미리 받으며, 1쪽에서 뒤로 두 번이면 이전 권 마지막 쪽`() = runTest(testDispatcher) {
        openBook()
        addPreviousBook()
        advanceUntilIdle()
        assertEquals(listOf("b1"), bookRepository.previousRequests)
        assertTrue(reading.preloadSpreads.flatten().any { it.endsWith("/books/b0/pages/8") })

        viewModel.previousSpread()
        assertEquals("b0", reading.startPage!!.previousBook?.id)

        viewModel.previousSpread()
        // 이전 권을 불러오는 동안에도 로딩 화면으로 바뀌지 않는다
        assertTrue(viewModel.uiState.value is ReaderUiState.Reading)
        advanceUntilIdle()

        assertEquals(8, reading.totalPages)
        assertEquals(8, reading.currentSpread.pages.first().number)
        assertEquals(null, reading.startPage)
        // 끝에서 열기만 했으니 이전 권 진행은 그대로
        assertTrue(bookRepository.progressUpdates.none { it.bookId == "b0" })
    }

    @Test
    fun `이전 권 - 첫 권이면 책 첫 화면에 머물고, 앞으로 가면 1쪽`() = runTest(testDispatcher) {
        openBook()
        advanceUntilIdle()

        viewModel.previousSpread()
        viewModel.previousSpread()
        advanceUntilIdle()
        assertTrue(reading.startPage!!.previousResolved)
        assertEquals(null, reading.startPage!!.previousBook)

        viewModel.nextSpread()
        assertEquals(null, reading.startPage)
        assertEquals(1, reading.currentSpread.pages.first().number)
    }

    @Test
    fun `이전 권 - 책 중간에서 열면 처음 근처로 갈 때까지 찾지 않는다`() = runTest(testDispatcher) {
        openBook(progress = ReadProgress(page = 8, completed = false))
        addPreviousBook()
        advanceUntilIdle()
        assertTrue(bookRepository.previousRequests.isEmpty())

        repeat(4) { viewModel.previousSpread() } // 4쪽
        advanceUntilIdle()
        assertEquals(listOf("b1"), bookRepository.previousRequests)
    }

    @Test
    fun `이전 권 - 세로 스크롤은 1쪽 처음에서 이전이면 책 첫 카드로, 그다음 이전 권 끝`() = runTest(testDispatcher) {
        readerPreferences.saveReaderType(ReaderType.CONTINUOUS)
        openBook()
        addPreviousBook()
        advanceUntilIdle()
        // 1쪽이어도 맨 위 책 첫 카드 아래(첫 타일)로 스크롤한다
        assertEquals(0, reading.scrollToTileIndex)
        viewModel.onTileScrolled()
        viewModel.onContinuousVisibleTilesChanged(listOf("1_0"))

        viewModel.onDpadLeft()
        advanceUntilIdle()
        assertEquals(-1, reading.scrollToTileIndex)
        assertEquals("b0", reading.startPage?.previousBook?.id)

        viewModel.onDpadLeft()
        advanceUntilIdle()
        assertEquals(8, reading.totalPages)
        // 마지막 타일이 화면(1000px)을 채우게 — 쪽 하나가 화면 한 장
        assertEquals("8_0", reading.continuousTiles[reading.scrollToTileIndex!!].key)
    }

    @Test
    fun `EPUB 책은 EPUB 리더로 넘긴다`() = runTest(testDispatcher) {
        seriesRepository.seriesMap["s1"] = createTestSeries(id = "s1")
        bookRepository.books["e1"] = createTestBook(id = "e1", seriesId = "s1", pageCount = 0)
            .copy(mediaProfile = MediaProfile.EPUB)

        viewModel.loadBook("e1")
        advanceUntilIdle()

        assertEquals(ReaderUiState.Epub("e1"), viewModel.uiState.value)
    }

    @Test
    fun `세로 스크롤 설정 - 여백과 간격은 바로 반영되고 저장된다`() = runTest(testDispatcher) {
        readerPreferences.saveReaderType(ReaderType.CONTINUOUS)
        openBook()
        advanceUntilIdle()

        viewModel.setContinuousPadding(0.1f)
        viewModel.setContinuousPageSpacing(16)
        advanceUntilIdle()

        assertEquals(800, reading.continuousTiles.first().displayWidth)
        assertEquals(16, reading.continuousTiles.first().bottomSpacingDp)
        val saved = readerPreferences.series.value.values.single()
        assertEquals(0.1f, saved.continuousPadding)
        assertEquals(16, saved.continuousPageSpacing)
    }

    private fun createTestSeries(id: String, direction: ReadingDirection? = null) = Series(
        id = id,
        libraryId = "lib1",
        name = "Test Series",
        booksCount = 1,
        booksReadCount = 0,
        booksInProgressCount = 0,
        metadata = SeriesMetadata(
            title = "Test Series",
            summary = "",
            status = "ONGOING",
            publisher = "",
            genres = emptyList(),
            readingDirection = direction,
        ),
    )

    private fun createTestBook(
        id: String,
        seriesId: String,
        pageCount: Int,
        progress: ReadProgress? = null,
    ) = Book(
        id = id,
        seriesId = seriesId,
        seriesTitle = "Test Series",
        name = "Chapter 1",
        number = 1,
        pageCount = pageCount,
        metadata = BookMetadata(
            title = "Chapter 1",
            summary = "",
            number = "1",
        ),
        readProgress = progress,
    )

    private data class ProgressUpdate(val bookId: String, val page: Int, val completed: Boolean)

    private class FakeBookRepository : BookRepository {
        val books = mutableMapOf<String, Book>()
        val pagesMap = mutableMapOf<String, List<BookPage>>()
        val progressUpdates = mutableListOf<ProgressUpdate>()

        /** 책 ID → 다음 권 ID */
        val nextOf = mutableMapOf<String, String>()
        val nextRequests = mutableListOf<String>()

        override suspend fun getNextBook(bookId: String): Book? {
            nextRequests += bookId
            return nextOf[bookId]?.let { books[it] }
        }

        /** 책 ID → 이전 권 ID */
        val previousOf = mutableMapOf<String, String>()
        val previousRequests = mutableListOf<String>()

        override suspend fun getPreviousBook(bookId: String): Book? {
            previousRequests += bookId
            return previousOf[bookId]?.let { books[it] }
        }

        override fun getBooks(seriesId: String, page: Int, size: Int): Flow<BookPageResult> {
            throw NotImplementedError()
        }

        override fun getBooksInProgress(page: Int, size: Int): Flow<BookPageResult> {
            throw NotImplementedError()
        }

        override suspend fun getBookById(bookId: String): Book {
            return books[bookId] ?: throw NoSuchElementException("Book not found: $bookId")
        }

        var pagesError: Exception? = null

        override suspend fun getBookPages(bookId: String): List<BookPage> {
            pagesError?.let { throw it }
            return pagesMap[bookId] ?: (1..(books[bookId]?.pageCount ?: 1)).map { BookPage(number = it) }
        }

        override suspend fun updateReadProgress(bookId: String, page: Int, completed: Boolean) {
            progressUpdates.add(ProgressUpdate(bookId, page, completed))
        }

        override suspend fun getBooksOnDeck(size: Int): List<Book> = emptyList()

        override suspend fun getLatestBooks(size: Int): List<Book> = emptyList()

        override fun searchBooks(query: String, page: Int, size: Int): Flow<BookPageResult> = throw NotImplementedError()

        override suspend fun getEpubManifest(bookId: String): EpubManifest = throw NotImplementedError()

        override suspend fun getEpubPosition(bookId: String): EpubPosition? = null

        override suspend fun saveEpubPosition(bookId: String, position: EpubPosition) = Unit

        override suspend fun markRead(bookId: String) = Unit

        override suspend fun markUnread(bookId: String) = Unit

        override suspend fun analyze(bookId: String) = Unit
    }

    private class FakeSeriesRepository : SeriesRepository {
        val seriesMap = mutableMapOf<String, Series>()

        override fun getSeries(page: Int, size: Int, libraryId: String?): Flow<SeriesPageResult> {
            throw NotImplementedError()
        }

        override suspend fun getSeriesById(seriesId: String): Series {
            return seriesMap[seriesId] ?: throw NoSuchElementException("Series not found: $seriesId")
        }

        override fun searchSeries(query: String, page: Int, size: Int): Flow<SeriesPageResult> {
            throw NotImplementedError()
        }

        override suspend fun getNewSeries(size: Int): List<Series> = emptyList()

        override suspend fun getUpdatedSeries(size: Int): List<Series> = emptyList()
    }

    private class FakeReaderPreferences(
        initialSettings: ReaderSettings = ReaderSettings(),
    ) : ReaderPreferences {
        private val _settings = MutableStateFlow(initialSettings)
        override val readerSettings: Flow<ReaderSettings> = _settings.asStateFlow()

        /** 작품별 설정 (시리즈 ID → 설정) */
        val series = MutableStateFlow<Map<String, ReaderSettings>>(emptyMap())

        override suspend fun saveReaderSettings(settings: ReaderSettings) {
            _settings.value = settings
        }

        override suspend fun saveEpubTextZoom(zoom: Int) {
            _settings.value = _settings.value.copy(epubTextZoom = zoom)
        }

        override fun seriesSettings(seriesId: String): Flow<ReaderSettings?> = series.map { it[seriesId] }

        override suspend fun saveSeriesSettings(seriesId: String, settings: ReaderSettings) {
            series.value = series.value + (seriesId to settings)
        }

        override suspend fun clearSeriesSettings(seriesId: String) {
            series.value = series.value - seriesId
        }

        // 테스트 준비용: 앱 기본값 한 항목만 바꾸기
        suspend fun saveReadingDirection(direction: ReadingDirection) =
            saveReaderSettings(_settings.value.copy(readingDirection = direction))

        suspend fun savePageLayout(layout: PageLayout) = saveReaderSettings(_settings.value.copy(pageLayout = layout))

        suspend fun saveReaderType(type: ReaderType) = saveReaderSettings(_settings.value.copy(readerType = type))
    }

    private class FakeTileSource : TileSource {
        val sizes = mutableMapOf<Int, Pair<Int, Int>>()

        override suspend fun tile(bookId: String, tile: PageTile, sharpen: Float?): ImageBitmap = throw NotImplementedError()

        override suspend fun bounds(bookId: String, pageNumber: Int): Pair<Int, Int>? = sizes[pageNumber]

        override suspend fun prefetch(bookId: String, pageNumber: Int) = Unit

        override fun clear() = Unit
    }

    private object FakeImageUrls : ImageUrls {
        private const val BASE = "http://localhost:8080"
        override fun seriesThumbnail(seriesId: String) = "$BASE/api/v1/series/$seriesId/thumbnail"
        override fun bookThumbnail(bookId: String) = "$BASE/api/v1/books/$bookId/thumbnail"
        override fun page(bookId: String, pageNumber: Int) = "$BASE/api/v1/books/$bookId/pages/$pageNumber"
    }
}
