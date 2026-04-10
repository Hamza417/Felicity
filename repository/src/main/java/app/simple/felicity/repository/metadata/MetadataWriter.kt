package app.simple.felicity.repository.metadata

import android.util.Log
import app.simple.felicity.repository.metadata.MetadataWriter.write
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.images.ArtworkFactory
import java.io.File

/**
 * Writes audio tag metadata to a file on disk using JAudioTagger.
 *
 * Call [write] to apply a set of field updates and optional embedded artwork to an
 * existing audio file. The file is modified in-place; a backup is not created.
 *
 * @author Hamza417
 */
object MetadataWriter {

    private const val TAG = "MetadataWriter"

    /**
     * Holds all editable metadata fields for a single audio track.
     *
     * @param title       Song title.
     * @param artist      Primary artist name.
     * @param album       Album name.
     * @param albumArtist Album-level artist (e.g. "Various Artists").
     * @param year        Release year string (e.g. "2023").
     * @param trackNumber Track number within the album (e.g. "1" or "1/12").
     * @param numTracks   Total track count in the album.
     * @param discNumber  Disc number (e.g. "1" or "1/2").
     * @param genre       Genre string.
     * @param composer    Composer name.
     * @param writer      Lyricist / writer name.
     * @param compilation Compilation flag ("1" or "").
     * @param comment     Free-form comment embedded in the file.
     * @param lyrics      Unsynchronised lyrics embedded in the file.
     * @param artworkFile Optional image file to embed as album artwork.
     */
    data class Fields(
            val title: String?,
            val artist: String?,
            val album: String?,
            val albumArtist: String?,
            val year: String?,
            val trackNumber: String?,
            val numTracks: String?,
            val discNumber: String?,
            val genre: String?,
            val composer: String?,
            val writer: String?,
            val compilation: String?,
            val comment: String?,
            val lyrics: String?,
            val artworkFile: File? = null
    )

    /**
     * Writes the supplied [fields] to [targetFile] on disk.
     *
     * Each field that is null is left unchanged in the tag. Each non-null
     * field (including an empty string) will overwrite the existing value.
     *
     * @param targetFile  The audio file whose embedded tags will be updated.
     * @param fields      The [Fields] data to write into the file.
     * @throws Exception  Any JAudioTagger exception if the file cannot be read or written.
     */
    fun write(targetFile: File, fields: Fields) {
        val audioFile = AudioFileIO.read(targetFile)
        var tag = audioFile.tag

        if (tag == null) {
            audioFile.createDefaultTag()
            tag = audioFile.tag
        }

        fun setField(key: FieldKey, value: String?) {
            if (value != null) {
                runCatching {
                    tag?.setField(key, value)
                }.onFailure {
                    Log.w(TAG, "Failed to set field $key: ${it.message}")
                }
            }
        }

        setField(FieldKey.TITLE, fields.title)
        setField(FieldKey.ARTIST, fields.artist)
        setField(FieldKey.ALBUM, fields.album)
        setField(FieldKey.ALBUM_ARTIST, fields.albumArtist)
        setField(FieldKey.YEAR, fields.year)
        setField(FieldKey.TRACK, fields.trackNumber)
        setField(FieldKey.TRACK_TOTAL, fields.numTracks)
        setField(FieldKey.DISC_NO, fields.discNumber)
        setField(FieldKey.GENRE, fields.genre)
        setField(FieldKey.COMPOSER, fields.composer)
        setField(FieldKey.LYRICIST, fields.writer)
        setField(FieldKey.IS_COMPILATION, fields.compilation)
        setField(FieldKey.COMMENT, fields.comment)
        setField(FieldKey.LYRICS, fields.lyrics)

        fields.artworkFile?.let { artFile ->
            runCatching {
                val artwork = ArtworkFactory.createArtworkFromFile(artFile)
                tag?.deleteArtworkField()
                tag?.setField(artwork)
            }.onFailure {
                Log.w(TAG, "Failed to embed artwork: ${it.message}")
            }
        }

        AudioFileIO.write(audioFile)
        Log.d(TAG, "Metadata written successfully to: ${targetFile.absolutePath}")
    }
}

