package com.komgatv.core.data.repository

import com.komgatv.core.model.Library
import com.komgatv.core.network.KomgaApi
import com.komgatv.core.network.dto.toDomain
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 라이브러리 저장소 인터페이스
 *
 * Komga 서버의 라이브러리 데이터에 접근하기 위한 추상화 계층입니다.
 */
interface LibraryRepository {

    /** 모든 라이브러리 목록을 Flow로 조회 */
    fun getLibraries(): Flow<List<Library>>

    /** ID로 특정 라이브러리 조회 */
    suspend fun getLibraryById(libraryId: String): Library?
}

/**
 * 라이브러리 저장소 구현체
 *
 * KomgaApi를 사용하여 서버에서 라이브러리 데이터를 가져옵니다.
 * DTO를 도메인 모델로 변환하여 반환합니다.
 */
@Singleton
class LibraryRepositoryImpl @Inject constructor(
    private val komgaApi: KomgaApi,
) : LibraryRepository {

    /**
     * 모든 라이브러리 목록 조회
     *
     * 서버 API를 호출하여 라이브러리 목록을 가져오고
     * DTO를 도메인 모델로 변환하여 Flow로 방출합니다.
     */
    override fun getLibraries(): Flow<List<Library>> = flow {
        val libraries = komgaApi.getLibraries().map { it.toDomain() }
        emit(libraries)
    }

    /**
     * ID로 특정 라이브러리 조회
     *
     * 전체 목록에서 ID가 일치하는 라이브러리를 찾아 반환합니다.
     * 해당 ID의 라이브러리가 없으면 null을 반환합니다.
     */
    override suspend fun getLibraryById(libraryId: String): Library? {
        return komgaApi.getLibraries()
            .map { it.toDomain() }
            .find { it.id == libraryId }
    }
}
