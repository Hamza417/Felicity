package app.simple.felicity.glide.artistcover

import app.simple.felicity.repository.models.Artist
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ArtistCoverLoaderTest {

    @Test
    fun cacheKeyDiffersForDifferentArtistsWithoutSavedImages() {
        val firstArtist = artist(id = 1L, name = "Artist X", songPaths = listOf("content://song/1"))
        val secondArtist = artist(id = 2L, name = "Artist Y", songPaths = listOf("content://song/2"))

        assertNotEquals(
                ArtistCoverLoader.createCacheKey(firstArtist, lastModified = 0L),
                ArtistCoverLoader.createCacheKey(secondArtist, lastModified = 0L)
        )
    }

    @Test
    fun cacheKeyChangesWhenArtistSongPathsChange() {
        val original = artist(id = 1L, name = "Artist X", songPaths = listOf("content://song/1"))
        val updated = original.copy(songPaths = listOf("content://song/2"))

        assertNotEquals(
                ArtistCoverLoader.createCacheKey(original, lastModified = 0L),
                ArtistCoverLoader.createCacheKey(updated, lastModified = 0L)
        )
    }

    @Test
    fun cacheKeyIsStableForUnchangedArtworkSources() {
        val artist = artist(id = 1L, name = "Artist X", songPaths = listOf("content://song/1"))

        assertEquals(
                ArtistCoverLoader.createCacheKey(artist, lastModified = 123L),
                ArtistCoverLoader.createCacheKey(artist, lastModified = 123L)
        )
    }

    private fun artist(id: Long, name: String, songPaths: List<String>): Artist {
        return Artist(
                id = id,
                name = name,
                albumCount = 1,
                trackCount = songPaths.size,
                songPaths = songPaths
        )
    }
}