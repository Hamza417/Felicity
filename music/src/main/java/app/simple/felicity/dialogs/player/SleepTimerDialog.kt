package app.simple.felicity.dialogs.player

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.transition.TransitionManager
import app.simple.felicity.R
import app.simple.felicity.databinding.DialogSleepTimerBinding
import app.simple.felicity.decorations.toggles.FelicityButtonGroup.Companion.Button
import app.simple.felicity.decorations.views.RollingTimePicker
import app.simple.felicity.engine.managers.MediaPlaybackManager
import app.simple.felicity.extensions.dialogs.ScopedBottomSheetFragment
import app.simple.felicity.preferences.PlayerPreferences
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SleepTimerDialog : ScopedBottomSheetFragment() {

    private lateinit var binding: DialogSleepTimerBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = DialogSleepTimerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        updateFinalAction()

        val isRunning = MediaPlaybackManager.isSleepTimerRunning()

        if (isRunning) {
            binding.timePicker.isEnabled = false
            // Prevents jumping issue on BottomSheetDialogFragment
            // Usually happens when the visibility of a view is changed
            TransitionManager.beginDelayedTransition(binding.startButton.parent as ViewGroup)
            binding.startButton.visibility = View.GONE
            binding.stopButton.visibility = View.VISIBLE
            binding.tillThisSon.visibility = View.GONE

            viewLifecycleOwner.lifecycleScope.launch {
                viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    var isFirstTick = true
                    MediaPlaybackManager.sleepTimerRemainingFlow.collectLatest { remaining ->
                        binding.timePicker.setTime(
                                remaining.hours, remaining.minutes, remaining.seconds, animate = !isFirstTick)
                        isFirstTick = false

                        if (!MediaPlaybackManager.isSleepTimerRunning()) {
                            if (isAdded) {
                                dismiss()
                            }
                        } else {
                            Log.d(TAG, "Sleep timer remaining: ${remaining.hours}h ${remaining.minutes}m ${remaining.seconds}s")
                        }
                    }
                }
            }
        } else {
            binding.timePicker.isEnabled = true
            // Prevents jumping issue on BottomSheetDialogFragment
            // Usually happens when the visibility of a view is changed
            TransitionManager.beginDelayedTransition(binding.stopButton.parent as ViewGroup)
            binding.stopButton.visibility = View.GONE

            binding.timePicker.setOnTimeChangeListener(object : RollingTimePicker.OnTimeChangeListener {
                override fun onTimeChanged(hours: Int, minutes: Int, seconds: Int) {
                    val targetVisibility = if (hours == 0 && minutes == 0 && seconds == 0) {
                        View.GONE
                    } else {
                        View.VISIBLE
                    }

                    if (binding.startButton.visibility != targetVisibility) {
                        TransitionManager.beginDelayedTransition(binding.startButton.parent as ViewGroup)
                        binding.startButton.visibility = targetVisibility
                    }
                }
            })

            val duration = MediaPlaybackManager.getDuration()
            val position = MediaPlaybackManager.getSeekPosition()
            val remainingMs = duration - position

            if (remainingMs > 10000L) {
                binding.tillThisSon.visibility = View.VISIBLE
            } else {
                binding.tillThisSon.visibility = View.GONE
            }

            binding.timePicker.setTime(0, 0, 0, animate = false)
            binding.startButton.visibility = View.GONE
        }

        binding.tillThisSon.setOnClickListener {
            val duration = MediaPlaybackManager.getDuration()
            val position = MediaPlaybackManager.getSeekPosition()
            val remainingMs = duration - position
            if (remainingMs > 0) {
                val totalSeconds = (remainingMs / 1000).toInt()
                val h = totalSeconds / 3600
                val m = (totalSeconds % 3600) / 60
                val s = totalSeconds % 60
                binding.timePicker.setTime(h, m, s, animate = true)

                postDelayed(500L) {
                    MediaPlaybackManager.setSleepTimer(h, m, s)
                    MediaPlaybackManager.play() // start playing too?
                    dismiss()
                }
            }
        }

        binding.stopButton.setOnClickListener {
            MediaPlaybackManager.stopSleepTimer()
            binding.timePicker.setTime(0, 0, 0, animate = true)

            postDelayed(500L) {
                if (isAdded) {
                    dismiss()
                }
            }
        }

        binding.startButton.setOnClickListener {
            MediaPlaybackManager.setSleepTimer(
                    binding.timePicker.hours,
                    binding.timePicker.minutes,
                    binding.timePicker.seconds
            )

            dismiss()
        }
    }

    private fun updateFinalAction() {
        binding.finalAction.iconSize = 14F
        binding.finalAction.setButtons(
                listOf(
                        Button(iconResId = R.drawable.ic_pause),
                        Button(iconResId = R.drawable.ic_power_off),
                )
        )

        when (PlayerPreferences.getSleepTimerAction()) {
            PlayerPreferences.SLEEP_TIMER_ACTION_PAUSE -> binding.finalAction.setSelectedIndex(0)
            PlayerPreferences.SLEEP_TIMER_ACTION_SHUTDOWN -> binding.finalAction.setSelectedIndex(1)
        }

        binding.finalAction.setOnButtonSelectedListener {
            when (it) {
                0 -> PlayerPreferences.setSleepTimerAction(PlayerPreferences.SLEEP_TIMER_ACTION_PAUSE)
                1 -> PlayerPreferences.setSleepTimerAction(PlayerPreferences.SLEEP_TIMER_ACTION_SHUTDOWN)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
    }

    companion object {
        fun newInstance(): SleepTimerDialog {
            return SleepTimerDialog()
        }

        fun FragmentManager.openSleepTimerDialog(): SleepTimerDialog {
            val dialog = newInstance()
            dialog.show(this, TAG)
            return dialog
        }

        private const val TAG = "SleepTimerDialog"
    }
}
