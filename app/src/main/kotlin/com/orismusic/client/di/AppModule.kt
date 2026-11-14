package com.orismusic.client.di

import android.content.Context
import androidx.media3.datasource.DefaultHttpDataSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideHttpDataSource(context: Context): DefaultHttpDataSource.Factory {
        return DefaultHttpDataSource.Factory()
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
}

@Module
@InstallIn(SingletonComponent::class)
object PlaybackModule {

    @Provides
    @Singleton
    fun providePlayerCache(): androidx.media3.datasource.cache.SimpleCache {
        // TODO: Implement proper cache directory
        return androidx.media3.datasource.cache.SimpleCache(
            java.io.File(android.os.Environment.getExternalStorageDirectory(), "OrisMusic/player_cache"),
            androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor(100 * 1024 * 1024), // 100MB
            androidx.media3.datasource.cache.CacheDataSource.DEFAULT_CACHE_DATA_SOURCE_FACTORY
        )
    }

    @Provides
    @Singleton
    fun provideDownloadCache(): androidx.media3.datasource.cache.SimpleCache {
        return androidx.media3.datasource.cache.SimpleCache(
            java.io.File(android.os.Environment.getExternalStorageDirectory(), "OrisMusic/download_cache"),
            androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor(500 * 1024 * 1024), // 500MB
            androidx.media3.datasource.cache.CacheDataSource.DEFAULT_CACHE_DATA_SOURCE_FACTORY
        )
    }
}