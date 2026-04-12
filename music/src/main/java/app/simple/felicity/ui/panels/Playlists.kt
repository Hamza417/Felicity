package app.simple.felicity.ui.panels

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.viewModels
import app.simple.felicity.R
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
 * Panel fragment that displays all user-created playlists with sort, list-style,
 * "New Playlist" creation support, and M3U import. Follows the same structural
 * pattern as [Songs] and [Favorites].
 *
 * @author Hamza417
 */
@AndroidEntryPoint
class Playlists : BasePanelFragment() {

    private lateinit var binding: FragmentPlaylistsBinding
    private lateinit var headerBinding: HeaderPlaylistsBinding

    private var adapterPlaylists: AdapterPlaylists? = null

    private val playlistsViewModel: PlaylistsViewModel by viewModels()

    /**
     * Launcher that opens the system file picker filtered to M3U and M3U8 files.
     * When the user picks a file (or backs out), the result lands here and kicks
     * off the import through the ViewModel.
     */
    private val m3uFilePicker = registerForActivityResult(
            ActivityResultContracts.OpenDocument()
    ) { uri ->
        // User pressed back without picking anything — nothing to do here.
        uri ?: return@registerForActivityResult
        playlistsViewModel.importM3u(uri)
    }

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

        // Watch for import results so we can let the user know how it went.
        observeImportState()
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

        // Open the file picker when the user taps "Import M3U". We accept both
        // the plain .m3u and the extended .m3u8 variants.
        headerBinding.importM3u.setOnClickListener {
            m3uFilePicker.launch(arrayOf("audio/x-mpegurl", "audio/mpegurl", "*/*"))
        }
    }

    /**
     * Collects import state events from the ViewModel and shows simple feedback
     * via a Toast. Nothing fancy — the playlist list will refresh on its own
     * through the reactive Room query once the import writes to the database.
     */
    private fun observeImportState() {
        playlistsViewModel.importResult.collectWhenStarted { state ->
            when (state) {
                is PlaylistsViewModel.ImportState.Loading -> {
                    // Could show a progress indicator here later. For now, silence.
                }
                is PlaylistsViewModel.ImportState.Success -> {
                    val msg = getString(
                            R.string.m3u_import_success,
                            state.result.playlistName,
                            state.result.totalTracks
                    )
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
                }
                is PlaylistsViewModel.ImportState.Error -> {
                    Toast.makeText(
                            requireContext(),
                            getString(R.string.m3u_import_failed),
                            Toast.LENGTH_LONG
                    ).show()
                }
            }
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
