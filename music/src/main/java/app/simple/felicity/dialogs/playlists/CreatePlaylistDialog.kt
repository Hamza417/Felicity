package app.simple.felicity.dialogs.playlists

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import app.simple.felicity.databinding.DialogCreatePlaylistBinding
import app.simple.felicity.extensions.dialogs.ScopedBottomSheetFragment
import app.simple.felicity.repository.repositories.PlaylistRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Bottom-sheet dialog that lets the user create a new named playlist.
 *
 * <p>Presents a single text field for the playlist name and Cancel/Create action
 * buttons. On confirmation the playlist is created via [PlaylistRepository] on the
 * IO dispatcher, and the optional [OnPlaylistCreatedListener] is notified on the
 * main thread before the sheet is dismissed.</p>
 *
 * <p>Usage:
 * ```
 * childFragmentManager.showCreatePlaylistDialog { name ->
 *     // react to the newly created playlist
 * }
 * ```
 * </p>
 *
 * @author Hamza417
 */
@AndroidEntryPoint
class CreatePlaylistDialog : ScopedBottomSheetFragment() {

    private lateinit var binding: DialogCreatePlaylistBinding

    @Inject
    lateinit var playlistRepository: PlaylistRepository

    private var onPlaylistCreatedListener: OnPlaylistCreatedListener? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = DialogCreatePlaylistBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.playlistNameInput.requestFocus()

        binding.cancel.setOnClickListener {
            dismiss()
        }

        binding.create.setOnClickListener {
            val name = binding.playlistNameInput.text?.toString()?.trim()
            if (name.isNullOrEmpty()) return@setOnClickListener

            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                playlistRepository.createPlaylist(name)
                withContext(Dispatchers.Main) {
                    onPlaylistCreatedListener?.onPlaylistCreated(name)
                    dismiss()
                }
            }
        }
    }

    /**
     * Registers a callback that is invoked after a new playlist is successfully created.
     *
     * @param listener The listener to receive the creation event.
     */
    fun setOnPlaylistCreatedListener(listener: OnPlaylistCreatedListener) {
        onPlaylistCreatedListener = listener
    }

    /**
     * Listener interface for the create-playlist confirmation event.
     */
    interface OnPlaylistCreatedListener {
        /**
         * Called when the new playlist has been persisted.
         *
         * @param name The name of the newly created playlist.
         */
        fun onPlaylistCreated(name: String)
    }

    companion object {
        private const val TAG = "CreatePlaylistDialog"

        fun newInstance(): CreatePlaylistDialog {
            return CreatePlaylistDialog().apply { arguments = Bundle() }
        }

        /**
         * Shows the "Create Playlist" bottom-sheet dialog using the given [FragmentManager].
         *
         * @param listener Optional callback invoked once the playlist is created.
         * @return The shown [CreatePlaylistDialog] instance.
         */
        fun FragmentManager.showCreatePlaylistDialog(
                listener: OnPlaylistCreatedListener? = null
        ): CreatePlaylistDialog {
            val dialog = newInstance()
            listener?.let { dialog.setOnPlaylistCreatedListener(it) }
            dialog.show(this, TAG)
            return dialog
        }
    }
}

