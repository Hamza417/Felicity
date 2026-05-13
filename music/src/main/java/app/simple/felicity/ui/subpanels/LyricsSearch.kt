package app.simple.felicity.ui.subpanels

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.GridLayoutManager
import app.simple.felicity.R
import app.simple.felicity.adapters.ui.lists.AdapterLrcSearch
import app.simple.felicity.databinding.FragmentLyricsSearchBinding
import app.simple.felicity.databinding.HeaderGenericSearchBinding
import app.simple.felicity.decorations.views.AppHeader
import app.simple.felicity.extensions.fragments.MediaFragment
import app.simple.felicity.repository.constants.BundleConstants
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.ui.subpanels.LyricsSearch.Companion.REQUEST_KEY_LYRICS_FOR_EDITOR
import app.simple.felicity.utils.ParcelUtils.parcelable
import app.simple.felicity.viewmodels.panels.LyricsSearchViewModel
import dagger.hilt.android.AndroidEntryPoint

/**
 * Panel that allows the user to search for and download LRC lyrics from LrcLib.
 *
 * When opened, the panel automatically searches for lyrics matching the currently playing song.
 * The search keyword is pre-populated with the song title and is preserved across configuration
 * changes and process death. The user may edit the keyword and press the search button (or the
 * IME search action) to run a custom query.
 *
 * Tapping a result in the list downloads its synced lyrics and saves them as a `.lrc` sidecar
 * file next to the audio file, then navigates back to the calling screen.
 *
 * @author Hamza417
 */
@AndroidEntryPoint
class LyricsSearch : MediaFragment() {

    private lateinit var binding: FragmentLyricsSearchBinding
    private lateinit var headerBinding: HeaderGenericSearchBinding

    private var adapterLrcSearch: AdapterLrcSearch? = null

    /**
     * When this fragment is opened from [MetadataEditor], this holds the audio whose
     * lyrics we are searching for. When null, the screen operates in its normal mode
     * and saves the picked result as a sidecar `.lrc` file.
     */
    private val editorAudio: Audio? by lazy {
        arguments?.parcelable<Audio>(BundleConstants.AUDIO)
    }

    /**
     * Guards against the [android.text.TextWatcher] treating programmatic text updates as user edits.
     * Set to `true` before calling [android.widget.EditText.setText] programmatically
     * and reset to `false` immediately after.
     */
    private var isProgrammaticTextSet = false

    private val viewModel: LyricsSearchViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentLyricsSearchBinding.inflate(inflater, container, false)
        headerBinding = HeaderGenericSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireHiddenMiniPlayer()

        binding.appHeader.setContentView(headerBinding.root)
        binding.appHeader.attachTo(binding.recyclerView, AppHeader.ScrollMode.HIDE_ON_SCROLL)
        binding.recyclerView.layoutManager = GridLayoutManager(requireContext(), 1)

        setupSearchBox()
        setupSearchButton()
        observeViewModel()

