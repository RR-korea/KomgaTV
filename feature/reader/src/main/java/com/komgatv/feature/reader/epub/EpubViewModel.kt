package com.komgatv.feature.reader.epub

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.komgatv.core.data.epub.EpubResource
import com.komgatv.core.data.epub.EpubResourceLoader
import com.komgatv.core.data.error.toLoadError
import com.komgatv.core.data.repository.BookRepository
import com.komgatv.core.datastore.ReaderPreferences
import com.komgatv.core.model.EpubChapter
import com.komgatv.core.model.EpubPosition
import com.komgatv.core.model.EpubTocEntry
import com.komgatv.core.model.LoadError
import com.komgatv.core.model.displayName
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/** EPUB 리더 상태 */
sealed interface EpubUiState {
    data object Loading : EpubUiState

    data class Error(val error: LoadError) : EpubUiState

    /**
     * @property startProgression 장을 열면 이 위치(0~1)로 스크롤한다. 한 번 쓰면 [EpubViewModel.onChapterShown]으로 지운다.
     * @property progression 지금 장 안의 위치 0~1
     */
    data class Reading(
        val title: String,
        val chapters: List<EpubChapter>,
        val chapterIndex: Int,
        val startProgression: Float?,
        val progression: Float,
        val textZoom: Int,
        val toc: List<EpubTocEntry> = emptyList(),
        val showOverlay: Boolean = false,
        val showToc: Boolean = false,
    ) : EpubUiState {
        val chapter: EpubChapter get() = chapters[chapterIndex]

        /** 책 전체에서의 위치 0~1 (장 수로 나눈 근사) */
        val totalProgression: Float get() = ((chapterIndex + progression) / chapters.size).coerceIn(0f, 1f)
    }
}

/**
 * EPUB 리더 — Komga WebPub 읽기 순서를 장 단위로 WebView에 연다.
 *
 * 읽은 자리는 Komga Readium progression(장 href + 장 안 위치)으로 저장한다 (2초 디바운스 + 나갈 때 즉시).
 * 다 읽은 책은 처음부터 연다 (이미지 리더와 같음).
 */
