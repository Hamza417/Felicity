package app.simple.felicity.repository.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Represents a single recording result from MusicBrainz, packaged as a [Parcelable]
 * so it can travel from [app.simple.felicity.ui.subpanels.MetadataSearch] back to
 * [app.simple.felicity.ui.subpanels.MetadataEditor] via the Fragment Result API.
 *
 * Every non-null field corresponds to a tag the metadata editor can fill in automatically
 * once the user picks a result. Fields that MusicBrainz did not return are left null so
 * the editor keeps whatever the user already typed in those boxes.
 *
 * @param title   The track title as reported by MusicBrainz.
 * @param artist  The primary credited artist name.
 * @param album   The album (release) title this recording appeared on, if available.
 * @param year    The four-digit release year, if available.
 * @param genre   The top community-voted genre tag from MusicBrainz, if available.
 *
 * @author Hamza417
 */
@Parcelize
data class MetadataSearchResult(
        val title: String,
        val artist: String,
        val album: String?,
        val year: String?,
        val genre: String?
) : Parcelable
