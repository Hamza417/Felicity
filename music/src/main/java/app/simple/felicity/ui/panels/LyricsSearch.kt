package app.simple.felicity.ui.panels

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
import app.simple.felicity.viewmodels.panels.LyricsSearchViewModel
import app.simple.felicity.viewmodels.player.LyricsViewModel
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.withCreationCallback

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
     * Guards against the [TextWatcher] treating programmatic text updates as user edits.
     * Set to `true` before calling [android.widget.EditText.setText] programmatically
     * and reset to `false` immediately after.
     */
    private var isProgrammaticTextSet = false

    private val viewModel: LyricsSearchViewModel by viewModels()

    /**
     * Activity-scoped reference to [LyricsViewModel] — the same instance held by the
     * [Lyrics] panel. Calling [LyricsViewModel.reloadLrcData] here causes that panel's
     * lrc observer to receive the newly saved lyrics without requiring the user to close
     * and reopen the Lyrics screen.
     */
    private val lyricsViewModel: LyricsViewModel by viewModels(
            ownerProducer = { requireActivity() },
            extrasProducer = {
                defaultViewModelCreationExtras.withCreationCallback<LyricsViewModel.Factory> {
                    it.create(audio = null)
                }
            }
    )

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
                binding.emptyText.isVisible = false
                headerBinding.count.text = getString(R.string.loading)
            }
        }

        viewModel.searchResults.observe(viewLifecycleOwner) { results ->
            ensureAdapterCreated()
            adapterLrcSearch?.updateResults(results)

            val isLoading = viewModel.isLoading.value ?: false
            binding.emptyText.isVisible = results.isEmpty() && !isLoading

            headerBinding.count.text = if (results.isNotEmpty()) {
                getString(R.string.x_results, results.size)
            } else {
                ""
            }
        }

        viewModel.lrcSaved.observe(viewLifecycleOwner) { saved ->
            if (saved) {
                // Reload the lyrics in the Lyrics panel via the shared activity-scoped ViewModel
                // so it updates immediately when this fragment pops from the back stack.
                lyricsViewModel.reloadLrcData()
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
    }
}

