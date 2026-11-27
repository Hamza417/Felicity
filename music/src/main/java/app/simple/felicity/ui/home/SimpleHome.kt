package app.simple.felicity.ui.home

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import app.simple.felicity.R
import app.simple.felicity.adapters.home.main.AdapterSimpleHome
import app.simple.felicity.adapters.home.main.AdapterSimpleHome.Companion.AdapterSimpleHomeCallbacks
import app.simple.felicity.constants.CommonPreferencesConstants
import app.simple.felicity.databinding.FragmentHomeSimpleBinding
import app.simple.felicity.databinding.HeaderHomeBinding
import app.simple.felicity.decorations.popups.PopupMenuItem
import app.simple.felicity.decorations.popups.SharedScrollViewPopup
import app.simple.felicity.decorations.utils.TextViewUtils.setStartDrawable
import app.simple.felicity.decorations.views.AppHeader
import app.simple.felicity.dialogs.app.AppLabel.Companion.showAppLabel
import app.simple.felicity.dialogs.player.SleepTimerDialog.Companion.openSleepTimerDialog
import app.simple.felicity.engine.managers.MediaPlaybackManager
import app.simple.felicity.extensions.fragments.BaseHomeFragment
import app.simple.felicity.preferences.HomePreferences
import app.simple.felicity.preferences.MainPreferences
import app.simple.felicity.preferences.QueueLabelPreferences
import app.simple.felicity.preferences.UserInterfacePreferences
import app.simple.felicity.server.ServerModeService
import app.simple.felicity.shared.utils.ViewUtils.gone
import app.simple.felicity.shared.utils.ViewUtils.visible
import app.simple.felicity.ui.panels.PlayingQueue
import app.simple.felicity.viewmodels.panels.SimpleHomeViewModel
import app.simple.felicity.viewmodels.panels.SimpleHomeViewModel.Companion.Panel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Calendar

/**
 * Simple home screen that displays all library panels in a scrollable list or grid.
 *
 * The header is managed by [AppHeader] and hides on downward scroll. The layout type
 * (list vs. grid) and grid span count are driven by [HomePreferences] and update live
 * when the user changes them through [SimpleHomeMenu].
 *
 * @author Hamza417
 */
@AndroidEntryPoint
class SimpleHome : BaseHomeFragment() {

    private lateinit var binding: FragmentHomeSimpleBinding
    private lateinit var headerBinding: HeaderHomeBinding

    private val homeViewModel: SimpleHomeViewModel by viewModels({ requireActivity() })

