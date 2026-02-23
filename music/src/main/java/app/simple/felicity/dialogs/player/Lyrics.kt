package app.simple.felicity.dialogs.player

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.viewModels
import app.simple.felicity.databinding.DialogLyricsBinding
import app.simple.felicity.extensions.dialogs.MediaDialogFragment
import app.simple.felicity.repository.constants.BundleConstants
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.utils.ParcelUtils.parcelable
import app.simple.felicity.viewmodels.player.LyricsViewModel
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.withCreationCallback

@AndroidEntryPoint
class Lyrics : MediaDialogFragment() {

    private lateinit var binding: DialogLyricsBinding

    private val audio: Audio? by lazy {
        requireArguments().parcelable(BundleConstants.AUDIO)
    }

    private val lyricsViewModel: LyricsViewModel by viewModels(
            extrasProducer = {
                defaultViewModelCreationExtras.withCreationCallback<LyricsViewModel.Factory> {
                    it.create(audio = audio)
                }
            }
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = DialogLyricsBinding.inflate(inflater, container, false)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.title.text = audio?.title
        binding.artists.text = audio?.artist

        lyricsViewModel.getLrcData().observe(viewLifecycleOwner) { lrcData ->
            if (lrcData.isEmpty) {
                binding.lrcView.reset()
            } else {
                binding.lrcView.setLrcData(lrcData)
            }
        }
    }

    companion object {
        fun newInstance(audio: Audio): Lyrics {
            val args = Bundle()
            args.putParcelable(BundleConstants.AUDIO, audio)
            val fragment = Lyrics()
            fragment.arguments = args
            return fragment
        }

        private const val TAG = "Lyrics"

        fun FragmentManager.showLyrics(audio: Audio) {
            val dialog = newInstance(audio)
            dialog.show(this, TAG)
        }
    }
}