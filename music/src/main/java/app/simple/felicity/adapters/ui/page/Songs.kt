package app.simple.felicity.adapters.ui.page

import app.simple.felicity.databinding.AdapterStyleListBinding
import app.simple.felicity.decorations.overscroll.VerticalListViewHolder
import app.simple.felicity.decorations.utils.TextViewUtils.setTextOrUnknown
import app.simple.felicity.glide.util.AudioCoverUtils.loadArtCoverWithPayload
import app.simple.felicity.repository.models.Song

class Songs(val binding: AdapterStyleListBinding) : VerticalListViewHolder(binding.root) {
    fun bind(song: Song) {
        binding.apply {
            title.setTextOrUnknown(song.title)
            secondaryDetail.setTextOrUnknown(song.artist)
            tertiaryDetail.setTextOrUnknown(song.album)

            cover.loadArtCoverWithPayload(song)
            cover.transitionName = song.path
        }
    }
}
