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
import app.simple.felicity.extensions.fragments.BasePageFragment
import app.simple.felicity.popups.PopupGenreMenu
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
