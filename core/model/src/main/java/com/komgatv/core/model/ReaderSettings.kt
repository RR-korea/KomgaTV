package com.komgatv.core.model

/**
 * 리더 종류
 *
 * Komelia ReaderType(Apache-2.0)과 동일한 옵션 및 기본값 (PANELS는 범위 제외)
 */
enum class ReaderType {
    /** 페이지 단위 넘김 (기본값) */
    PAGED,

    /** 세로 연속 스크롤 (웹툰) */
    CONTINUOUS,
}

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
 * 화질 개선 — 화면에 맞춰 키우거나 줄일 때의 처리
 *
 * TV는 만화 페이지를 보통 크게 키워 보므로 확대 품질과 선명도가 중요하다.
 */
enum class ImageEnhancement {
    /** 끄기 — 기본 필터 */
    OFF,

    /** 부드럽게 확대 — 고품질 필터(바이큐빅·밉맵), 계단 현상이 줄어든다 */
    SMOOTH,

    /** 선명하게 — 고품질 필터 + 언샤프 마스크(약하게), 흐린 스캔본이 또렷해진다 */
    SHARPEN,

    /** 더 선명하게 — 고품질 필터 + 언샤프 마스크(강하게) */
    SHARPEN_STRONG,
}

/**
 * 리더 보기 설정 모델 (앱 전체 기본값, 또는 한 작품의 설정)
 *
 * @property readerType 리더 종류 (기본값: PAGED)
 * @property pageLayout 페이지 표시 방식 (기본값: SINGLE_PAGE)
 * @property scaleType 크기 맞춤 방식 (기본값: SCREEN)
 * @property stretchToFit 화면보다 작은 이미지를 확대할지 여부 (기본값: true)
 * @property readingDirection 기본 읽기 방향 (기본값: LEFT_TO_RIGHT)
 * @property continuousPadding 세로 스크롤 시 좌우 여백 (화면 너비 비율, 기본값: 0f)
 * @property continuousPageSpacing 세로 스크롤 시 쪽 사이 간격 dp (기본값: 0)
 * @property zoomPercent 페이지 넘김에서 크기 맞춤으로 맞춘 결과를 더 확대(줌인)하는 비율 %
 *   ([MIN_ZOOM_PERCENT]~[MAX_ZOOM_PERCENT], 1% 단위). 스캔 여백을 잘라 크게 볼 때 — 작품별로만 바꾼다.
 */
data class ReaderSettings(
    val readerType: ReaderType = ReaderType.PAGED,
    val pageLayout: PageLayout = PageLayout.SINGLE_PAGE,
    val scaleType: ScaleType = ScaleType.SCREEN,
    val stretchToFit: Boolean = true,
    val readingDirection: ReadingDirection = ReadingDirection.LEFT_TO_RIGHT,
    val continuousPadding: Float = 0f,
    val continuousPageSpacing: Int = 0,
    val imageEnhancement: ImageEnhancement = ImageEnhancement.OFF,
    /** EPUB 글자 크기 (WebView textZoom, %) */
    val epubTextZoom: Int = 100,
    val zoomPercent: Int = MIN_ZOOM_PERCENT,
) {
    companion object {
        const val MIN_ZOOM_PERCENT = 100
        const val MAX_ZOOM_PERCENT = 300
    }
}
