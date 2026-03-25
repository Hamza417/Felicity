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
 * @author Hamza417
 */
class Song(val binding: AdapterStyleListBinding) : VerticalListViewHolder(binding.root) {

    /**
     * Binds [audio] data to the view. Calls [setAudioID] so the container registers
     * with [MediaManager] and animates highlight changes autonomously.
     *
     * @param audio the [Audio] item to display.
     */
    fun bind(audio: Audio) {
        binding.apply {
            title.setTextOrUnknown(audio.title)
            title.addAudioQualityIcon(audio)
            secondaryDetail.setTextOrUnknown(audio.getArtists())
            tertiaryDetail.setTextOrUnknown(audio.album)
            container.setAudioID(audio.id)
            cover.loadArtCoverWithPayload(audio)
            cover.transitionName = audio.path
        }
    }
}
