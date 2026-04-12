package app.simple.felicity.ui.panels

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import app.simple.felicity.adapters.ui.lists.AdapterPlaylists
import app.simple.felicity.databinding.FragmentPlaylistsBinding
import app.simple.felicity.databinding.HeaderPlaylistsBinding
import app.simple.felicity.decorations.views.AppHeader
import app.simple.felicity.dialogs.playlists.CreatePlaylistDialog.Companion.showCreatePlaylistDialog
import app.simple.felicity.dialogs.playlists.PlaylistsMenu.Companion.showPlaylistsMenu
import app.simple.felicity.dialogs.playlists.PlaylistsSort.Companion.showPlaylistsSort
import app.simple.felicity.extensions.fragments.BasePanelFragment
import app.simple.felicity.preferences.PlaylistPreferences
import app.simple.felicity.repository.models.PlaylistWithSongs
import app.simple.felicity.repository.sort.PlaylistSort.setPlaylistOrder
import app.simple.felicity.repository.sort.PlaylistSort.setPlaylistSort
import app.simple.felicity.ui.pages.PlaylistPage
import app.simple.felicity.viewmodels.panels.PlaylistsViewModel
import dagger.hilt.android.AndroidEntryPoint

/**
 * Panel fragment that displays all playlists — both manually created ones and those
 * automatically imported from M3U files found on the device. Follows the same structural
 * pattern as [Songs] and [Favorites].
 *
 * <p>M3U playlists are now automatically maintained by the library scanner, so there's
 * no manual "Import M3U" button here anymore. The scanner does all the heavy lifting
 * in the background after each audio scan completes.</p>
 *
 * @author Hamza417
 */
@AndroidEntryPoint
class Playlists : BasePanelFragment() {

    private lateinit var binding: FragmentPlaylistsBinding
    private lateinit var headerBinding: HeaderPlaylistsBinding

    private var adapterPlaylists: AdapterPlaylists? = null

    private val playlistsViewModel: PlaylistsViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentPlaylistsBinding.inflate(inflater, container, false)
        headerBinding = HeaderPlaylistsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.recyclerView.requireAttachedMiniPlayer()
        binding.appHeader.setContentView(headerBinding.root)
        binding.appHeader.attachTo(binding.recyclerView, AppHeader.ScrollMode.HIDE_ON_SCROLL)

        binding.recyclerView.setupGridLayoutManager(PlaylistPreferences.getGridSize().spanCount)

        setupClickListeners()

        playlistsViewModel.playlists.collectListWhenStarted({ adapterPlaylists != null }) { playlists ->
            updateList(playlists)
        }
    }

    override fun onDestroyView() {
        adapterPlaylists = null
        super.onDestroyView()
    }

    private fun setupClickListeners() {
        headerBinding.sortStyle.setPlaylistSort()
        headerBinding.sortOrder.setPlaylistOrder()

        headerBinding.menu.setOnClickListener {
            childFragmentManager.showPlaylistsMenu()
        }

        headerBinding.sortStyle.setOnClickListener {
            childFragmentManager.showPlaylistsSort()
        }

        headerBinding.sortOrder.setOnClickListener {
            childFragmentManager.showPlaylistsSort()
        }

        headerBinding.newPlaylist.setOnClickListener {
            childFragmentManager.showCreatePlaylistDialog()
        }
    }

    /**
     * Initializes or updates the playlist adapter and refreshes the header count chip.
     *
     * @param playlists The latest sorted list of [PlaylistWithSongs] objects.
     */
    private fun updateList(playlists: List<PlaylistWithSongs>) {
        if (adapterPlaylists == null) {
            adapterPlaylists = AdapterPlaylists(playlists)
            adapterPlaylists?.setOnPlaylistClicked { playlist ->
                openFragment(PlaylistPage.newInstance(playlist), PlaylistPage.TAG)
            }
            adapterPlaylists?.setOnPlaylistLongClicked { item, imageView ->
                openPlaylistMenu(item, imageView)
            }
            binding.recyclerView.adapter = adapterPlaylists
        } else {
            adapterPlaylists?.updatePlaylists(playlists)
            if (binding.recyclerView.adapter == null) {
                binding.recyclerView.adapter = adapterPlaylists
            }
        }

        headerBinding.count.text = playlists.size.toString()
        headerBinding.sortStyle.setPlaylistSort()
        headerBinding.sortOrder.setPlaylistOrder()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        super.onSharedPreferenceChanged(sharedPreferences, key)
        when (key) {
            PlaylistPreferences.SONG_SORT,
            PlaylistPreferences.SORTING_STYLE -> {
                headerBinding.sortStyle.setPlaylistSort()
                headerBinding.sortOrder.setPlaylistOrder()
            }
            PlaylistPreferences.GRID_SIZE_PORTRAIT, PlaylistPreferences.GRID_SIZE_LANDSCAPE -> {
                val newMode = PlaylistPreferences.getGridSize()
                adapterPlaylists?.layoutMode = newMode
                applyGridSizeUpdate(binding.recyclerView, newMode.spanCount)
            }
        }
    }

    companion object {
        const val TAG = "Playlists"

        fun newInstance(): Playlists {
            return Playlists().apply {
                arguments = Bundle()
            }
        }
    }
}
