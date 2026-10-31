package com.komgatv.core.model

/** Komga 책 (권/화) */
data class Book(
    val id: String,
    val seriesId: String,
    val seriesTitle: String,
    val name: String,
    val number: Int,
    /** 페이지 수. 페이지 번호는 1부터 시작한다. */
    val pageCount: Int,
    val metadata: BookMetadata,
    val readProgress: ReadProgress?,
    val mediaProfile: MediaProfile = MediaProfile.DIVINA,
)

data class BookMetadata(
    val title: String,
    val summary: String,
    val number: String,
)

/** 서버에 저장된 읽기 진행. page는 1부터 시작한다. */
data class ReadProgress(
    val page: Int,
    val completed: Boolean,
)

/**
 * 책의 개별 페이지 정보
 *
 * @property number 1부터 시작하는 페이지 번호
 * @property fileName 파일 이름
 * @property mediaType 미디어 MIME 타입
 * @property width 이미지 가로 픽셀 너비 (알 수 없는 경우 null)
 * @property height 이미지 세로 픽셀 높이 (알 수 없는 경우 null)
 * @property sizeBytes 파일 크기 바이트 (알 수 없는 경우 null)
 */
data class BookPage(
    val number: Int,
    val fileName: String = "",
    val mediaType: String = "",
    val width: Int? = null,
    val height: Int? = null,
    val sizeBytes: Long? = null,
) {
    /**
     * 가로가 긴 페이지인지 여부 (가로 > 세로)
     * 크기가 null인 경우 세로(false)로 판단합니다.
     */
    fun isLandscape(): Boolean = if (width == null || height == null) false else width > height
}

/** 책 형식 — Komga MediaProfile과 같은 값. EPUB은 글 리더로, 나머지는 페이지 이미지 리더로 연다. */
enum class MediaProfile {
    DIVINA,
    PDF,
    EPUB,
}

/** EPUB 목차 한 줄 (WebPub toc). [depth]는 들여쓰기 단계(0부터), [href]는 #조각이 붙을 수 있다. */
data class EpubTocEntry(
    val title: String,
    val href: String,
    val depth: Int,
)

/** EPUB 구성: 읽기 순서(장)와 목차 */
data class EpubManifest(
    val chapters: List<EpubChapter>,
    val toc: List<EpubTocEntry>,
)

/** EPUB 읽기 순서의 한 장 (WebPub readingOrder) */
data class EpubChapter(
    val href: String,
    val title: String?,
)

/**
 * EPUB 읽은 자리 (Readium progression)
 *
 * @property progression 그 장 안에서의 위치 0~1
 * @property totalProgression 책 전체에서의 위치 0~1
 */
data class EpubPosition(
    val href: String,
    val progression: Float,
    val totalProgression: Float,
)

/** 보여 줄 책 이름 — 이름이 파일 해시처럼 보이면(16자 이상 16진수) 시리즈 제목을 쓴다 */
fun Book.displayName(): String =
    if (name.length >= 16 && name.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }) seriesTitle else name
