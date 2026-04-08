package app.simple.felicity.ui.panels

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import app.simple.felicity.adapters.ui.lists.AdapterPlaylists
import app.simple.felicity.databinding.DialogCreatePlaylistBinding
import app.simple.felicity.databinding.FragmentPlaylistsBinding
import app.simple.felicity.databinding.HeaderPlaylistsBinding
import app.simple.felicity.decorations.popups.SimpleDialog
import app.simple.felicity.decorations.views.AppHeader
import app.simple.felicity.dialogs.playlists.PlaylistsSort.Companion.showPlaylistsSort
import app.simple.felicity.extensions.fragments.PanelFragment
import app.simple.felicity.preferences.PlaylistPreferences
import app.simple.felicity.repository.models.Playlist
import app.simple.felicity.repository.repositories.PlaylistRepository
import app.simple.felicity.repository.sort.PlaylistSort.setPlaylistOrder
import app.simple.felicity.repository.sort.PlaylistSort.setPlaylistSort
import app.simple.felicity.viewmodels.panels.PlaylistsViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Panel fragment that displays all user-created playlists with sort, search, and
 * "New Playlist" creation support. Follows the same structural pattern as
 * [Songs] and [Favorites].
 *
 * @author Hamza417
 */
@AndroidEntryPoint
class Playlists : PanelFragment() {

    private lateinit var binding: FragmentPlaylistsBinding
    private lateinit var headerBinding: HeaderPlaylistsBinding

    private var adapterPlaylists: AdapterPlaylists? = null

    @Inject
    lateinit var playlistRepository: PlaylistRepository

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
        binding.recyclerView.layoutManager = GridLayoutManager(requireContext(), 1)

        setupClickListeners()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                playlistsViewModel.playlists.collect { playlists ->
                    if (playlists.isNotEmpty()) {
                        updateList(playlists)
                    } else if (adapterPlaylists != null) {
                        updateList(playlists)
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        adapterPlaylists = null
        super.onDestroyView()
    }

    private fun setupClickListeners() {
        headerBinding.sortStyle.setPlaylistSort()
        headerBinding.sortOrder.setPlaylistOrder()

        headerBinding.sortStyle.setOnClickListener {
            childFragmentManager.showPlaylistsSort()
        }

        headerBinding.sortOrder.setOnClickListener {
            childFragmentManager.showPlaylistsSort()
        }

        headerBinding.newPlaylist.setOnClickListener {
            showCreatePlaylistDialog()
        }
    }

    /**
     * Initializes or updates the playlist adapter and refreshes the header count chip.
     *
     * @param playlists The latest sorted list of [Playlist] objects.
     */
    private fun updateList(playlists: List<Playlist>) {
        if (adapterPlaylists == null) {
            adapterPlaylists = AdapterPlaylists(playlists)
            adapterPlaylists?.setOnPlaylistClicked { playlist ->
                // TODO: open playlist detail panel
            }
            adapterPlaylists?.setOnPlaylistLongClicked { playlist ->
                // TODO: show playlist context menu
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

    /**
     * Shows the inline "New Playlist" creation dialog with a name input.
     */
    private fun showCreatePlaylistDialog() {
        SimpleDialog.Builder(
                container = requireContainerView(),
                inflateBinding = DialogCreatePlaylistBinding::inflate)
            .onViewCreated { dialogBinding ->
                dialogBinding.playlistNameInput.requestFocus()
            }
            .onDialogInflated { dialogBinding, dismiss ->
                dialogBinding.cancel.setOnClickListener { dismiss() }

                dialogBinding.create.setOnClickListener {
                    val name = dialogBinding.playlistNameInput.text?.toString()?.trim()
                    if (name.isNullOrEmpty()) return@setOnClickListener
                    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                        playlistRepository.createPlaylist(name)
                    }
                    dismiss()
                }
            }
            .build()
            .show()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        super.onSharedPreferenceChanged(sharedPreferences, key)
        when (key) {
            PlaylistPreferences.SONG_SORT,
            PlaylistPreferences.SORTING_STYLE -> {
                headerBinding.sortStyle.setPlaylistSort()
                headerBinding.sortOrder.setPlaylistOrder()
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

