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
import app.simple.felicity.adapters.ui.lists.AdapterMetadataSearch
import app.simple.felicity.databinding.FragmentMetadataSearchBinding
import app.simple.felicity.databinding.HeaderMetadataSearchBinding
import app.simple.felicity.decorations.views.AppHeader
import app.simple.felicity.extensions.fragments.MediaFragment
import app.simple.felicity.repository.constants.BundleConstants
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.repository.models.MetadataSearchResult
import app.simple.felicity.ui.subpanels.MetadataSearch.Companion.KEY_RESULT
import app.simple.felicity.utils.ParcelUtils.parcelable
import app.simple.felicity.viewmodels.panels.MetadataSearchViewModel
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.withCreationCallback

/**
 * Panel that lets the user look up metadata for a track using the LrcLib API and
 * pick one result to auto-fill the metadata editor's fields.
 *
 * When opened, the search box is pre-filled with the track's title and artist so the
 * most relevant results appear immediately. The user can edit the keyword and press
 * the search button (or the IME Search key) to run a custom query.
 *
 * Tapping a result packages it into a [MetadataSearchResult] and delivers it back to
 * [MetadataEditor] via the Fragment Result API, then navigates back automatically.
 *
 * @author Hamza417
 */
@AndroidEntryPoint
class MetadataSearch : MediaFragment() {

    private lateinit var binding: FragmentMetadataSearchBinding
    private lateinit var headerBinding: HeaderMetadataSearchBinding

    private var adapter: AdapterMetadataSearch? = null

    /**
     * Guards against the [TextWatcher] firing when text is set programmatically.
     */
    private var isProgrammaticTextSet = false

    private val audio: Audio by lazy {
        requireArguments().parcelable<Audio>(BundleConstants.AUDIO)
            ?: throw IllegalArgumentException("Audio argument is required for MetadataSearch")
    }

    private val viewModel: MetadataSearchViewModel by viewModels(
            extrasProducer = {
                defaultViewModelCreationExtras.withCreationCallback<MetadataSearchViewModel.Factory> {
                    it.create(audio)
                }
            }
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentMetadataSearchBinding.inflate(inflater, container, false)
        headerBinding = HeaderMetadataSearchBinding.inflate(inflater, container, false)
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
        adapter = null
        super.onDestroyView()
    }

    private fun setupSearchBox() {
        isProgrammaticTextSet = true
        headerBinding.titleEditText.setText(viewModel.titleKeyword)
        headerBinding.titleEditText.setSelection(headerBinding.titleEditText.text?.length ?: 0)
        headerBinding.artistEditText.setText(viewModel.artistKeyword)
        headerBinding.artistEditText.setSelection(headerBinding.artistEditText.text?.length ?: 0)
        isProgrammaticTextSet = false

        headerBinding.titleEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                if (!isProgrammaticTextSet) viewModel.setUserTitle(s?.toString() ?: "")
            }
        })

        headerBinding.artistEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                if (!isProgrammaticTextSet) viewModel.setUserArtist(s?.toString() ?: "")
            }
        })

        headerBinding.titleEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                viewModel.searchWithCurrentKeyword()
                true
            } else {
                false
            }
        }

        headerBinding.artistEditText.setOnEditorActionListener { _, actionId, _ ->
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
                if (viewModel.searchResults.value.isNullOrEmpty()) {
                    headerBinding.count.text = getString(R.string.no_metadata_found)
                }
            }
        }

        viewModel.searchResults.observe(viewLifecycleOwner) { results ->
            ensureAdapterCreated()
            adapter?.updateResults(results)

            val isLoading = viewModel.isLoading.value ?: false
            headerBinding.count.text = if (results.isNotEmpty()) {
                getString(R.string.x_results, results.size)
            } else {
                if (isLoading) getString(R.string.loading)
                else getString(R.string.no_lyrics_found)
            }
        }

        viewModel.getWarning().observe(viewLifecycleOwner) { message ->
            if (!message.isNullOrBlank()) {
                viewModel.warning.postValue(null)
            }
        }
    }

    private fun ensureAdapterCreated() {
        if (adapter == null) {
            adapter = AdapterMetadataSearch { recording ->
                // Pull the credited artist names into one display string.
                val artist = recording.artistCredit
                    ?.mapNotNull { it.name?.takeIf { n -> n.isNotBlank() } }
                    ?.joinToString(", ")
                    .orEmpty()

                // Use the first linked release for album and year info.
                val firstRelease = recording.releases?.firstOrNull()
                val album = firstRelease?.title?.takeIf { it.isNotBlank() }
                val year = firstRelease?.date?.take(4)?.takeIf { it.isNotBlank() }

                // Pick the highest-voted genre tag if the recording has any.
                val genre = recording.tags
                    ?.maxByOrNull { it.count }
                    ?.name
                    ?.takeIf { it.isNotBlank() }

                val result = MetadataSearchResult(
                        title = recording.title.orEmpty(),
                        artist = artist,
                        album = album,
                        year = year,
                        genre = genre
                )
                parentFragmentManager.setFragmentResult(
                        REQUEST_KEY_METADATA_RESULT,
                        Bundle().apply { putParcelable(KEY_RESULT, result) }
                )
                goBack()
            }
            binding.recyclerView.adapter = adapter
        }
    }

    companion object {
        const val TAG = "MetadataSearch"

        /**
         * Fragment Result API key that [MetadataEditor] listens on. The bundle always
         * contains a [MetadataSearchResult] under [KEY_RESULT].
         */
        const val REQUEST_KEY_METADATA_RESULT = "metadata_search_result"

        /** Bundle key for the [MetadataSearchResult] parcelable inside the result bundle. */
        const val KEY_RESULT = "result"

        /**
         * Creates a new [MetadataSearch] fragment pre-loaded with the given [audio]'s
         * title and artist so the first search runs automatically on open.
         *
         * @param audio The track whose metadata is being edited.
         */
        fun newInstance(audio: Audio): MetadataSearch {
            return MetadataSearch().apply {
                arguments = Bundle().apply {
                    putParcelable(BundleConstants.AUDIO, audio)
                }
            }
        }
    }
}

