package app.simple.felicity.dialogs.app

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import app.simple.felicity.adapters.dialogs.AdapterAudioInformation
import app.simple.felicity.databinding.DialogAudioInfoBinding
import app.simple.felicity.extensions.dialogs.ScopedBottomSheetFragment
import app.simple.felicity.repository.constants.BundleConstants
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.utils.ParcelUtils.parcelable
import app.simple.felicity.viewmodels.dialogs.AudioInformationViewModel
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.withCreationCallback
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AudioInformation : ScopedBottomSheetFragment() {

    private lateinit var binding: DialogAudioInfoBinding

    private val audio: Audio by lazy {
        requireArguments().parcelable(BundleConstants.AUDIO)
            ?: throw IllegalArgumentException("Audio is required")
    }

    private val viewModel by viewModels<AudioInformationViewModel>(
            extrasProducer = {
                defaultViewModelCreationExtras.withCreationCallback<AudioInformationViewModel.Factory> {
                    it.create(audio = audio)
                }
            }
    )

    companion object {
        private const val TAG = "AudioInformation"
        private const val SPAN_COUNT = 2

        fun newInstance(audio: Audio): AudioInformation {
            val args = Bundle()
            args.putParcelable(BundleConstants.AUDIO, audio)
            val fragment = AudioInformation()
            fragment.arguments = args
            return fragment
        }

        fun FragmentManager.showAudioInfo(audio: Audio) {
            val dialog = newInstance(audio)
            dialog.show(this, TAG)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = DialogAudioInfoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val gridLayoutManager = GridLayoutManager(requireContext(), SPAN_COUNT)
        binding.recyclerView.layoutManager = gridLayoutManager

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.info.collect { data ->
                    if (data.isNotEmpty()) {
                        val adapter = AdapterAudioInformation(data)
                        gridLayoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                            override fun getSpanSize(position: Int): Int {
                                return adapter.getSpanSize(position, SPAN_COUNT)
                            }
                        }
                        binding.recyclerView.adapter = adapter
                    } else {
                        Log.w(TAG, "No information to display for audio: ${audio.path}")
                    }
                }
            }
        }
    }
}