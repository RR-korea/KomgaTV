package com.komgatv.core.datastore.di

import com.komgatv.core.datastore.DefaultHomePreferences
import com.komgatv.core.datastore.DefaultReaderPreferences
import com.komgatv.core.datastore.HomePreferences
import com.komgatv.core.datastore.DefaultServerPreferences
import com.komgatv.core.datastore.ReaderPreferences
import com.komgatv.core.datastore.ServerPreferences
import com.komgatv.core.datastore.crypto.CredentialEncryptor
import com.komgatv.core.datastore.crypto.KeystoreCredentialEncryptor
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataStoreModule {

    @Binds
    @Singleton
    abstract fun bindCredentialEncryptor(
        impl: KeystoreCredentialEncryptor,
    ): CredentialEncryptor

    @Binds
    @Singleton
    abstract fun bindServerPreferences(
        impl: DefaultServerPreferences,
    ): ServerPreferences

    @Binds
    @Singleton
    abstract fun bindHomePreferences(
        impl: DefaultHomePreferences,
    ): HomePreferences

    @Binds
    @Singleton
    abstract fun bindReaderPreferences(
        impl: DefaultReaderPreferences,
    ): ReaderPreferences
}
