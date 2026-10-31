package com.komgatv.core.data.repository

import com.komgatv.core.data.mapper.toDomain
import com.komgatv.core.model.Book
import com.komgatv.core.model.BookPage
import com.komgatv.core.network.ServerSessionManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import snd.komga.client.book.KomgaBook
import snd.komga.client.book.KomgaBookId
import snd.komga.client.book.KomgaBookReadProgressUpdateRequest
import snd.komga.client.book.KomgaBookSearch
import snd.komga.client.book.KomgaReadStatus
import snd.komga.client.common.KomgaPageRequest
import snd.komga.client.common.KomgaSort.KomgaBooksSort
import snd.komga.client.common.Page
import snd.komga.client.search.allOfBooks
import snd.komga.client.series.KomgaSeriesId
import javax.inject.Inject
import javax.inject.Singleton

interface BookRepository {
    fun getBooks(seriesId: String, page: Int = 0, size: Int = 20): Flow<BookPageResult>
    fun getBooksInProgress(page: Int = 0, size: Int = 20): Flow<BookPageResult>
    suspend fun getBookById(bookId: String): Book

    /** 책의 페이지 목록(크기/메타데이터 포함)을 조회합니다. 기본값은 빈 목록입니다. */
    suspend fun getBookPages(bookId: String): List<BookPage> = emptyList()

    /** page는 1부터 시작한다. */
    suspend fun updateReadProgress(bookId: String, page: Int, completed: Boolean)
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
    }
}

private fun Page<KomgaBook>.toBookPageResult() = BookPageResult(
    books = content.map { it.toDomain() },
    totalPages = totalPages,
    totalElements = totalElements.toLong(),
    currentPage = number,
    isLast = last,
)
