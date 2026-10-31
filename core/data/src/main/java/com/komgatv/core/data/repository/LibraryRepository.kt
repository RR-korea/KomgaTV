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
}
