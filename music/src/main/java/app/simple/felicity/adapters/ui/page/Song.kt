package app.simple.felicity.adapters.ui.page

import app.simple.felicity.databinding.AdapterStyleListBinding
import app.simple.felicity.decorations.overscroll.VerticalListViewHolder
import app.simple.felicity.decorations.utils.TextViewUtils.setTextOrUnknown
import app.simple.felicity.glide.util.AudioCoverUtils.loadArtCoverWithPayload
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.repository.utils.AudioUtils.getArtists
import app.simple.felicity.utils.AdapterUtils.addAudioQualityIcon

/**
 * ViewHolder for a single song row inside a page (album, artist, genre, folder, or year).
 * Selection highlighting is handled automatically by [MediaAwareRippleConstraintLayout]
 * via [setAudioID] — no external payload or notify calls are required.
 *
 * When [showTrackInfo] is true (AlbumPage sorted by track number), the tertiary detail
 * shows the track position in the format {@code trackNumber/totalSongs} (e.g. "1/12"),
 * replacing the album name to surface ordering information that is meaningful within the
 * album context.
 *
 * @author Hamza417
 */
class Song(val binding: AdapterStyleListBinding) : VerticalListViewHolder(binding.root) {

    /**
     * Binds [audio] data to the view. Calls [setAudioID] so the container registers
     * with [MediaManager] and animates highlight changes autonomously.
     *
     * @param audio         The [Audio] item to display.
     * @param showTrackInfo When true the tertiary detail shows track position as "N/total".
     * @param totalSongs    Total number of songs in the current page list, used when [showTrackInfo] is true.
     */
    fun bind(audio: Audio, showTrackInfo: Boolean = false, totalSongs: Int = 0) {
        binding.apply {
            title.setTextOrUnknown(audio.title)
            title.addAudioQualityIcon(audio)
            secondaryDetail.setTextOrUnknown(audio.getArtists())
            if (showTrackInfo && totalSongs > 0) {
                // trackNumber may be stored as "N/total" by the tag reader, so extract
                // only the part before the slash to avoid showing "N/total/total".
                val rawPart = audio.trackNumber?.trim()?.split("/")?.firstOrNull()?.trim() ?: ""
                val trackNum = rawPart.trimStart('0').takeIf { it.isNotEmpty() } ?: rawPart.ifEmpty { "?" }
                tertiaryDetail.text = "$trackNum/$totalSongs"
            } else {
                tertiaryDetail.setTextOrUnknown(audio.album)
            }
            container.setAudioID(audio.id)
            cover.loadArtCoverWithPayload(audio)
            cover.transitionName = audio.uri
        }
    }
}
