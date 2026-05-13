package app.simple.felicity.ui.panels

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import app.simple.felicity.R
import app.simple.felicity.adapters.ui.lists.AdapterComposers
import app.simple.felicity.callbacks.GeneralAdapterCallbacks
import app.simple.felicity.constants.CommonPreferencesConstants
import app.simple.felicity.databinding.FragmentComposersBinding
import app.simple.felicity.databinding.HeaderComposersBinding
import app.simple.felicity.decorations.fastscroll.SectionedFastScroller
import app.simple.felicity.decorations.views.AppHeader
import app.simple.felicity.dialogs.app.GenericListStyleDialog
import app.simple.felicity.dialogs.app.GenericListStyleDialog.Companion.showListStyleDialog
import app.simple.felicity.dialogs.composers.ComposersSort.Companion.showComposersSort
import app.simple.felicity.extensions.fragments.BasePanelFragment
import app.simple.felicity.preferences.ComposerPreferences
import app.simple.felicity.repository.models.Artist
import app.simple.felicity.repository.sort.ComposerSort.setCurrentSortStyle
import app.simple.felicity.ui.pages.ComposerPage
import app.simple.felicity.viewmodels.panels.ComposersViewModel

/**
 * Panel fragment that shows all unique composers found in the user's library.
 * It supports sorting, alphabetical fast-scroll, and layout style switching —
 * just like the Artists panel, but grouped by the composer tag instead.
 *
 * @author Hamza417
 */
class Composers : BasePanelFragment() {

    private lateinit var binding: FragmentComposersBinding
    private lateinit var headerBinding: HeaderComposersBinding

    private var adapterComposers: AdapterComposers? = null

    private val composersViewModel: ComposersViewModel by viewModels({ requireActivity() })

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentComposersBinding.inflate(inflater, container, false)
        headerBinding = HeaderComposersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.header.setContentView(headerBinding.root)
        binding.header.attachTo(binding.recyclerView, AppHeader.ScrollMode.HIDE_ON_SCROLL)
        binding.recyclerView.attachSlideFastScroller()
        binding.recyclerView.requireAttachedMiniPlayer()

        binding.recyclerView.setupGridLayoutManager(ComposerPreferences.getGridSize().spanCount)

        setupClickListeners()

        adapterComposers?.let { binding.recyclerView.adapter = it }

        composersViewModel.composers.collectListWhenStarted({ adapterComposers != null }) { composers ->
            updateComposersList(composers.toMutableList())
        }
    }

    override fun onDestroyView() {
        adapterComposers = null
        super.onDestroyView()
    }

    private fun setupClickListeners() {
        headerBinding.sortStyle.setOnClickListener {
            childFragmentManager.showComposersSort()
        }

        headerBinding.search.setOnClickListener {
            openSearch()
        }

        headerBinding.menu.setOnClickListener {
            openPreferencesPanel()
        }

        headerBinding.listStyle.setOnClickListener {
            childFragmentManager.showListStyleDialog(GenericListStyleDialog.Companion.PANEL.COMPOSERS)
        }
    }

    private fun updateComposersList(composers: MutableList<Artist>) {
        if (adapterComposers == null) {
            adapterComposers = AdapterComposers(composers)
            adapterComposers?.setHasStableIds(true)
            adapterComposers?.setGeneralAdapterCallbacks(object : GeneralAdapterCallbacks {
                override fun onComposerClicked(composers: List<Artist>, position: Int, view: View) {
                    openFragment(ComposerPage.newInstance(composers[position]), ComposerPage.TAG)
                }

                override fun onComposerLongClicked(composers: List<Artist>, position: Int, imageView: android.widget.ImageView?) {
                    val composer = composers.getOrNull(position) ?: return
                    openArtistMenu(composer, imageView)
                }
            })
            binding.recyclerView.adapter = adapterComposers
        } else {
            adapterComposers?.updateList(composers)
            if (binding.recyclerView.adapter == null) {
                binding.recyclerView.adapter = adapterComposers
            }
        }

        headerBinding.count.text = getString(R.string.x_composers, composers.size)
        binding.recyclerView.requireAttachedSectionScroller(
                sections = provideScrollPositions(composers),
                header = binding.header,
                view = headerBinding.scroll)

        headerBinding.sortStyle.setCurrentSortStyle()
        headerBinding.scroll.hideOnUnfavorableSort(
                sorts = listOf(CommonPreferencesConstants.BY_NAME),
                preference = ComposerPreferences.getComposerSort()
        )
    }

    /**
     * Builds the fast-scroller section map when the list is sorted alphabetically by name.
     * Any other sort style returns an empty list so the scroller stays hidden.
     */
    private fun provideScrollPositions(composers: List<Artist>): List<SectionedFastScroller.Position> {
        when (ComposerPreferences.getComposerSort()) {
            CommonPreferencesConstants.BY_NAME -> {
                val firstCharToIndex = linkedMapOf<String, Int>()
                composers.forEachIndexed { index, composer ->
                    val firstChar = composer.name?.firstOrNull()?.uppercaseChar()
                    val key = if (firstChar != null && firstChar.isLetter()) firstChar.toString() else "#"
                    if (!firstCharToIndex.containsKey(key)) firstCharToIndex[key] = index
                }
                return firstCharToIndex.map { (char, index) -> SectionedFastScroller.Position(char, index) }
            }
        }
        return emptyList()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        super.onSharedPreferenceChanged(sharedPreferences, key)
        when (key) {
            ComposerPreferences.GRID_SIZE_PORTRAIT, ComposerPreferences.GRID_SIZE_LANDSCAPE -> {
                applyGridSizeUpdate(binding.recyclerView, ComposerPreferences.getGridSize().spanCount)
            }
        }
    }

    companion object {
        fun newInstance(): Composers {
            val args = Bundle()
            val fragment = Composers()
            fragment.arguments = args
            return fragment
        }

        const val TAG = "Composers"
    }
}

