package com.orismusic.client.di

import android.content.Context
import com.orismusic.client.playback.MediaLibrarySessionCallback
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlin.annotation.AnnotationRetention
import kotlin.annotation.AnnotationTarget
import kotlin.annotation.AnnotationTarget.FIELD
import kotlin.annotation.AnnotationTarget.FUNCTION
import kotlin.annotation.AnnotationTarget.PROPERTY_GETTER
import kotlin.annotation.AnnotationTarget.PROPERTY_SETTER
import kotlin.annotation.AnnotationTarget.VALUE_PARAMETER

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
@Target(FIELD, FUNCTION, PROPERTY_GETTER, PROPERTY_SETTER, VALUE_PARAMETER)
annotation class PlayerCache

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
@Target(FIELD, FUNCTION, PROPERTY_GETTER, PROPERTY_SETTER, VALUE_PARAMETER)
annotation class DownloadCache

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

interface CastConnectionHandler {
    val isCasting: kotlinx.coroutines.flow.MutableStateFlow<Boolean>
    val castIsPlaying: kotlinx.coroutines.flow.MutableStateFlow<Boolean>
    fun play()
    fun pause()
    fun seekTo(position: Long)
    fun skipToNext()
    fun skipToPrevious()
}

interface ForcedRoute

interface CustomStreamResult {
    val streamUrl: String get() = ""
    val cacheKey: String? get() = null
}

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
    fun provideMediaLibrarySessionCallback(): MediaLibrarySessionCallback = MediaLibrarySessionCallback()

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
    fun provideCastConnectionHandler(): CastConnectionHandler = object : CastConnectionHandler {
        override val isCasting = kotlinx.coroutines.flow.MutableStateFlow(false)
        override val castIsPlaying = kotlinx.coroutines.flow.MutableStateFlow(false)
        override fun play() {}
        override fun pause() {}
        override fun seekTo(position: Long) {}
        override fun skipToNext() {}
        override fun skipToPrevious() {}
    }
}