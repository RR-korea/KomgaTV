package com.komgatv.feature.reader.di

import com.komgatv.feature.reader.continuous.TileDecoder
import com.komgatv.feature.reader.continuous.TileSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.scopes.ViewModelScoped

@Module
@InstallIn(ViewModelComponent::class)
abstract class ReaderModule {

    /** 리더 화면마다 하나. 리더를 떠나면 디코더와 타일 메모리를 함께 놓는다 */
    @Binds
    @ViewModelScoped
    abstract fun bindTileSource(impl: TileDecoder): TileSource
}
