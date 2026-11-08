package app.simple.felicity.dialogs.player

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.simple.felicity.databinding.DialogSleepTimerBinding
import app.simple.felicity.engine.managers.MediaPlaybackManager
import app.simple.felicity.extensions.dialogs.ScopedBottomSheetFragment
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

        val isRunning = MediaPlaybackManager.isSleepTimerRunning()

        if (isRunning) {
            binding.timePicker.isEnabled = false
            binding.startButton.visibility = View.GONE
            binding.stopButton.visibility = View.VISIBLE

            viewLifecycleOwner.lifecycleScope.launch {
                viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    var isFirstTick = true
                    MediaPlaybackManager.sleepTimerRemainingFlow.collectLatest { remaining ->
                        binding.timePicker.setTime(
                                remaining.hours, remaining.minutes, remaining.seconds, animate = !isFirstTick)
                        isFirstTick = false
                        if (!MediaPlaybackManager.isSleepTimerRunning()) {
                            dismiss()
                        }
                    }
                }
            }
        } else {
            binding.timePicker.isEnabled = true
            binding.startButton.visibility = View.VISIBLE
            binding.stopButton.visibility = View.GONE

            binding.timePicker.setTime(0, 0, 0, animate = false)
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
                binding.tillThisSon.postDelayed({
                                                    if (isAdded) {
                                                        MediaPlaybackManager.setSleepTimer(h, m, s)
                                                        MediaPlaybackManager.play() // start playing too?
                                                        dismiss()
                                                    }
                                                }, 500) // Delay to let the animation play
            }
        }

        binding.cancelButton.setOnClickListener {
            dismiss()
        }

        binding.stopButton.setOnClickListener {
            MediaPlaybackManager.stopSleepTimer()
            dismiss()
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
