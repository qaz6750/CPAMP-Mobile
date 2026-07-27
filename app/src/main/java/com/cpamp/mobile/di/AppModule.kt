package com.cpamp.mobile.di

import com.cpamp.mobile.data.security.AndroidKeystoreSecretStore
import com.cpamp.mobile.data.security.SecretStore
import android.content.Context
import androidx.room.Room
import com.cpamp.mobile.data.cache.CPAMPCacheDatabase
import com.cpamp.mobile.data.cache.CacheDao
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.serialization.json.Json

@Module
@InstallIn(SingletonComponent::class)
abstract class AppBindings {
    @Binds
    @Singleton
    abstract fun bindSecretStore(implementation: AndroidKeystoreSecretStore): SecretStore
}

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        isLenient = false
        encodeDefaults = true
    }

    @Provides
    @Singleton
    fun provideCacheDatabase(
        @ApplicationContext context: Context,
    ): CPAMPCacheDatabase = Room.databaseBuilder(
        context,
        CPAMPCacheDatabase::class.java,
        "cpamp_cache.db",
    ).fallbackToDestructiveMigration().build()

    @Provides
    fun provideCacheDao(database: CPAMPCacheDatabase): CacheDao = database.cacheDao()
}
