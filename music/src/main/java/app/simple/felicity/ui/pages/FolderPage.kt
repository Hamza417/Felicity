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
import app.simple.felicity.databinding.FragmentPageFolderBinding
import app.simple.felicity.decorations.views.PopupMenuItem
import app.simple.felicity.decorations.views.SharedScrollViewPopup
import app.simple.felicity.extensions.fragments.BasePageFragment
import app.simple.felicity.repository.constants.BundleConstants
import app.simple.felicity.repository.models.Folder
import app.simple.felicity.utils.ParcelUtils.parcelable
import app.simple.felicity.viewmodels.viewer.FolderViewerViewModel
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.withCreationCallback
import kotlinx.coroutines.launch

@AndroidEntryPoint
class FolderPage : BasePageFragment() {

    private lateinit var binding: FragmentPageFolderBinding

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

    override val pageRecyclerView: RecyclerView
        get() = binding.recyclerView

    override val pageType: PageAdapter.PageType by lazy { PageAdapter.PageType.FolderPage(folder) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentPageFolderBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        collectPageData { folderViewerViewModel.data }
    }

    override fun resortPageData() {
        folderViewerViewModel.resort()
    }

    override fun onMenuClicked(view: View) {
        viewLifecycleOwner.lifecycleScope.launch {
            val currentData = folderViewerViewModel.data.value ?: return@launch

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
