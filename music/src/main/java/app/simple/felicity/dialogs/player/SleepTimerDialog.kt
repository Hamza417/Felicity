package app.simple.felicity.dialogs.player

import android.os.Bundle
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import app.simple.felicity.databinding.DialogSleepTimerBinding
import app.simple.felicity.engine.managers.MediaPlaybackManager
import app.simple.felicity.extensions.dialogs.ScopedBottomSheetFragment

class SleepTimerDialog : ScopedBottomSheetFragment() {

    private lateinit var binding: DialogSleepTimerBinding

    var onTimerSet: ((hours: Int, minutes: Int, seconds: Int) -> Unit)? = null
    var onTimerStop: (() -> Unit)? = null

    private var displayTimer: CountDownTimer? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = DialogSleepTimerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val isRunning = MediaPlaybackManager.isSleepTimerRunning()

        if (isRunning) {
            binding.timePicker.isEnabled = false
            binding.startButton.visibility = View.GONE
            binding.stopButton.visibility = View.VISIBLE

            displayTimer = object : CountDownTimer(MediaPlaybackManager.sleepTimerRemainingMs, 1000) {
                override fun onTick(millisUntilFinished: Long) {
                    val totalSeconds = millisUntilFinished / 1000
                    val h = (totalSeconds / 3600).toInt()
                    val m = ((totalSeconds % 3600) / 60).toInt()
                    val s = (totalSeconds % 60).toInt()
                    binding.timePicker.hours = h
                    binding.timePicker.minutes = m
                    binding.timePicker.seconds = s
                }

                override fun onFinish() {
                    dismiss()
                }
            }.start()
        } else {
            binding.timePicker.isEnabled = true
            binding.startButton.visibility = View.VISIBLE
            binding.stopButton.visibility = View.GONE

            binding.timePicker.apply {
                hours = 0
                minutes = 0
                seconds = 0
            }
        }

        binding.cancelButton.setOnClickListener {
            dismiss()
        }

        binding.stopButton.setOnClickListener {
            onTimerStop?.invoke()
            dismiss()
        }

        binding.startButton.setOnClickListener {
            onTimerSet?.invoke(
                    binding.timePicker.hours,
                    binding.timePicker.minutes,
                    binding.timePicker.seconds
            )
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        displayTimer?.cancel()
        displayTimer = null
    }

    companion object {
        fun newInstance(): SleepTimerDialog {
            return SleepTimerDialog()
        }
    }
}
