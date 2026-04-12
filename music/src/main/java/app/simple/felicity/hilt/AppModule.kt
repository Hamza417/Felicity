package app.simple.felicity.hilt

import android.content.Context
import app.simple.felicity.repository.loader.AudioDatabaseLoader
import app.simple.felicity.repository.loader.PlaylistDatabaseLoader
import app.simple.felicity.repository.repositories.AlbumRepository
import app.simple.felicity.repository.repositories.ArtistRepository
import app.simple.felicity.repository.repositories.AudioRepository
import app.simple.felicity.repository.repositories.LrcRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideAlbumRepository(@ApplicationContext context: Context): AlbumRepository {
        return AlbumRepository(context)
    }

    @Provides
    @Singleton
    fun provideArtistRepository(@ApplicationContext context: Context): ArtistRepository {
        return ArtistRepository(context)
    }

    @Provides
    @Singleton
    fun provideAudioDatabaseLoader(@ApplicationContext context: Context): AudioDatabaseLoader {
        return AudioDatabaseLoader(context)
    }

    /**
     * Provides the singleton [PlaylistDatabaseLoader] so Hilt can inject it into the
     * [app.simple.felicity.repository.services.AudioDatabaseService]. It lives next
     * to the audio loader because the two always run together — audio first, then
     * playlists on top of whatever tracks were just scanned.
     */
    @Provides
    @Singleton
    fun providePlaylistDatabaseLoader(@ApplicationContext context: Context): PlaylistDatabaseLoader {
        return PlaylistDatabaseLoader(context)
    }

    @Provides
    @Singleton
    fun provideAudioRepository(@ApplicationContext context: Context): AudioRepository {
        return AudioRepository(context)
    }

    @Provides
    @Singleton
    fun providesLrcRepository(): LrcRepository {
        return LrcRepository()
    }
}