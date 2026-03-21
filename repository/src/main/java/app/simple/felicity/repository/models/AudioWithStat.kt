package app.simple.felicity.repository.models

import androidx.room.ColumnInfo
import androidx.room.Embedded

/**
 * Projection returned by stat-enriched audio queries. The {@link Audio} record is fully
 * embedded so all track metadata is accessible, while {@code lastPlayed} and
 * {@code playCount} are included from the {@code song_stats} join.
 *
 * <p>This class is intentionally <em>not</em> a Room {@code @Entity} – it is a plain
 * read-only projection (POJO) produced by JOIN queries defined in {@link SongStatDao}.
 * It is never written to the database directly.</p>
 *
 * @author Hamza417
 */
data class AudioWithStat(
        @Embedded val audio: Audio,
        @ColumnInfo(name = "lastPlayed") val lastPlayed: Long,
        @ColumnInfo(name = "playCount") val playCount: Int
)

