package app.simple.felicity.dialogs.player

import android.os.Bundle
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import app.simple.felicity.databinding.DialogSleepTimerBinding
import app.simple.felicity.decorations.corners.LayoutBackground
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

        LayoutBackground.setBackground(requireContext(), binding.root, null)

        val isRunning = MediaPlaybackManager.isSleepTimerRunning()

        if (isRunning) {
            binding.timePicker.visibility = View.GONE
            binding.countdownText.visibility = View.VISIBLE
            binding.startButton.visibility = View.GONE
            binding.stopButton.visibility = View.VISIBLE

            displayTimer = object : CountDownTimer(MediaPlaybackManager.sleepTimerRemainingMs, 1000) {
                override fun onTick(millisUntilFinished: Long) {
                    val totalSeconds = millisUntilFinished / 1000
                    val h = totalSeconds / 3600
                    val m = (totalSeconds % 3600) / 60
                    val s = totalSeconds % 60
                    binding.countdownText.text = String.format(java.util.Locale.getDefault(), "%02d:%02d:%02d", h, m, s)
                }

                override fun onFinish() {
                    dismiss()
                }
            }.start()
        } else {
            binding.timePicker.visibility = View.VISIBLE
            binding.countdownText.visibility = View.GONE
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
