package com.komgatv.core.data.repository

import com.komgatv.core.data.mapper.toDomain
import com.komgatv.core.model.Series
import com.komgatv.core.network.ServerSessionManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import snd.komga.client.common.KomgaPageRequest
import snd.komga.client.common.KomgaSort
import snd.komga.client.common.KomgaSort.KomgaSeriesSort
import snd.komga.client.common.Page
import snd.komga.client.library.KomgaLibraryId
import snd.komga.client.series.KomgaSeries
import snd.komga.client.series.KomgaSeriesId
import snd.komga.client.series.KomgaSeriesSearch
import snd.komga.client.search.allOfSeries
import javax.inject.Inject
import javax.inject.Singleton

interface SeriesRepository {
    fun getSeries(page: Int = 0, size: Int = 20, libraryId: String? = null): Flow<SeriesPageResult>
    suspend fun getSeriesById(seriesId: String): Series
    fun searchSeries(query: String, page: Int = 0, size: Int = 20): Flow<SeriesPageResult>

    /** 최근 추가된 시리즈 */
    suspend fun getNewSeries(size: Int = 20): List<Series>

    /** 최근에 책이 더해진 시리즈 */
    suspend fun getUpdatedSeries(size: Int = 20): List<Series>
}

/** 시리즈 목록 한 페이지. currentPage는 0부터 시작한다. */
data class SeriesPageResult(
    val series: List<Series>,
    val totalPages: Int,
    val totalElements: Long,
    val currentPage: Int,
    val isLast: Boolean,
)

@Singleton
class SeriesRepositoryImpl @Inject constructor(
    private val sessionManager: ServerSessionManager,
) : SeriesRepository {

    override fun getSeries(page: Int, size: Int, libraryId: String?): Flow<SeriesPageResult> = flow {
        val condition = libraryId?.let { id ->
            allOfSeries { library { isEqualTo(KomgaLibraryId(id)) } }.toSeriesCondition()
        }
        emit(fetch(KomgaSeriesSearch(condition = condition), page, size, KomgaSeriesSort.byTitleAsc()))
    }

    override suspend fun getSeriesById(seriesId: String): Series =
        sessionManager.requireClient().seriesClient().getOneSeries(KomgaSeriesId(seriesId)).toDomain()

    override fun searchSeries(query: String, page: Int, size: Int): Flow<SeriesPageResult> = flow {
        emit(fetch(KomgaSeriesSearch(fullTextSearch = query), page, size, KomgaSort.Unsorted))
    }

    override suspend fun getNewSeries(size: Int): List<Series> =
        sessionManager.requireClient().seriesClient()
            .getNewSeries(libraryIds = emptyList(), oneshot = null, deleted = false, pageRequest = KomgaPageRequest(size = size))
            .content.map { it.toDomain() }

    override suspend fun getUpdatedSeries(size: Int): List<Series> =
        sessionManager.requireClient().seriesClient()
            .getUpdatedSeries(libraryIds = emptyList(), oneshot = null, deleted = false, pageRequest = KomgaPageRequest(size = size))
            .content.map { it.toDomain() }

    private suspend fun fetch(search: KomgaSeriesSearch, page: Int, size: Int, sort: KomgaSort): SeriesPageResult =
        sessionManager.requireClient().seriesClient()
            .getSeriesList(search, KomgaPageRequest(pageIndex = page, size = size, sort = sort))
            .toSeriesPageResult()
}

private fun Page<KomgaSeries>.toSeriesPageResult() = SeriesPageResult(
    series = content.map { it.toDomain() },
    totalPages = totalPages,
    totalElements = totalElements.toLong(),
    currentPage = number,
    isLast = last,
)
