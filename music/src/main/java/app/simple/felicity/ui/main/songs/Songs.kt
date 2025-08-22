package app.simple.felicity.ui.main.songs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import app.simple.felicity.adapters.ui.lists.songs.SongsAdapter
import app.simple.felicity.callbacks.GeneralAdapterCallbacks
import app.simple.felicity.core.R
import app.simple.felicity.core.utils.TimeUtils.toHighlightedTimeString
import app.simple.felicity.databinding.AdapterSongHeaderBinding
import app.simple.felicity.databinding.FragmentSongsBinding
import app.simple.felicity.decorations.fastscroll.FelicityFastScroller
import app.simple.felicity.decorations.fastscroll.SectionedFastScroller
import app.simple.felicity.decorations.itemanimators.FlipItemAnimator
import app.simple.felicity.decorations.itemdecorations.SpacingItemDecoration
import app.simple.felicity.decorations.views.AppHeader
import app.simple.felicity.dialogs.songs.SongMenu.Companion.showSongMenu
import app.simple.felicity.dialogs.songs.SongsMenu.Companion.showSongsMenu
import app.simple.felicity.dialogs.songs.SongsSort.Companion.showSongsSort
import app.simple.felicity.extensions.fragments.MediaFragment
import app.simple.felicity.preferences.SongsPreferences
import app.simple.felicity.repository.models.Song
import app.simple.felicity.theme.managers.ThemeManager
import app.simple.felicity.viewmodels.main.songs.SongsViewModel

class Songs : MediaFragment() {

    private lateinit var binding: FragmentSongsBinding
    private lateinit var headerBinding: AdapterSongHeaderBinding

    private var songsAdapter: SongsAdapter? = null

    private val songsViewModel: SongsViewModel by viewModels({ requireActivity() })

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentSongsBinding.inflate(inflater, container, false)
        headerBinding = AdapterSongHeaderBinding.inflate(inflater, binding.recyclerView, false)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.recyclerView.addItemDecoration(SpacingItemDecoration(48))
        binding.recyclerView.itemAnimator = FlipItemAnimator()
        binding.appHeader.setContentView(headerBinding.root)
        binding.appHeader.attachTo(binding.recyclerView, AppHeader.ScrollMode.HIDE_ON_SCROLL)
        SectionedFastScroller.attach(binding.recyclerView)

