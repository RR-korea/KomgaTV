package com.komgatv.core.data.mapper

import com.komgatv.core.model.Book
import com.komgatv.core.model.BookMetadata
import com.komgatv.core.model.Library
import com.komgatv.core.model.ReadProgress
import com.komgatv.core.model.ReadingDirection
import com.komgatv.core.model.Series
import com.komgatv.core.model.SeriesMetadata
import snd.komga.client.book.KomgaBook
import snd.komga.client.common.KomgaReadingDirection
import snd.komga.client.library.KomgaLibrary
import snd.komga.client.series.KomgaSeries

// komga-client 모델 → core:model 변환

internal fun KomgaLibrary.toDomain() = Library(
    id = id.value,
    name = name,
)

internal fun KomgaSeries.toDomain() = Series(
    id = id.value,
    libraryId = libraryId.value,
    name = metadata.title.ifBlank { name },
    booksCount = booksCount,
    booksReadCount = booksReadCount,
    booksInProgressCount = booksInProgressCount,
    metadata = SeriesMetadata(
        title = metadata.title,
        summary = metadata.summary,
        status = metadata.status.name,
        publisher = metadata.publisher,
        genres = metadata.genres,
        readingDirection = metadata.readingDirection?.toDomain(),
    ),
)

internal fun KomgaBook.toDomain() = Book(
    id = id.value,
    seriesId = seriesId.value,
    seriesTitle = seriesTitle,
    name = metadata.title.ifBlank { name },
    number = number,
    pageCount = media.pagesCount,
    metadata = BookMetadata(
        title = metadata.title,
        summary = metadata.summary,
        number = metadata.number,
    ),
    readProgress = readProgress?.let { ReadProgress(page = it.page, completed = it.completed) },
)

private fun KomgaReadingDirection.toDomain() = when (this) {
    KomgaReadingDirection.LEFT_TO_RIGHT -> ReadingDirection.LEFT_TO_RIGHT
    KomgaReadingDirection.RIGHT_TO_LEFT -> ReadingDirection.RIGHT_TO_LEFT
    KomgaReadingDirection.VERTICAL -> ReadingDirection.VERTICAL
    KomgaReadingDirection.WEBTOON -> ReadingDirection.WEBTOON
}
