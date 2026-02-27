package app.simple.felicity.dialogs.lyrics

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.viewModels
import app.simple.felicity.databinding.DialogLyricsBinding
import app.simple.felicity.decorations.lrc.view.ModernLrcView
import app.simple.felicity.dialogs.lyrics.AddLyrics.Companion.showAddLyrics
import app.simple.felicity.extensions.dialogs.MediaBottomDialogFragment
import app.simple.felicity.repository.constants.BundleConstants
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.shared.utils.ViewUtils.gone
import app.simple.felicity.shared.utils.ViewUtils.visible
import app.simple.felicity.utils.ParcelUtils.parcelable
import app.simple.felicity.viewmodels.player.LyricsViewModel
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.withCreationCallback

@AndroidEntryPoint
class Lyrics : MediaBottomDialogFragment(), AddLyrics.Companion.OnLyricsCreatedListener {

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

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = DialogLyricsBinding.inflate(inflater, container, false)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Allow the BottomSheet to take over (dismiss) once the user scrolls past the end of lyrics
        binding.lrcView.setParentDismissEnabled(true)
        binding.lrcView.setFadeLength(ModernLrcView.DEFAULT_FADE_LENGTH.div(2))

        binding.title.text = audio?.title
        binding.artists.text = audio?.artist

        lyricsViewModel.getLrcData().observe(viewLifecycleOwner) { lrcData ->
            if (lrcData.isEmpty) {
                binding.lrcView.reset()
                binding.addLyrics.visible(animate = true)
            } else {
                binding.lrcView.setLrcData(lrcData)
                binding.addLyrics.gone(animate = true)
            }
        }

        binding.addLyrics.setOnClickListener {
            audio?.let {
                childFragmentManager.showAddLyrics(audio!!)
                    .setOnLyricsCreatedListener(this)
            }
        }
    }

    override fun onLyricsCreated() {
        // Reload the lyrics view after the sidecar file has been created
        lyricsViewModel.reloadLrcData()
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