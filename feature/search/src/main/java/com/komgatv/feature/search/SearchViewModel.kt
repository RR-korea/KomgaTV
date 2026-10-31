package com.komgatv.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.komgatv.core.data.error.toLoadError
import com.komgatv.core.data.image.ImageUrls
import com.komgatv.core.data.repository.BookRepository
import com.komgatv.core.data.repository.SeriesRepository
import com.komgatv.core.model.Book
import com.komgatv.core.model.LoadError
import com.komgatv.core.model.Series
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/** 검색 결과 한 갈래 (시리즈 또는 책) */
data class SearchSection<T>(
    val items: List<T> = emptyList(),
    val total: Long = 0,
    val currentPage: Int = 0,
    val isLastPage: Boolean = true,
    val isLoadingMore: Boolean = false,
)

/** 검색 화면 상태 */
sealed interface SearchUiState {
    /** 검색어가 비어 있음 */
    data object Idle : SearchUiState

    data object Loading : SearchUiState

    data class Results(
        val query: String,
        val series: SearchSection<Series>,
        val books: SearchSection<Book>,
    ) : SearchUiState

    /** 맞는 시리즈도 책도 없음 */
    data class Empty(val query: String) : SearchUiState

    data class Error(val error: LoadError) : SearchUiState
}

/**
 * 시리즈·책 제목 검색 (Komga 전문 검색)
 *
 * 입력이 멈추고 [DEBOUNCE_MILLIS] 뒤에 시리즈와 책을 함께 찾는다. 새 검색어가 오면 앞 검색은 취소된다.
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val seriesRepository: SeriesRepository,
    private val bookRepository: BookRepository,
    val imageUrls: ImageUrls,
) : ViewModel() {

    companion object {
        const val DEBOUNCE_MILLIS = 400L
        const val PAGE_SIZE = 50
    }

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val loadMoreJobs = mutableListOf<Job>()

    init {
        viewModelScope.launch {
            _query.map { it.trim() }
                .debounce(DEBOUNCE_MILLIS)
                .distinctUntilChanged()
                .collectLatest { search(it) }
        }
    }

    /** 입력칸 글자나 음성 인식 결과 */
    fun onQueryChange(text: String) {
        _query.value = text
    }

    private suspend fun search(query: String) {
        loadMoreJobs.forEach { it.cancel() }
        loadMoreJobs.clear()
        if (query.isEmpty()) {
            _uiState.value = SearchUiState.Idle
            return
        }
        _uiState.value = SearchUiState.Loading
        try {
            coroutineScope {
                val series = async { seriesRepository.searchSeries(query, page = 0, size = PAGE_SIZE).first() }
                val books = async { bookRepository.searchBooks(query, page = 0, size = PAGE_SIZE).first() }
                val s = series.await()
                val b = books.await()
                _uiState.value = if (s.series.isEmpty() && b.books.isEmpty()) {
                    SearchUiState.Empty(query)
                } else {
                    SearchUiState.Results(
                        query = query,
                        series = SearchSection(s.series, s.totalElements, s.currentPage, s.isLast),
                        books = SearchSection(b.books, b.totalElements, b.currentPage, b.isLast),
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "검색 실패: $query")
            _uiState.value = SearchUiState.Error(e.toLoadError())
        }
    }

    /** 시리즈 줄 끝에 가까워지면 다음 페이지 */
    fun loadMoreSeries() = loadMore(
        section = { it.series },
        update = { state, section -> state.copy(series = section) },
        fetch = { query, page ->
            seriesRepository.searchSeries(query, page, PAGE_SIZE).first().let { Triple(it.series, it.currentPage, it.isLast) }
        },
    )

    /** 책 줄 끝에 가까워지면 다음 페이지 */
    fun loadMoreBooks() = loadMore(
        section = { it.books },
        update = { state, section -> state.copy(books = section) },
        fetch = { query, page ->
            bookRepository.searchBooks(query, page, PAGE_SIZE).first().let { Triple(it.books, it.currentPage, it.isLast) }
        },
    )

    private fun <T> loadMore(
        section: (SearchUiState.Results) -> SearchSection<T>,
        update: (SearchUiState.Results, SearchSection<T>) -> SearchUiState.Results,
        fetch: suspend (query: String, page: Int) -> Triple<List<T>, Int, Boolean>,
    ) {
        val current = _uiState.value as? SearchUiState.Results ?: return
        val start = section(current)
        if (start.isLastPage || start.isLoadingMore) return
        _uiState.value = update(current, start.copy(isLoadingMore = true))
        loadMoreJobs += viewModelScope.launch {
            try {
                val (items, page, isLast) = fetch(current.query, start.currentPage + 1)
                val latest = _uiState.value as? SearchUiState.Results ?: return@launch
                if (latest.query != current.query) return@launch
                val now = section(latest)
                _uiState.value = update(
                    latest,
                    now.copy(items = now.items + items, currentPage = page, isLastPage = isLast, isLoadingMore = false),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "검색 다음 페이지 실패")
                (_uiState.value as? SearchUiState.Results)?.let { latest ->
                    _uiState.value = update(latest, section(latest).copy(isLoadingMore = false))
                }
            }
        }
    }
}
