package com.komgatv.core.data.repository

import com.komgatv.core.data.mapper.toDomain
import com.komgatv.core.model.Book
import com.komgatv.core.model.BookPage
import com.komgatv.core.model.EpubChapter
import com.komgatv.core.model.EpubManifest
import com.komgatv.core.model.EpubTocEntry
import com.komgatv.core.model.EpubPosition
import com.komgatv.core.network.ServerSessionManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import snd.komga.client.book.KomgaBook
import snd.komga.client.book.KomgaBookId
import snd.komga.client.book.KomgaBookReadProgressUpdateRequest
import snd.komga.client.book.KomgaBookSearch
import snd.komga.client.book.KomgaReadStatus
import snd.komga.client.book.R2Device
import snd.komga.client.book.R2Location
import snd.komga.client.book.R2Locator
import snd.komga.client.book.R2Progression
import snd.komga.client.book.WPLink
import snd.komga.client.common.KomgaPageRequest
import snd.komga.client.common.KomgaSort.KomgaBooksSort
import snd.komga.client.common.Page
import snd.komga.client.search.allOfBooks
import snd.komga.client.series.KomgaSeriesId
import timber.log.Timber
import javax.inject.Inject
import kotlin.time.Clock
import javax.inject.Singleton

interface BookRepository {
    fun getBooks(seriesId: String, page: Int = 0, size: Int = 20): Flow<BookPageResult>
    fun getBooksInProgress(page: Int = 0, size: Int = 20): Flow<BookPageResult>
    suspend fun getBookById(bookId: String): Book

    /** 책의 페이지 목록(번호·가로·세로 크기). 두 쪽 보기의 펼침 계산에 쓴다. */
    suspend fun getBookPages(bookId: String): List<BookPage>

    /** page는 1부터 시작한다. */
    suspend fun updateReadProgress(bookId: String, page: Int, completed: Boolean)

    /** 다음에 읽을 책 — 읽던 시리즈의 다음 권 (Komga on deck) */
    suspend fun getBooksOnDeck(size: Int = 20): List<Book>

    /** 최근 추가된 책 */
    suspend fun getLatestBooks(size: Int = 20): List<Book>

    /** 책 제목 전문 검색 */
    fun searchBooks(query: String, page: Int = 0, size: Int = 20): Flow<BookPageResult>

    /** EPUB 읽기 순서(장 목록)와 목차 */
    suspend fun getEpubManifest(bookId: String): EpubManifest

    /** EPUB 읽은 자리. 읽은 적 없으면 null */
    suspend fun getEpubPosition(bookId: String): EpubPosition?

    /** 같은 시리즈의 다음 권 (Komga 정렬 순서). 마지막 권이면 null */
    suspend fun getNextBook(bookId: String): Book?

    /** 같은 시리즈의 이전 권 (Komga 정렬 순서). 첫 권이면 null */
    suspend fun getPreviousBook(bookId: String): Book?

    /** 다 읽음으로 표시 */
    suspend fun markRead(bookId: String)

    /** 읽지 않음으로 — 읽기 진행(EPUB 읽은 자리 포함)을 지운다 */
    suspend fun markUnread(bookId: String)

    /** 서버에 책 분석을 다시 요청한다 (관리자만, 분석은 서버에서 따로 진행) */
    suspend fun analyze(bookId: String)

    /** EPUB 읽은 자리 저장 (Komga가 읽기 진행으로 바꾼다) */
    suspend fun saveEpubPosition(bookId: String, position: EpubPosition)
}

/** 책 목록 한 페이지. currentPage는 0부터 시작한다. */
data class BookPageResult(
    val books: List<Book>,
    val totalPages: Int,
    val totalElements: Long,
    val currentPage: Int,
    val isLast: Boolean,
)

