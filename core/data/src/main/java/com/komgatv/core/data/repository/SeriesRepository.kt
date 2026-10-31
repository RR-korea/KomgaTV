package com.komgatv.core.data.repository

import com.komgatv.core.model.Series
import com.komgatv.core.network.KomgaApi
import com.komgatv.core.network.dto.PagedResponse
import com.komgatv.core.network.dto.SeriesDto
import com.komgatv.core.network.dto.toDomain
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 시리즈 저장소 인터페이스
 *
 * Komga 서버의 시리즈 데이터에 접근하기 위한 추상화 계층입니다.
 */
interface SeriesRepository {

    /**
     * 시리즈 목록 조회 (페이지네이션)
     *
     * @param page 페이지 번호 (0-based)
     * @param size 페이지 크기
     * @param libraryId 라이브러리 ID 필터 (선택)
     * @return 시리즈 목록과 페이지 정보를 포함한 결과
     */
    fun getSeries(
        page: Int = 0,
        size: Int = 20,
        libraryId: String? = null,
    ): Flow<SeriesPageResult>

    /** ID로 단일 시리즈 조회 */
    suspend fun getSeriesById(seriesId: String): Series

    /**
     * 시리즈 검색
     *
     * @param query 검색어
     * @param page 페이지 번호
     * @param size 페이지 크기
     */
    fun searchSeries(
        query: String,
        page: Int = 0,
        size: Int = 20,
    ): Flow<SeriesPageResult>
}

/**
 * 시리즈 페이지 결과
 *
 * 페이지네이션 정보와 함께 시리즈 목록을 포함합니다.
 *
 * @property series 현재 페이지의 시리즈 목록
 * @property totalPages 전체 페이지 수
 * @property totalElements 전체 항목 수
 * @property currentPage 현재 페이지 번호
 * @property isLast 마지막 페이지 여부
 */
data class SeriesPageResult(
    val series: List<Series>,
    val totalPages: Int,
    val totalElements: Long,
    val currentPage: Int,
    val isLast: Boolean,
)

/**
 * 시리즈 저장소 구현체
 */
@Singleton
class SeriesRepositoryImpl @Inject constructor(
    private val komgaApi: KomgaApi,
) : SeriesRepository {

    override fun getSeries(
        page: Int,
        size: Int,
        libraryId: String?,
    ): Flow<SeriesPageResult> = flow {
        val response = komgaApi.getSeries(
            page = page,
            size = size,
            libraryId = libraryId,
        )
        emit(response.toSeriesPageResult())
    }

    override suspend fun getSeriesById(seriesId: String): Series {
        return komgaApi.getSeriesById(seriesId).toDomain()
    }

    override fun searchSeries(
        query: String,
        page: Int,
        size: Int,
    ): Flow<SeriesPageResult> = flow {
        val response = komgaApi.getSeries(
            page = page,
            size = size,
            search = query,
        )
        emit(response.toSeriesPageResult())
    }
}

/** PagedResponse를 SeriesPageResult로 변환하는 확장 함수 */
private fun PagedResponse<SeriesDto>.toSeriesPageResult(): SeriesPageResult {
    return SeriesPageResult(
        series = content.map { it.toDomain() },
        totalPages = totalPages,
        totalElements = totalElements,
        currentPage = number,
        isLast = last,
    )
}
