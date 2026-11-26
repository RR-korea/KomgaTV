package com.komgatv.feature.reader.enhance

import kotlinx.coroutines.sync.Mutex

/**
 * 쪽 전체를 훑는 무거운 처리(칸 간격, 테두리 잘라내기)는 한 번에 한 쪽씩 — 미리 받는 쪽들이 한꺼번에 돌면
 * 큰 배열이 겹쳐 TV 메모리가 모자란다.
 */
internal val pageWorkLock = Mutex()
