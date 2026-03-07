package app.simple.felicity.adapters.ui.page

import app.simple.felicity.databinding.AdapterStyleListBinding
import app.simple.felicity.decorations.overscroll.VerticalListViewHolder
import app.simple.felicity.glide.util.AudioCoverUtils.loadArtCoverWithPayload
import app.simple.felicity.repository.managers.MediaManager
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.shared.utils.TextViewUtils.setTextOrUnknown
import app.simple.felicity.utils.AdapterUtils.addAudioQualityIcon

class Songs(val binding: AdapterStyleListBinding) : VerticalListViewHolder(binding.root) {

    fun bindSelectionState(audio: Audio) {
        binding.container.isSelected = MediaManager.getCurrentSongId() == audio.id
    }

    fun bind(audio: Audio) {
        binding.apply {
            title.setTextOrUnknown(audio.title)
            title.addAudioQualityIcon(audio)
            secondaryDetail.setTextOrUnknown(audio.artist)
            tertiaryDetail.setTextOrUnknown(audio.album)
            bindSelectionState(audio)

            cover.loadArtCoverWithPayload(audio)
            cover.transitionName = audio.path
        }
    }
}
