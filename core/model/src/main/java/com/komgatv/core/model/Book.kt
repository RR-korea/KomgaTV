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
