package com.komgatv.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.komgatv.core.data.error.toLoadError
import com.komgatv.core.data.image.ImageUrls
import com.komgatv.core.data.repository.SeriesRepository
import com.komgatv.core.model.LoadError
import com.komgatv.core.model.Series
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
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

/** 검색 화면 상태 */
sealed interface SearchUiState {
    /** 검색어가 비어 있음 */
    data object Idle : SearchUiState

    data object Loading : SearchUiState

    data class Results(
        val query: String,
        val series: List<Series>,
        val currentPage: Int,
        val isLastPage: Boolean,
        val isLoadingMore: Boolean = false,
    ) : SearchUiState

    /** 맞는 시리즈가 없음 */
    data class Empty(val query: String) : SearchUiState

    data class Error(val error: LoadError) : SearchUiState
}

/**
 * 시리즈 검색 (Komga 전문 검색)
 *
 * 입력이 멈추고 [DEBOUNCE_MILLIS] 뒤에 검색한다. 새 검색어가 오면 앞 검색은 취소된다.
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val seriesRepository: SeriesRepository,
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

    private var loadMoreJob: Job? = null

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
        loadMoreJob?.cancel()
        if (query.isEmpty()) {
            _uiState.value = SearchUiState.Idle
            return
        }
        _uiState.value = SearchUiState.Loading
        try {
            val result = seriesRepository.searchSeries(query, page = 0, size = PAGE_SIZE).first()
            _uiState.value = if (result.series.isEmpty()) {
                SearchUiState.Empty(query)
            } else {
                SearchUiState.Results(query, result.series, result.currentPage, result.isLast)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "검색 실패: $query")
            _uiState.value = SearchUiState.Error(e.toLoadError())
        }
    }

    /** 결과 끝에 가까워지면 다음 페이지 */
    fun loadMore() {
        val current = _uiState.value as? SearchUiState.Results ?: return
        if (current.isLastPage || current.isLoadingMore) return
        _uiState.value = current.copy(isLoadingMore = true)
        loadMoreJob = viewModelScope.launch {
            try {
                val next = seriesRepository.searchSeries(current.query, current.currentPage + 1, PAGE_SIZE).first()
                val latest = _uiState.value as? SearchUiState.Results ?: return@launch
                if (latest.query != current.query) return@launch
                _uiState.value = latest.copy(
                    series = latest.series + next.series,
                    currentPage = next.currentPage,
                    isLastPage = next.isLast,
                    isLoadingMore = false,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "검색 다음 페이지 실패")
                (_uiState.value as? SearchUiState.Results)?.let { _uiState.value = it.copy(isLoadingMore = false) }
            }
        }
    }
}