@Singleton
class BookRepositoryImpl @Inject constructor(
    private val sessionManager: ServerSessionManager,
    private val progressEvents: ReadProgressEvents,
) : BookRepository {

    override fun getBooks(seriesId: String, page: Int, size: Int): Flow<BookPageResult> = flow {
        val condition = allOfBooks { seriesId { isEqualTo(KomgaSeriesId(seriesId)) } }.toBookCondition()
        val result = sessionManager.requireClient().bookClient().getBookList(
            search = KomgaBookSearch(condition = condition),
            pageRequest = KomgaPageRequest(pageIndex = page, size = size, sort = KomgaBooksSort.byNumberAsc()),
        )
        emit(result.toBookPageResult())
    }

    override fun getBooksInProgress(page: Int, size: Int): Flow<BookPageResult> = flow {
        val condition = allOfBooks {
            readStatus { isEqualTo(KomgaReadStatus.IN_PROGRESS) }
        }.toBookCondition()
        val result = sessionManager.requireClient().bookClient().getBookList(
            search = KomgaBookSearch(condition = condition),
            pageRequest = KomgaPageRequest(
                pageIndex = page,
                size = size,
                sort = KomgaBooksSort.byReadDateDesc(),
            ),
        )
        emit(result.toBookPageResult())
    }

    override suspend fun getBookById(bookId: String): Book =
        sessionManager.requireClient().bookClient().getOne(KomgaBookId(bookId)).toDomain()

    override suspend fun getBookPages(bookId: String): List<BookPage> =
        sessionManager.requireClient().bookClient().getBookPages(KomgaBookId(bookId)).map { it.toDomain() }

    override suspend fun updateReadProgress(bookId: String, page: Int, completed: Boolean) {
        sessionManager.requireClient().bookClient().markReadProgress(
            bookId = KomgaBookId(bookId),
            request = KomgaBookReadProgressUpdateRequest(page = page, completed = completed),
        )
        progressEvents.notifyUpdated(bookId)
    }

    override suspend fun getBooksOnDeck(size: Int): List<Book> =
        sessionManager.requireClient().bookClient()
            .getBooksOnDeck(libraryIds = emptyList(), pageRequest = KomgaPageRequest(size = size))
            .content.map { it.toDomain() }

    override suspend fun getLatestBooks(size: Int): List<Book> =
        sessionManager.requireClient().bookClient()
            .getLatestBooks(KomgaPageRequest(size = size))
            .content.map { it.toDomain() }

    override suspend fun getEpubManifest(bookId: String): EpubManifest {
        val manifest = sessionManager.requireClient().bookClient().getWebPubManifest(KomgaBookId(bookId))
        return EpubManifest(
            chapters = manifest.readingOrder.mapNotNull { link -> link.href?.let { EpubChapter(href = it, title = link.title) } },
            toc = flattenToc(manifest.toc, depth = 0),
        )
    }

    /** 목차 나무를 들여쓰기 단계와 함께 한 줄로 편다 (제목이나 주소가 없는 항목은 건너뛰고 아래 항목은 남긴다) */
    private fun flattenToc(links: List<WPLink>, depth: Int): List<EpubTocEntry> = links.flatMap { link ->
        val title = link.title?.trim().orEmpty()
        val href = link.href
        val self = if (title.isNotEmpty() && href != null) listOf(EpubTocEntry(title, href, depth)) else emptyList()
        self + flattenToc(link.children, depth + 1)
    }

    // 다음 권이 없으면 komga-client가 null을 돌려준다 (서버 404)
    override suspend fun getNextBook(bookId: String): Book? =
        sessionManager.requireClient().bookClient().getBookSiblingNext(KomgaBookId(bookId))?.toDomain()

    override suspend fun getPreviousBook(bookId: String): Book? =
        sessionManager.requireClient().bookClient().getBookSiblingPrevious(KomgaBookId(bookId))?.toDomain()

    override suspend fun markRead(bookId: String) {
        sessionManager.requireClient().bookClient().markReadProgress(
            bookId = KomgaBookId(bookId),
            request = KomgaBookReadProgressUpdateRequest(completed = true),
        )
        progressEvents.notifyUpdated(bookId)
    }

    override suspend fun markUnread(bookId: String) {
        sessionManager.requireClient().bookClient().deleteReadProgress(KomgaBookId(bookId))
        progressEvents.notifyUpdated(bookId)
    }

    override suspend fun analyze(bookId: String) {
        sessionManager.requireClient().bookClient().analyze(KomgaBookId(bookId))
    }

    override suspend fun getEpubPosition(bookId: String): EpubPosition? {
        val progression = try {
            sessionManager.requireClient().bookClient().getReadiumProgression(KomgaBookId(bookId))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // 읽은 적 없으면 Komga는 내용 없이 답한다
            Timber.d(e, "EPUB 읽은 자리 없음: $bookId")
            return null
        } ?: return null
        val href = progression.locator.href ?: return null
        val locations = progression.locator.locations
        return EpubPosition(
            href = href,
            progression = locations?.progression ?: 0f,
            totalProgression = locations?.totalProgression ?: 0f,
        )
    }

    override suspend fun saveEpubPosition(bookId: String, position: EpubPosition) {
        sessionManager.requireClient().bookClient().updateReadiumProgression(
            KomgaBookId(bookId),
            R2Progression(
                modified = Clock.System.now(),
                device = R2Device(id = DEVICE_ID, name = DEVICE_NAME),
                locator = R2Locator(
                    // Komga는 책 안의 경로를 받는다 (manifest의 href는 서버 주소가 붙은 전체 URL)
                    href = position.href.substringAfter("/resource/"),
                    type = "application/xhtml+xml",
                    locations = R2Location(
                        progression = position.progression,
                        totalProgression = position.totalProgression,
                    ),
                ),
            ),
        )
        progressEvents.notifyUpdated(bookId)
    }

    private companion object {
        const val DEVICE_ID = "komgatv"
        const val DEVICE_NAME = "KomgaTV"
    }

    override fun searchBooks(query: String, page: Int, size: Int): Flow<BookPageResult> = flow {
        val result = sessionManager.requireClient().bookClient().getBookList(
            search = KomgaBookSearch(fullTextSearch = query),
            pageRequest = KomgaPageRequest(pageIndex = page, size = size),
        )
        emit(result.toBookPageResult())
    }
}

private fun Page<KomgaBook>.toBookPageResult() = BookPageResult(
    books = content.map { it.toDomain() },
    totalPages = totalPages,
    totalElements = totalElements.toLong(),
    currentPage = number,
    isLast = last,
)
