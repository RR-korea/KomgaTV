package com.komgatv.core.model

/** Komga 책 (권/화) */
data class Book(
    val id: String,
    val seriesId: String,
    val seriesTitle: String,
    val name: String,
    val number: Int,
    /** 페이지 수. 페이지 번호는 1부터 시작한다. */
    val pageCount: Int,
    val metadata: BookMetadata,
    val readProgress: ReadProgress?,
)

data class BookMetadata(
    val title: String,
    val summary: String,
    val number: String,
)

/** 서버에 저장된 읽기 진행. page는 1부터 시작한다. */
data class ReadProgress(
    val page: Int,
    val completed: Boolean,
)
