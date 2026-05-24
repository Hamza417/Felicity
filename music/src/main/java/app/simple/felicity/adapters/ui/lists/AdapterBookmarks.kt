package app.simple.felicity.adapters.ui.lists

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import app.simple.felicity.R
import app.simple.felicity.databinding.AdapterStyleListBinding
import app.simple.felicity.decorations.overscroll.VerticalListViewHolder
import app.simple.felicity.glide.util.AudioCoverUtils.loadArtCoverWithPayload
import app.simple.felicity.repository.models.AudioWithBookmarks
import app.simple.felicity.repository.models.AudioWithStat
import app.simple.felicity.repository.utils.AudioUtils.getProperAlbum
import app.simple.felicity.repository.utils.AudioUtils.getProperArtists
import app.simple.felicity.repository.utils.AudioUtils.getProperTitle

/**
 * Shows every audio track that has at least one saved bookmark as a simple flat list.
 * Tapping a row fires [Callbacks.onSongClicked] so the caller can open a dialog
 * with that song's full bookmark list.
 *
 * @author Hamza417
 */
class AdapterBookmarks(
        private var items: List<AudioWithBookmarks>
) : RecyclerView.Adapter<VerticalListViewHolder>() {

    var callbacks: Callbacks? = null

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VerticalListViewHolder {
        return SongHolder(AdapterStyleListBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: VerticalListViewHolder, position: Int) {
        (holder as SongHolder).bind(items[position])
    }

    inner class SongHolder(private val binding: AdapterStyleListBinding) :
            VerticalListViewHolder(binding.root) {

        fun bind(item: AudioWithBookmarks) {
            binding.title.text = item.audio.getProperTitle()
            binding.secondaryDetail.text = item.audio.getProperArtists()
            binding.tertiaryDetail.text = context.buildTertiaryText(item)
            binding.cover.loadArtCoverWithPayload(item.audio)

            binding.container.setOnClickListener {
                callbacks?.onSongClicked(item)
            }
        }
    }

    /**
     * Builds the combined play count + album string for the tertiary detail line.
     * If the album name is absent the count text is shown on its own.
     *
     * @param item the [AudioWithStat] whose tertiary text is being built
     */
    private fun Context.buildTertiaryText(item: AudioWithBookmarks): String {
        val album = item.audio.getProperAlbum().takeIf { it.isNotEmpty() }
        val stat = resources.getQuantityString(R.plurals.number_of_bookmarks, item.bookmarks.size, item.bookmarks.size)
        return if (album != null) "$stat \u2022 $album" else stat
    }

    /** Refreshes the list while keeping scroll position. */
    fun updateItems(newItems: List<AudioWithBookmarks>) {
        items = newItems
        notifyDataSetChanged()
    }

    interface Callbacks {
        /** Called when the user taps a song row — open the bookmarks dialog for that song. */
        fun onSongClicked(item: AudioWithBookmarks)
    }
}
