package com.komgatv.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

/** 우리 저장소와 존경을 담은 두 프로젝트 */
private const val KOMGATV_GITHUB = "github.com/RR-korea/KomgaTV"
private const val KOMGA_GITHUB = "github.com/gotson/komga"
private const val KOMELIA_GITHUB = "github.com/Snd-R/Komelia"
private const val DEVELOPER = "David Hojin Kim"
private const val DEVELOPER_EMAIL = "sinabro@gmail.com"

/**
 * 앱 정보 — 만든 사람, 저장소, Komga·Komelia에 대한 감사, 오픈소스 고지.
 *
 * 글뿐이지만 TV에서 리모컨으로 스크롤해 볼 수 있게 상자 자체가 포커스를 받는다.
 */
@Composable
internal fun AboutSection() {
    val context = LocalContext.current
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
    }
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(8.dp)
    val colors = MaterialTheme.colorScheme

    Text(
        text = stringResource(R.string.about_title),
        style = MaterialTheme.typography.titleSmall,
        color = colors.onSurface,
    )
    Column(
        modifier = Modifier
            .padding(top = 8.dp)
            .fillMaxWidth()
            .background(colors.surfaceVariant, shape)
            .border(if (focused) 2.dp else 1.dp, if (focused) colors.primary else colors.border, shape)
            .focusable(interactionSource = interaction)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = stringResource(R.string.about_app_version, version),
            style = MaterialTheme.typography.titleMedium,
            color = colors.onSurface,
        )
        Text(
            text = stringResource(R.string.about_developer, DEVELOPER, DEVELOPER_EMAIL),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurface,
        )
        // 만든 사람 소개
        Text(
            text = stringResource(R.string.about_developer_bio),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurface,
        )
        Text(
            text = stringResource(R.string.about_developer_wish),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurface,
        )
        Text(text = KOMGATV_GITHUB, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF80B9FF))
        Text(
            text = stringResource(R.string.about_tribute),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurface,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(text = "Komga — $KOMGA_GITHUB", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF80B9FF))
        Text(text = "Komelia — $KOMELIA_GITHUB", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF80B9FF))
        Text(
            text = stringResource(R.string.about_licenses),
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
