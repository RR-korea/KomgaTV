package com.komgatv.core.network.dto

import com.komgatv.core.model.Book
import com.komgatv.core.model.BookMetadata
import com.komgatv.core.model.ReadProgress
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 책 API 응답 DTO
 */
@Serializable
data class BookDto(
    val id: String,
    val seriesId: String,
    val name: String,
    val number: Int = 0,
    val sizeBytes: Long = 0L,
    val pagesCount: Int = 0,
    val metadata: BookMetadataDto = BookMetadataDto(),
    val readProgress: ReadProgressDto? = null,
)

/**
 * 책 메타데이터 DTO
 */
@Serializable
data class BookMetadataDto(
    val title: String = "",
    val summary: String = "",
    val authors: List<String> = emptyList(),
    val releaseDate: String? = null,
    val isbn: String = "",
)

/**
 * 읽기 진행 상태 DTO
 */
@Serializable
data class ReadProgressDto(
    val page: Int = 0,
    val completed: Boolean = false,
    val readDate: String = "",
)

/**
 * 읽기 진행 상태 업데이트 요청 DTO
 */
@Serializable
data class ReadProgressUpdateDto(
    val page: Int,
    val completed: Boolean = false,
)

/**
 * 페이지 정보 DTO
 */
@Serializable
data class PageDto(
    val number: Int,
    val fileName: String = "",
    val mediaType: String = "",
    val width: Int = 0,
    val height: Int = 0,
)

/** 책 DTO를 도메인 모델로 변환 */
fun BookDto.toDomain(): Book = Book(
    id = id,
    seriesId = seriesId,
    name = name,
    number = number,
    sizeBytes = sizeBytes,
    pageCount = pagesCount,
    metadata = metadata.toDomain(),
    readProgress = readProgress?.toDomain(),
)

/** 책 메타데이터 DTO를 도메인 모델로 변환 */
fun BookMetadataDto.toDomain(): BookMetadata = BookMetadata(
    title = title,
    summary = summary,
    authors = authors,
    releaseDate = releaseDate?.let {
        try {
            LocalDate.parse(it, DateTimeFormatter.ISO_DATE)
        } catch (e: Exception) {
            null
        }
    },
    isbn = isbn,
)

/** 읽기 진행 DTO를 도메인 모델로 변환 */
fun ReadProgressDto.toDomain(): ReadProgress = ReadProgress(
    page = page,
    completed = completed,
    readDate = parseDateTime(readDate),
)
