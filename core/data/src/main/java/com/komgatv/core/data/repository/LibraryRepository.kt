package com.komgatv.core.data.repository

import com.komgatv.core.data.mapper.toDomain
import com.komgatv.core.model.Library
import com.komgatv.core.network.ServerSessionManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import snd.komga.client.library.KomgaLibraryId
import javax.inject.Inject
import javax.inject.Singleton

interface LibraryRepository {
    fun getLibraries(): Flow<List<Library>>
    suspend fun getLibraryById(libraryId: String): Library

    /** 라이브러리 폴더를 다시 훑는다 (관리자만, 서버가 따로 진행). 지운 파일은 휴지통으로 간다. */
    suspend fun scan(libraryId: String)

    /** 휴지통 비우기 — 파일이 없어진 책·시리즈를 지운다 (관리자만) */
    suspend fun emptyTrash(libraryId: String)
}

@Singleton
class LibraryRepositoryImpl @Inject constructor(
    private val sessionManager: ServerSessionManager,
) : LibraryRepository {

    override fun getLibraries(): Flow<List<Library>> = flow {
        emit(sessionManager.requireClient().libraryClient().getLibraries().map { it.toDomain() })
    }

    override suspend fun getLibraryById(libraryId: String): Library =
        sessionManager.requireClient().libraryClient().getLibrary(KomgaLibraryId(libraryId)).toDomain()

    override suspend fun scan(libraryId: String) {
        sessionManager.requireClient().libraryClient().scan(KomgaLibraryId(libraryId), deep = false)
    }

    override suspend fun emptyTrash(libraryId: String) {
        sessionManager.requireClient().libraryClient().emptyTrash(KomgaLibraryId(libraryId))
    }
}
