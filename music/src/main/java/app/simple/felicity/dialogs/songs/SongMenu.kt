package app.simple.felicity.dialogs.songs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import app.simple.felicity.databinding.DialogSongMenuBinding
import app.simple.felicity.extensions.fragments.ScopedBottomSheetFragment
import app.simple.felicity.repository.constants.BundleConstants
import app.simple.felicity.repository.models.Song

class SongMenu : ScopedBottomSheetFragment() {

    private lateinit var binding: DialogSongMenuBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = DialogSongMenuBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
    }

    companion object {
        fun newInstance(song: Song): SongMenu {
            val args = Bundle()
            args.putParcelable(BundleConstants.SONG, song)
            val fragment = SongMenu()
            fragment.arguments = args
            return fragment
        }

        fun FragmentManager.showSongMenu(song: Song): SongMenu {
            val fragment = newInstance(song = song)
            fragment.show(this, TAG)
            return fragment
        }

        private const val TAG = "SongMenu"
    }
}