package app.simple.felicity.hilt

import android.content.Context
import app.simple.felicity.repository.loader.AudioDatabaseLoader
import app.simple.felicity.repository.repositories.AlbumRepository
import app.simple.felicity.repository.repositories.ArtistRepository
import app.simple.felicity.repository.repositories.AudioRepository
import app.simple.felicity.repository.repositories.GenreRepository
import app.simple.felicity.repository.repositories.SongRepository
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
    fun provideSongRepository(@ApplicationContext context: Context): SongRepository {
        return SongRepository(context)
    }

    @Provides
    @Singleton
    fun provideAlbumRepository(@ApplicationContext context: Context): AlbumRepository {
        return AlbumRepository(context)
    }

    @Provides
    @Singleton
    fun provideGenreRepository(@ApplicationContext context: Context): GenreRepository {
        return GenreRepository(context)
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

    @Provides
    @Singleton
    fun provideAudioRepository(@ApplicationContext context: Context): AudioRepository {
        return AudioRepository(context)
    }
}