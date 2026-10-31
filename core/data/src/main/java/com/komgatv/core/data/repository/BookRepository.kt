package com.komgatv.core.data.repository

import com.komgatv.core.model.Book
import com.komgatv.core.network.KomgaApi
import com.komgatv.core.network.dto.BookDto
import com.komgatv.core.network.dto.PagedResponse
import com.komgatv.core.network.dto.ReadProgressUpdateDto
import com.komgatv.core.network.dto.toDomain
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import okhttp3.ResponseBody
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 책 저장소 인터페이스
 *
 * Komga 서버의 책 데이터에 접근하기 위한 추상화 계층입니다.
 */
interface BookRepository {

    /**
     * 시리즈에 속한 책 목록 조회
     *
     * @param seriesId 시리즈 ID
     * @param page 페이지 번호
     * @param size 페이지 크기
     */
    fun getBooks(
        seriesId: String,
        page: Int = 0,
        size: Int = 20,
    ): Flow<BookPageResult>

    /** ID로 단일 책 조회 */
    suspend fun getBookById(bookId: String): Book

    /**
     * 책의 특정 페이지 이미지 조회
     *
     * @param bookId 책 ID
     * @param pageNumber 페이지 번호 (1-based)
     * @return 이미지 바이너리 데이터
     */
    suspend fun getPage(bookId: String, pageNumber: Int): ResponseBody

    /**
     * 읽기 진행 상태 업데이트
     *
     * @param bookId 책 ID
     * @param page 현재 페이지 번호
     * @param completed 완독 여부
     */
    suspend fun updateReadProgress(bookId: String, page: Int, completed: Boolean)
}

/**
 * 책 페이지 결과
 *
 * @property books 현재 페이지의 책 목록
 * @property totalPages 전체 페이지 수
 * @property totalElements 전체 항목 수
 * @property currentPage 현재 페이지 번호
 * @property isLast 마지막 페이지 여부
 */
data class BookPageResult(
    val books: List<Book>,
    val totalPages: Int,
    val totalElements: Long,
    val currentPage: Int,
    val isLast: Boolean,
)

/**
 * 책 저장소 구현체
 */
@Singleton
class BookRepositoryImpl @Inject constructor(
    private val komgaApi: KomgaApi,
) : BookRepository {

    override fun getBooks(
        seriesId: String,
        page: Int,
        size: Int,
    ): Flow<BookPageResult> = flow {
        val response = komgaApi.getBooks(
            seriesId = seriesId,
            page = page,
            size = size,
        )
        emit(response.toBookPageResult())
    }

    override suspend fun getBookById(bookId: String): Book {
        return komgaApi.getBookById(bookId).toDomain()
    }

    override suspend fun getPage(bookId: String, pageNumber: Int): ResponseBody {
        return komgaApi.getPage(bookId, pageNumber)
    }

    /**
     * 읽기 진행 상태를 서버에 동기화
     *
     * 현재 읽고 있는 페이지와 완독 여부를 서버에 전송합니다.
     */
    override suspend fun updateReadProgress(bookId: String, page: Int, completed: Boolean) {
        komgaApi.updateReadProgress(
            bookId = bookId,
            readProgress = ReadProgressUpdateDto(
                page = page,
                completed = completed,
            ),
        )
    }
}

/** PagedResponse를 BookPageResult로 변환 */
private fun PagedResponse<BookDto>.toBookPageResult(): BookPageResult {
    return BookPageResult(
        books = content.map { it.toDomain() },
        totalPages = totalPages,
        totalElements = totalElements,
        currentPage = number,
        isLast = last,
    )
}
