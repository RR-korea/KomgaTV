package com.komgatv.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import androidx.tv.material3.Typography

/**
 * KomgaTV 컬러 팔레트
 *
 * TV의 10-foot UI 환경에 최적화된 다크 테마 색상입니다.
 * 어두운 배경 위에 높은 대비를 유지하여 먼 거리에서도 가독성을 보장합니다.
 */
private object KomgaTVColors {
    // 기본 배경 및 표면 색상 (어두운 톤)
    val Background = Color(0xFF121212)
    val Surface = Color(0xFF1E1E1E)
    val SurfaceVariant = Color(0xFF2C2C2C)

    // 프라이머리 색상 (Komga 브랜드 컬러 기반)
    val Primary = Color(0xFF6DB0F6)
    val OnPrimary = Color(0xFF003258)
    val PrimaryContainer = Color(0xFF00497D)
    val OnPrimaryContainer = Color(0xFFD1E4FF)

    // 세컨더리 색상
    val Secondary = Color(0xFFBBC7DB)
    val OnSecondary = Color(0xFF263141)
    val SecondaryContainer = Color(0xFF3C4858)
    val OnSecondaryContainer = Color(0xFFD7E3F7)

    // 텍스트 및 콘텐츠 색상 (높은 대비)
    val OnBackground = Color(0xFFE3E2E6)
    val OnSurface = Color(0xFFE3E2E6)
    val OnSurfaceVariant = Color(0xFFC3C6CF)

    // 에러 색상
    val Error = Color(0xFFFFB4AB)
    val OnError = Color(0xFF690005)
    val ErrorContainer = Color(0xFF93000A)
    val OnErrorContainer = Color(0xFFFFDAD6)

    // 포커스/선택 강조 색상 (TV D-Pad 네비게이션용)
    val FocusHighlight = Color(0xFF80B9FF)
    val SelectedBorder = Color(0xFF4A90D9)
}

/**
 * TV에 최적화된 다크 컬러 스킴
 */
@OptIn(ExperimentalTvMaterial3Api::class)
private val KomgaTVDarkColorScheme = darkColorScheme(
    primary = KomgaTVColors.Primary,
    onPrimary = KomgaTVColors.OnPrimary,
    primaryContainer = KomgaTVColors.PrimaryContainer,
    onPrimaryContainer = KomgaTVColors.OnPrimaryContainer,
    secondary = KomgaTVColors.Secondary,
    onSecondary = KomgaTVColors.OnSecondary,
    secondaryContainer = KomgaTVColors.SecondaryContainer,
    onSecondaryContainer = KomgaTVColors.OnSecondaryContainer,
    background = KomgaTVColors.Background,
    onBackground = KomgaTVColors.OnBackground,
    surface = KomgaTVColors.Surface,
    onSurface = KomgaTVColors.OnSurface,
    surfaceVariant = KomgaTVColors.SurfaceVariant,
    onSurfaceVariant = KomgaTVColors.OnSurfaceVariant,
    error = KomgaTVColors.Error,
    onError = KomgaTVColors.OnError,
    errorContainer = KomgaTVColors.ErrorContainer,
    onErrorContainer = KomgaTVColors.OnErrorContainer,
)

/**
 * KomgaTV 앱 테마
 *
 * TV의 10-foot UI 환경에 최적화된 머티리얼 다크 테마입니다.
 * - 어두운 배경으로 눈의 피로 최소화
 * - 높은 대비로 먼 거리에서의 가독성 보장
 * - D-Pad 포커스 상태에 대한 시각적 피드백 지원
 *
 * @param content 테마가 적용될 컴포저블 콘텐츠
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun KomgaTVTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = KomgaTVDarkColorScheme,
        content = content,
    )
}
