package app.simple.felicity.repository.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Represents a single search result from the LrcLib metadata search, packaged
 * as a [Parcelable] so it can be delivered from [app.simple.felicity.ui.subpanels.MetadataSearch]
 * back to [app.simple.felicity.ui.subpanels.MetadataEditor] via the Fragment Result API.
 *
 * Every non-null field in this class corresponds to a field the metadata editor
 * can fill in automatically once the user selects a result.
 *
 * @param title         Song title from LrcLib.
 * @param artist        Primary artist name.
 * @param album         Album name, or null if not provided by the API.
 * @param plainLyrics   Unsynchronized (plain text) lyrics to embed in the file's tag.
 * @param syncedLyrics  Synchronized LRC lyrics to save as a sidecar .lrc file.
 * @param isInstrumental True when the track has no lyrics by design.
 *
 * @author Hamza417
 */
@Parcelize
data class MetadataSearchResult(
        val title: String,
        val artist: String,
        val album: String?,
        val plainLyrics: String?,
        val syncedLyrics: String?,
        val isInstrumental: Boolean
) : Parcelable

