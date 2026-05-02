package app.simple.felicity.dialogs.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.simple.felicity.databinding.DialogAudioInfoBinding
import app.simple.felicity.extensions.dialogs.ScopedBottomSheetFragment
import app.simple.felicity.repository.constants.BundleConstants
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.utils.ParcelUtils.parcelable
import app.simple.felicity.viewmodels.dialogs.AudioInformationViewModel
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.withCreationCallback
import kotlinx.coroutines.launch

/**
 * @author Hamza417
 */
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

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.info.collect { info ->
                    info ?: return@collect
                    bindInfo(info)
                }
            }
        }
    }

    /**
     * Takes the fully loaded info snapshot and stuffs each value into the
     * matching TextView in the layout.
     */
    private fun bindInfo(info: AudioInformationViewModel.AudioInfo) {
        with(binding) {
            valueTitle.text = info.title
            valuePath.text = info.path
            valueAlbum.text = info.album
            valueArtist.text = info.artist
            valueAlbumArtist.text = info.albumArtist
            valueDuration.text = info.duration
            valueSize.text = info.size
            valueBitrate.text = info.bitrate
            valueSampleRate.text = info.sampleRate
            valueBitDepth.text = info.bitDepth
            valueMimeType.text = info.mimeType
            valueFormat.text = info.format
            valueGenre.text = info.genre
            valueYear.text = info.year
            valueTrack.text = info.track
            valueTrackNumber.text = info.trackNumber
            valueNumTracks.text = info.numTracks
            valueDisc.text = info.disc
            valueComposer.text = info.composer
            valueAuthor.text = info.author
            valueWriter.text = info.writer
            valueCompilation.text = info.compilation
            valueDate.text = info.date
            valueDateAdded.text = info.dateAdded
            valueDateModified.text = info.dateModified
            valueDateTaken.text = info.dateTaken
            valueHasEmbeddedArt.text = info.hasEmbeddedArt
            valueHasLrc.text = info.hasLrc
            valueAudioId.text = info.audioId
        }
    }
}