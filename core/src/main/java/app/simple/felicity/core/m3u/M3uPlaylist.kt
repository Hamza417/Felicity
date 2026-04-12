package app.simple.felicity.core.m3u

/**
 * Holds the complete result of parsing an M3U or M3U8 playlist file.
 *
 * <p>The {@code name} field is populated from an optional {@code #PLAYLIST}
 * directive near the top of the file. When no such tag is present it stays
 * null and callers typically fall back to the filename — perfectly reasonable
 * for a playlist that never bothered to introduce itself.</p>
 *
 * <p>The {@code entries} list preserves the original order from the file so
 * that the import step can reproduce the author's intended track sequence.</p>
 *
 * @param name    Optional playlist display name sourced from a {@code #PLAYLIST}
 *                directive, or null when the file omits it.
 * @param entries All the track entries found in the file, in order.
 *
 * @author Hamza417
 */
data class M3uPlaylist(
        val name: String? = null,
        val entries: List<M3uEntry>
)

