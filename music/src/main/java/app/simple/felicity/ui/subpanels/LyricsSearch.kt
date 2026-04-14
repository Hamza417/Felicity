package app.simple.felicity.ui.subpanels

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.GridLayoutManager
import app.simple.felicity.R
import app.simple.felicity.adapters.ui.lists.AdapterLrcSearch
import app.simple.felicity.databinding.FragmentLyricsSearchBinding
import app.simple.felicity.databinding.HeaderGenericSearchBinding
import app.simple.felicity.decorations.views.AppHeader
import app.simple.felicity.extensions.fragments.MediaFragment
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
                parentFragmentManager.setFragmentResult(REQUEST_KEY_LYRICS_SAVED, bundleOf())
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
                viewModel.downloadAndSaveLrc(lrcResponse)
            }
            binding.recyclerView.adapter = adapterLrcSearch
        }
    }

    companion object {
        /**
         * Creates a new instance of [LyricsSearch].
         *
         * @return a fresh [LyricsSearch] fragment.
         */
        fun newInstance(): LyricsSearch {
            val args = Bundle()
            val fragment = LyricsSearch()
            fragment.arguments = args
            return fragment
        }

        const val TAG = "LyricsSearch"

        /**
         * Fragment Result API key broadcast by [LyricsSearch] when a lyrics file is
         * successfully saved. [app.simple.felicity.ui.panels.Lyrics] listens for this key and reloads its lyrics data.
         */
        const val REQUEST_KEY_LYRICS_SAVED = "lyrics_search_lyrics_saved"
    }
}