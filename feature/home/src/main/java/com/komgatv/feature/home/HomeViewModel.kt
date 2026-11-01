package com.komgatv.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.komgatv.core.data.error.toLoadError
import com.komgatv.core.data.image.ImageUrls
import com.komgatv.core.data.repository.BookRepository
import com.komgatv.core.data.repository.LibraryRepository
import com.komgatv.core.data.repository.ReadProgressEvents
import com.komgatv.core.datastore.HomePreferences
import com.komgatv.core.data.repository.SeriesRepository
import com.komgatv.core.model.Book
import com.komgatv.core.model.Library
import com.komgatv.core.model.LoadError
import com.komgatv.core.model.Series
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * 홈 화면 UI 상태 (MVI 패턴)
 */
sealed interface HomeUiState {

    /** 데이터 로딩 중 */
    data object Loading : HomeUiState

    /**
     * 데이터 로딩 성공 (Komelia 홈과 같은 줄)
     *
     * @property continueReading 읽는 중인 책 (IN_PROGRESS, 최근 읽은 순)
     * @property onDeck 다음에 읽을 책 (읽던 시리즈의 다음 권)
     * @property latestBooks 최근 추가된 책
     * @property newSeries 최근 추가된 시리즈
     * @property updatedSeries 최근에 책이 더해진 시리즈
     * @property libraries 라이브러리 목록
     */
    data class Success(
        val continueReading: List<Book> = emptyList(),
        val onDeck: List<Book> = emptyList(),
        val latestBooks: List<Book> = emptyList(),
        val newSeries: List<Series> = emptyList(),
        val updatedSeries: List<Series> = emptyList(),
        val libraries: List<Library> = emptyList(),
    ) : HomeUiState

    /** 에러 발생 — 화면이 [error] 종류를 현재 언어 문구로 보여준다 */
    data class Error(val error: LoadError) : HomeUiState
}

