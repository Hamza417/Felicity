package app.simple.felicity.dialogs.lyrics

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.simple.felicity.databinding.DialogLyricsAddBinding
import app.simple.felicity.extensions.dialogs.MediaBottomDialogFragment
import app.simple.felicity.repository.constants.BundleConstants
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.utils.ParcelUtils.parcelable
import app.simple.felicity.viewmodels.dialogs.AddLyricsViewModel
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.withCreationCallback
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AddLyrics : MediaBottomDialogFragment() {

    private lateinit var binding: DialogLyricsAddBinding

    private var listener: OnLyricsCreatedListener? = null

    private val audio: Audio by lazy {
        requireArguments().parcelable(BundleConstants.AUDIO)!!
    }

    private val addLyricsViewModel: AddLyricsViewModel by viewModels(
            extrasProducer = {
                defaultViewModelCreationExtras.withCreationCallback<AddLyricsViewModel.Factory> {
                    it.create(audio = audio)
                }
            }
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = DialogLyricsAddBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.cancel.setOnClickListener {
            dismiss()
        }

        binding.save.setOnClickListener {
            val text = binding.editText.text?.toString() ?: ""
            addLyricsViewModel.saveLyrics(text)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                addLyricsViewModel.saveResult.collect { result ->
                    when (result) {
                        is AddLyricsViewModel.SaveResult.Success -> {
                            listener?.onLyricsCreated()
                            dismiss()
                        }
                        is AddLyricsViewModel.SaveResult.Error -> {
                            Toast.makeText(requireContext(), result.message, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    fun setOnLyricsCreatedListener(listener: OnLyricsCreatedListener) {
        this.listener = listener
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

        interface OnLyricsCreatedListener {
            fun onLyricsCreated()
        }
    }
}