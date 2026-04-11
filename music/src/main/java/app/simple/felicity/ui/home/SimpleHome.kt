package app.simple.felicity.ui.home

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import app.simple.felicity.R
import app.simple.felicity.adapters.home.main.AdapterSimpleHome
import app.simple.felicity.adapters.home.main.AdapterSimpleHome.Companion.AdapterSimpleHomeCallbacks
import app.simple.felicity.constants.CommonPreferencesConstants
import app.simple.felicity.databinding.FragmentHomeSimpleBinding
import app.simple.felicity.databinding.HeaderHomeBinding
import app.simple.felicity.decorations.views.AppHeader
import app.simple.felicity.dialogs.app.AppLabel.Companion.showAppLabel
import app.simple.felicity.dialogs.home.SimpleHomeMenu.Companion.showHomeMenu
import app.simple.felicity.extensions.fragments.BaseHomeFragment
import app.simple.felicity.preferences.HomePreferences
import app.simple.felicity.preferences.MainPreferences
import app.simple.felicity.viewmodels.panels.SimpleHomeViewModel
import app.simple.felicity.viewmodels.panels.SimpleHomeViewModel.Companion.Panel

/**
 * Simple home screen that displays all library panels in a scrollable list or grid.
 *
 * The header is managed by [AppHeader] and hides on downward scroll. The layout type
 * (list vs. grid) and grid span count are driven by [HomePreferences] and update live
 * when the user changes them through [SimpleHomeMenu].
 *
 * @author Hamza417
 */
class SimpleHome : BaseHomeFragment() {

    private lateinit var binding: FragmentHomeSimpleBinding
    private lateinit var headerBinding: HeaderHomeBinding

    private var homeViewModel: SimpleHomeViewModel? = null
    private var adapterSimpleHome: AdapterSimpleHome? = null
    private var gridLayoutManager: GridLayoutManager? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentHomeSimpleBinding.inflate(inflater, container, false)
        headerBinding = HeaderHomeBinding.inflate(inflater, container, false)
        homeViewModel = ViewModelProvider(requireActivity())[SimpleHomeViewModel::class.java]

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.appHeader.setContentView(headerBinding.root)
        binding.appHeader.attachTo(binding.recyclerView, AppHeader.ScrollMode.HIDE_ON_SCROLL)
        binding.recyclerView.requireAttachedMiniPlayer()
        headerBinding.label.setAppLabel()

        gridLayoutManager = GridLayoutManager(requireContext(), 1)
        updateGridLayoutSpanCount()
        binding.recyclerView.layoutManager = gridLayoutManager

        headerBinding.search.setOnClickListener {
            openSearch()
        }

        headerBinding.menu.setOnClickListener {
            childFragmentManager.showHomeMenu()
        }

        setupServerToggle(headerBinding.serverToggle)

        homeViewModel!!.getHomeData().observe(viewLifecycleOwner) { list ->
            adapterSimpleHome = AdapterSimpleHome(list)
            adapterSimpleHome!!.setLayoutType(HomePreferences.getHomeLayoutType())
            setupAdapterCallbacks()
            binding.recyclerView.adapter = adapterSimpleHome
        }

        headerBinding.label.setOnClickListener {
            childFragmentManager.showAppLabel()
        }
    }

    private fun setupAdapterCallbacks() {
        adapterSimpleHome?.setAdapterSimpleHomeCallbacks(object : AdapterSimpleHomeCallbacks {
            override fun onItemClicked(panel: Panel, position: Int, view: View) {
                navigateToPanel(panel)
            }
        })
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        /**
         * When the view is recreated (back navigation, rotation, or process restore), the
         * [AppHeader] restores its hidden state via [android.os.Parcelable] in its own
         * [android.view.View.onRestoreInstanceState], which uses a [android.view.View.post]
         * internally. We must post our reset AFTER that runnable so it wins the ordering
         * race and leaves the header fully visible.
         *
         * We always reset regardless of [savedInstanceState] because [SimpleHome] always
         * creates a fresh [app.simple.felicity.adapters.home.main.AdapterSimpleHome] in its
         * LiveData observer, which resets the RecyclerView scroll offset to 0 in every
         * scenario — including rotation. Preserving a hidden header state while the list is
         * at position 0 would leave the header permanently stuck off-screen with no way to
         * scroll it back into view.
         */
        binding.appHeader.post {
            binding.appHeader.resetScrollingState()
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        super.onSharedPreferenceChanged(sharedPreferences, key)
        when (key) {
            HomePreferences.HOME_LAYOUT_TYPE -> {
                val layoutType = HomePreferences.getHomeLayoutType()
                adapterSimpleHome?.setLayoutType(layoutType)
                updateGridLayoutSpanCount()
                binding.recyclerView.scheduleLayoutAnimation()
                adapterSimpleHome?.notifyItemRangeChanged(0, adapterSimpleHome?.itemCount ?: 0)
            }
            MainPreferences.APP_LABEL -> {
                headerBinding.label.setAppLabel()
            }
        }
    }

    private fun updateGridLayoutSpanCount() {
        val spanCount = if (HomePreferences.getHomeLayoutType() == CommonPreferencesConstants.GRID_TYPE_GRID) {
            resources.getInteger(R.integer.home_grid_span_count)
        } else {
            1
        }

        gridLayoutManager?.spanCount = spanCount

        gridLayoutManager?.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                return if (adapterSimpleHome?.getItemViewType(position) == AdapterSimpleHome.VIEW_TYPE_GROUP) {
                    spanCount
                } else {
                    1
                }
            }
        }
    }

    companion object {
        /**
         * Creates a new instance of [SimpleHome].
         *
         * @return A fresh [SimpleHome] fragment.
         */
        fun newInstance(): SimpleHome {
            val args = Bundle()
            val fragment = SimpleHome()
            fragment.arguments = args
            return fragment
        }

        const val TAG = "SimpleHome"
    }
}
