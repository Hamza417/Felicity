package app.simple.felicity.repository.models

/**
 * Groups a single audio track together with all of its bookmarks.
 *
 * This is not a Room relation — it is assembled at the application layer by
 * joining the bookmarks table with the audio table on the content hash. Doing
 * it this way avoids adding a foreign key to [AudioBookmark] and keeps the two
 * tables fully independent, which is intentional: bookmarks must survive even
 * when the audio row is deleted and later re-added.
 *
 * @author Hamza417
 */
data class AudioWithBookmarks(
        /** The audio track these bookmarks belong to. */
        val audio: Audio,
        /** All bookmarks for this track, ordered by timestamp ascending. */
        val bookmarks: List<AudioBookmark>
)

