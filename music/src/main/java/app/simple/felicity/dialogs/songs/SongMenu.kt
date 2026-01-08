package app.simple.felicity.dialogs.songs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.BundleCompat
import androidx.fragment.app.FragmentManager
import app.simple.felicity.databinding.DialogSongMenuBinding
import app.simple.felicity.extensions.dialogs.MediaDialogFragment
import app.simple.felicity.repository.constants.BundleConstants
import app.simple.felicity.repository.models.Song

class SongMenu : MediaDialogFragment() {

    private lateinit var binding: DialogSongMenuBinding
    private var songs: ArrayList<Song> = ArrayList()

    private var songId: Long = -1L

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = DialogSongMenuBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        songs = BundleCompat.getParcelableArrayList(requireArguments(), BundleConstants.SONGS, Song::class.java) ?: ArrayList()
        songId = requireArguments().getLong(BundleConstants.SONG_ID, -1L)

        binding.play.setOnClickListener {
            val position = songs.indexOfFirst { it.id == songId }.coerceAtLeast(0)
            setMediaItems(songs, position)
            dismiss()
        }
    }

    companion object {
        fun newInstance(songs: ArrayList<Song>, id: Long): SongMenu {
            val args = Bundle()
            args.putParcelableArrayList(BundleConstants.SONGS, songs)
            args.putLong(BundleConstants.SONG_ID, id)
            val fragment = SongMenu()
            fragment.arguments = args
            return fragment
        }

        fun FragmentManager.showSongMenu(songs: ArrayList<Song>, id: Long): SongMenu {
            val fragment = newInstance(songs = songs, id = id)
            fragment.show(this, TAG)
            return fragment
        }

        fun FragmentManager.showSongMenu(songs: List<Song>, id: Long): SongMenu {
            val fragment = newInstance(songs = ArrayList(songs), id = id)
            fragment.show(this, TAG)
            return fragment
        }

        private const val TAG = "SongMenu"
    }
}