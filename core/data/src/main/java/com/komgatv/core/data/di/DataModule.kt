package com.komgatv.core.data.di

import com.komgatv.core.data.image.ImageUrls
import com.komgatv.core.data.image.SessionImageUrls
import com.komgatv.core.data.repository.BookRepository
import com.komgatv.core.data.repository.BookRepositoryImpl
import com.komgatv.core.data.repository.LibraryRepository
import com.komgatv.core.data.repository.LibraryRepositoryImpl
import com.komgatv.core.data.repository.SeriesRepository
import com.komgatv.core.data.repository.SeriesRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 데이터 계층 의존성 주입 모듈
 *
 * 리포지토리 인터페이스와 구현체를 바인딩합니다.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds
    @Singleton
    abstract fun bindLibraryRepository(
        impl: LibraryRepositoryImpl,
    ): LibraryRepository

    @Binds
    @Singleton
    abstract fun bindSeriesRepository(
        impl: SeriesRepositoryImpl,
    ): SeriesRepository

    @Binds
    @Singleton
    abstract fun bindBookRepository(
        impl: BookRepositoryImpl,
    ): BookRepository

    @Binds
    @Singleton
    abstract fun bindImageUrls(
        impl: SessionImageUrls,
    ): ImageUrls
}
