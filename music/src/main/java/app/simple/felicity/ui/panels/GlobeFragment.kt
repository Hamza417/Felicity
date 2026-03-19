package app.simple.felicity.ui.panels

import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.simple.felicity.databinding.FragmentGlobeBinding
import app.simple.felicity.decorations.artflow.ArtFlowDataProvider
import app.simple.felicity.decorations.globe.Globe.OnAlbumTapListener
import app.simple.felicity.dialogs.songs.SongsMenu.Companion.showSongsMenu
import app.simple.felicity.extensions.fragments.MediaFragment
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.shared.utils.WindowUtil
import app.simple.felicity.viewmodels.panels.SongsViewModel
import kotlinx.coroutines.launch

/**
 * A full-screen fragment that hosts the [app.simple.felicity.decorations.globe.Globe] view,
 * displaying the user's song library as textured quads on an interactive 3-D sphere.
 *
 * Tapping an album art immediately starts playback of the corresponding song.
 * The user can freely rotate the sphere with a single-finger drag and zoom with a
 * two-finger pinch gesture.
 *
 * @author Hamza417
 */
class GlobeFragment : MediaFragment() {

    private lateinit var binding: FragmentGlobeBinding
    private val songsViewModel: SongsViewModel by viewModels({ requireActivity() })
    private val coverCache = ArtFlowCoverCache(maxMemoryCacheSizeMB = 50)

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View {
        binding = FragmentGlobeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireLightBarIcons()
        requireTransparentMiniPlayer()

        WindowUtil.getStatusBarHeightWhenAvailable(binding.topMenuContainer) { height ->
            binding.topMenuContainer.setPadding(
                    binding.topMenuContainer.paddingLeft,
                    height,
                    binding.topMenuContainer.paddingRight,
                    binding.topMenuContainer.paddingBottom
            )
        }

        binding.globe.setOnAlbumTapListener(object : OnAlbumTapListener {
            override fun onAlbumTapped(index: Int, itemId: Any?) {
                val songs = songsViewModel.songs.value
                if (index in songs.indices) {
                    setMediaItems(songs, index)
                }
            }
        })

        binding.menu.setOnClickListener {
            parentFragmentManager.showSongsMenu()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                songsViewModel.songs.collect { audioList ->
                    updateGlobe(audioList) // Limit to 96 items for performance and visual clarity
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        coverCache.release()
    }

    /** Wires the cover cache and data provider to the globe view. */
    private fun updateGlobe(audioList: List<Audio>) {
        coverCache.setAudioList(audioList)
        val provider = GlobeAlbumArtProvider(audioList)
        binding.globe.setDataProvider(provider)
        coverCache.preloadAround(0, radius = 20, maxDimension = 512)
    }

    override val wantsMiniPlayerVisible: Boolean
        get() = false

    override fun getTransitionType(): TransitionType {
        return TransitionType.SLIDE
    }

    /** [ArtFlowDataProvider] backed by the [ArtFlowCoverCache] LRU cache. */
    private inner class GlobeAlbumArtProvider(
            private val audioList: List<Audio>
    ) : ArtFlowDataProvider {
        override fun getItemCount(): Int = audioList.size

        override fun loadArtwork(index: Int, maxDimension: Int): Bitmap? {
            coverCache.getOrNull(index)?.let { return it }
            return coverCache.loadSync(index, maxDimension.coerceAtMost(512))
        }

        override fun getItemId(index: Int): Any? =
            if (index in audioList.indices) audioList[index] else null
    }

    companion object {
        /** Creates a new instance of [GlobeFragment] with no arguments. */
        fun newInstance(): GlobeFragment {
            return GlobeFragment().apply {
                arguments = Bundle()
            }
        }

        /** Back-stack tag used for fragment transactions. */
        const val TAG = "GlobeFragment"
    }
}

