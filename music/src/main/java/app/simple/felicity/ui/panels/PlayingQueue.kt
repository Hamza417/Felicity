package app.simple.felicity.ui.panels

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import app.simple.felicity.R
import app.simple.felicity.adapters.ui.lists.AdapterPlayingQueue
import app.simple.felicity.callbacks.GeneralAdapterCallbacks
import app.simple.felicity.databinding.FragmentPlayingQueueBinding
import app.simple.felicity.databinding.HeaderPlayingQueueBinding
import app.simple.felicity.decorations.views.AppHeader
import app.simple.felicity.extensions.fragments.PanelFragment
import app.simple.felicity.repository.managers.MediaManager
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.shared.utils.TimeUtils.toHighlightedTimeString
import app.simple.felicity.theme.managers.ThemeManager
import app.simple.felicity.viewmodels.panels.PlayingQueueViewModel
import kotlinx.coroutines.launch

class PlayingQueue : PanelFragment() {

    private lateinit var binding: FragmentPlayingQueueBinding
    private lateinit var headerBinding: HeaderPlayingQueueBinding

    private var adapterPlayingQueue: AdapterPlayingQueue? = null
    private var gridLayoutManager: GridLayoutManager? = null
    private var hasScrolledToInitialPosition = false

    private val playingQueueViewModel: PlayingQueueViewModel by viewModels({ requireActivity() })

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentPlayingQueueBinding.inflate(inflater, container, false)
        headerBinding = HeaderPlayingQueueBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.recyclerView.requireAttachedMiniPlayer()
        binding.appHeader.setContentView(headerBinding.root)
        binding.appHeader.attachTo(binding.recyclerView, AppHeader.ScrollMode.HIDE_ON_SCROLL)
        binding.recyclerView.attachSlideFastScroller()

        // Use single-column list (queue always shows as list, no grid switching)
        gridLayoutManager = GridLayoutManager(requireContext(), 1)
        binding.recyclerView.layoutManager = gridLayoutManager

        setupHeaderClicks()
        observeQueue()
    }

    override fun onDestroyView() {
        adapterPlayingQueue = null
        gridLayoutManager = null
        hasScrolledToInitialPosition = false
        super.onDestroyView()
    }

    private fun setupHeaderClicks() {
        // Reserved for future header actions
    }

    private fun observeQueue() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                playingQueueViewModel.songs.collect { songs ->
                    if (songs.isNotEmpty()) {
                        updateQueueList(songs)
                    }
                }
            }
        }
    }

    private fun updateQueueList(songs: List<Audio>) {
        if (adapterPlayingQueue == null) {
            adapterPlayingQueue = AdapterPlayingQueue(songs)
            adapterPlayingQueue?.setHasStableIds(true)

            adapterPlayingQueue?.setGeneralAdapterCallbacks(object : GeneralAdapterCallbacks {
                override fun onSongClicked(songs: MutableList<Audio>, position: Int, view: View) {
                    // Explicit tap on a queue item always starts playback.
                    MediaManager.updatePosition(position, forcePlay = true)
                }

                override fun onSongLongClicked(audios: MutableList<Audio>, position: Int, imageView: ImageView?) {
                    openSongsMenu(audios, position, imageView)
                }
            })


            adapterPlayingQueue?.setOnItemSwipedCallback { position ->
                MediaManager.removeQueueItemSilently(position)
            }

            binding.recyclerView.adapter = adapterPlayingQueue
        } else {
            adapterPlayingQueue?.updateSongs(songs)
            if (binding.recyclerView.adapter == null) {
                binding.recyclerView.adapter = adapterPlayingQueue
            }
        }

        // Update header info
        headerBinding.count.text = getString(R.string.x_songs, songs.size)
        headerBinding.hours.text = songs.sumOf { it.duration }
            .toHighlightedTimeString(ThemeManager.theme.textViewTheme.tertiaryTextColor)


        // Check if current song is already visible; if not, scroll to it.
        // Only do this once on initial load — subsequent queue updates (drag reorder,
        // swipe-to-remove, song change) must NOT trigger a programmatic scroll because
        // that creates a feedback loop: queue change → scroll → onScrolled → show/hide
        // animations keep recycling, which floods the RV with continuous scroll calls.
        if (!hasScrolledToInitialPosition && binding.recyclerView.layoutManager is GridLayoutManager) {
            hasScrolledToInitialPosition = true
            val layoutManager = binding.recyclerView.layoutManager as GridLayoutManager
            val currentPosition = MediaManager.getCurrentPosition()
            binding.recyclerView.post {
                // Post so the layout has had a chance to measure before we read visible range
                val firstVisible = layoutManager.findFirstVisibleItemPosition()
                val lastVisible = layoutManager.findLastVisibleItemPosition()
                if (currentPosition !in firstVisible..lastVisible) {
                    layoutManager.scrollToPositionWithOffset(
                            /* position = */ currentPosition,
                            /* offset = */ binding.appHeader.height + resources.getDimensionPixelSize(R.dimen.padding_8))
                }
            }
        }
    }


    companion object {
        const val TAG = "PlayingQueue"

        fun newInstance(): PlayingQueue {
            val args = Bundle()
            val fragment = PlayingQueue()
            fragment.arguments = args
            return fragment
        }
    }
}