        // If we were opened from the metadata editor, pre-populate the search with
        // the audio's title and kick off a fresh search right away.
        editorAudio?.let { audio ->
            val keyword = audio.title?.takeIf { it.isNotBlank() } ?: audio.name
            isProgrammaticTextSet = true
            headerBinding.editText.setText(keyword)
            headerBinding.editText.setSelection(keyword.length)
            isProgrammaticTextSet = false
            viewModel.setUserKeyword(keyword)
            viewModel.searchWithCurrentKeyword()
        }
    }

    override fun onDestroyView() {
        adapterLrcSearch = null
        super.onDestroyView()
    }

    private fun setupSearchBox() {
        // Populate the search box with the current keyword without triggering the user-modified flag.
        isProgrammaticTextSet = true
        headerBinding.editText.setText(viewModel.keyword)
        headerBinding.editText.setSelection(headerBinding.editText.text?.length ?: 0)
        isProgrammaticTextSet = false

        headerBinding.editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                if (!isProgrammaticTextSet) {
                    viewModel.setUserKeyword(s?.toString() ?: "")
                }
            }
        })

        // Allow triggering a search via the IME "Search" action key on the keyboard.
        headerBinding.editText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                viewModel.searchWithCurrentKeyword()
                true
            } else {
                false
            }
        }
    }

    private fun setupSearchButton() {
        headerBinding.search.setOnClickListener {
            viewModel.searchWithCurrentKeyword()
        }
    }

    private fun observeViewModel() {
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            headerBinding.progress.isVisible = isLoading
            if (isLoading) {
                headerBinding.count.text = getString(R.string.loading)
            } else {
                // If loading just finished and there are no results, show "No lyrics found".
                if (viewModel.searchResults.value.isNullOrEmpty()) {
                    headerBinding.count.text = getString(R.string.no_lyrics_found)
                }
            }
        }

        viewModel.searchResults.observe(viewLifecycleOwner) { results ->
            ensureAdapterCreated()
            adapterLrcSearch?.updateResults(results)

            val isLoading = viewModel.isLoading.value ?: false

            headerBinding.count.text = if (results.isNotEmpty()) {
                getString(R.string.x_results, results.size)
            } else {
                if (isLoading) {
                    getString(R.string.loading)
                } else {
                    getString(R.string.no_lyrics_found)
                }
            }
        }

        viewModel.lrcSaved.observe(viewLifecycleOwner) { saved ->
            if (saved) {
                // Notify the Lyrics panel that new lyrics were saved so it can reload without
                // requiring the user to close and reopen the screen.
                parentFragmentManager.setFragmentResult(REQUEST_KEY_LYRICS_SAVED, Bundle())
                goBack()
            }
        }

        viewModel.getWarning().observe(viewLifecycleOwner) { message ->
            if (!message.isNullOrBlank()) {
                viewModel.warning.postValue(null)
            }
        }
    }

    private fun ensureAdapterCreated() {
        if (adapterLrcSearch == null) {
            adapterLrcSearch = AdapterLrcSearch { lrcResponse ->
                if (editorAudio != null) {
                    // When opened from the editor, deliver the lyrics back via the
                    // Fragment Result API so the editor can paste them into the field.
                    parentFragmentManager.setFragmentResult(
                            REQUEST_KEY_LYRICS_FOR_EDITOR,
                            Bundle().apply {
                                putString(KEY_PLAIN_LYRICS, lrcResponse.plainLyrics)
                                putString(KEY_SYNCED_LYRICS, lrcResponse.syncedLyrics)
                            }
                    )
                    goBack()
                } else {
                    viewModel.downloadAndSaveLrc(lrcResponse)
                }
            }
            binding.recyclerView.adapter = adapterLrcSearch
        }
    }

    companion object {
        /**
         * Creates a new [LyricsSearch] instance in its normal mode — results are saved
         * as a sidecar `.lrc` file next to the currently playing audio.
         */
        fun newInstance(): LyricsSearch {
            return LyricsSearch().apply { arguments = Bundle() }
        }

        /**
         * Creates a [LyricsSearch] instance in editor mode. The search is pre-populated
         * with [audio]'s title, and picking a result fires [REQUEST_KEY_LYRICS_FOR_EDITOR]
         * back to [MetadataEditor] instead of saving a sidecar file.
         *
         * @param audio The track whose lyrics the user is looking up in the editor.
         */
        fun newInstanceForEditor(audio: Audio): LyricsSearch {
            return LyricsSearch().apply {
                arguments = Bundle().apply {
                    putParcelable(BundleConstants.AUDIO, audio)
                }
            }
        }

        const val TAG = "LyricsSearch"

        /** Fragment Result API key used when the screen is opened from [MetadataEditor]. */
        const val REQUEST_KEY_LYRICS_FOR_EDITOR = "lyrics_search_for_editor"

        /** Bundle key carrying the plain (unsynced) lyrics string. */
        const val KEY_PLAIN_LYRICS = "plain_lyrics"

        /** Bundle key carrying the synced LRC lyrics string. */
        const val KEY_SYNCED_LYRICS = "synced_lyrics"

        /**
         * Fragment Result API key broadcast by [LyricsSearch] when a lyrics file is
         * successfully saved. [app.simple.felicity.ui.panels.Lyrics] listens for this key and reloads its lyrics data.
         */
        const val REQUEST_KEY_LYRICS_SAVED = "lyrics_search_lyrics_saved"
    }
}