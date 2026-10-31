package app.simple.felicity.ui.panels

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.fragment.app.viewModels
import app.simple.felicity.R
import app.simple.felicity.adapters.ui.lists.AdapterRecentlyAdded
import app.simple.felicity.callbacks.GeneralAdapterCallbacks
import app.simple.felicity.constants.CommonPreferencesConstants
import app.simple.felicity.databinding.FragmentMixBinding
import app.simple.felicity.databinding.HeaderMixBinding
import app.simple.felicity.decorations.highlight.HighlightTextView
import app.simple.felicity.decorations.views.AppHeader
import app.simple.felicity.dialogs.app.TotalTime.Companion.showTotalTime
import app.simple.felicity.extensions.fragments.BasePanelFragment
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.shared.utils.TimeUtils.toDynamicTimeString
import app.simple.felicity.viewmodels.panels.MixesViewModel
import app.simple.felicity.viewmodels.panels.MixesViewModel.Mix
import app.simple.felicity.viewmodels.panels.MixesViewModel.MixType
import dagger.hilt.android.AndroidEntryPoint

/**
 * Panel showing the songs of one auto-generated mix, either Discover or Random Picks
 * depending on the [MixType] it was opened with. The list is shared with the dashboard
 * "Mixed" row through [MixesViewModel] and only changes when the user asks for a new mix.
 *
 * @author Hamza417
 */
@AndroidEntryPoint
class MixPanel : BasePanelFragment() {

    private lateinit var binding: FragmentMixBinding
    private lateinit var headerBinding: HeaderMixBinding

    private var adapterSongs: AdapterRecentlyAdded? = null

    private val mixesViewModel: MixesViewModel by viewModels({ requireActivity() })

    private val mixType: MixType
        get() = MixType.valueOf(requireArguments().getString(MIX_TYPE, MixType.DISCOVER.name))

    private val currentSongs: List<Audio>
        get() = mixesViewModel.mixes.value.firstOrNull { it.type == mixType }?.songs.orEmpty()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentMixBinding.inflate(inflater, container, false)
        headerBinding = HeaderMixBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        headerBinding.title.setText(getTitleRes())

        binding.recyclerView.requireAttachedMiniPlayer()
        binding.recyclerView.attachSlideFastScroller()
        binding.appHeader.setContentView(headerBinding.root)
        binding.appHeader.attachTo(binding.recyclerView, AppHeader.ScrollMode.HIDE_ON_SCROLL)

        binding.recyclerView.setupGridLayoutManager(CommonPreferencesConstants.LayoutMode.LIST_ONE.spanCount)

        setupClickListeners()

        mixesViewModel.mixes.collectWhenStarted { mixes ->
            mixes.firstOrNull { it.type == mixType }?.let { updateSongsList(it) }
        }
    }

    override fun onDestroyView() {
        adapterSongs = null
        super.onDestroyView()
    }

    override fun getShuffleButton(): HighlightTextView {
        return headerBinding.shuffle
    }

    override fun onShuffleClicked() {
        super.onShuffleClicked()
        shuffleMediaItems(currentSongs)
    }

    private fun getTitleRes(): Int {
        return when (mixType) {
            MixType.DISCOVER -> R.string.discover
            MixType.RANDOM_PICKS -> R.string.random_picks
        }
    }

    private fun setupClickListeners() {
        headerBinding.search.setOnClickListener {
            openSearch()
        }

        headerBinding.menu.setOnClickListener {
            openPreferencesPanel()
        }

        headerBinding.newMix.setOnClickListener {
            mixesViewModel.refresh()
        }
    }

    private fun updateSongsList(mix: Mix) {
        val songs = mix.songs

        if (adapterSongs == null) {
            adapterSongs = AdapterRecentlyAdded(songs).also {
                it.layoutMode = CommonPreferencesConstants.LayoutMode.LIST_ONE
            }
            adapterSongs?.setGeneralAdapterCallbacks(object : GeneralAdapterCallbacks {
                override fun onSongClicked(songs: MutableList<Audio>, position: Int, view: View) {
                    setMediaItems(songs, position)
                }

                override fun onSongLongClicked(audios: MutableList<Audio>, position: Int, imageView: ImageView?) {
                    openSongsMenu(audios, position, imageView)
                }
            })
            binding.recyclerView.adapter = adapterSongs
        } else {
            adapterSongs?.updateSongs(songs)
        }

        headerBinding.count.text = getString(R.string.x_songs, songs.size)
        headerBinding.hours.text = songs.sumOf { it.duration }.toDynamicTimeString()

        headerBinding.hours.setOnClickListener {
            childFragmentManager.showTotalTime(
                    totalTime = songs.sumOf { it.duration },
                    count = songs.size
            )
        }
    }

    companion object {
        private const val MIX_TYPE = "mix_type"

        const val TAG = "MixPanel"

        /**
         * Creates a panel for the given [type] of mix.
         */
        fun newInstance(type: MixType): MixPanel {
            val args = Bundle()
            args.putString(MIX_TYPE, type.name)
            val fragment = MixPanel()
            fragment.arguments = args
            return fragment
        }
    }
}
