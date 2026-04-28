package app.simple.felicity.ui.pages

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.net.toUri
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import app.simple.felicity.R
import app.simple.felicity.adapters.ui.page.PageAdapter
import app.simple.felicity.databinding.FragmentPageArtistBinding
import app.simple.felicity.decorations.views.PopupMenuItem
import app.simple.felicity.decorations.views.SharedScrollViewPopup
import app.simple.felicity.extensions.fragments.BasePageFragment
import app.simple.felicity.repository.constants.BundleConstants
import app.simple.felicity.repository.models.Album
import app.simple.felicity.utils.ParcelUtils.parcelable
import app.simple.felicity.viewmodels.viewer.AlbumViewerViewModel
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.withCreationCallback
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AlbumPage : BasePageFragment() {

    private lateinit var binding: FragmentPageArtistBinding

    private val album: Album by lazy {
        requireArguments().parcelable(BundleConstants.ALBUM)
            ?: throw IllegalArgumentException("Album is required")
    }

    private val albumViewerViewModel: AlbumViewerViewModel by viewModels(
            ownerProducer = { this },
            extrasProducer = {
                defaultViewModelCreationExtras.withCreationCallback<AlbumViewerViewModel.Factory> {
                    it.create(album = album)
                }
            }
    )

    override val pageRecyclerView: RecyclerView
        get() = binding.recyclerView

    override val pageType: PageAdapter.PageType by lazy { PageAdapter.PageType.AlbumPage(album) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentPageArtistBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        collectPageData { albumViewerViewModel.data }
    }

    override fun resortPageData() {
        albumViewerViewModel.resort()
    }

    override fun onMenuClicked(view: View) {
        viewLifecycleOwner.lifecycleScope.launch {
            val currentData = albumViewerViewModel.data.value ?: return@launch

            SharedScrollViewPopup(
                    container = requireContainerView(),
                    anchorView = view,
                    menuItems = listOf(
                            PopupMenuItem(title = R.string.play, icon = R.drawable.ic_play),
                            PopupMenuItem(title = R.string.shuffle, icon = R.drawable.ic_shuffle),
                            PopupMenuItem(title = R.string.send, icon = R.drawable.ic_send)
                    ),
                    onMenuItemClick = {
                        when (it) {
                            R.string.play -> setMediaItems(currentData.songs.toMutableList(), 0)
                            R.string.shuffle -> shuffleMediaItems(currentData.songs)
                            R.string.send -> {
                                val audioUris = currentData.songs.map { audio ->
                                    java.io.File(audio.uri).toUri()
                                }
                                val shareIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                                    setType("audio/*")
                                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(audioUris))
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                startActivity(Intent.createChooser(shareIntent, getString(R.string.send)))
                            }
                        }
                    },
                    onDismiss = {}
            ).show()
        }
    }

    companion object {
        const val TAG = "AlbumPage"

        fun newInstance(album: Album): AlbumPage {
            val args = Bundle()
            // Strip the song paths before parceling — they can be huge for big libraries
            // and will blow past the 1 MB Binder transaction limit in no time. The
            // ViewModel fetches the songs fresh from the repository anyway.
            args.putParcelable(BundleConstants.ALBUM, album.copy(songPaths = emptyList()))
            val fragment = AlbumPage()
            fragment.arguments = args
            return fragment
        }
    }
}