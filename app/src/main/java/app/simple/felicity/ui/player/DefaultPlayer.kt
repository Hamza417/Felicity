package app.simple.felicity.ui.player

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
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
import app.simple.felicity.preferences.MusicPreferences
import app.simple.felicity.services.AudioService
import app.simple.felicity.viewmodels.ui.PlayerViewModel
import kotlinx.coroutines.launch

class DefaultPlayer : ScopedFragment() {

    private lateinit var defaultPlayerBinding: FragmentDefaultPlayerBinding
    private lateinit var playerViewModel: PlayerViewModel

    private var audioService: AudioService? = null
    private var serviceConnection: ServiceConnection? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        defaultPlayerBinding = FragmentDefaultPlayerBinding.inflate(inflater, container, false)
        playerViewModel = ViewModelProvider(requireActivity())[PlayerViewModel::class.java]
        return defaultPlayerBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        startPostponedEnterTransition()

        playerViewModel.getSongs().observe(viewLifecycleOwner) { list ->
            defaultPlayerBinding.artSlider.setSliderAdapter(DefaultPlayerAdapter(list))
            defaultPlayerBinding.artSlider.currentPagePosition = MusicPreferences.getMusicPosition()

            defaultPlayerBinding.artSlider.setCurrentPageListener {
                MusicPreferences.setMusicPosition(it)
                audioService?.setCurrentPosition(it)
            }
        }

        serviceConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                audioService = (service as AudioService.AudioBinder).getService()
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                audioService = null
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                requireContext().bindService(
                        AudioService.getIntent(requireContext()),
                        serviceConnection!!, Context.BIND_AUTO_CREATE)
            }
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