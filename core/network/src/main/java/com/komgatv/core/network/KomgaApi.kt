package com.komgatv.core.network

import com.komgatv.core.network.dto.BookDto
import com.komgatv.core.network.dto.LibraryDto
import com.komgatv.core.network.dto.PagedResponse
import com.komgatv.core.network.dto.ReadProgressUpdateDto
import com.komgatv.core.network.dto.SeriesDto
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Komga 서버 REST API 인터페이스
 *
 * Komga 서버와 통신하기 위한 Retrofit API 정의입니다.
 * 라이브러리, 시리즈, 책, 페이지 관련 엔드포인트를 포함합니다.
 *
 * @see <a href="https://komga.org/docs/api/">Komga API 문서</a>
 */
interface KomgaApi {

    // ========== 라이브러리 API ==========

    /** 모든 라이브러리 목록 조회 */
    @GET("api/v1/libraries")
    suspend fun getLibraries(): List<LibraryDto>

    // ========== 시리즈 API ==========

    /**
     * 시리즈 목록 조회 (페이지네이션 지원)
     *
     * @param page 페이지 번호 (0-based)
     * @param size 페이지 당 항목 수
     * @param libraryId 특정 라이브러리로 필터링 (선택)
     * @param search 검색어 (선택)
     */
    @GET("api/v1/series")
    suspend fun getSeries(
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20,
        @Query("library_id") libraryId: String? = null,
        @Query("search") search: String? = null,
    ): PagedResponse<SeriesDto>

    /** 시리즈 ID로 단일 시리즈 조회 */
    @GET("api/v1/series/{seriesId}")
    suspend fun getSeriesById(
        @Path("seriesId") seriesId: String,
    ): SeriesDto

    // ========== 책 API ==========

    /**
     * 시리즈에 속한 책 목록 조회
     *
     * @param seriesId 시리즈 ID
     * @param page 페이지 번호
     * @param size 페이지 당 항목 수
     */
    @GET("api/v1/series/{seriesId}/books")
    suspend fun getBooks(
        @Path("seriesId") seriesId: String,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20,
    ): PagedResponse<BookDto>

    /** 책 ID로 단일 책 조회 */
    @GET("api/v1/books/{bookId}")
    suspend fun getBookById(
        @Path("bookId") bookId: String,
    ): BookDto

    // ========== 페이지 API ==========

    /**
     * 책의 특정 페이지 이미지 조회
     *
     * @param bookId 책 ID
     * @param pageNumber 페이지 번호 (1-based)
     * @return 페이지 이미지 바이너리 데이터
     */
    @GET("api/v1/books/{bookId}/pages/{pageNumber}")
    suspend fun getPage(
        @Path("bookId") bookId: String,
        @Path("pageNumber") pageNumber: Int,
    ): ResponseBody

    /**
     * 책의 특정 페이지 썸네일 이미지 조회
     *
     * @param bookId 책 ID
     * @param pageNumber 페이지 번호 (1-based)
     * @return 썸네일 이미지 바이너리 데이터
     */
    @GET("api/v1/books/{bookId}/pages/{pageNumber}/thumbnail")
    suspend fun getPageThumbnail(
        @Path("bookId") bookId: String,
        @Path("pageNumber") pageNumber: Int,
    ): ResponseBody

    // ========== 읽기 진행 API ==========

    /**
     * 책의 읽기 진행 상태 업데이트
     *
     * @param bookId 책 ID
     * @param readProgress 업데이트할 진행 정보
     */
    @PATCH("api/v1/books/{bookId}/read-progress")
    suspend fun updateReadProgress(
        @Path("bookId") bookId: String,
        @Body readProgress: ReadProgressUpdateDto,
    )
}
