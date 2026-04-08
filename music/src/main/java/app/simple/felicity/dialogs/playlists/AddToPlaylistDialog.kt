package app.simple.felicity.dialogs.playlists

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import app.simple.felicity.adapters.ui.dialogs.AdapterPlaylistCheckbox
import app.simple.felicity.databinding.DialogAddToPlaylistBinding
import app.simple.felicity.dialogs.playlists.AddToPlaylistDialog.Companion.newInstance
import app.simple.felicity.dialogs.playlists.CreatePlaylistDialog.Companion.showCreatePlaylistDialog
import app.simple.felicity.extensions.dialogs.ScopedBottomSheetFragment
import app.simple.felicity.repository.constants.BundleConstants
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.utils.ParcelUtils.parcelable
import app.simple.felicity.viewmodels.dialogs.AddToPlaylistViewModel
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.withCreationCallback
import kotlinx.coroutines.launch

/**
 * Bottom-sheet dialog that allows the user to add one or more [Audio] tracks to
 * existing playlists via a checkbox list, or create a brand-new playlist inline.
 *
 * <p>Usage: call [show] from any [FragmentManager] and pass the target audio via
 * [newInstance]. The dialog observes [AddToPlaylistViewModel.state] to keep the checkbox
 * list reactive — playlist additions and song-count changes surface automatically without
 * manual reload calls. Pre-checked state reflects actual database membership so the user
 * can clearly see which playlists already contain the track.</p>
 *
 * @author Hamza417
 */
@AndroidEntryPoint
class AddToPlaylistDialog : ScopedBottomSheetFragment() {

    private lateinit var binding: DialogAddToPlaylistBinding
    private var adapter: AdapterPlaylistCheckbox? = null

    /** Whether the adapter has been created from the first ViewModel state emission. */
    private var adapterInitialized = false

    private val audio: Audio by lazy {
        requireArguments().parcelable<Audio>(BundleConstants.AUDIO)
            ?: error("AddToPlaylistDialog requires an Audio argument")
    }

    private val viewModel: AddToPlaylistViewModel by viewModels(
            extrasProducer = {
                defaultViewModelCreationExtras.withCreationCallback<AddToPlaylistViewModel.Factory> {
                    it.create(audio = audio)
                }
            }
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = DialogAddToPlaylistBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())

        binding.createPlaylist.setOnClickListener {
            showCreatePlaylistDialog()
        }

        binding.cancel.setOnClickListener {
            dismiss()
        }

        binding.save.setOnClickListener {
            viewModel.saveMembership(adapter?.getCheckedIds() ?: emptySet())
        }

        observeViewModel()
    }

    /**
     * Subscribes to [AddToPlaylistViewModel.state] and [AddToPlaylistViewModel.saveComplete].
     * The first non-null state emission initializes the adapter; subsequent emissions call
     * [AdapterPlaylistCheckbox.updateData] so the user's checkbox selections are preserved
     * across playlist-list or song-count changes.
     */
    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    state ?: return@collect
                    if (!adapterInitialized) {
                        adapterInitialized = true
                        adapter = AdapterPlaylistCheckbox(
                                state.playlists,
                                state.preCheckedIds,
                                state.songCounts
                        )
                        binding.recyclerView.adapter = adapter
                    } else {
                        adapter?.updateData(state.playlists, state.songCounts)
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.saveComplete.collect { dismiss() }
            }
        }
    }

    /**
     * Shows the "Create Playlist" bottom-sheet via [parentFragmentManager] so it sits on top
     * of this dialog. Because [AddToPlaylistViewModel] observes [PlaylistRepository.getAllPlaylistsWithSongs]
     * reactively, the new playlist appears in the list automatically — no manual reload is needed.
     */
    private fun showCreatePlaylistDialog() {
        parentFragmentManager.showCreatePlaylistDialog()
    }

    companion object {
        private const val TAG = "AddToPlaylistDialog"

        fun newInstance(audio: Audio): AddToPlaylistDialog {
            return AddToPlaylistDialog().apply {
                arguments = Bundle().apply {
                    putParcelable(BundleConstants.AUDIO, audio)
                }
            }
        }

        /**
         * Shows the "Add to Playlist" dialog for the given [audio] track.
         *
         * @param audio The audio track to add to playlists.
         * @return The shown [AddToPlaylistDialog] instance.
         */
        fun FragmentManager.showAddToPlaylistDialog(audio: Audio): AddToPlaylistDialog {
            val dialog = newInstance(audio)
            dialog.show(this, TAG)
            return dialog
        }
    }
}