/**
 * 홈 화면 ViewModel
 *
 * 줄들을 한꺼번에 읽는다. 라이브러리·읽는 중은 꼭 필요하고(실패하면 오류 화면),
 * 나머지 줄은 실패하면 빈 줄로 둔다 (오래된 Komga에 없는 API일 수도 있다).
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
    private val bookRepository: BookRepository,
    private val seriesRepository: SeriesRepository,
    private val progressEvents: ReadProgressEvents,
    private val homePreferences: HomePreferences,
    val imageUrls: ImageUrls,
) : ViewModel() {

    companion object {
        const val ROW_SIZE = 20
    }

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadHomeData()
        // 리더를 나올 때의 저장이 돌아온 뒤에 끝나도 "읽는 중"·"다음 권" 줄을 맞춘다
        viewModelScope.launch {
            progressEvents.updates.collect { refreshReadingRows() }
        }
    }

    /**
     * 홈 화면 데이터 로드
     */
    fun loadHomeData() {
        viewModelScope.launch {
            // 이미 내용이 보이면 로딩 화면으로 바꾸지 않는다 (포커스·스크롤 위치 유지)
            val showing = _uiState.value as? HomeUiState.Success
            if (showing == null) _uiState.value = HomeUiState.Loading

            try {
                coroutineScope {
                    val libraries = async { libraryRepository.getLibraries().first() }
                    val continueReading = async { loadContinueReading() }
                    val onDeck = async { optionalRow("다음 권") { bookRepository.getBooksOnDeck(ROW_SIZE) } }
                    val latestBooks = async { optionalRow("최근 추가된 책") { bookRepository.getLatestBooks(ROW_SIZE) } }
                    val newSeries = async { optionalRow("최근 추가된 시리즈") { seriesRepository.getNewSeries(ROW_SIZE) } }
                    val updatedSeries = async {
                        optionalRow("최근 업데이트된 시리즈") { seriesRepository.getUpdatedSeries(ROW_SIZE) }
                    }

                    val reading = readingRows(continueReading.await(), onDeck.await())
                    _uiState.value = HomeUiState.Success(
                        continueReading = reading.continueReading,
                        onDeck = reading.onDeck,
                        latestBooks = latestBooks.await(),
                        newSeries = newSeries.await(),
                        updatedSeries = updatedSeries.await(),
                        libraries = libraries.await(),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "홈 데이터 로드 실패")
                // 보이는 내용이 있으면 그대로 두고, 처음 읽기에 실패했을 때만 오류 화면을 보인다
                if (showing != null) return@launch
                _uiState.value = HomeUiState.Error(e.toLoadError())
            }
        }
    }

    /** 실패해도 홈 전체를 막지 않는 줄 */
    private suspend fun <T> optionalRow(name: String, load: suspend () -> List<T>): List<T> = try {
        load()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.w(e, "홈 줄 불러오기 실패: $name")
        emptyList()
    }

    /** "읽는 중에서 빼기"로 뺀 시리즈를 거른다. 뺀 뒤에 다시 읽은 시리즈는 다시 보이고 기록을 지운다 */
    private suspend fun readingRows(continueReading: List<Book>, onDeck: List<Book>): ReadingRows {
        val rows = filterReadingRows(continueReading, onDeck, homePreferences.hiddenReadingSeries.first())
        homePreferences.unhideReadingSeries(rows.reactivated)
        return rows
    }

    /**
     * 이 책의 시리즈를 "읽는 중"과 "다음 권"에서 뺀다.
     * 읽던 책이면 서버의 읽기 진행도 지운다(다른 기기의 "읽는 중"에서도 빠진다).
     * "다음 권"은 서버에서 지울 것이 없어 이 TV에서만 숨긴다 — 그 시리즈를 다시 읽으면 다시 보인다.
     */
    fun hideFromReading(book: Book) {
        viewModelScope.launch {
            // TV 시계가 서버보다 늦어도 방금 읽은 책이 "다시 읽음"으로 보이지 않게 서버의 읽은 시각보다 뒤로
            val at = maxOf(System.currentTimeMillis(), book.readProgress?.readDateMillis ?: 0L)
            homePreferences.hideReadingSeries(book.seriesId, at)
            (_uiState.value as? HomeUiState.Success)?.let { state ->
                _uiState.value = state.copy(
                    continueReading = state.continueReading.filter { it.seriesId != book.seriesId },
                    onDeck = state.onDeck.filter { it.seriesId != book.seriesId },
                )
            }
            val progress = book.readProgress
            if (progress != null && !progress.completed) {
                try {
                    bookRepository.markUnread(book.id)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // 서버에서 못 지워도 이 TV에서는 빠진 채로 둔다
                    Timber.w(e, "읽기 진행 지우기 실패: ${book.id}")
                }
            }
        }
    }

    /** 읽는 중인 책 목록 로드 (IN_PROGRESS, 최근 읽은 순) */
    private suspend fun loadContinueReading(): List<Book> {
        return bookRepository.getBooksInProgress(page = 0, size = ROW_SIZE).first().books
    }

    /** 읽기 진행이 바뀌면 달라지는 줄("읽는 중", "다음 권")만 다시 읽는다 (내용이 보일 때만) */
    private suspend fun refreshReadingRows() {
        if (_uiState.value !is HomeUiState.Success) return
        try {
            coroutineScope {
                val continueReading = async { loadContinueReading() }
                val onDeck = async { optionalRow("다음 권") { bookRepository.getBooksOnDeck(ROW_SIZE) } }
                val reading = readingRows(continueReading.await(), onDeck.await())
                (_uiState.value as? HomeUiState.Success)?.let {
                    _uiState.value = it.copy(continueReading = reading.continueReading, onDeck = reading.onDeck)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "읽는 중 목록 새로고침 실패")
        }
    }

    /** 라이브러리 메뉴 결과 — 화면이 짧게 알려 준다 */
    private val _libraryMessage = MutableStateFlow<LibraryActionResult?>(null)
    val libraryMessage: StateFlow<LibraryActionResult?> = _libraryMessage.asStateFlow()

    /** 라이브러리 다시 훑기. 서버가 따로 하므로 요청만 보낸다. */
    fun scanLibrary(libraryId: String) = libraryAction(LibraryActionResult.ScanRequested) {
        libraryRepository.scan(libraryId)
    }

    /** 휴지통 비우기 — 지운 파일의 책이 목록에서 사라진다. 홈 줄을 다시 읽는다. */
    fun emptyTrash(libraryId: String) = libraryAction(LibraryActionResult.TrashEmptied) {
        libraryRepository.emptyTrash(libraryId)
        loadHomeData()
    }

    fun clearLibraryMessage() {
        _libraryMessage.value = null
    }

    private fun libraryAction(success: LibraryActionResult, action: suspend () -> Unit) {
        viewModelScope.launch {
            _libraryMessage.value = try {
                action()
                success
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "라이브러리 메뉴 실패")
                LibraryActionResult.Failed
            }
        }
    }

    /** 데이터 새로고침 (다른 화면에서 돌아왔을 때 — 읽기 진행이 바뀌었을 수 있다) */
    fun refresh() {
        loadHomeData()
    }
}

/** 라이브러리 메뉴 결과 */
enum class LibraryActionResult { ScanRequested, TrashEmptied, Failed }
