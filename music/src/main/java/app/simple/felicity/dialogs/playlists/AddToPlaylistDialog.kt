package app.simple.felicity.dialogs.playlists

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import app.simple.felicity.adapters.ui.dialogs.AdapterPlaylistCheckbox
import app.simple.felicity.databinding.DialogAddToPlaylistBinding
import app.simple.felicity.dialogs.playlists.AddToPlaylistDialog.Companion.newInstance
import app.simple.felicity.dialogs.playlists.CreatePlaylistDialog.Companion.showCreatePlaylistDialog
import app.simple.felicity.extensions.dialogs.ScopedBottomSheetFragment
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.repository.models.Playlist
import app.simple.felicity.repository.repositories.PlaylistRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Bottom-sheet dialog that allows the user to add one or more [Audio] tracks to
 * existing playlists via a checkbox list, or create a brand-new playlist inline.
 *
 * <p>Usage: call [show] from any [FragmentManager] and pass the target audio via
 * [newInstance]. The dialog loads all playlists and pre-checks those that already
 * contain the song so users can clearly see and update membership.</p>
 *
 * @author Hamza417
 */
@AndroidEntryPoint
class AddToPlaylistDialog : ScopedBottomSheetFragment() {

    private lateinit var binding: DialogAddToPlaylistBinding
    private var adapter: AdapterPlaylistCheckbox? = null

    @Inject
    lateinit var playlistRepository: PlaylistRepository

    /** The audio track to add to the selected playlists. */
    private val audio: Audio? by lazy {
        @Suppress("DEPRECATION")
        arguments?.getParcelable(ARG_AUDIO)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = DialogAddToPlaylistBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())

        loadPlaylists()

        binding.createPlaylist.setOnClickListener {
            showCreatePlaylistDialog()
        }

        binding.cancel.setOnClickListener {
            dismiss()
        }

        binding.save.setOnClickListener {
            saveMembership()
        }
    }

    /**
     * Loads all playlists from the repository and determines which ones already contain
     * the target audio. Populates the RecyclerView with a pre-checked checkbox state.
     */
    private fun loadPlaylists() {
        val audioHash = audio?.hash ?: return

        viewLifecycleOwner.lifecycleScope.launch {
            val playlists: List<Playlist>
            val preCheckedIds: Set<Long>

            withContext(Dispatchers.IO) {
                playlists = playlistRepository.getAllPlaylists().first()
                preCheckedIds = playlists
                    .filter { playlistRepository.isSongInPlaylist(it.id, audioHash) }
                    .map { it.id }
                    .toSet()
            }

            adapter = AdapterPlaylistCheckbox(playlists, preCheckedIds)
            binding.recyclerView.adapter = adapter
        }
    }

    /**
     * Persists the updated playlist membership: adds the song to newly checked playlists
     * and removes it from playlists that were unchecked.
     */
    private fun saveMembership() {
        val audioHash = audio?.hash ?: return
        val checkedIds = adapter?.getCheckedIds() ?: emptySet()

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val allPlaylists = playlistRepository.getAllPlaylists().first()

            allPlaylists.forEach { playlist ->
                val wasIn = playlistRepository.isSongInPlaylist(playlist.id, audioHash)
                val isNowIn = checkedIds.contains(playlist.id)

                when {
                    !wasIn && isNowIn -> playlistRepository.addSong(playlist.id, audioHash)
                    wasIn && !isNowIn -> playlistRepository.removeSong(playlist.id, audioHash)
                }
            }

            withContext(Dispatchers.Main) { dismiss() }
        }
    }

    /**
     * Shows the "Create Playlist" bottom-sheet via [parentFragmentManager] so it sits on top
     * of this dialog without being dismissed when this sheet is hidden. Reloads the playlist
     * list once the new playlist is successfully created.
     */
    private fun showCreatePlaylistDialog() {
        parentFragmentManager.showCreatePlaylistDialog(
                listener = object : CreatePlaylistDialog.OnPlaylistCreatedListener {
                    override fun onPlaylistCreated(name: String) {
                        loadPlaylists()
                    }
                }
        )
    }

    companion object {
        private const val TAG = "AddToPlaylistDialog"
        private const val ARG_AUDIO = "audio"

        fun newInstance(audio: Audio): AddToPlaylistDialog {
            return AddToPlaylistDialog().apply {
                arguments = Bundle().apply {
                    putParcelable(ARG_AUDIO, audio)
                }
            }
        }

        /**
         * Shows the "Add to Playlist" dialog for the given [audio] track.
         *
         * @param audio The audio track to add to playlists.
         */
        fun FragmentManager.showAddToPlaylistDialog(audio: Audio): AddToPlaylistDialog {
            val dialog = newInstance(audio)
            dialog.show(this, TAG)
            return dialog
        }
    }
}
