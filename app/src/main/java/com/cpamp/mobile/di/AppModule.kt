package com.cpamp.mobile.di

import com.cpamp.mobile.data.security.AndroidKeystoreSecretStore
import com.cpamp.mobile.data.security.SecretStore
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
}

