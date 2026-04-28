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
import app.simple.felicity.databinding.FragmentPageYearBinding
import app.simple.felicity.decorations.views.PopupMenuItem
import app.simple.felicity.decorations.views.SharedScrollViewPopup
import app.simple.felicity.extensions.fragments.BasePageFragment
import app.simple.felicity.repository.constants.BundleConstants
import app.simple.felicity.repository.models.YearGroup
import app.simple.felicity.utils.ParcelUtils.parcelable
import app.simple.felicity.viewmodels.viewer.YearViewerViewModel
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.withCreationCallback
import kotlinx.coroutines.launch

@AndroidEntryPoint
class YearPage : BasePageFragment() {

    private lateinit var binding: FragmentPageYearBinding

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

    override val pageRecyclerView: RecyclerView
        get() = binding.recyclerView

    override val pageType: PageAdapter.PageType by lazy { PageAdapter.PageType.YearPage(yearGroup) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentPageYearBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        collectPageData { yearViewerViewModel.data }
    }

    override fun resortPageData() {
        yearViewerViewModel.resort()
    }

    override fun onMenuClicked(view: View) {
        viewLifecycleOwner.lifecycleScope.launch {
            val currentData = yearViewerViewModel.data.value ?: return@launch

            SharedScrollViewPopup(
                    container = requireContainerView(),
                    anchorView = view,
                    menuItems = listOf(
                            PopupMenuItem(title = R.string.play, icon = R.drawable.ic_play),
                            PopupMenuItem(title = R.string.shuffle, icon = R.drawable.ic_shuffle),
                            PopupMenuItem(title = R.string.add_to_queue, icon = R.drawable.ic_add_to_queue),
                            PopupMenuItem(title = R.string.add_to_playlist, icon = R.drawable.ic_add_to_playlist)
                    ),
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
        const val TAG = "YearPage"

        fun newInstance(yearGroup: YearGroup): YearPage {
            val args = Bundle()
            // Strip the song paths before parceling — they can be huge for big libraries
            // and will blow past the 1 MB Binder transaction limit in no time. The
            // ViewModel fetches the songs fresh from the repository anyway.
            args.putParcelable(BundleConstants.YEAR_GROUP, yearGroup.copy(songPaths = emptyList()))
            val fragment = YearPage()
            fragment.arguments = args
            return fragment
        }
    }
}
