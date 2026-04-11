package app.simple.felicity.ui.panels

import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import app.simple.felicity.R
import app.simple.felicity.adapters.ui.lists.AdapterLrcEditor
import app.simple.felicity.databinding.FragmentLrcEditorBinding
import app.simple.felicity.decorations.seekbars.FelicitySeekbar
import app.simple.felicity.extensions.fragments.MediaFragment
import app.simple.felicity.repository.constants.BundleConstants
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.repository.utils.AudioUtils.getArtists
import app.simple.felicity.ui.panels.LrcEditor.Companion.SEEK_JUMP_MS
import app.simple.felicity.utils.ParcelUtils.parcelable
import app.simple.felicity.viewmodels.player.LrcEditorViewModel
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.withCreationCallback

/**
 * Full-screen panel that lets the user create and edit LRC lyric files for a specific
 * audio track using a local [androidx.media3.exoplayer.ExoPlayer] instance.
 *
 * When this fragment is visible the global [app.simple.felicity.engine.managers.MediaPlaybackManager]
 * service is paused, and all playback controls (rewind, play/pause, forward) operate only
 * on the local player so that the user can listen while stamping timestamps. The global
 * service remains paused when the user navigates back, as the user may want to continue
 * with manual control rather than an automatic resume.
 *
 * The editor shows a scrollable list of lyric entries. Each entry has:
 *  - A clock icon button to stamp the current seek position as that line's timestamp.
 *  - An editable timestamp field in `MM:SS.mmm` format.
 *  - An editable lyric text field.
 *  - A delete button.
 *
 * Additional controls in the bottom toolbar:
 *  - **Paste** — reads the clipboard, splits by newlines, and appends one entry per line
 *    with an empty (0 ms) timestamp.
 *  - **Rewind / Forward** — seek the local player by [SEEK_JUMP_MS].
 *  - **Play/Pause** — toggle local playback.
 *  - **Save** — write the edited entries back to the `.lrc` sidecar file.
 *
 * @author Hamza417
 */
@AndroidEntryPoint
class LrcEditor : MediaFragment() {

    private lateinit var binding: FragmentLrcEditorBinding

    private val audio: Audio by lazy {
        requireArguments().parcelable<Audio>(BundleConstants.AUDIO)!!
    }

    private val viewModel: LrcEditorViewModel by viewModels(
            extrasProducer = {
                defaultViewModelCreationExtras.withCreationCallback<LrcEditorViewModel.Factory> {
                    it.create(audio)
                }
            }
    )

    private var adapter: AdapterLrcEditor? = null

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View {
        binding = FragmentLrcEditorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireHiddenMiniPlayer()

        binding.name.text = audio.title
        binding.artist.text = audio.getArtists()

        setupRecyclerView()
        setupSeekbar()
        setupControls()
        observeViewModel()
    }

    private fun setupRecyclerView() {
        binding.entriesRecycler.layoutManager = LinearLayoutManager(requireContext())
    }

    private fun setupSeekbar() {
        val durationMs = audio.duration
        binding.seekbar.setMax(durationMs.toFloat())
        binding.seekbar.setMin(0f)
        binding.seekbar.setDefaultIndicatorEnabled(false)

        binding.seekbar.setLeftLabelProvider { progress, _, _ ->
            DateUtils.formatElapsedTime(progress.toLong().div(1000))
        }

        binding.seekbar.setRightLabelProvider { _, _, max ->
            DateUtils.formatElapsedTime(max.toLong().div(1000))
        }

        binding.seekbar.setOnSeekChangeListener(object : FelicitySeekbar.OnSeekChangeListener {
            override fun onProgressChanged(
                    seekbar: FelicitySeekbar,
                    progress: Float,
                    fromUser: Boolean
            ) {
                if (fromUser) {
                    viewModel.seekTo(progress.toLong())
                }
            }

            override fun onStopTrackingTouch(seekbar: FelicitySeekbar) {
                viewModel.seekTo(seekbar.getProgress().toLong())
            }
        })
    }

