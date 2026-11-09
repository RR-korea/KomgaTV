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

    /** Lanczos2 — 디코딩할 때 화면 크기로 리샘플링(CPU), 빠르고 부드럽다 */
    LANCZOS2,

    /** Lanczos3 — 디코딩할 때 화면 크기로 리샘플링(CPU). 바이큐빅보다 선이 또렷하다 (저장 이름은 예전 그대로) */
    LANCZOS,

    /** Lanczos4 — 더 넓은 창으로 리샘플링, 가장 또렷하지만 느리다 */
    LANCZOS4,

    /** 선명하게 — 고품질 필터 + 언샤프 마스크(약하게), 흐린 스캔본이 또렷해진다 */
    SHARPEN,

    /** 더 선명하게 — 고품질 필터 + 언샤프 마스크(강하게) */
    SHARPEN_STRONG,
}

/**
 * 페이지 넘김 화면 전환 효과. 이미 그린 화면을 옮기거나 겹치기만 해서(GPU) 부담이 적다.
 */
enum class PageTransition {
    /** 바로 바꾼다 */
    OFF,

    /** 읽는 방향으로 밀기 (0.18초) */
    SLIDE,

    /** 겹쳐 바꾸기 (0.15초) */
    FADE,
}

/**
 * 리더 보기 설정 모델 (앱 전체 기본값, 또는 한 작품의 설정)
 *
 * @property readerType 리더 종류 (기본값: PAGED)
 * @property pageLayout 페이지 표시 방식 (기본값: SINGLE_PAGE)
 * @property scaleType 크기 맞춤 방식 (기본값: SCREEN)
 * @property readingDirection 기본 읽기 방향 (기본값: LEFT_TO_RIGHT)
 * @property continuousPadding 세로 스크롤 시 좌우 여백 (화면 너비 비율, 기본값: 0f)
 * @property continuousPageSpacing 세로 스크롤 시 쪽 사이 간격 dp (기본값: 0)
 * @property zoomPercent 페이지 넘김에서 크기 맞춤으로 맞춘 결과를 더 확대(줌인)하는 비율 %
 *   ([MIN_ZOOM_PERCENT]~[MAX_ZOOM_PERCENT], 1% 단위 — 100이면 확대 안 함, 150%까지).
 *   스캔 여백을 잘라 크게 볼 때 — 작품별로만 바꾼다.
 */
data class ReaderSettings(
    val readerType: ReaderType = ReaderType.PAGED,
    val pageLayout: PageLayout = PageLayout.SINGLE_PAGE,
    val scaleType: ScaleType = ScaleType.SCREEN,
    val readingDirection: ReadingDirection = ReadingDirection.LEFT_TO_RIGHT,
    val continuousPadding: Float = 0f,
    val continuousPageSpacing: Int = 0,
    val imageEnhancement: ImageEnhancement = ImageEnhancement.OFF,
    /** EPUB 글자 크기 (WebView textZoom, %) */
    val epubTextZoom: Int = 100,
    val zoomPercent: Int = MIN_ZOOM_PERCENT,
    /** 대비 % (작품별, [MIN_CONTRAST_PERCENT]~[MAX_CONTRAST_PERCENT], 5% 단위, 100 = 그대로) — 흐린 스캔본을 진하게 */
    val contrastPercent: Int = 100,
    /** 밝기 % (작품별, [MIN_BRIGHTNESS_PERCENT]~[MAX_BRIGHTNESS_PERCENT], 5% 단위, 100 = 그대로) — 어두운 스캔본을 밝게, 밤에는 어둡게 */
    val brightnessPercent: Int = 100,
    /**
     * 확대했을 때 위아래로 옮겨 둔 자리 % (작품별, [MIN_ZOOM_PAN_PERCENT]~[MAX_ZOOM_PAN_PERCENT], [ZOOM_PAN_STEP] 단위).
     * 0 = 가운데, -100 = 맨 위, 100 = 맨 아래. 쪽을 넘겨도 그 자리를 유지한다.
     */
    val zoomPanPercent: Int = 0,
    /** 칸 사이 위아래 여백을 없애고 칸들을 쪽 가운데로 모은다 (작품별, 페이지 넘김) */
    val removeGutters: Boolean = false,
    /**
     * 확대해서 쪽 끝에서 잘리는 대사를 옮길 때, 쪽 끝에 걸려 잘린 말풍선은 말풍선째 옮긴다 (작품별, 페이지 넘김).
     * 꺼져 있어도 확대하면 말풍선 안에서 대사는 옮긴다.
     */
    val moveBalloons: Boolean = false,
    /** 흑백 쪽을 자동으로 칠한다 (작품별, 페이지 넘김) — 처음 켤 때 채색 모델을 받는다 */
    val colorize: Boolean = false,
    /** 페이지 넘김 화면 전환 효과 */
    val pageTransition: PageTransition = PageTransition.SLIDE,
) {
    companion object {
        const val MIN_ZOOM_PERCENT = 100
        const val MAX_ZOOM_PERCENT = 150
        const val MIN_CONTRAST_PERCENT = 50
        const val MAX_CONTRAST_PERCENT = 190
        const val CONTRAST_STEP = 5
        const val MIN_BRIGHTNESS_PERCENT = 10
        const val MIN_ZOOM_PAN_PERCENT = -100
        const val MAX_ZOOM_PAN_PERCENT = 100
        const val ZOOM_PAN_STEP = 25
        const val MAX_BRIGHTNESS_PERCENT = 150
        const val BRIGHTNESS_STEP = 5
    }
}
