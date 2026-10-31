package com.komgatv.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.komgatv.core.model.LoadError
import com.komgatv.core.ui.R

/** 오류 종류를 현재 언어의 문구로 바꾼다. */
@Composable
fun loadErrorText(error: LoadError): String = stringResource(
    when (error) {
        LoadError.NOT_CONNECTED -> R.string.error_not_connected
        LoadError.UNREACHABLE -> R.string.error_unreachable
        LoadError.UNAUTHORIZED -> R.string.error_unauthorized
        LoadError.UNKNOWN -> R.string.error_unknown
    }
)