    private fun setupControls() {
        binding.play.setOnClickListener {
            viewModel.flipPlayback()
        }

        binding.rewind.setOnClickListener {
            viewModel.seekRelative(-SEEK_JUMP_MS)
        }

        binding.forward.setOnClickListener {
            viewModel.seekRelative(SEEK_JUMP_MS)
        }

        binding.rewind.setOnLongClickListener {
            viewModel.seekTo(0L)
            true
        }

        binding.paste.setOnClickListener {
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clip = clipboard?.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val text = clip.getItemAt(0).coerceToText(requireContext()).toString()
                if (text.isNotBlank()) {
                    viewModel.pasteLines(text)
                } else {
                    Toast.makeText(requireContext(), R.string.lrc_clipboard_empty, Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(requireContext(), R.string.lrc_clipboard_empty, Toast.LENGTH_SHORT).show()
            }
        }

        binding.save.setOnClickListener {
            viewModel.saveLrc()
        }
    }

    private fun observeViewModel() {
        viewModel.getEntries().observe(viewLifecycleOwner) { entries ->
            if (adapter == null) {
                adapter = AdapterLrcEditor(
                        entries = entries,
                        onStamp = { index -> viewModel.stampTimestamp(index) },
                        onDelete = { index ->
                            viewModel.removeEntry(index)
                        }
                )
                binding.entriesRecycler.adapter = adapter
            } else {
                adapter?.submitList(entries)
            }
        }

        viewModel.getSeekPosition().observe(viewLifecycleOwner) { positionMs ->
            binding.seekbar.setProgress(positionMs.toFloat(), fromUser = false, animate = false)
        }

        viewModel.getDuration().observe(viewLifecycleOwner) { durationMs ->
            binding.seekbar.setMax(durationMs.toFloat())
        }

        viewModel.isPlayingLiveData().observe(viewLifecycleOwner) { isPlaying ->
            if (isPlaying) binding.play.playing() else binding.play.paused()
        }

        viewModel.getSaved().observe(viewLifecycleOwner) { saved ->
            if (saved == true) {
                Toast.makeText(requireContext(), R.string.lyrics_saved, Toast.LENGTH_SHORT).show()
                // Signal the Lyrics panel to reload if it is in the back stack.
                parentFragmentManager.setFragmentResult(REQUEST_KEY_LRC_SAVED, Bundle())
                goBack()
            }
        }
    }

    // The global service controls are intentionally suppressed below because this editor
    // manages its own local player. Overriding these no-ops prevents the base class from
    // reacting to global playback state changes while the editor is active.

    override fun onAudio(audio: Audio) = Unit

    override fun onSeekChanged(seek: Long) = Unit

    override fun onPlaybackStateChanged(state: Int) = Unit

    override val wantsMiniPlayerVisible: Boolean
        get() = false

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        super.onSharedPreferenceChanged(sharedPreferences, key)
    }

    override fun onDestroyView() {
        adapter = null
        super.onDestroyView()
    }

    companion object {
        const val TAG = "LrcEditor"

        /**
         * Fragment Result API key emitted when the user saves the LRC file.
         * Observers (e.g., [Lyrics]) can listen to this key and reload their lyrics view.
         */
        const val REQUEST_KEY_LRC_SAVED = "lrc_editor_lrc_saved"

        /** Milliseconds to seek on a single tap of the rewind or forward button. */
        private const val SEEK_JUMP_MS = 2000L

        /**
         * Creates a new [LrcEditor] for the given [audio] track.
         *
         * @param audio The track whose LRC file will be created or edited.
         * @return a configured [LrcEditor] instance.
         */
        fun newInstance(audio: Audio): LrcEditor {
            return LrcEditor().also { fragment ->
                fragment.arguments = Bundle().apply {
                    putParcelable(BundleConstants.AUDIO, audio)
                }
            }
        }
    }
}

