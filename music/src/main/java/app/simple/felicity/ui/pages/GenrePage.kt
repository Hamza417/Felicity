package app.simple.felicity.ui.pages

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import app.simple.felicity.R
import app.simple.felicity.adapters.ui.page.PageAdapter
import app.simple.felicity.databinding.FragmentViewerGenresBinding
import app.simple.felicity.extensions.fragments.BasePageFragment
import app.simple.felicity.popups.PopupGenreMenu
import app.simple.felicity.repository.constants.BundleConstants
import app.simple.felicity.repository.models.Genre
import app.simple.felicity.utils.ParcelUtils.parcelable
import app.simple.felicity.viewmodels.viewer.GenreViewerViewModel
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.withCreationCallback
import kotlinx.coroutines.launch

@AndroidEntryPoint
class GenrePage : BasePageFragment() {

    private lateinit var binding: FragmentViewerGenresBinding

    private val genre: Genre by lazy {
        requireArguments().parcelable(BundleConstants.GENRE)
            ?: throw IllegalArgumentException("Genre is required")
    }

    private val genreViewerViewModel by viewModels<GenreViewerViewModel>(
            extrasProducer = {
                defaultViewModelCreationExtras.withCreationCallback<GenreViewerViewModel.Factory> {
                    it.create(genre = genre)
                }
            }
    )

    override val pageRecyclerView: RecyclerView
        get() = binding.recyclerView

    override val pageType: PageAdapter.PageType by lazy { PageAdapter.PageType.GenrePage(genre) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentViewerGenresBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        collectPageData { genreViewerViewModel.data }
    }

    override fun resortPageData() {
        genreViewerViewModel.resort()
    }

    override fun onMenuClicked(view: View) {
        viewLifecycleOwner.lifecycleScope.launch {
            val currentData = genreViewerViewModel.data.value ?: return@launch

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

    companion object {
        const val TAG = "GenreSongs"

        fun newInstance(genre: Genre): GenrePage {
            val args = Bundle()
            args.putParcelable(BundleConstants.GENRE, genre)
            val fragment = GenrePage()
            fragment.arguments = args
            return fragment
        }
    }
}