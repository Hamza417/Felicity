package app.simple.felicity.ui.panels

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import app.simple.felicity.R
import app.simple.felicity.adapters.ui.lists.AdapterBookmarks
import app.simple.felicity.databinding.FragmentBookmarksBinding
import app.simple.felicity.databinding.HeaderBookmarksBinding
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
     * Opens the bookmarks list dialog for a specific song, with the ability to
     * delete individual bookmarks using the delete button on each row.
     */
    private fun openBookmarksList(item: AudioWithBookmarks) {
        val bookmarks = item.bookmarks.sortedBy { it.timestampMs }
        openBookmarksList(
                bookmarks = bookmarks,
                onTimestampClicked = { bookmark, dismiss ->
                    setMediaItemWithSeek(item.audio, bookmark.timestampMs)
                    openDefaultPlayer()
                    dismiss()
                },
                onDelete = { bookmark, rowView ->
                    viewModel.deleteBookmark(bookmark)
                    rowView.visibility = View.GONE
                }
        )
    }

    companion object {
        const val TAG = "Bookmarks"

        fun newInstance(): Bookmarks {
            return Bookmarks().also { it.arguments = Bundle() }
        }
    }
}