@HiltViewModel
class EpubViewModel @Inject constructor(
    private val bookRepository: BookRepository,
    private val readerPreferences: ReaderPreferences,
    private val resourceLoader: EpubResourceLoader,
) : ViewModel() {

    companion object {
        const val SAVE_DEBOUNCE_MILLIS = 2000L
        const val MIN_TEXT_ZOOM = 60
        const val MAX_TEXT_ZOOM = 200
        const val TEXT_ZOOM_STEP = 10
    }

    private val _uiState = MutableStateFlow<EpubUiState>(EpubUiState.Loading)
    val uiState: StateFlow<EpubUiState> = _uiState.asStateFlow()

    private var bookId = ""
    private var saveJob: Job? = null
    private var dirty = false

    fun loadBook(id: String) {
        if (bookId == id && _uiState.value is EpubUiState.Reading) return
        bookId = id
        viewModelScope.launch {
            _uiState.value = EpubUiState.Loading
            try {
                val book = bookRepository.getBookById(id)
                val manifest = bookRepository.getEpubManifest(id)
                val chapters = manifest.chapters
                if (chapters.isEmpty()) throw IllegalStateException("EPUB에 장이 없음: $id")
                val position = if (book.readProgress?.completed == true) null else bookRepository.getEpubPosition(id)
                // 저장된 장을 못 찾으면(책이 바뀜 등) 처음부터
                val found = position?.let { pos -> chapters.indexOfFirst { sameResource(it.href, pos.href) } } ?: -1
                val index = found.coerceAtLeast(0)
                val start = if (found >= 0) position?.progression ?: 0f else 0f
                _uiState.value = EpubUiState.Reading(
                    title = book.displayName(),
                    chapters = chapters,
                    chapterIndex = index,
                    startProgression = start,
                    progression = start,
                    textZoom = readerPreferences.readerSettings.first().epubTextZoom,
                    // 목차가 없거나 한 줄뿐이면("Start"만 있는 책 등) 장 목록으로
                    toc = manifest.toc.takeIf { it.size >= 2 } ?: chapterToc(chapters),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "EPUB 열기 실패: $id")
                _uiState.value = EpubUiState.Error(e.toLoadError())
            }
        }
    }

    /** 장을 열고 시작 위치로 스크롤했다 */
    fun onChapterShown() {
        val state = _uiState.value as? EpubUiState.Reading ?: return
        _uiState.value = state.copy(startProgression = null)
    }

    /** 스크롤한 뒤 장 안의 위치 (0~1) */
    fun onScrolled(progression: Float) {
        val state = _uiState.value as? EpubUiState.Reading ?: return
        val p = progression.coerceIn(0f, 1f)
        if (p == state.progression) return
        _uiState.value = state.copy(progression = p)
        scheduleSave()
    }

    fun nextChapter() = moveChapter(+1, startAt = 0f)

    /** 이전 장. [fromTop]이면 그 장 처음, 아니면(위로 스크롤하다 넘어감) 그 장 끝에서 */
    fun previousChapter(fromTop: Boolean = true) = moveChapter(-1, startAt = if (fromTop) 0f else 1f)

    private fun moveChapter(delta: Int, startAt: Float) {
        val state = _uiState.value as? EpubUiState.Reading ?: return
        val index = state.chapterIndex + delta
        if (index !in state.chapters.indices) return
        _uiState.value = state.copy(chapterIndex = index, startProgression = startAt, progression = startAt)
        scheduleSave()
    }

    fun toggleOverlay() {
        val state = _uiState.value as? EpubUiState.Reading ?: return
        _uiState.value = state.copy(showOverlay = !state.showOverlay)
    }

    fun closeOverlay() {
        val state = _uiState.value as? EpubUiState.Reading ?: return
        _uiState.value = state.copy(showOverlay = false, showToc = false)
    }

    /** 목차 열기 (오버레이에서 OK) */
    fun openToc() {
        val state = _uiState.value as? EpubUiState.Reading ?: return
        _uiState.value = state.copy(showToc = true)
    }

    /** 목차 닫기 — 오버레이로 돌아간다 */
    fun closeToc() {
        val state = _uiState.value as? EpubUiState.Reading ?: return
        _uiState.value = state.copy(showToc = false)
    }

    /** 목차 항목으로 이동 — 그 장 처음에서 연다. 목차와 오버레이는 닫힌다. */
    fun jumpToToc(entry: EpubTocEntry) {
        val state = _uiState.value as? EpubUiState.Reading ?: return
        val index = state.chapters.indexOfFirst { sameResource(it.href, entry.href) }
        if (index < 0) return
        _uiState.value = state.copy(
            chapterIndex = index,
            startProgression = 0f,
            progression = 0f,
            showToc = false,
            showOverlay = false,
        )
        scheduleSave()
    }

    /** 글자 크기를 [TEXT_ZOOM_STEP]만큼 키우거나(+1) 줄인다(-1). 기본값으로 저장한다. */
    fun changeTextZoom(direction: Int) {
        val state = _uiState.value as? EpubUiState.Reading ?: return
        val zoom = (state.textZoom + direction * TEXT_ZOOM_STEP).coerceIn(MIN_TEXT_ZOOM, MAX_TEXT_ZOOM)
        if (zoom == state.textZoom) return
        // 글자 크기가 바뀌면 줄 바꿈이 달라지니 지금 위치를 다시 맞춘다
        _uiState.value = state.copy(textZoom = zoom, startProgression = state.progression)
        viewModelScope.launch { readerPreferences.saveEpubTextZoom(zoom) }
    }

    /** WebView가 요청한 파일 (연결된 서버의 주소만, 인증 포함). WebView 작업 스레드에서 불린다. */
    fun loadResource(url: String): EpubResource? = try {
        resourceLoader.fetch(url)
    } catch (e: Exception) {
        Timber.w(e, "EPUB 파일 받기 실패: $url")
        null
    }

    /** 나갈 때 기다리던 저장을 바로 한다 */
    fun saveOnExit(): Job {
        saveJob?.cancel()
        val position = currentPosition()
        if (!dirty || position == null) return Job().apply { complete() }
        return viewModelScope.launch(NonCancellable) { save(position) }
    }

    private fun scheduleSave() {
        dirty = true
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(SAVE_DEBOUNCE_MILLIS)
            currentPosition()?.let { save(it) }
        }
    }

    private fun currentPosition(): EpubPosition? {
        val state = _uiState.value as? EpubUiState.Reading ?: return null
        return EpubPosition(state.chapter.href, state.progression, state.totalProgression)
    }

    private suspend fun save(position: EpubPosition) {
        try {
            bookRepository.saveEpubPosition(bookId, position)
            dirty = false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "EPUB 읽은 자리 저장 실패: $bookId")
        }
    }
}

/** 같은 EPUB 파일인지 — 서버 주소가 붙은 href와 상대 href를 함께 비교한다 (조각 #… 무시) */
internal fun sameResource(a: String, b: String): Boolean {
    fun key(href: String) = href.substringBefore('#').substringAfter("/resource/")
    return key(a) == key(b)
}

/** 목차가 없는 책: 장마다 한 줄 (장 제목, 없으면 null — 화면이 "n장"으로) */
internal fun chapterToc(chapters: List<EpubChapter>): List<EpubTocEntry> =
    chapters.map { chapter -> EpubTocEntry(chapter.title?.takeIf { it.isNotBlank() } ?: "", chapter.href, 0) }

/** 지금 장이 목차의 어느 줄인지 — 그 장을 가리키는 마지막 줄 (없으면 그 앞 장의 줄) */
internal fun currentTocIndex(toc: List<EpubTocEntry>, chapters: List<EpubChapter>, chapterIndex: Int): Int {
    for (i in chapterIndex downTo 0) {
        val at = toc.indexOfLast { sameResource(it.href, chapters[i].href) }
        if (at >= 0) return at
    }
    return 0
}
