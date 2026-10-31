package com.komgatv.feature.reader.continuous

import com.komgatv.core.model.ReaderType
import com.komgatv.core.model.ReadingDirection

/**
 * 시리즈 읽기 방향 메타데이터와 저장된 전역 설정을 바탕으로 리더 종류를 결정합니다.
 *
 * Komelia ReaderState.kt (Apache-2.0, Snd-R/Komelia)와 동일한 우선순위 규칙:
 * 1. 시리즈 방향이 LEFT_TO_RIGHT / RIGHT_TO_LEFT 이면 PAGED
 * 2. 시리즈 방향이 WEBTOON 이면 CONTINUOUS
 * 3. 시리즈 방향이 VERTICAL 이거나 null(미지정)이면 저장된 readerType 사용
 */
object ReaderTypeResolver {
    fun resolveReaderType(
        seriesReadingDirection: ReadingDirection?,
        savedReaderType: ReaderType,
    ): ReaderType {
        return when (seriesReadingDirection) {
            ReadingDirection.LEFT_TO_RIGHT,
            ReadingDirection.RIGHT_TO_LEFT -> ReaderType.PAGED

            ReadingDirection.WEBTOON -> ReaderType.CONTINUOUS

            ReadingDirection.VERTICAL,
            null -> savedReaderType
        }
    }
}