        songsViewModel.getSongs().observe(viewLifecycleOwner) {
            val nav = FelicityFastScroller.attach(binding.recyclerView)
            nav.setPositions(provideScrollPositionDataBasedOnSortStyle(it))

            nav.setOnPositionSelectedListener { position ->
                binding.recyclerView.scrollToPosition(position.index)
                if (position.index > 10) {
                    binding.appHeader.hideHeader()
                    binding.appHeader.resumeAutoBehavior()
                } else {
                    binding.appHeader.showHeader()
                    binding.appHeader.resumeAutoBehavior()
                }
            }

            songsAdapter = SongsAdapter(it)
            binding.recyclerView.adapter = songsAdapter

            songsAdapter?.setGeneralAdapterCallbacks(object : GeneralAdapterCallbacks {
                override fun onSongClicked(songs: List<Song>, position: Int, view: View?) {
                    setMediaItems(songs, position)
                }

                override fun onSongLongClicked(songs: List<Song>, position: Int, view: View?) {
                    childFragmentManager.showSongMenu(songs[position])
                }
            })

            headerBinding.sortStyle.text = when (SongsPreferences.getSongSort()) {
                SongsPreferences.BY_TITLE -> binding.root.context.getString(R.string.title)
                SongsPreferences.BY_ARTIST -> binding.root.context.getString(R.string.artist)
                SongsPreferences.BY_ALBUM -> binding.root.context.getString(R.string.album)
                SongsPreferences.PATH -> binding.root.context.getString(R.string.path)
                SongsPreferences.BY_DATE_ADDED -> binding.root.context.getString(R.string.date_added)
                SongsPreferences.BY_DATE_MODIFIED -> binding.root.context.getString(R.string.date_added)
                SongsPreferences.BY_DURATION -> binding.root.context.getString(R.string.duration)
                SongsPreferences.BY_YEAR -> binding.root.context.getString(R.string.year)
                SongsPreferences.BY_TRACK_NUMBER -> binding.root.context.getString(R.string.track_number)
                SongsPreferences.BY_COMPOSER -> binding.root.context.getString(R.string.composer)
                else -> binding.root.context.getString(R.string.unknown)
            }

            headerBinding.count.text = getString(R.string.x_songs, it.size)

            headerBinding.sortOrder.text = when (SongsPreferences.getSortingStyle()) {
                SongsPreferences.ACCENDING -> binding.root.context.getString(R.string.normal)
                SongsPreferences.DESCENDING -> binding.root.context.getString(R.string.reversed)
                else -> binding.root.context.getString(R.string.unknown)
            }

            headerBinding.hours.text = it.sumOf { it.duration }.toHighlightedTimeString(ThemeManager.theme.textViewTheme.tertiaryTextColor)

            headerBinding.menu.setOnClickListener {
                childFragmentManager.showSongsMenu()
            }

            headerBinding.sortStyle.setOnClickListener {
                childFragmentManager.showSongsSort()
            }

            headerBinding.sortOrder.setOnClickListener {
                childFragmentManager.showSongsSort()
            }

            headerBinding.scroll.setOnClickListener {
                nav.show()
            }
        }
    }

    override fun onSong(song: Song) {
        super.onSong(song)
        songsAdapter?.currentlyPlayingSong = song
    }

    private fun provideScrollPositionDataBasedOnSortStyle(songs: List<Song>): List<FelicityFastScroller.Position> {
        return when (SongsPreferences.getSongSort()) {
            SongsPreferences.BY_TITLE -> {
                val firstAlphabetToIndex = linkedMapOf<String, Int>()
                songs.forEachIndexed { index, song ->
                    val firstChar = song.title?.firstOrNull()?.uppercaseChar()
                    val key = if (firstChar != null && firstChar.isLetter()) {
                        firstChar.toString()
                    } else {
                        "#"
                    }
                    if (!firstAlphabetToIndex.containsKey(key)) {
                        firstAlphabetToIndex[key] = index
                    }
                }
                firstAlphabetToIndex.map { (char, index) ->
                    FelicityFastScroller.Position(char, index)
                }
            }
            SongsPreferences.BY_ARTIST -> {
                val firstAlphabetToIndex = linkedMapOf<Char, Int>()
                songs.forEachIndexed { index, song ->
                    song.artist?.firstOrNull()?.uppercaseChar()?.let { firstChar ->
                        if (firstChar.isLetter() && !firstAlphabetToIndex.containsKey(firstChar)) {
                            firstAlphabetToIndex[firstChar] = index
                        }
                    }
                }
                firstAlphabetToIndex.map { (char, index) ->
                    FelicityFastScroller.Position(char.toString(), index)
                }
            }
            SongsPreferences.BY_ALBUM -> {
                val firstAlphabetToIndex = linkedMapOf<Char, Int>()
                songs.forEachIndexed { index, song ->
                    song.album?.firstOrNull()?.uppercaseChar()?.let { firstChar ->
                        if (firstChar.isLetter() && !firstAlphabetToIndex.containsKey(firstChar)) {
                            firstAlphabetToIndex[firstChar] = index
                        }
                    }
                }
                firstAlphabetToIndex.map { (char, index) ->
                    FelicityFastScroller.Position(char.toString(), index)
                }
            }
            SongsPreferences.BY_YEAR -> {
                val firstAlphabetToIndex = linkedMapOf<String, Int>()
                songs.forEachIndexed { index, song ->
                    val key = song.year?.toString()?.takeIf { it.all { ch -> ch.isDigit() } } ?: "#"
                    if (!firstAlphabetToIndex.containsKey(key)) {
                        firstAlphabetToIndex[key] = index
                    }
                }
                firstAlphabetToIndex.map { (year, index) ->
                    FelicityFastScroller.Position(year, index)
                }
            }
            else -> {
                val firstAlphabetToIndex = linkedMapOf<Char, Int>()
                songs.forEachIndexed { index, song ->
                    song.title?.firstOrNull()?.uppercaseChar()?.let { firstChar ->
                        if (firstChar.isLetter() && !firstAlphabetToIndex.containsKey(firstChar)) {
                            firstAlphabetToIndex[firstChar] = index
                        }
                    }
                }
                firstAlphabetToIndex.map { (char, index) ->
                    FelicityFastScroller.Position(char.toString(), index)
                }
            }
        }
    }

    companion object {
        const val TAG = "Songs"

        fun newInstance(): Songs {
            val args = Bundle()
            val fragment = Songs()
            fragment.arguments = args
            return fragment
        }
    }
}
