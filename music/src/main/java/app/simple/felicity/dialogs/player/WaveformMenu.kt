package app.simple.felicity.dialogs.player

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import app.simple.felicity.R
import app.simple.felicity.databinding.DialogWaveformMenuBinding
import app.simple.felicity.decorations.seekbars.FelicitySeekbar
import app.simple.felicity.decorations.toggles.FelicityButtonGroup.Companion.Button
import app.simple.felicity.extensions.dialogs.MediaBottomDialogFragment
import app.simple.felicity.preferences.UserInterfacePreferences

class WaveformMenu : MediaBottomDialogFragment() {

    private lateinit var binding: DialogWaveformMenuBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = DialogWaveformMenuBinding.inflate(inflater, container, false)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.stackMediaControlsSwitch.isChecked = UserInterfacePreferences.isStackMediaControls()
        binding.opticsSeekbar.setProgress(UserInterfacePreferences.getWaveformOptics() * WAVEFORM_COMPENSATION)
        setTimerPosition()
        setWaveformMode()

        binding.stackMediaControlsSwitch.setOnCheckedChangeListener { _, isChecked ->
            UserInterfacePreferences.setStackMediaControls(isChecked)
            if (isChecked) {
                if (UserInterfacePreferences.getTimerPosition() == UserInterfacePreferences.TIMER_POSITION_CENTER) {
                    UserInterfacePreferences.setTimerPosition(UserInterfacePreferences.TIMER_POSITION_TOP)
                }
            }
        }

        binding.opticsSeekbar.setOnSeekChangeListener(object : FelicitySeekbar.OnSeekChangeListener {
            override fun onProgressChanged(seekbar: FelicitySeekbar, progress: Float, fromUser: Boolean) {
                UserInterfacePreferences.setWaveformOptics(progress / WAVEFORM_COMPENSATION)
            }
        })
    }

    private fun setTimerPosition() {
        binding.timerGravityButtonGroup.setButtons(
                if (UserInterfacePreferences.isStackMediaControls()) {
                    listOf(
                            Button(iconResId = R.drawable.ic_vertical_align_top),
                            Button(iconResId = R.drawable.ic_vertical_align_bottom)
                    )
                } else {
                    listOf(
                            Button(iconResId = R.drawable.ic_vertical_align_top),
                            Button(iconResId = R.drawable.ic_vertical_align_center),
                            Button(iconResId = R.drawable.ic_vertical_align_bottom)
                    )
                }
        )

        if (UserInterfacePreferences.isStackMediaControls()) {
            when (UserInterfacePreferences.getTimerPosition()) {
                UserInterfacePreferences.TIMER_POSITION_TOP -> binding.timerGravityButtonGroup.setSelectedIndex(0)
                UserInterfacePreferences.TIMER_POSITION_BOTTOM -> binding.timerGravityButtonGroup.setSelectedIndex(1)
            }
        } else {
            when (UserInterfacePreferences.getTimerPosition()) {
                UserInterfacePreferences.TIMER_POSITION_TOP -> binding.timerGravityButtonGroup.setSelectedIndex(0)
                UserInterfacePreferences.TIMER_POSITION_CENTER -> binding.timerGravityButtonGroup.setSelectedIndex(1)
                UserInterfacePreferences.TIMER_POSITION_BOTTOM -> binding.timerGravityButtonGroup.setSelectedIndex(2)
            }
        }

        binding.timerGravityButtonGroup.setOnButtonSelectedListener {
            if (UserInterfacePreferences.isStackMediaControls()) {
                when (it) {
                    0 -> UserInterfacePreferences.setTimerPosition(UserInterfacePreferences.TIMER_POSITION_TOP)
                    1 -> UserInterfacePreferences.setTimerPosition(UserInterfacePreferences.TIMER_POSITION_BOTTOM)
                }
            } else {
                when (it) {
                    0 -> UserInterfacePreferences.setTimerPosition(UserInterfacePreferences.TIMER_POSITION_TOP)
                    1 -> UserInterfacePreferences.setTimerPosition(UserInterfacePreferences.TIMER_POSITION_CENTER)
                    2 -> UserInterfacePreferences.setTimerPosition(UserInterfacePreferences.TIMER_POSITION_BOTTOM)
                }
            }
        }
    }

    private fun setWaveformMode() {
        binding.waveformModeButtonGroup.setButtons(
                listOf(
                        Button(iconResId = R.drawable.ic_close_fullscreen),
                        Button(iconResId = R.drawable.ic_swipe)
                )
        )

        binding.waveformModeButtonGroup.setSelectedIndex(
                if (UserInterfacePreferences.isWaveformFullMode()) 0 else 1
        )

        binding.waveformModeButtonGroup.setOnButtonSelectedListener {
            when (it) {
                0 -> UserInterfacePreferences.setWaveformFullMode(true)
                1 -> UserInterfacePreferences.setWaveformFullMode(false)
            }
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        super.onSharedPreferenceChanged(sharedPreferences, key)
        when (key) {
            UserInterfacePreferences.STACK_MEDIA_CONTROLS -> setTimerPosition()
            UserInterfacePreferences.TIMER_POSITION -> setTimerPosition()
        }
    }

    companion object {
        fun newInstance(): WaveformMenu {
            val args = Bundle()
            val fragment = WaveformMenu()
            fragment.arguments = args
            return fragment
        }

        fun FragmentManager.showWaveformMenu() {
            val dialog = newInstance()
            dialog.show(this, TAG)
        }

        private const val WAVEFORM_COMPENSATION = 100F

        const val TAG = "WaveformMenu"
    }
}