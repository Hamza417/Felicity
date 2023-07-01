package app.simple.felicity.ui.player

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.simple.felicity.adapters.ui.DefaultPlayerAdapter
import app.simple.felicity.databinding.FragmentDefaultPlayerBinding
import app.simple.felicity.extensions.fragments.ScopedFragment
import app.simple.felicity.models.Audio
import app.simple.felicity.preferences.MusicPreferences
import app.simple.felicity.services.AudioService
import app.simple.felicity.viewmodels.ui.PlayerViewModel
import kotlinx.coroutines.launch

class DefaultPlayer : ScopedFragment() {

    private lateinit var binding: FragmentDefaultPlayerBinding
    private lateinit var playerViewModel: PlayerViewModel

    private var audioService: AudioService? = null
    private var serviceConnection: ServiceConnection? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentDefaultPlayerBinding.inflate(inflater, container, false)
        playerViewModel = ViewModelProvider(requireActivity())[PlayerViewModel::class.java]
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        startPostponedEnterTransition()

        serviceConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                audioService = (service as AudioService.AudioBinder).getService()
                Log.d("AudioService", "Connected")
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                audioService = null
            }
        }

        playerViewModel.getSongs().observe(viewLifecycleOwner) { list ->
            binding.artSlider.setSliderAdapter(DefaultPlayerAdapter(list))
            binding.artSlider.currentPagePosition = MusicPreferences.getMusicPosition()
            binding.artSlider.isAutoCycle = false
            setData(list[MusicPreferences.getMusicPosition()])

            binding.artSlider.setCurrentPageListener {
                MusicPreferences.setMusicPosition(it)
                MusicPreferences.setLastMusicId(list[it].id)
                audioService?.setCurrentPosition(it)
                setData(list[it])
            }

            lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.RESUMED) {
                    requireContext().bindService(
                            AudioService.getIntent(requireContext()),
                            serviceConnection!!, Context.BIND_AUTO_CREATE)
                }
            }
        }
    }

    private fun setData(audio: Audio) {
        binding.title.text = audio.title
        binding.artist.text = audio.artist
        binding.album.text = audio.album
        binding.info.text = buildString {
            append(".")
            append(audio.path.substringAfterLast("."))
            append(" | ")
            append(audio.bitrate)
            append(" | ")
            append(audio.mimeType)
        }
    }

    companion object {
        fun newInstance(): DefaultPlayer {
            val args = Bundle()
            val fragment = DefaultPlayer()
            fragment.arguments = args
            return fragment
        }
    }
}