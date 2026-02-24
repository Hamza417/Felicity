package app.simple.felicity.dialogs.lyrics

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import app.simple.felicity.databinding.DialogLyricsAddBinding
import app.simple.felicity.extensions.dialogs.MediaDialogFragment
import app.simple.felicity.repository.constants.BundleConstants
import app.simple.felicity.repository.models.Audio

class AddLyrics : MediaDialogFragment() {

    private lateinit var binding: DialogLyricsAddBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = DialogLyricsAddBinding.inflate(inflater, container, false)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.cancel.setOnClickListener {
            dismiss()
        }
    }

    companion object {
        fun newInstance(audio: Audio): AddLyrics {
            val args = Bundle()
            args.putParcelable(BundleConstants.AUDIO, audio)
            val fragment = AddLyrics()
            fragment.arguments = args
            return fragment
        }

        private const val TAG = "AddLyrics"

        fun FragmentManager.showAddLyrics(audio: Audio): AddLyrics {
            val dialog = newInstance(audio)
            dialog.show(this, TAG)
            return dialog
        }
    }

}