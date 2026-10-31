package com.komgatv.core.network.dto

import com.komgatv.core.model.Series
import com.komgatv.core.model.SeriesMetadata
import kotlinx.serialization.Serializable

/**
 * 시리즈 API 응답 DTO
 */
@Serializable
data class SeriesDto(
    val id: String,
    val libraryId: String,
    val name: String,
    val booksCount: Int = 0,
    val metadata: SeriesMetadataDto = SeriesMetadataDto(),
)

/**
 * 시리즈 메타데이터 DTO
 */
@Serializable
data class SeriesMetadataDto(
    val status: String = "",
    val title: String = "",
    val summary: String = "",
    val publisher: String = "",
    val genres: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val totalBookCount: Int = 0,
    val ageRating: Int? = null,
    val language: String = "",
)

/** 시리즈 DTO를 도메인 모델로 변환 */
fun SeriesDto.toDomain(): Series = Series(
    id = id,
    libraryId = libraryId,
    name = name,
    booksCount = booksCount,
    metadata = metadata.toDomain(),
)

/** 시리즈 메타데이터 DTO를 도메인 모델로 변환 */
fun SeriesMetadataDto.toDomain(): SeriesMetadata = SeriesMetadata(
    status = status,
    title = title,
    summary = summary,
    publisher = publisher,
    genres = genres,
    tags = tags,
    totalBookCount = totalBookCount,
    ageRating = ageRating,
    language = language,
)
