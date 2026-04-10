package app.simple.felicity.ui.panels

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.GridLayoutManager
import app.simple.felicity.R
import app.simple.felicity.adapters.ui.lists.AdapterPlayingQueue
import app.simple.felicity.callbacks.GeneralAdapterCallbacks
import app.simple.felicity.databinding.FragmentPlayingQueueBinding
import app.simple.felicity.databinding.HeaderPlayingQueueBinding
import app.simple.felicity.decorations.views.AppHeader
import app.simple.felicity.dialogs.app.TotalTime.Companion.showTotalTime
import app.simple.felicity.engine.managers.MediaPlaybackManager
import app.simple.felicity.extensions.fragments.BasePanelFragment
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.shared.utils.TimeUtils.toDynamicTimeString
import app.simple.felicity.viewmodels.panels.PlayingQueueViewModel

class PlayingQueue : BasePanelFragment() {

    private lateinit var binding: FragmentPlayingQueueBinding
    private lateinit var headerBinding: HeaderPlayingQueueBinding

    private var adapterPlayingQueue: AdapterPlayingQueue? = null
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

        /** Queue always shows as a single-column list; no grid switching. */
        binding.recyclerView.setupGridLayoutManager(1)

        setupHeaderClicks()

        playingQueueViewModel.songs.collectWhenStarted { songs ->
            if (songs.isNotEmpty()) {
                updateQueueList(songs)
            }
        }
    }

    override fun onDestroyView() {
        adapterPlayingQueue = null
        hasScrolledToInitialPosition = false
        super.onDestroyView()
    }

    private fun setupHeaderClicks() {
        // Reserved for future header actions
    }

    private fun updateQueueList(songs: List<Audio>) {
        if (adapterPlayingQueue == null) {
            adapterPlayingQueue = AdapterPlayingQueue(songs)
            adapterPlayingQueue?.setHasStableIds(true)

            adapterPlayingQueue?.setGeneralAdapterCallbacks(object : GeneralAdapterCallbacks {
                override fun onSongClicked(songs: MutableList<Audio>, position: Int, view: View) {
                    MediaPlaybackManager.updatePosition(position, forcePlay = true)
                }

                override fun onSongLongClicked(audios: MutableList<Audio>, position: Int, imageView: ImageView?) {
                    openSongsMenu(audios, position, imageView)
                }
            })

            adapterPlayingQueue?.setOnItemSwipedCallback { position ->
                MediaPlaybackManager.removeQueueItemSilently(position)
            }

            binding.recyclerView.adapter = adapterPlayingQueue
        } else {
            adapterPlayingQueue?.updateSongs(songs)
            if (binding.recyclerView.adapter == null) {
                binding.recyclerView.adapter = adapterPlayingQueue
            }
        }

        headerBinding.count.text = getString(R.string.x_songs, songs.size)
        headerBinding.hours.text = songs.sumOf { it.duration }.toDynamicTimeString()

        headerBinding.hours.setOnClickListener {
            childFragmentManager.showTotalTime(
                    totalTime = songs.sumOf { it.duration },
                    count = songs.size
            )
        }

        /**
         * Scroll to the currently playing song only on the initial load. Subsequent queue
         * changes (drag reorder, swipe-to-remove, song change) must NOT trigger a scroll
         * to avoid a feedback loop that floods the RecyclerView with continuous scroll events.
         */
        if (!hasScrolledToInitialPosition && binding.recyclerView.layoutManager is GridLayoutManager) {
            hasScrolledToInitialPosition = true
            val layoutManager = binding.recyclerView.layoutManager as GridLayoutManager
            val currentPosition = MediaPlaybackManager.getCurrentSongPosition()
            binding.recyclerView.post {
                val firstVisible = layoutManager.findFirstVisibleItemPosition()
                val lastVisible = layoutManager.findLastVisibleItemPosition()
                if (currentPosition !in firstVisible..lastVisible) {
                    layoutManager.scrollToPositionWithOffset(
                            currentPosition,
                            binding.appHeader.height + resources.getDimensionPixelSize(R.dimen.padding_8))
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