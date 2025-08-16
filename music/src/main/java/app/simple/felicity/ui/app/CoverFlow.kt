package app.simple.felicity.ui.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import app.simple.felicity.databinding.FragmentCoverflowBinding
import app.simple.felicity.extensions.fragments.ScopedFragment
import app.simple.felicity.repository.utils.SongUtils
import app.simple.felicity.viewmodels.main.songs.SongsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CoverFlow : ScopedFragment() {

    private lateinit var binding: FragmentCoverflowBinding
    private val songsViewModel: SongsViewModel by viewModels({ requireActivity() })

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentCoverflowBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        songsViewModel.getSongs().observe(viewLifecycleOwner) { songs ->
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Default) {
                val uris = songs.mapNotNull { SongUtils.getArtworkUri(requireContext(), it.albumId, it.id) }

                withContext(Dispatchers.Main) {
                    binding.coverFlow.setUris(uris)
                }
            }
        }
    }

    companion object {
        fun newInstance(): CoverFlow {
            val args = Bundle()
            val fragment = CoverFlow()
            fragment.arguments = args
            return fragment
        }

        const val TAG = "CoverFlow"
    }
}