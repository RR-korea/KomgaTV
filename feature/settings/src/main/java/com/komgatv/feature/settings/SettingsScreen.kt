package com.komgatv.feature.settings

import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.RadioButton
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import com.komgatv.core.datastore.SavedServer
import com.komgatv.core.ui.components.TvTextField
import com.komgatv.core.ui.settings.ViewSettingsForm

/**
 * Komga 서버 연결 설정 화면
 *
 * D-pad 네비게이션에 최적화되어 있으며, 화면 진입 시 서버 URL 입력칸에 자동으로 포커스됩니다.
 *
 * @param onNavigateBack 뒤로가기 콜백
 * @param onConnectSuccess 서버 연결 및 저장 성공 시 호출되는 콜백 (홈 화면 이동 등)
 * @param viewModel 설정 ViewModel
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onConnectSuccess: () -> Unit = onNavigateBack,
    onServerSwitched: () -> Unit = onConnectSuccess,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val savedServers by viewModel.savedServers.collectAsState()
    val viewDefaults by viewModel.viewDefaults.collectAsState()

    // 다른 서버로 바꾸면 홈부터 다시 연다 (옛 서버 내용이 남지 않게)
    LaunchedEffect(uiState.serverSwitched) {
        if (uiState.serverSwitched) onServerSwitched()
    }
    val scrollState = rememberScrollState()
    val firstFocusRequester = remember { FocusRequester() }

    // 저장 성공 시 홈 또는 이전 화면으로 이동
    LaunchedEffect(uiState.saveSuccess) {
        if (uiState.saveSuccess) {
            onConnectSuccess()
        }
    }

    // 화면 진입 시 첫 입력칸(서버 URL)에 자동 포커스
    LaunchedEffect(Unit) {
        firstFocusRequester.requestFocus()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 48.dp, vertical = 32.dp),
    ) {
        // 화면 제목
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Spacer(modifier = Modifier.height(24.dp))

        // ========== 저장된 서버 ==========
        if (savedServers.isNotEmpty()) {
            SavedServersSection(
                servers = savedServers,
                onSwitch = viewModel::switchServer,
                onRemove = viewModel::removeServer,
                onAddNew = {
                    viewModel.startNewServer()
                    firstFocusRequester.requestFocus()
                },
            )
            Spacer(modifier = Modifier.height(32.dp))
        }

        // ========== 서버 URL ==========
        Text(
            text = stringResource(R.string.settings_server_url_label),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(8.dp))

        TvTextField(
            value = uiState.serverUrl,
            onValueChange = viewModel::updateServerUrl,
            placeholder = stringResource(R.string.settings_server_url_placeholder),
            keyboardType = KeyboardType.Uri,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(firstFocusRequester),
        )

        Spacer(modifier = Modifier.height(24.dp))

        // ========== 인증 방식 선택 ==========
        Text(
            text = stringResource(R.string.settings_auth_method_label),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            AuthMethodOption(
                title = stringResource(R.string.settings_auth_method_basic),
                selected = uiState.authMethod == "basic",
                onSelect = { viewModel.updateAuthMethod("basic") },
            )

            AuthMethodOption(
                title = stringResource(R.string.settings_auth_method_apikey),
                selected = uiState.authMethod == "apikey",
                onSelect = { viewModel.updateAuthMethod("apikey") },
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ========== 인증 정보 입력 칸 ==========
        if (uiState.authMethod == "basic") {
            Text(
                text = stringResource(R.string.settings_username_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))
            TvTextField(
                value = uiState.username,
                onValueChange = viewModel::updateUsername,
                placeholder = stringResource(R.string.settings_username_placeholder),
                keyboardType = KeyboardType.Email,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = stringResource(R.string.settings_password_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))
            TvTextField(
                value = uiState.password,
                onValueChange = viewModel::updatePassword,
                placeholder = stringResource(R.string.settings_password_placeholder),
                visualTransformation = PasswordVisualTransformation(),
                keyboardType = KeyboardType.Password,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Text(
                text = stringResource(R.string.settings_apikey_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))
            TvTextField(
                value = uiState.apiKey,
                onValueChange = viewModel::updateApiKey,
                placeholder = stringResource(R.string.settings_apikey_placeholder),
                visualTransformation = PasswordVisualTransformation(),
                keyboardType = KeyboardType.Password,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ========== 연결 테스트 / 저장 버튼 ==========
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = viewModel::testConnection,
            ) {
                Text(stringResource(R.string.settings_btn_test))
            }

            Button(
                onClick = viewModel::saveSettings,
            ) {
                Text(stringResource(R.string.settings_btn_save))
            }
        }

        // ========== 연결 상태 및 에러 메시지 표시 ==========
        if (uiState.connectionResult != ConnectionResult.None) {
            Spacer(modifier = Modifier.height(16.dp))
            ConnectionStatusBanner(result = uiState.connectionResult)
        }

        Spacer(modifier = Modifier.height(36.dp))

        // ========== 보기 기본값 (앱 전체) ==========
        Text(
            text = stringResource(R.string.settings_section_view_defaults),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.settings_view_defaults_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
        ViewSettingsForm(
            settings = viewDefaults,
            onChange = viewModel::updateViewDefaults,
            modifier = Modifier.widthIn(max = 720.dp),
        )

        Spacer(modifier = Modifier.height(36.dp))

        // ========== 캐시 관리 ==========
        Text(
            text = stringResource(R.string.settings_section_cache),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Button(
                onClick = viewModel::clearImageCache,
            ) {
                Text(stringResource(R.string.settings_btn_clear_cache))
            }

            if (uiState.cacheCleared) {
                Text(
                    text = stringResource(R.string.settings_status_cache_cleared),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        Spacer(modifier = Modifier.height(36.dp))

        // ========== 앱 정보 ==========
        AboutSection()

        Spacer(modifier = Modifier.height(32.dp))
    }
}

/**
 * 저장된 서버 목록. 지금 서버는 "사용 중", 다른 서버는 [바꾸기]·[삭제].
 */
