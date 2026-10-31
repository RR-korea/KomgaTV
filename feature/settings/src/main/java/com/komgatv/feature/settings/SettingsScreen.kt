package com.komgatv.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.RadioButton
import androidx.tv.material3.Text
import androidx.tv.material3.TextField
import com.komgatv.core.data.preferences.ServerPreferences
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 설정 화면
 *
 * Komga 서버 연결 설정을 관리합니다:
 * - 서버 URL 입력
 * - 인증 방식 선택 (Basic Auth / API Key)
 * - 연결 테스트
 * - 캐시 관리
 *
 * @param onNavigateBack 뒤로가기 콜백
 * @param viewModel 설정 ViewModel
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 48.dp, vertical = 32.dp),
    ) {
        // 화면 제목
        Text(
            text = "설정",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Spacer(modifier = Modifier.height(32.dp))

        // ========== 서버 URL 입력 ==========
        Text(
            text = "서버 URL",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(8.dp))

        var serverUrl by remember { mutableStateOf(uiState.serverUrl) }
        TextField(
            value = serverUrl,
            onValueChange = { serverUrl = it },
            placeholder = { Text("http://192.168.1.100:25600") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(24.dp))

        // ========== 인증 방식 선택 ==========
        Text(
            text = "인증 방식",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(8.dp))

        var authMethod by remember { mutableStateOf(uiState.authMethod) }

        // Basic Auth 옵션
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = authMethod == "basic",
                onClick = { authMethod = "basic" },
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "기본 인증 (ID/비밀번호)",
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        // API Key 옵션
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = authMethod == "apikey",
                onClick = { authMethod = "apikey" },
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "API 키",
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ========== 인증 정보 입력 필드 ==========
        if (authMethod == "basic") {
            // Basic Auth: 사용자명 + 비밀번호
            var username by remember { mutableStateOf(uiState.username) }
            var password by remember { mutableStateOf(uiState.password) }

            Text(
                text = "사용자 이름",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextField(
                value = username,
                onValueChange = { username = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "비밀번호",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextField(
                value = password,
                onValueChange = { password = it },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )

            // 저장 시 username/password를 ViewModel에 전달
            Spacer(modifier = Modifier.height(24.dp))
            SaveButton(
                onClick = {
                    scope.launch {
                        viewModel.saveSettings(
                            serverUrl = serverUrl,
                            authMethod = "basic",
                            username = username,
                            password = password,
                        )
                    }
                },
            )
        } else {
            // API Key 입력
            var apiKey by remember { mutableStateOf(uiState.apiKey) }

            Text(
                text = "API 키",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(24.dp))
            SaveButton(
                onClick = {
                    scope.launch {
                        viewModel.saveSettings(
                            serverUrl = serverUrl,
                            authMethod = "apikey",
                            apiKey = apiKey,
                        )
                    }
                },
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ========== 연결 테스트 버튼 ==========
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(
                onClick = { scope.launch { viewModel.testConnection() } },
            ) {
                Text("연결 테스트")
            }

            // 연결 테스트 결과 표시
            when (uiState.connectionStatus) {
                ConnectionStatus.Success -> {
                    Text(
                        text = "✅ 연결 성공",
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.align(Alignment.CenterVertically),
                    )
                }
                ConnectionStatus.Failed -> {
                    Text(
                        text = "❌ 연결 실패",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.CenterVertically),
                    )
                }
                ConnectionStatus.Testing -> {
                    Text(
                        text = "🔄 테스트 중...",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterVertically),
                    )
                }
                ConnectionStatus.None -> { /* 표시 없음 */ }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // ========== 캐시 관리 ==========
        Text(
            text = "캐시 관리",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = { scope.launch { viewModel.clearImageCache() } },
        ) {
            Text("이미지 캐시 삭제")
        }
    }
}

/**
 * 저장 버튼 컴포저블
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SaveButton(onClick: () -> Unit) {
    Button(onClick = onClick) {
        Text("저장")
    }
}
