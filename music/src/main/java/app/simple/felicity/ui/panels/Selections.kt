package app.simple.felicity.ui.panels

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.lifecycle.lifecycleScope
import app.simple.felicity.adapters.ui.lists.AdapterSongs
import app.simple.felicity.callbacks.GeneralAdapterCallbacks
import app.simple.felicity.databinding.FragmentSelectionsBinding
import app.simple.felicity.databinding.HeaderSelectionsBinding
import app.simple.felicity.decorations.views.AppHeader
import app.simple.felicity.extensions.fragments.BasePanelFragment
import app.simple.felicity.repository.managers.SelectionManager
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.shared.utils.TimeUtils.toDynamicTimeString
import kotlinx.coroutines.launch

/**
 * A panel that shows every song the user has currently selected, all in one tidy place.
 * Think of it as your "shopping cart" view — tap a song to play the whole selection
 * starting from it, or long-press to remove it from the basket.
 *
 * The list stays live: if you go back and deselect something, this panel updates
 * automatically — no refresh button needed.
 *
 * @author Hamza417
 */
class Selections : BasePanelFragment() {

    private lateinit var binding: FragmentSelectionsBinding
    private lateinit var headerBinding: HeaderSelectionsBinding

    /**
     * Holds the adapter so we can diff-update it when the selection changes,
     * instead of rebuilding the whole list every time.
     */
    private var adapterSongs: AdapterSongs? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentSelectionsBinding.inflate(inflater, container, false)
        headerBinding = HeaderSelectionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.recyclerView.requireAttachedMiniPlayer()
        binding.appHeader.setContentView(headerBinding.root)
        binding.appHeader.attachTo(binding.recyclerView, AppHeader.ScrollMode.HIDE_ON_SCROLL)

        // One column list — same comfortable feel as the Songs panel in list mode.
        binding.recyclerView.setupGridLayoutManager(1)

        setupClickListeners()

        // Watch the selection basket and refresh the list every time it changes.
        viewLifecycleOwner.lifecycleScope.launch {
            SelectionManager.selectedAudios.collect { selected ->
                updateList(selected)
            }
        }
    }

    override fun onDestroyView() {
        adapterSongs = null
        super.onDestroyView()
    }

    private fun setupClickListeners() {
        // Tapping the X button clears everything and sends the user back —
        // essentially "cancel" for the whole selection session.
        headerBinding.close.setOnClickListener {
            SelectionManager.clear()
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        // The shuffle chip plays the entire selection in random order — because
        // sometimes you just want chaos.
        headerBinding.shuffle.setOnClickListener {
            val songs = SelectionManager.selectedAudios.value
            if (songs.isNotEmpty()) shuffleMediaItems(songs)
        }
    }

    /**
     * Refreshes the song list and all the little header chips whenever the
     * selection basket changes. First call creates the adapter; later calls
     * diff the new list in smoothly.
     *
     * @param songs The up-to-date list of selected [Audio] tracks.
     */
    private fun updateList(songs: List<Audio>) {
        if (adapterSongs == null) {
            adapterSongs = AdapterSongs(songs)
            adapterSongs?.setHasStableIds(true)
            adapterSongs?.setGeneralAdapterCallbacks(object : GeneralAdapterCallbacks {
                override fun onSongClicked(songs: MutableList<Audio>, position: Int, view: View) {
                    // Play the selected queue starting from whatever the user tapped.
                    setMediaItems(songs, position)
                }

                override fun onSongLongClicked(audios: MutableList<Audio>, position: Int, imageView: ImageView?) {
                    // Long-press deselects the song — handy for trimming the basket
                    // without having to go back to the list.
                    audios.getOrNull(position)?.let { SelectionManager.deselect(it) }
                }
            })
            binding.recyclerView.adapter = adapterSongs
        } else {
            adapterSongs?.updateSongs(songs)
            if (binding.recyclerView.adapter == null) {
                binding.recyclerView.adapter = adapterSongs
            }
        }

        // Update the count and total duration chips at the top so the user
        // always knows what they've got in the cart.
        headerBinding.count.text = songs.size.toString()
        headerBinding.hours.text = songs.sumOf { it.duration }.toDynamicTimeString()

        // If the basket is completely empty, there's nothing left to show here —
        // politely close the panel and let the user get on with their day.
        if (songs.isEmpty()) {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
    }

    companion object {
        const val TAG = "Selections"

        fun newInstance(): Selections {
            return Selections().apply {
                arguments = Bundle()
            }
        }
    }
}

