package app.simple.felicity.dialogs.queue

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import app.simple.felicity.databinding.DialogMainAppLabelBinding
import app.simple.felicity.extensions.dialogs.ScopedBottomSheetFragment

class QueueLabel : ScopedBottomSheetFragment() {

    private lateinit var binding: DialogMainAppLabelBinding
    private lateinit var callbacks: QueueLabelCallbacks

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = DialogMainAppLabelBinding.inflate(inflater, container, false)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.editText.setText(requireArguments().getString(ARG_CURRENT_LABEL, ""))

        binding.save.setOnClickListener {
            val label = binding.editText.text.toString()
            callbacks.onQueueLabelChanged(label)
            dismiss()
        }
    }

    fun setOnQueueLabelChangedListener(callbacks: QueueLabelCallbacks) {
        this.callbacks = callbacks
    }

    companion object {
        fun newInstance(currentLabel: String): QueueLabel {
            val args = Bundle()
            args.putString(ARG_CURRENT_LABEL, currentLabel)
            val fragment = QueueLabel()
            fragment.arguments = args
            return fragment
        }

        fun FragmentManager.openQueueLabel(currentLabel: String): QueueLabel {
            val dialog = newInstance(currentLabel = currentLabel)
            dialog.show(this, TAG)
            return dialog
        }

        interface QueueLabelCallbacks {
            fun onQueueLabelChanged(label: String)
        }

        const val TAG = "QueueLabel"
        private const val ARG_CURRENT_LABEL = "currentLabel"
    }
}