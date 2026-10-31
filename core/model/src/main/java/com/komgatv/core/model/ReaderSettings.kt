package com.komgatv.core.model

/**
 * 리더 페이지 레이아웃
 *
 * Komelia PageDisplayLayout(Apache-2.0)과 동일한 옵션 및 기본값
 */
enum class PageLayout {
    /** 단일 페이지 보기 */
    SINGLE_PAGE,

    /** 두 쪽 보기 (첫 페이지/표지는 단독) */
    DOUBLE_PAGES,

    /** 두 쪽 보기 (첫 페이지부터 두 쪽씩) */
    DOUBLE_PAGES_NO_COVER,
}

/**
 * 리더 페이지 크기 맞춤 방식
 *
 * Komelia LayoutScaleType(Apache-2.0)과 동일한 옵션 및 기본값
 */
enum class ScaleType {
    /** 화면에 맞춤 (기본값) */
    SCREEN,

    /** 너비에 맞춤 */
    FIT_WIDTH,

    /** 높이에 맞춤 */
    FIT_HEIGHT,

    /** 원본 크기 */
    ORIGINAL,
}

/**
 * 리더 전역 보기 설정 모델
 *
 * @property pageLayout 페이지 표시 방식 (기본값: SINGLE_PAGE)
 * @property scaleType 크기 맞춤 방식 (기본값: SCREEN)
 * @property stretchToFit 화면보다 작은 이미지를 확대할지 여부 (기본값: true)
 * @property readingDirection 기본 읽기 방향 (기본값: LEFT_TO_RIGHT)
 */
data class ReaderSettings(
    val pageLayout: PageLayout = PageLayout.SINGLE_PAGE,
    val scaleType: ScaleType = ScaleType.SCREEN,
    val stretchToFit: Boolean = true,
    val readingDirection: ReadingDirection = ReadingDirection.LEFT_TO_RIGHT,
)