    private var adapterSimpleHome: AdapterSimpleHome? = null
    private var gridLayoutManager: GridLayoutManager? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentHomeSimpleBinding.inflate(inflater, container, false)
        headerBinding = HeaderHomeBinding.inflate(inflater, container, false)

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
            openPreferencesPanel()
        }

        setupServerToggle(headerBinding.serverToggle)

        homeViewModel.getHomeData().observe(viewLifecycleOwner) { list ->
            adapterSimpleHome = AdapterSimpleHome(list)
            adapterSimpleHome!!.setLayoutType(HomePreferences.getHomeLayoutType())
            setupAdapterCallbacks()
            binding.recyclerView.adapter = adapterSimpleHome
        }

        headerBinding.label.setOnClickListener {
            childFragmentManager.showAppLabel()
        }

        setupInfoStrip()
    }

    private fun setupInfoStrip() {
        updateGridTypeChip()
        setWeatherChipState()

        headerBinding.gridType.setOnClickListener {
            hideMiniPlayer()

            SharedScrollViewPopup(
                    container = requireContainerView(),
                    anchorView = headerBinding.gridType,
                    menuItems = listOf(
                            PopupMenuItem(title = R.string.grid, icon = R.drawable.ic_grid_16dp),
                            PopupMenuItem(title = R.string.list, icon = R.drawable.ic_list_16dp)
                    ),
                    onMenuItemClick = { titleRes ->
                        when (titleRes) {
                            R.string.grid -> HomePreferences.setHomeLayoutType(CommonPreferencesConstants.GRID_TYPE_GRID)
                            R.string.list_style -> HomePreferences.setHomeLayoutType(CommonPreferencesConstants.GRID_TYPE_LIST)
                        }
                    },
                    onDismiss = {
                        showMiniPlayer()
                    }
            ).show()
        }

        headerBinding.sleepTimer.setOnClickListener {
            childFragmentManager.openSleepTimerDialog()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                homeViewModel.libraryStats.collect { stats ->
                    if (stats != null) {
                        val formatter = NumberFormat.getNumberInstance()
                        headerBinding.libraryStats.text = getString(
                                R.string.library_stats,
                                formatter.format(stats.trackCount),
                                formatter.format(stats.totalHours)
                        )
                        headerBinding.strip.visible(true)
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                ServerModeService.isRunning.collect { running ->
                    if (running) headerBinding.serverActiveChip.visible(true)
                    else headerBinding.serverActiveChip.gone()
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                MediaPlaybackManager.sleepTimerStateFlow.collectLatest { running ->
                    if (running) {
                        headerBinding.sleepTimer.visible(true)
                    } else {
                        headerBinding.sleepTimer.gone()
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                MediaPlaybackManager.sleepTimerRemainingFlow.collectLatest { remaining ->
                    if (MediaPlaybackManager.isSleepTimerRunning()) {
                        headerBinding.sleepTimer.text = remaining.formatted
                    } else if (MediaPlaybackManager.pauseAfterCurrentSong) {
                        headerBinding.sleepTimer.text = getString(R.string.till_this_song)
                    } else {
                        headerBinding.sleepTimer.text = ""
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                homeViewModel.activeQueue.collect { idx ->
                    headerBinding.activeQueue.text = QueueLabelPreferences.getDisplayLabel(idx) { _ ->
                        getString(R.string.current_queue, idx + 1)
                    }

                    headerBinding.activeQueue.setOnClickListener {
                        openFragment(PlayingQueue.newInstance(), PlayingQueue.TAG)
                    }
                }
            }
        }
    }

    private fun setWeatherChipState() {
        fun updateChipLabel(mode: Int) {
            when (mode) {
                UserInterfacePreferences.WEATHER_EFFECT_OFF -> {
                    headerBinding.weather.text = getString(R.string.off)
                    headerBinding.weather.setStartDrawable(R.drawable.ic_cloud_off_16dp)
                }
                UserInterfacePreferences.WEATHER_EFFECT_WINTER -> {
                    headerBinding.weather.text = getString(R.string.winter)
                    headerBinding.weather.setStartDrawable(R.drawable.ic_cloud_16dp)
                }
                UserInterfacePreferences.WEATHER_EFFECT_AUTUMN -> {
                    headerBinding.weather.text = getString(R.string.autumn)
                    headerBinding.weather.setStartDrawable(R.drawable.ic_cloud_16dp)
                }
                UserInterfacePreferences.WEATHER_EFFECT_SUMMER -> {
                    headerBinding.weather.text = getString(R.string.summer)
                    headerBinding.weather.setStartDrawable(R.drawable.ic_cloud_16dp)
                }
                else -> {
                    // Auto
                    val month = Calendar.getInstance().get(Calendar.MONTH)
                    when (month) {
                        Calendar.DECEMBER, Calendar.JANUARY, Calendar.FEBRUARY -> {
                            updateChipLabel(UserInterfacePreferences.WEATHER_EFFECT_WINTER)
                        }
                        Calendar.SEPTEMBER, Calendar.OCTOBER, Calendar.NOVEMBER -> {
                            updateChipLabel(UserInterfacePreferences.WEATHER_EFFECT_AUTUMN)
                        }
                        Calendar.MAY, Calendar.JUNE, Calendar.JULY, Calendar.AUGUST -> {
                            headerBinding.weather.text = getString(R.string.summer)
                            updateChipLabel(UserInterfacePreferences.WEATHER_EFFECT_SUMMER)
                        }
                        else -> {
                            headerBinding.weather.text = getString(R.string.off)
                            updateChipLabel(UserInterfacePreferences.WEATHER_EFFECT_OFF)
                        }
                    }
                }
            }
        }

        headerBinding.weather.setOnClickListener {
            hideMiniPlayer()

            SharedScrollViewPopup(
                    container = requireContainerView(),
                    anchorView = headerBinding.weather,
                    menuItems = listOf(
                            PopupMenuItem(title = R.string.off),
                            PopupMenuItem(title = R.string.auto),
                            PopupMenuItem(title = R.string.winter),
                            PopupMenuItem(title = R.string.autumn),
                            PopupMenuItem(title = R.string.summer)
                    ),
                    onMenuItemClick = {
                        when (it) {
                            R.string.off -> {
                                UserInterfacePreferences.setWeatherEffect(UserInterfacePreferences.WEATHER_EFFECT_OFF)
                                updateChipLabel(UserInterfacePreferences.WEATHER_EFFECT_OFF)
                            }
                            R.string.auto -> {
                                UserInterfacePreferences.setWeatherEffect(UserInterfacePreferences.WEATHER_EFFECT_AUTO)
                                updateChipLabel(UserInterfacePreferences.WEATHER_EFFECT_AUTO)
                            }
                            R.string.winter -> {
                                UserInterfacePreferences.setWeatherEffect(UserInterfacePreferences.WEATHER_EFFECT_WINTER)
                                updateChipLabel(UserInterfacePreferences.WEATHER_EFFECT_WINTER)
                            }
                            R.string.autumn -> {
                                UserInterfacePreferences.setWeatherEffect(UserInterfacePreferences.WEATHER_EFFECT_AUTUMN)
                                updateChipLabel(UserInterfacePreferences.WEATHER_EFFECT_AUTUMN)
                            }
                            R.string.summer -> {
                                UserInterfacePreferences.setWeatherEffect(UserInterfacePreferences.WEATHER_EFFECT_SUMMER)
                                updateChipLabel(UserInterfacePreferences.WEATHER_EFFECT_SUMMER)
                            }
                        }
                    },
                    onDismiss = {
                        showMiniPlayer()
                    }
            ).show()
        }

        updateChipLabel(UserInterfacePreferences.getWeatherEffect())
    }

    private fun updateGridTypeChip() {
        when (HomePreferences.getHomeLayoutType()) {
            CommonPreferencesConstants.GRID_TYPE_GRID -> {
                headerBinding.gridType.text = getString(R.string.grid)
                headerBinding.gridType.setStartDrawable(R.drawable.ic_grid_16dp)
            }
            CommonPreferencesConstants.GRID_TYPE_LIST -> {
                headerBinding.gridType.text = getString(R.string.list_style)
                headerBinding.gridType.setStartDrawable(R.drawable.ic_list_16dp)
            }
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
                updateGridTypeChip()
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