@Composable
private fun SavedServersSection(
    servers: List<SavedServer>,
    onSwitch: (String) -> Unit,
    onRemove: (String) -> Unit,
    onAddNew: () -> Unit,
) {
    Text(
        text = stringResource(R.string.settings_saved_servers),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
    )
    Spacer(modifier = Modifier.height(8.dp))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        servers.forEach { server ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = server.url,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = if (server.authMethod == "apikey") {
                            stringResource(R.string.settings_auth_method_apikey)
                        } else {
                            server.username
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (server.isActive) {
                    Text(
                        text = stringResource(R.string.settings_server_active),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    OutlinedButton(onClick = { onSwitch(server.id) }) {
                        Text(stringResource(R.string.settings_server_switch))
                    }
                    OutlinedButton(onClick = { onRemove(server.id) }) {
                        Text(stringResource(R.string.settings_server_remove))
                    }
                }
            }
        }
        OutlinedButton(onClick = onAddNew) {
            Text(stringResource(R.string.settings_server_add))
        }
    }
}

/**
 * TV D-pad 포커스 가능한 인증 방식 선택 라디오 옵션
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun AuthMethodOption(
    title: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val shape = RoundedCornerShape(8.dp)
    val colors = MaterialTheme.colorScheme

    Row(
        modifier = Modifier
            .clip(shape)
            .focusable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onSelect,
            )
            .background(colors.surfaceVariant, shape)
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) colors.primary else colors.border,
                shape = shape,
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = onSelect,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isFocused) colors.onSurface else colors.onSurfaceVariant,
        )
    }
}

/**
 * 연결 상태 및 원인별 실패 메시지 배너
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ConnectionStatusBanner(result: ConnectionResult) {
    val message: String
    val isSuccess: Boolean
    val isTesting: Boolean

    when (result) {
        ConnectionResult.Testing -> {
            message = stringResource(R.string.settings_status_testing)
            isSuccess = false
            isTesting = true
        }
        ConnectionResult.Success -> {
            message = stringResource(R.string.settings_status_success)
            isSuccess = true
            isTesting = false
        }
        ConnectionResult.Failed.InvalidUrl -> {
            message = stringResource(R.string.settings_error_invalid_url)
            isSuccess = false
            isTesting = false
        }
        ConnectionResult.Failed.Unreachable -> {
            message = stringResource(R.string.settings_error_unreachable)
            isSuccess = false
            isTesting = false
        }
        ConnectionResult.Failed.Unauthorized -> {
            message = stringResource(R.string.settings_error_unauthorized)
            isSuccess = false
            isTesting = false
        }
        ConnectionResult.Failed.SslError -> {
            message = stringResource(R.string.settings_error_ssl)
            isSuccess = false
            isTesting = false
        }
        ConnectionResult.Failed.MissingFields -> {
            message = stringResource(R.string.settings_error_missing_fields)
            isSuccess = false
            isTesting = false
        }
        is ConnectionResult.Failed.Other -> {
            message = stringResource(R.string.settings_error_other, result.message ?: "")
            isSuccess = false
            isTesting = false
        }
        ConnectionResult.None -> return
    }

    val textColor = when {
        isSuccess -> MaterialTheme.colorScheme.primary
        isTesting -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.error
    }

    Text(
        text = message,
        style = MaterialTheme.typography.bodyLarge,
        color = textColor,
        modifier = Modifier.padding(vertical = 4.dp),
    )
}
