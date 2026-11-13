package app.simple.felicity.dialogs.player

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import app.simple.felicity.databinding.DialogEqInputBinding
import app.simple.felicity.decorations.equalizer.FelicityEqualizer
import app.simple.felicity.extensions.dialogs.ScopedDialogFragment
import app.simple.felicity.shared.utils.ViewUtils.shakeView

class EqualizerBandInputDialog : ScopedDialogFragment() {

    private lateinit var binding: DialogEqInputBinding

    private var isPeqMode: Boolean = false
    private var currentGain: Float = 0f
    private var currentFreq: Float = 0f
    private var currentQ: Float = 0f

    var onGainSave: ((Float) -> Unit)? = null
    var onPeqSave: ((Float, Float, Float) -> Unit)? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = DialogEqInputBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        requireArguments().let {
            isPeqMode = it.getBoolean(ARG_IS_PEQ_MODE, false)
            currentGain = it.getFloat(ARG_GAIN, 0f)
            currentFreq = it.getFloat(ARG_FREQ, 0f)
            currentQ = it.getFloat(ARG_Q, 0f)
        }

        if (isPeqMode) {
            binding.inputFreq.visibility = View.VISIBLE
            binding.inputQ.visibility = View.VISIBLE
            binding.inputFreq.setText(currentFreq.toString())
            binding.inputQ.setText(currentQ.toString())
        } else {
            binding.inputFreq.visibility = View.GONE
            binding.inputQ.visibility = View.GONE
        }

        binding.inputGain.setText(currentGain.toString())
        binding.inputGain.setSelection(binding.inputGain.text?.length ?: 0)
        binding.inputGain.requestFocus()
        binding.inputGain.showInput()

        binding.cancel.setOnClickListener {
            dismissAllowingStateLoss()
        }

        binding.save.setOnClickListener {
            val newGain = binding.inputGain.text.toString().toFloatOrNull()

            if (isPeqMode) {
                val newFreq = binding.inputFreq.text.toString().toFloatOrNull()
                val newQ = binding.inputQ.text.toString().toFloatOrNull()

                if (newGain != null && newFreq != null && newQ != null &&
                        newGain in FelicityEqualizer.MIN_DB..FelicityEqualizer.MAX_DB &&
                        newFreq in FelicityEqualizer.PEQ_FREQ_MIN_HZ..FelicityEqualizer.PEQ_FREQ_MAX_HZ &&
                        newQ in FelicityEqualizer.PEQ_Q_MIN..FelicityEqualizer.PEQ_Q_MAX) {
                    onPeqSave?.invoke(newGain, newFreq, newQ)
                    dismissAllowingStateLoss()
                } else {
                    binding.inputGain.shakeView()
                }
            } else {
                if (newGain != null && newGain in FelicityEqualizer.MIN_DB..FelicityEqualizer.MAX_DB) {
                    onGainSave?.invoke(newGain)
                    dismissAllowingStateLoss()
                } else {
                    binding.inputGain.shakeView()
                }
            }
        }
    }

    companion object {
        const val TAG = "EqualizerBandInputDialog"

        private const val ARG_IS_PEQ_MODE = "arg_is_peq_mode"
        private const val ARG_GAIN = "arg_gain"
        private const val ARG_FREQ = "arg_freq"
        private const val ARG_Q = "arg_q"

        fun newInstance(isPeqMode: Boolean, gain: Float, freq: Float, q: Float): EqualizerBandInputDialog {
            return EqualizerBandInputDialog().apply {
                arguments = Bundle().apply {
                    putBoolean(ARG_IS_PEQ_MODE, isPeqMode)
                    putFloat(ARG_GAIN, gain)
                    putFloat(ARG_FREQ, freq)
                    putFloat(ARG_Q, q)
                }
            }
        }

        fun FragmentManager.showGraphicEqInputDialog(gain: Float, onSave: (Float) -> Unit) {
            val fragment = newInstance(false, gain, 0f, 0f)
            fragment.onGainSave = onSave
            fragment.show(this, TAG)
        }

        fun FragmentManager.showPeqInputDialog(gain: Float, freq: Float, q: Float, onSave: (Float, Float, Float) -> Unit) {
            val fragment = newInstance(true, gain, freq, q)
            fragment.onPeqSave = onSave
            fragment.show(this, TAG)
        }
    }
}
