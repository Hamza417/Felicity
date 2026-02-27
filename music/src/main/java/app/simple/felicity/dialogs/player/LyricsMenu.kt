package app.simple.felicity.dialogs.player

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import app.simple.felicity.databinding.DialogLyricsMenuBinding
import app.simple.felicity.decorations.seekbars.FelicitySeekbar
import app.simple.felicity.extensions.dialogs.MediaBottomDialogFragment
import app.simple.felicity.preferences.LyricsPreferences

class LyricsMenu : MediaBottomDialogFragment() {

    private lateinit var binding: DialogLyricsMenuBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = DialogLyricsMenuBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.textSizeSeekbar.setProgress(LyricsPreferences.getLrcTextSize())

        when (LyricsPreferences.getLrcAlignment()) {
            LyricsPreferences.LEFT -> binding.left.isChecked = true
            LyricsPreferences.CENTER -> binding.center.isChecked = true
            LyricsPreferences.RIGHT -> binding.right.isChecked = true
        }

        binding.alignmentGroup.addOnButtonCheckedListener { _, i, bool ->
            if (bool) {
                when (i) {
                    binding.left.id -> {
                        LyricsPreferences.setLrcAlignment(LyricsPreferences.LEFT)
                    }
                    binding.center.id -> {
                        LyricsPreferences.setLrcAlignment(LyricsPreferences.CENTER)
                    }
                    binding.right.id -> {
                        LyricsPreferences.setLrcAlignment(LyricsPreferences.RIGHT)
                    }
                }
            }
        }

        binding.textSizeSeekbar.setOnSeekChangeListener(object : FelicitySeekbar.OnSeekChangeListener {
            override fun onProgressChanged(seekbar: FelicitySeekbar, progress: Float, fromUser: Boolean) {
                if (fromUser) {
                    LyricsPreferences.setLrcTextSize(progress)
                }
            }
        })
    }

    companion object {
        fun newInstance(): LyricsMenu {
            val args = Bundle()
            val fragment = LyricsMenu()
            fragment.arguments = args
            return fragment
        }

        fun FragmentManager.showLyricsMenu() {
            val dialog = newInstance()
            dialog.show(this, TAG)
        }

        private const val TAG = "LyricsMenu"
    }
}