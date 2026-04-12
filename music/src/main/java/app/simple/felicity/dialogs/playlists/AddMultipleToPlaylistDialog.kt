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
import app.simple.felicity.dialogs.playlists.AddMultipleToPlaylistDialog.Companion.newInstance
import app.simple.felicity.dialogs.playlists.AddMultipleToPlaylistDialog.Companion.showAddMultipleToPlaylistDialog
import app.simple.felicity.dialogs.playlists.CreatePlaylistDialog.Companion.showCreatePlaylistDialog
import app.simple.felicity.extensions.dialogs.ScopedBottomSheetFragment
import app.simple.felicity.repository.constants.BundleConstants
import app.simple.felicity.repository.managers.SelectionManager
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.utils.ParcelUtils.parcelableArrayList
import app.simple.felicity.viewmodels.dialogs.AddMultipleToPlaylistViewModel
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.withCreationCallback
import kotlinx.coroutines.launch

/**
 * Bottom-sheet dialog for adding a batch of selected songs to one or more playlists.
 *
 * This is the "add many at once" sibling of [AddToPlaylistDialog]. It shows the full
 * list of playlists without any pre-checked state (since we're doing a bulk add, not
 * editing existing membership). The user checks all the playlists they want, taps Save,
 * and every selected song gets appended to each checked playlist in one go.
 *
 * On a successful save the [SelectionManager] selection is also cleared automatically,
 * since the user is done with their batch operation.
 *
 * Usage: call [showAddMultipleToPlaylistDialog] from any [FragmentManager] and pass the
 * batch of audio tracks via [newInstance].
 *
 * @author Hamza417
 */
@AndroidEntryPoint
class AddMultipleToPlaylistDialog : ScopedBottomSheetFragment() {

    private lateinit var binding: DialogAddToPlaylistBinding
    private var adapter: AdapterPlaylistCheckbox? = null

    /** Whether the adapter has been created from the first ViewModel state emission. */
    private var adapterInitialized = false

    private val audios: ArrayList<Audio> by lazy {
        requireArguments().parcelableArrayList<Audio>(BundleConstants.SONGS)
            ?: error("AddMultipleToPlaylistDialog requires a songs argument")
    }

    private val viewModel: AddMultipleToPlaylistViewModel by viewModels(
            extrasProducer = {
                defaultViewModelCreationExtras.withCreationCallback<AddMultipleToPlaylistViewModel.Factory> {
                    it.create(audios = audios)
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
            // Hand off the checked playlist IDs to the ViewModel, which takes it from here.
            viewModel.saveToPlaylists(adapter?.getCheckedIds() ?: emptySet())
        }

        observeViewModel()
    }

    /**
     * Subscribes to [AddMultipleToPlaylistViewModel.state] so the playlist list
     * stays fresh even if a new playlist is created mid-session via the "Create" button.
     * On [AddMultipleToPlaylistViewModel.saveComplete], the selection basket is cleared
     * and the dialog is dismissed.
     */
    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    state ?: return@collect
                    if (!adapterInitialized) {
                        adapterInitialized = true
                        // No playlists are pre-checked — we're doing a fresh bulk add, not editing membership.
                        adapter = AdapterPlaylistCheckbox(
                                state.playlists,
                                emptySet(),
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
                viewModel.saveComplete.collect {
                    // Clean up the selection basket now that the songs are safely in their playlists.
                    SelectionManager.clear()
                    dismiss()
                }
            }
        }
    }

    /**
     * Opens the "Create Playlist" bottom-sheet on top of this dialog. Because
     * [AddMultipleToPlaylistViewModel] observes the playlists table reactively, any newly
     * created playlist appears in the list automatically — no manual reload needed.
     */
    private fun showCreatePlaylistDialog() {
        parentFragmentManager.showCreatePlaylistDialog()
    }

    companion object {
        private const val TAG = "AddMultipleToPlaylistDialog"

        fun newInstance(audios: List<Audio>): AddMultipleToPlaylistDialog {
            return AddMultipleToPlaylistDialog().apply {
                arguments = Bundle().apply {
                    putParcelableArrayList(BundleConstants.SONGS, ArrayList(audios))
                }
            }
        }

        /**
         * Shows the "Add to Playlist" dialog for a batch of [audios] tracks.
         *
         * @param audios The audio tracks to add to one or more playlists.
         * @return The shown [AddMultipleToPlaylistDialog] instance.
         */
        fun FragmentManager.showAddMultipleToPlaylistDialog(audios: List<Audio>): AddMultipleToPlaylistDialog {
            val dialog = newInstance(audios)
            dialog.show(this, TAG)
            return dialog
        }
    }
}

