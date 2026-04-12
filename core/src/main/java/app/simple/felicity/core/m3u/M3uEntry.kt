package app.simple.felicity.core.m3u

/**
 * Represents a single track entry inside an M3U playlist file.
 *
 * <p>When an M3U file contains an {@code #EXTINF} line before a path, the
 * duration and title from that tag are captured here. If no {@code #EXTINF}
 * is present the fields fall back to their default values — think of it as a
 * track that prefers to keep a low profile.</p>
 *
 * @param path            The raw path or URL as written in the M3U file. This
 *                        may be absolute, relative, or a network URL — the
 *                        parser hands it back exactly as found and lets the
 *                        importer figure out where it actually lives.
 * @param title           Optional display name from the {@code #EXTINF} tag.
 *                        Null when the file has no extended info for this track.
 * @param durationSeconds Track length in whole seconds from {@code #EXTINF},
 *                        or {@code -1} if no duration was specified.
 *
 * @author Hamza417
 */
data class M3uEntry(
        val path: String,
        val title: String? = null,
        val durationSeconds: Long = -1L
)

