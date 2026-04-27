package app.simple.felicity.ui.panels

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.fragment.app.viewModels
import app.simple.felicity.adapters.ui.lists.AdapterSongs
import app.simple.felicity.callbacks.GeneralAdapterCallbacks
import app.simple.felicity.databinding.FragmentAlwaysSkippedBinding
import app.simple.felicity.databinding.HeaderAlwaysSkippedBinding
import app.simple.felicity.decorations.views.AppHeader
import app.simple.felicity.extensions.fragments.BasePanelFragment
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.shared.utils.TimeUtils.toDynamicTimeString
import app.simple.felicity.viewmodels.panels.AlwaysSkippedViewModel
import dagger.hilt.android.AndroidEntryPoint

/**
 * Shows every song the user has condemned to the "always skip" list —
 * a graveyard of tracks that committed some offense against the listener's ears.
 *
 * Tapping a song plays just that one track (no queue flooding with songs
 * that are supposed to be skipped anyway — that would be a bit self-defeating).
 * Long-pressing opens the familiar song menu so you can pardon a song and
 * remove it from this list if you've had a change of heart.
 *
 * The list stays live via a Flow, so it instantly reflects any changes
 * made from the song menu elsewhere in the app.
 *
 * @author Hamza417
 */
@AndroidEntryPoint
class AlwaysSkipped : BasePanelFragment() {

    private lateinit var binding: FragmentAlwaysSkippedBinding
    private lateinit var headerBinding: HeaderAlwaysSkippedBinding

    /** Reuse the same songs adapter — no need to reinvent the wheel. */
    private var adapterSongs: AdapterSongs? = null

    private val alwaysSkippedViewModel: AlwaysSkippedViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentAlwaysSkippedBinding.inflate(inflater, container, false)
        headerBinding = HeaderAlwaysSkippedBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.recyclerView.requireAttachedMiniPlayer()
        binding.appHeader.setContentView(headerBinding.root)
        binding.appHeader.attachTo(binding.recyclerView, AppHeader.ScrollMode.HIDE_ON_SCROLL)

        // Single-column list — nice and clean, no grid needed for this panel.
        binding.recyclerView.setupGridLayoutManager(1)

        // Watch the database for changes so the list stays perfectly in sync.
        alwaysSkippedViewModel.skippedSongs.collectListWhenStarted({ adapterSongs != null }) { songs ->
            updateList(songs)
        }
    }

    override fun onDestroyView() {
        adapterSongs = null
        super.onDestroyView()
    }

    /**
     * Refreshes the list and header chips when the always-skipped set changes.
     * First call creates the adapter; subsequent calls diff the new data in smoothly.
     *
     * @param songs The latest list of always-skipped [Audio] tracks.
     */
    private fun updateList(songs: List<Audio>) {
        if (adapterSongs == null) {
            adapterSongs = AdapterSongs(songs)
            adapterSongs?.setHasStableIds(true)
            adapterSongs?.setGeneralAdapterCallbacks(object : GeneralAdapterCallbacks {
                override fun onSongClicked(songs: MutableList<Audio>, position: Int, view: View) {
                    // Play only the tapped song — we definitely don't want to flood the queue
                    // with songs that are marked to always be skipped. That would be a comedy of errors.
                    val tappedSong = songs.getOrNull(position) ?: return
                    setMediaItems(listOf(tappedSong), 0)
                }

                override fun onSongLongClicked(audios: MutableList<Audio>, position: Int, imageView: ImageView?) {
                    // Full song menu — this is where they can pardon a song and remove the skip flag.
                    openSongsMenu(audios, position, imageView)
                }
            })
            binding.recyclerView.adapter = adapterSongs
        } else {
            adapterSongs?.updateSongs(songs)
            if (binding.recyclerView.adapter == null) {
                binding.recyclerView.adapter = adapterSongs
            }
        }

        headerBinding.count.text = songs.size.toString()
        headerBinding.hours.text = songs.sumOf { it.duration }.toDynamicTimeString()
    }

    companion object {
        const val TAG = "AlwaysSkipped"

        fun newInstance(): AlwaysSkipped {
            return AlwaysSkipped().apply {
                arguments = Bundle()
            }
        }
    }
}