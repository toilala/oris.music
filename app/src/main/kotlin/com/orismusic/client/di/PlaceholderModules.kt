package com.orismusic.client.di

import android.content.Context
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

// Placeholder interfaces for missing dependencies
interface MusicDatabase {
    fun song(mediaId: String?): Any?
    fun format(mediaId: String?): Any?
    fun query(block: () -> Unit)
    suspend fun updateLyricsOffset(songId: String, offsetMs: Int)
}

interface LyricsHelper

interface SyncUtils

interface EqualizerService

interface AudioLabStore

interface AudioFxService

interface SongTuning

interface Scrobbler

interface DiscordPresence

interface SourceModules

interface MochyWidgetManager

interface NetworkConnectivityObserver

interface SleepTimer

interface CastConnectionHandler

interface ForcedRoute

interface CustomStreamResult

interface LoudnessEnhancer

interface HapticAudioProcessor

interface SilenceDetectorAudioProcessor

interface ReplayGainTags

interface CustomEqualizerAudioProcessor

interface CrossmixTransitionAudioProcessor

interface AudioSpectrum

interface CarLyrics

interface WatchEndpoint

@Module
@InstallIn(SingletonComponent::class)
object PlaceholderModule {

    @Provides
    @Singleton
    fun provideMusicDatabase(@ApplicationContext context: Context): MusicDatabase = object : MusicDatabase {
        override fun song(mediaId: String?): Any? = null
        override fun format(mediaId: String?): Any? = null
        override fun query(block: () -> Unit) = block()
        override suspend fun updateLyricsOffset(songId: String, offsetMs: Int) {}
    }

    @Provides
    @Singleton
    fun provideLyricsHelper(): LyricsHelper = object : LyricsHelper {}

    @Provides
    @Singleton
    fun provideSyncUtils(): SyncUtils = object : SyncUtils {}

    @Provides
    @Singleton
    fun provideEqualizerService(): EqualizerService = object : EqualizerService {}

    @Provides
    @Singleton
    fun provideAudioLabStore(): AudioLabStore = object : AudioLabStore {}

    @Provides
    @Singleton
    fun provideAudioFxService(): AudioFxService = object : AudioFxService {}

    @Provides
    @Singleton
    fun provideSongTuning(): SongTuning = object : SongTuning {}

    @Provides
    @Singleton
    fun provideScrobbler(): Scrobbler = object : Scrobbler {}

    @Provides
    @Singleton
    fun provideDiscordPresence(): DiscordPresence = object : DiscordPresence {}

    @Provides
    @Singleton
    fun provideSourceModules(): SourceModules = object : SourceModules {}

    @Provides
    @Singleton
    fun provideMochyWidgetManager(): MochyWidgetManager = object : MochyWidgetManager {}

    @Provides
    @Singleton
    fun provideNetworkConnectivityObserver(@ApplicationContext context: Context): NetworkConnectivityObserver = object : NetworkConnectivityObserver {}

    @Provides
    @Singleton
    fun provideSleepTimer(): SleepTimer = object : SleepTimer {}

    @Provides
    @Singleton
    fun provideCastConnectionHandler(): CastConnectionHandler = object : CastConnectionHandler {}
}

@Module
@InstallIn(SingletonComponent::class)
object PlaybackModule {

    @Provides
    @Singleton
    fun providePlayerCache(
        @ApplicationContext context: Context,
        databaseProvider: DatabaseProvider,
    ): androidx.media3.datasource.cache.SimpleCache {
        return androidx.media3.datasource.cache.SimpleCache(
            context.filesDir.resolve("exoplayer"),
            androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor(100 * 1024 * 1024), // 100MB
            databaseProvider,
        )
    }

    @Provides
    @Singleton
    fun provideDownloadCache(
        @ApplicationContext context: Context,
        databaseProvider: DatabaseProvider,
    ): androidx.media3.datasource.cache.SimpleCache {
        return androidx.media3.datasource.cache.SimpleCache(
            context.filesDir.resolve("download"),
            androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor(500 * 1024 * 1024), // 500MB
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