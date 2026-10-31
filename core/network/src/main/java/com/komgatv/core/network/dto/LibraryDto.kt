package com.komgatv.core.network.dto

import com.komgatv.core.model.Library
import kotlinx.serialization.Serializable
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 라이브러리 API 응답 DTO
 *
 * Komga 서버에서 반환하는 라이브러리 JSON 구조에 대응합니다.
 */
@Serializable
data class LibraryDto(
    val id: String,
    val name: String,
    val root: String,
    val createdDate: String = "",
    val lastModifiedDate: String = "",
)

/** DTO를 도메인 모델로 변환 */
fun LibraryDto.toDomain(): Library = Library(
    id = id,
    name = name,
    root = root,
    createdDate = parseDateTime(createdDate),
    lastModifiedDate = parseDateTime(lastModifiedDate),
)

/** ISO 8601 날짜/시간 문자열 파싱 헬퍼 */
internal fun parseDateTime(dateString: String): LocalDateTime {
    return try {
        LocalDateTime.parse(dateString, DateTimeFormatter.ISO_DATE_TIME)
    } catch (e: Exception) {
        LocalDateTime.MIN
    }
}
