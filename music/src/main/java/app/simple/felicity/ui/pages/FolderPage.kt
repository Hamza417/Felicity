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
import app.simple.felicity.databinding.FragmentPageFolderBinding
import app.simple.felicity.decorations.itemdecorations.PageSpacingItemDecoration
import app.simple.felicity.decorations.utils.RecyclerViewUtils.addItemDecorationSafely
import app.simple.felicity.extensions.fragments.MediaFragment
import app.simple.felicity.popups.PopupGenreMenu
import app.simple.felicity.preferences.AppearancePreferences
import app.simple.felicity.repository.constants.BundleConstants
import app.simple.felicity.repository.models.Album
import app.simple.felicity.repository.models.Artist
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.repository.models.Folder
import app.simple.felicity.repository.models.Genre
import app.simple.felicity.repository.models.PageData
import app.simple.felicity.utils.ParcelUtils.parcelable
import app.simple.felicity.viewmodels.viewer.FolderViewerViewModel
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.withCreationCallback
import kotlinx.coroutines.launch

@AndroidEntryPoint
class FolderPage : MediaFragment() {

    private lateinit var binding: FragmentPageFolderBinding
    private var pageAdapter: PageAdapter? = null

    private val folder: Folder by lazy {
        requireArguments().parcelable(BundleConstants.FOLDER)
            ?: throw IllegalArgumentException("Folder is required")
    }

    private val folderViewerViewModel by viewModels<FolderViewerViewModel>(
            extrasProducer = {
                defaultViewModelCreationExtras.withCreationCallback<FolderViewerViewModel.Factory> {
                    it.create(folder = folder)
                }
            }
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentPageFolderBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.recyclerView.requireAttachedMiniPlayer()
        postponeEnterTransition()

        Log.d(TAG, "onViewCreated: FolderPage for folder: ${folder.name}, adapter=${pageAdapter != null}")

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.CREATED) {
                folderViewerViewModel.data.collect { data ->
                    data?.let { updateFolderPage(it) }
                }
            }
        }
    }

    private fun updateFolderPage(data: PageData) {
        Log.d(TAG, "updateFolderPage: Updating UI for folder: ${folder.name} with ${data.songs.size} songs")

        if (pageAdapter == null) {
            pageAdapter = PageAdapter(data, PageAdapter.PageType.FolderPage(folder))
            val horPad = resources.getDimensionPixelSize(R.dimen.padding_10)
            binding.recyclerView.addItemDecorationSafely(PageSpacingItemDecoration(horPad, AppearancePreferences.getListSpacing().toInt()))
            binding.recyclerView.adapter = pageAdapter
            setupAdapterCallbacks()
        } else {
            Log.d(TAG, "updateFolderPage: Updating existing adapter with new data")
            pageAdapter?.updateData(data)

            if (binding.recyclerView.adapter == null) {
                Log.d(TAG, "updateFolderPage: Re-attaching adapter to RecyclerView")
                binding.recyclerView.adapter = pageAdapter
            }
        }

        requireView().startTransitionOnPreDraw()
    }

    private fun setupAdapterCallbacks() {
        pageAdapter?.setArtistAdapterListener(object : GeneralAdapterCallbacks {
            override fun onSongClicked(songs: MutableList<Audio>, position: Int, view: View) {
                Log.i(TAG, "onSongClicked: Song clicked in folder: ${folder.name}, position: $position")
                setMediaItems(songs, position)
            }

            override fun onSongLongClicked(songs: List<Audio>, position: Int, view: View) {
                openSongsMenu(songs, position, view as ImageView)
            }

            override fun onPlayClicked(audios: MutableList<Audio>, position: Int) {
                Log.i(TAG, "onPlayClicked: Play clicked for folder: ${folder.name}")
                setMediaItems(audios, position)
            }

            override fun onShuffleClicked(audios: MutableList<Audio>, position: Int) {
                Log.i(TAG, "onShuffleClicked: Shuffle clicked for folder: ${folder.name}")
                setMediaItems(audios.shuffled().toMutableList(), position)
            }

            override fun onArtistClicked(artists: List<Artist>, position: Int, view: View) {
                openFragment(ArtistPage.newInstance(artists[position]), ArtistPage.TAG)
            }

            override fun onAlbumClicked(albums: List<Album>, position: Int, view: View) {
                openFragment(AlbumPage.newInstance(albums[position]), AlbumPage.TAG)
            }

            override fun onGenreClicked(genre: Genre, view: View) {
                Log.i(TAG, "onGenreClicked: Genre clicked: ${genre.name}")
                openFragment(GenrePage.newInstance(genre), GenrePage.TAG)
            }

            override fun onMenuClicked(view: View) {
                Log.i(TAG, "onMenuClicked: Menu clicked for folder: ${folder.name}")

                viewLifecycleOwner.lifecycleScope.launch {
                    val currentData = folderViewerViewModel.data.value ?: return@launch

                    PopupGenreMenu(
                            container = requireContainerView(),
                            anchorView = view,
                            menuItems = listOf(R.string.play, R.string.shuffle, R.string.add_to_queue, R.string.add_to_playlist),
                            menuIcons = listOf(R.drawable.ic_play, R.drawable.ic_shuffle, R.drawable.ic_add_to_queue, R.drawable.ic_add_to_playlist),
                            onMenuItemClick = {
                                when (it) {
                                    R.string.play -> {
                                        Log.i(TAG, "onMenuItemClick: Play clicked for folder: ${folder.name}")
                                        setMediaItems(currentData.songs.toMutableList(), 0)
                                    }
                                    R.string.shuffle -> {
                                        Log.i(TAG, "onMenuItemClick: Shuffle clicked for folder: ${folder.name}")
                                        setMediaItems(currentData.songs.shuffled().toMutableList(), 0)
                                    }
                                }
                            },
                            onDismiss = {
                                Log.i(TAG, "onDismiss: Popup dismissed for folder: ${folder.name}")
                            }
                    ).show()
                }
            }
        })
    }

    companion object {
        const val TAG = "FolderPage"

        fun newInstance(folder: Folder): FolderPage {
            val args = Bundle()
            args.putParcelable(BundleConstants.FOLDER, folder)
            val fragment = FolderPage()
            fragment.arguments = args
            return fragment
        }
    }
}

