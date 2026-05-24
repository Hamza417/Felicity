package app.simple.felicity.ui.panels

import android.os.Bundle
import android.text.format.DateUtils
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import app.simple.felicity.R
import app.simple.felicity.adapters.ui.lists.AdapterBookmarks
import app.simple.felicity.databinding.DialogBookmarksListBinding
import app.simple.felicity.databinding.FragmentBookmarksBinding
import app.simple.felicity.databinding.HeaderBookmarksBinding
import app.simple.felicity.decorations.popups.SimpleDialog
import app.simple.felicity.decorations.ripple.DynamicRippleImageButton
import app.simple.felicity.decorations.typeface.TypeFaceTextView
import app.simple.felicity.decorations.utils.TextViewUtils.setStartDrawable
import app.simple.felicity.decorations.views.AppHeader
import app.simple.felicity.extensions.fragments.BasePanelFragment
import app.simple.felicity.repository.models.AudioWithBookmarks
import app.simple.felicity.viewmodels.panels.BookmarksListViewModel
import dagger.hilt.android.AndroidEntryPoint

/**
 * Panel that shows every audio track that has at least one saved bookmark.
 *
 * Tapping a song row opens a dialog listing all bookmarks for that track.
 * Tapping a bookmark in the dialog plays the song solo from that exact position.
 * Each bookmark row in the dialog has its own delete button, making it easy
 * to remove individual timestamps without clearing everything at once.
 *
 * @author Hamza417
 */
@AndroidEntryPoint
class Bookmarks : BasePanelFragment() {

    private lateinit var binding: FragmentBookmarksBinding
    private lateinit var headerBinding: HeaderBookmarksBinding

    private var adapter: AdapterBookmarks? = null

    private val viewModel: BookmarksListViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentBookmarksBinding.inflate(inflater, container, false)
        headerBinding = HeaderBookmarksBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.recyclerView.requireAttachedMiniPlayer()
        binding.appHeader.setContentView(headerBinding.root)
        binding.appHeader.attachTo(binding.recyclerView, AppHeader.ScrollMode.HIDE_ON_SCROLL)
        binding.recyclerView.setupGridLayoutManager(1)

        headerBinding.menu.setOnClickListener {
            openPreferencesPanel()
        }

        viewModel.bookmarks.collectWhenStarted { items ->
            updateList(items)
        }
    }

    override fun onDestroyView() {
        adapter = null
        super.onDestroyView()
    }

    private fun updateList(items: List<AudioWithBookmarks>) {
        headerBinding.count.text = getString(R.string.x_songs, items.size)

        if (adapter == null) {
            adapter = AdapterBookmarks(items)
            adapter!!.callbacks = object : AdapterBookmarks.Callbacks {
                override fun onSongClicked(item: AudioWithBookmarks) {
                    openBookmarksList(item)
                }
            }
            binding.recyclerView.adapter = adapter
        } else {
            adapter!!.updateItems(items)
        }
    }

    /**
     * Opens the existing bookmarks list dialog for a specific song.
     * Each row shows a timestamp and a small delete button. Tapping the timestamp
     * plays the song solo from that position and closes the dialog.
     */
    private fun openBookmarksList(item: AudioWithBookmarks) {
        val bookmarks = item.bookmarks.sortedBy { it.timestampMs }

        val onDialogInflated: (DialogBookmarksListBinding, () -> Unit, () -> Unit) -> Unit = { dialogBinding, dismiss, _ ->
            if (bookmarks.isEmpty()) {
                val empty = TypeFaceTextView(requireContext()).apply {
                    text = getString(R.string.no_bookmarks)
                    setPadding(
                            resources.getDimensionPixelSize(R.dimen.padding_15),
                            resources.getDimensionPixelSize(R.dimen.padding_10),
                            resources.getDimensionPixelSize(R.dimen.padding_15),
                            resources.getDimensionPixelSize(R.dimen.padding_10)
                    )
                }
                dialogBinding.bookmarksContainer.addView(empty)
            }

            bookmarks.forEach { bookmark ->
                val label = DateUtils.formatElapsedTime(bookmark.timestampMs / 1000L)

                // Each row is a horizontal box: timestamp on the left, delete button on the right.
                val row = android.widget.LinearLayout(requireContext()).apply {
                    orientation = android.widget.LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }

                val timestampView = TypeFaceTextView(requireContext()).apply {
                    text = label
                    setPadding(
                            resources.getDimensionPixelSize(R.dimen.padding_15),
                            resources.getDimensionPixelSize(R.dimen.padding_10),
                            resources.getDimensionPixelSize(R.dimen.padding_5),
                            resources.getDimensionPixelSize(R.dimen.padding_10)
                    )
                    layoutParams = android.widget.LinearLayout.LayoutParams(
                            0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                    )
                    setStartDrawable(R.drawable.ic_bookmark_16dp)
                    compoundDrawablePadding = resources.getDimensionPixelSize(R.dimen.padding_5)
                    setDrawableTineMode(TypeFaceTextView.DRAWABLE_ACCENT)
                    setTextColorMode(TypeFaceTextView.SECONDARY)
                    gravity = Gravity.START or Gravity.CENTER_VERTICAL

                    setOnClickListener {
                        setMediaItemWithSeek(item.audio, bookmark.timestampMs)
                        openDefaultPlayer()
                        dismiss()
                    }
                }

                val deleteButton = DynamicRippleImageButton(requireContext()).apply {
                    setImageResource(R.drawable.ic_delete_16dp)
                    val p = resources.getDimensionPixelSize(R.dimen.padding_10)
                    setPadding(p, p, p, p)
                    layoutParams = android.widget.LinearLayout.LayoutParams(
                            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                    )

                    setOnClickListener {
                        viewModel.deleteBookmark(bookmark)
                        row.visibility = View.GONE
                    }
                }

                row.addView(timestampView)
                row.addView(deleteButton)
                dialogBinding.bookmarksContainer.addView(row)
            }
        }

        SimpleDialog.Builder(
                container = requireContainerView(),
                inflateBinding = DialogBookmarksListBinding::inflate)
            .onDialogInflated(onDialogInflated)
            .onDismiss { /* no-op */ }
            .setWidthRatio(getDialogWidthRation())
            .build()
            .show()
    }

    companion object {
        const val TAG = "Bookmarks"

        fun newInstance(): Bookmarks {
            return Bookmarks().also { it.arguments = Bundle() }
        }
    }
}
