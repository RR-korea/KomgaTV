package com.komgatv.core.data.repository

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 읽기 진행 저장이 끝났음을 알린다 (책 ID).
 *
 * 리더를 나올 때의 저장은 홈·시리즈 화면이 돌아와 새로고침한 뒤에 끝날 수 있다.
 * 그 화면들이 이 알림을 듣고 진행 표시를 다시 읽는다.
 */
@Singleton
class ReadProgressEvents @Inject constructor() {
    private val _updates = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val updates: SharedFlow<String> = _updates.asSharedFlow()

    fun notifyUpdated(bookId: String) {
        _updates.tryEmit(bookId)
    }
}
