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
import app.simple.felicity.databinding.DialogPlaybackInfoBinding
import app.simple.felicity.extensions.dialogs.ScopedBottomSheetFragment
import app.simple.felicity.repository.constants.BundleConstants
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.utils.DateUtils.toDate
import app.simple.felicity.utils.ParcelUtils.parcelable
import app.simple.felicity.viewmodels.dialogs.PlaybackInfoViewModel
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.withCreationCallback
import kotlinx.coroutines.launch

/**
 * A bottom-sheet dialog that shows how a specific song has been interacted with —
 * how many times it was played, how many times it was skipped, and when it was
 * last played. There's also a handy "clear" button if you want to wipe the slate
 * clean and pretend the song is brand new.
 *
 * @author Hamza417
 */
@AndroidEntryPoint
class PlaybackInfo : ScopedBottomSheetFragment() {

    private lateinit var binding: DialogPlaybackInfoBinding

    private val audio: Audio by lazy {
        requireArguments().parcelable(BundleConstants.AUDIO)
            ?: throw IllegalArgumentException("Audio is required")
    }

    private val viewModel by viewModels<PlaybackInfoViewModel>(
            extrasProducer = {
                defaultViewModelCreationExtras.withCreationCallback<PlaybackInfoViewModel.Factory> {
                    it.create(audio = audio)
                }
            }
    )

    companion object {
        private const val TAG = "PlaybackInfo"

        fun newInstance(audio: Audio): PlaybackInfo {
            val args = Bundle()
            args.putParcelable(BundleConstants.AUDIO, audio)
            val fragment = PlaybackInfo()
            fragment.arguments = args
            return fragment
        }

        fun FragmentManager.showPlaybackInfo(audio: Audio) {
            val dialog = newInstance(audio)
            dialog.show(this, TAG)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = DialogPlaybackInfoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.title.text = audio.title

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.stat.collect { stat ->
                    if (stat != null) {
                        // Fill in the stats we fetched from the database
                        binding.playCount.text = stat.playCount.toString()
                        binding.skipCount.text = stat.skipCount.toString()
                        binding.lastPlayed.text = if (stat.lastPlayed > 0) {
                            stat.lastPlayed.toDate()
                        } else {
                            getString(app.simple.felicity.R.string.never_played)
                        }
                    } else {
                        // If there are no stats yet (or they were just cleared), show zeroes
                        binding.playCount.text = 0.toString()
                        binding.skipCount.text = 0.toString()
                        binding.lastPlayed.text = getString(app.simple.felicity.R.string.never_played)
                    }
                }
            }
        }

        // Tapping the cross button clears all stats and resets the display
        binding.clearStats.setOnClickListener {
            viewModel.clearStats()
        }
    }
}

