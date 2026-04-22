package app.simple.felicity.repository.metadata

/**
 * A plain data container that holds every piece of metadata TagLib
 * extracted from an audio file. The JNI layer fills this in and hands
 * it back to Kotlin, where [TagLibLoader] converts it into the actual
 * [app.simple.felicity.repository.models.Audio] database model.
 *
 * The order and types of the constructor parameters must match the JNI
 * constructor call in taglib_jni.cpp exactly — the C++ side creates
 * instances of this class by calling the constructor by signature, so a
 * mismatch here will cause a runtime crash, not a compile error.
 *
 * @author Hamza417
 */
data class TagLibMetadata(
        /** Song title embedded in the tags, or null if the tag is absent. */
        val title: String?,
        /** Primary artist name. */
        val artist: String?,
        /** Album name. */
        val album: String?,
        /** Genre string (could be numeric ID3v1 genre or a freeform name). */
        val genre: String?,
        /** Release year as a string, e.g. "2023". */
        val year: String?,
        /** Free-form comment tag. */
        val comment: String?,
        /** Album-level artist, e.g. "Various Artists". */
        val albumArtist: String?,
        /** Composer name. */
        val composer: String?,
        /** Lyricist / writer name. */
        val lyricist: String?,
        /** Disc number within a multi-disc set. */
        val discNumber: String?,
        /** Track number within the album. */
        val trackNumber: String?,
        /** Total number of tracks on the album. */
        val numTracks: String?,
        /** Compilation flag — "1" if this is part of a compilation. */
        val compilation: String?,
        /** Track duration in milliseconds. Zero means TagLib couldn't determine it. */
        val duration: Long,
        /** Average bitrate in kilobits per second. */
        val bitrate: Long,
        /** Sample rate in Hz (e.g. 44100, 48000, 96000). */
        val sampleRate: Long,
        /** Bit depth per sample (e.g. 16, 24, 32). Zero for lossy formats. */
        val bitsPerSample: Long
)

