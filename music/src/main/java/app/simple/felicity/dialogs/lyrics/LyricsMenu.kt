package app.simple.felicity.dialogs.lyrics

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import app.simple.felicity.R
import app.simple.felicity.databinding.DialogLyricsMenuBinding
import app.simple.felicity.decorations.seekbars.FelicitySeekbar
import app.simple.felicity.decorations.toggles.FelicityButtonGroup.Companion.Button
import app.simple.felicity.engine.managers.MediaPlaybackManager
import app.simple.felicity.extensions.dialogs.MediaBottomDialogFragment
import app.simple.felicity.preferences.LyricsPreferences
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.repository.utils.AudioUtils.hasLrc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale

class LyricsMenu : MediaBottomDialogFragment() {

    private lateinit var binding: DialogLyricsMenuBinding

    private var menuListener: LyricsMenuListener? = null
    private var audio: Audio? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = DialogLyricsMenuBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        updateLyricsAlignmentState()
        audio = MediaPlaybackManager.getCurrentSong()

        binding.textSizeSeekbar.setProgress(LyricsPreferences.getLrcTextSize())
        binding.textSizeSeekbar.setOnSeekChangeListener(object : FelicitySeekbar.OnSeekChangeListener {
            override fun onProgressChanged(seekbar: FelicitySeekbar, progress: Float, fromUser: Boolean) {
                if (fromUser) {
                    LyricsPreferences.setLrcTextSize(progress)
                }
            }
        })

        binding.textSizeSeekbar.setRightLabelProvider { progress, f1, f2 ->
            String.format(Locale.getDefault(), "%.1f px", progress)
        }

        binding.minus.setOnClickListener {
            menuListener?.onTimeMinusClicked()
        }

        binding.plus.setOnClickListener {
            menuListener?.onTimePlusClicked()
        }

        binding.delete.setOnClickListener {
            menuListener?.onLyricsDelete()
            dismiss()
        }

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Default) {
            val hasLrc = audio?.hasLrc() ?: false
            launch(Dispatchers.Main) {
                if (hasLrc) {
                    binding.edit.visibility = View.VISIBLE

                    binding.edit.setOnClickListener {
                        menuListener?.onLrcEdit()
                        dismiss()
                    }
                } else {
                    binding.edit.visibility = View.GONE
                }
            }
        }
    }

    fun updateLyricsAlignmentState() {
        binding.lyricsAlignmentGroup.iconSize = 14F
        binding.lyricsAlignmentGroup.setButtons(
                listOf(
                        Button(iconResId = R.drawable.ic_align_left_12dp),
                        Button(iconResId = R.drawable.ic_align_center_12dp),
                        Button(iconResId = R.drawable.ic_align_right_12dp)
                )
        )

        when (LyricsPreferences.getLrcAlignment()) {
            LyricsPreferences.LEFT -> binding.lyricsAlignmentGroup.setSelectedIndex(0)
            LyricsPreferences.CENTER -> binding.lyricsAlignmentGroup.setSelectedIndex(1)
            LyricsPreferences.RIGHT -> binding.lyricsAlignmentGroup.setSelectedIndex(2)
        }

        binding.lyricsAlignmentGroup.setOnButtonSelectedListener {
            when (it) {
                0 -> LyricsPreferences.setLrcAlignment(LyricsPreferences.LEFT)
                1 -> LyricsPreferences.setLrcAlignment(LyricsPreferences.CENTER)
                2 -> LyricsPreferences.setLrcAlignment(LyricsPreferences.RIGHT)
            }
        }
    }

    fun setOnMenuListener(listener: LyricsMenuListener): LyricsMenu {
        menuListener = listener
        return this
    }

    companion object {
        fun newInstance(): LyricsMenu {
            val args = Bundle()
            val fragment = LyricsMenu()
            fragment.arguments = args
            return fragment
        }

        fun FragmentManager.showLyricsMenu(): LyricsMenu {
            val dialog = newInstance()
            dialog.show(this, TAG)
            return dialog
        }

        private const val TAG = "LyricsMenu"

        interface LyricsMenuListener {
            fun onTimeMinusClicked()
            fun onTimePlusClicked()
            fun onLyricsDelete()
            fun onLrcEdit()
        }
    }
}

