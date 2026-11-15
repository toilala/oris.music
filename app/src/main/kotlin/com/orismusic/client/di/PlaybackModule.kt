package com.orismusic.client.di

import android.content.Context
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.database.DatabaseProvider
import androidx.media3.database.StandaloneDatabaseProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PlaybackModule {

    @Provides
    @Singleton
    fun providePlayerCache(
        @ApplicationContext context: Context,
        databaseProvider: DatabaseProvider,
    ): SimpleCache {
        return SimpleCache(
            context.filesDir.resolve("exoplayer"),
            LeastRecentlyUsedCacheEvictor(100 * 1024 * 1024), // 100MB
            databaseProvider,
        )
    }

    @Provides
    @Singleton
    fun provideDownloadCache(
        @ApplicationContext context: Context,
        databaseProvider: DatabaseProvider,
    ): SimpleCache {
        return SimpleCache(
            context.filesDir.resolve("download"),
            LeastRecentlyUsedCacheEvictor(500 * 1024 * 1024), // 500MB
            databaseProvider,
        )
    }
}

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideHttpDataSource(@ApplicationContext context: Context): androidx.media3.datasource.DefaultHttpDataSource.Factory {
        return androidx.media3.datasource.DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(mapOf("User-Agent" to "OrisMusic/1.0"))
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(20000)
            .setReadTimeoutMs(20000)
    }

    @Provides
    @Singleton
    fun provideApplicationScope(): CoroutineScope {
        return CoroutineScope(SupervisorJob() + Dispatchers.Main)
    }

    @Provides
    @Singleton
    fun provideDatabaseProvider(@ApplicationContext context: Context): DatabaseProvider = StandaloneDatabaseProvider(context)
}