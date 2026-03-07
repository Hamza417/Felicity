package app.simple.felicity.ui.pages

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.simple.felicity.R
import app.simple.felicity.adapters.ui.page.PageAdapter
import app.simple.felicity.callbacks.GeneralAdapterCallbacks
import app.simple.felicity.databinding.FragmentPageYearBinding
import app.simple.felicity.decorations.itemdecorations.PageSpacingItemDecoration
import app.simple.felicity.decorations.utils.RecyclerViewUtils.addItemDecorationSafely
import app.simple.felicity.extensions.fragments.MediaFragment
import app.simple.felicity.popups.PopupGenreMenu
import app.simple.felicity.preferences.AppearancePreferences
import app.simple.felicity.repository.constants.BundleConstants
import app.simple.felicity.repository.models.Album
import app.simple.felicity.repository.models.Artist
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.repository.models.Genre
import app.simple.felicity.repository.models.PageData
import app.simple.felicity.repository.models.YearGroup
import app.simple.felicity.utils.ParcelUtils.parcelable
import app.simple.felicity.viewmodels.viewer.YearViewerViewModel
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.withCreationCallback
import kotlinx.coroutines.launch

@AndroidEntryPoint
class YearPage : MediaFragment() {

    private lateinit var binding: FragmentPageYearBinding
    private var pageAdapter: PageAdapter? = null

    private val yearGroup: YearGroup by lazy {
        requireArguments().parcelable(BundleConstants.YEAR_GROUP)
            ?: throw IllegalArgumentException("YearGroup is required")
    }

    private val yearViewerViewModel by viewModels<YearViewerViewModel>(
            extrasProducer = {
                defaultViewModelCreationExtras.withCreationCallback<YearViewerViewModel.Factory> {
                    it.create(yearGroup = yearGroup)
                }
            }
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentPageYearBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.recyclerView.requireAttachedMiniPlayer()
        postponeEnterTransition()

        Log.d(TAG, "onViewCreated: YearPage for year: ${yearGroup.year}, adapter=${pageAdapter != null}")

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.CREATED) {
                yearViewerViewModel.data.collect { data ->
                    data?.let { updateYearPage(it) }
                }
            }
        }
    }

    private fun updateYearPage(data: PageData) {
        val horPad = resources.getDimensionPixelSize(R.dimen.padding_10)
        binding.recyclerView.addItemDecorationSafely(
                PageSpacingItemDecoration(horPad, AppearancePreferences.getListSpacing().toInt()))

        if (pageAdapter == null) {
            pageAdapter = PageAdapter(data, PageAdapter.PageType.YearPage(yearGroup))
            binding.recyclerView.adapter = pageAdapter
            setupAdapterCallbacks()
        } else {
            Log.d(TAG, "updateYearPage: Updating existing adapter with new data")
            pageAdapter?.updateData(data)
            if (binding.recyclerView.adapter == null) {
                binding.recyclerView.adapter = pageAdapter
            }
        }

        requireView().startTransitionOnPreDraw()
    }

    private fun setupAdapterCallbacks() {
        pageAdapter?.setArtistAdapterListener(object : GeneralAdapterCallbacks {
            override fun onSongClicked(songs: MutableList<Audio>, position: Int, view: View) {
                Log.i(TAG, "onSongClicked: position=$position for year: ${yearGroup.year}")
                setMediaItems(songs, position)
            }

            override fun onSongLongClicked(songs: List<Audio>, position: Int, view: View) {
                openSongsMenu(songs, position, view as ImageView)
            }

            override fun onPlayClicked(audios: MutableList<Audio>, position: Int) {
                setMediaItems(audios, position)
            }

            override fun onShuffleClicked(audios: MutableList<Audio>, position: Int) {
                shuffleMediaItems(audios)
            }

            override fun onArtistClicked(artists: List<Artist>, position: Int, view: View) {
                openFragment(ArtistPage.newInstance(artists[position]), ArtistPage.TAG)
            }

            override fun onAlbumClicked(albums: List<Album>, position: Int, view: View) {
                openFragment(AlbumPage.newInstance(albums[position]), AlbumPage.TAG)
            }

            override fun onGenreClicked(genre: Genre, view: View) {
                openFragment(GenrePage.newInstance(genre), GenrePage.TAG)
            }

            override fun onMenuClicked(view: View) {
                viewLifecycleOwner.lifecycleScope.launch {
                    val currentData = yearViewerViewModel.data.value ?: return@launch

                    PopupGenreMenu(
                            container = requireContainerView(),
                            anchorView = view,
                            menuItems = listOf(R.string.play, R.string.shuffle, R.string.add_to_queue, R.string.add_to_playlist),
                            menuIcons = listOf(R.drawable.ic_play, R.drawable.ic_shuffle, R.drawable.ic_add_to_queue, R.drawable.ic_add_to_playlist),
                            onMenuItemClick = {
                                when (it) {
                                    R.string.play -> setMediaItems(currentData.songs.toMutableList(), 0)
                                    R.string.shuffle -> shuffleMediaItems(currentData.songs)
                                }
                            },
                            onDismiss = {}
                    ).show()
                }
            }
        })
    }

    override fun onAudio(audio: Audio) {
        super.onAudio(audio)
        pageAdapter?.notifyCurrentSong()
    }

    companion object {
        const val TAG = "YearPage"

        fun newInstance(yearGroup: YearGroup): YearPage {
            val args = Bundle()
            args.putParcelable(BundleConstants.YEAR_GROUP, yearGroup)
            val fragment = YearPage()
            fragment.arguments = args
            return fragment
        }
    }
}

