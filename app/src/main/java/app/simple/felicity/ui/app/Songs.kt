package app.simple.felicity.ui.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProvider
import app.simple.felicity.R
import app.simple.felicity.adapters.ui.SongsAdapter
import app.simple.felicity.databinding.FragmentSongsBinding
import app.simple.felicity.extensions.fragments.ScopedFragment
import app.simple.felicity.loaders.MediaLoader
import app.simple.felicity.preferences.MusicPreferences
import app.simple.felicity.ui.player.DefaultPlayer
import app.simple.felicity.viewmodels.ui.SongsViewModel

class Songs : ScopedFragment() {

    private lateinit var binding: FragmentSongsBinding
    private lateinit var songsViewModel: SongsViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_songs, container, false)

        binding = FragmentSongsBinding.bind(view)
        songsViewModel = ViewModelProvider(requireActivity())[SongsViewModel::class.java]

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        startPostponedEnterTransition()

        songsViewModel.getSongs().observe(viewLifecycleOwner) {
            binding.recyclerView.adapter = SongsAdapter(it)
            (binding.recyclerView.adapter as SongsAdapter).onItemClickListener = { audio, position ->
                MusicPreferences.setMusicPosition(position)
                MusicPreferences.setMediaMusicCategory(MediaLoader.MEDIA_ID_SONGS)
                openFragmentSlide(DefaultPlayer.newInstance(), "default_player")
            }
        }
    }

    companion object {
        fun newInstance(): Songs {
            val args = Bundle()
            val fragment = Songs()
            fragment.arguments = args
            return fragment
        }
    }
}