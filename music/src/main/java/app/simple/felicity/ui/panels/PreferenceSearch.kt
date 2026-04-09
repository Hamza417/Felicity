package app.simple.felicity.ui.panels

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.simple.felicity.R
import app.simple.felicity.adapters.preference.GenericPreferencesAdapter
import app.simple.felicity.databinding.FragmentPreferenceSearchBinding
import app.simple.felicity.databinding.HeaderPreferenceSearchBinding
import app.simple.felicity.decorations.views.AppHeader
import app.simple.felicity.enums.PreferenceType
import app.simple.felicity.extensions.fragments.PreferenceFragment
import app.simple.felicity.models.Preference
import app.simple.felicity.viewmodels.panels.PreferenceSearchViewModel
import kotlinx.coroutines.launch

/**
 * A dedicated panel that allows users to search across all application preferences in real time.
 *
 * On opening, the panel merges the preference items from every category panel (Appearance,
 * User Interface, Behavior, Engine, Library, Accessibility, and About) into a single flat list.
 * Structural items such as [PreferenceType.HEADER] and [PreferenceType.SUB_HEADER] are excluded
 * because they carry no actionable content of their own.
 *
 * As the user types in the search field the list is filtered instantly against the resolved
 * title and summary strings of each preference item. Every result remains fully interactive,
 * so users can toggle switches, move sliders, or open sub-panels without ever leaving the
 * search screen.
 *
 * @author Hamza417
 */
class PreferenceSearch : PreferenceFragment() {

    private lateinit var binding: FragmentPreferenceSearchBinding
    private lateinit var headerBinding: HeaderPreferenceSearchBinding

    private val viewModel: PreferenceSearchViewModel by viewModels()

    /**
     * The complete, merged list of all actionable preference items built once in
     * [onViewCreated] by combining every category panel. HEADER and SUB_HEADER types
     * are removed at construction time so the filter never has to re-check them.
     */
    private var allPreferences: List<Preference> = emptyList()

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?): View {
        binding = FragmentPreferenceSearchBinding.inflate(inflater, container, false)
        headerBinding = HeaderPreferenceSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.appHeader.setContentView(headerBinding.root)
        binding.appHeader.attachTo(binding.recyclerView, AppHeader.ScrollMode.HIDE_ON_SCROLL)
        binding.recyclerView.setHasFixedSize(false)

        allPreferences = buildAllPreferences()

        setupSearchBox()
        observeSearchQuery()
    }

    override val wantsMiniPlayerVisible: Boolean
        get() = false

    /**
     * Builds and returns the merged list of all searchable preference items.
     *
     * Items whose type is [PreferenceType.HEADER] or [PreferenceType.SUB_HEADER]
     * are stripped out because they serve only as visual section dividers inside
     * individual category screens and carry no actionable information of their own.
     *
     * @return a flat list of every searchable preference item across all categories.
     */
    private fun buildAllPreferences(): List<Preference> {
        val all = mutableListOf<Preference>()
        all.addAll(createAppearancePanel())
        all.addAll(createUserInterfacePanel())
        all.addAll(createBehaviorPanel())
        all.addAll(createEnginePanel())
        all.addAll(createLibraryPanel())
        all.addAll(createAccessibilityPanel())
        all.addAll(createAboutPanel())
        return all.filter {
            it.type != PreferenceType.HEADER && it.type != PreferenceType.SUB_HEADER
        }
    }

    /**
     * Initializes the search field with the last known query from the [viewModel]
     * and attaches a [TextWatcher] that forwards every keystroke to the ViewModel.
     */
    private fun setupSearchBox() {
        val currentQuery = viewModel.searchQuery.value
        if (currentQuery.isNotEmpty()) {
            headerBinding.editText.setText(currentQuery)
            headerBinding.editText.setSelection(currentQuery.length)
        }

        headerBinding.editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                viewModel.setSearchQuery(s?.toString() ?: "")
            }
        })
    }

    /**
     * Starts collecting [PreferenceSearchViewModel.searchQuery] and applies the filter
     * each time the query changes. Collection is tied to the [Lifecycle.State.STARTED]
     * state so it automatically pauses when the fragment is not on screen.
     */
    private fun observeSearchQuery() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.searchQuery.collect { query ->
                    applyFilter(query)
                }
            }
        }
    }

    /**
     * Filters [allPreferences] against [query] and updates the RecyclerView adapter
     * and result count label.
     *
     * Matching is case-insensitive and checks both the resolved title string and the
     * resolved summary string. When [query] is blank the list is cleared and the count
     * label is hidden so users start with a clean slate.
     *
     * @param query the current text typed by the user; may be blank.
     */
    private fun applyFilter(query: String) {
        if (query.isBlank()) {
            binding.recyclerView.adapter = null
            headerBinding.count.text = ""
            return
        }

        val lowerQuery = query.trim().lowercase()
        val filtered = allPreferences.filter { pref ->
            val titleString = runCatching { getString(pref.title) }.getOrElse { "" }
            val summaryString = when (val summary = pref.summary) {
                is Int -> runCatching { getString(summary) }.getOrElse { "" }
                is String -> summary
                else -> ""
            }
            titleString.lowercase().contains(lowerQuery) || summaryString.lowercase().contains(lowerQuery)
        }

        binding.recyclerView.adapter = GenericPreferencesAdapter(filtered, keyword = lowerQuery)
        headerBinding.count.text = getString(R.string.x_results, filtered.size)
    }

    companion object {
        /**
         * Creates a new instance of [PreferenceSearch].
         *
         * @return a fresh [PreferenceSearch] fragment ready to be added to the back stack.
         */
        fun newInstance(): PreferenceSearch {
            val args = Bundle()
            val fragment = PreferenceSearch()
            fragment.arguments = args
            return fragment
        }

        const val TAG = "PreferenceSearch"
    }
}


