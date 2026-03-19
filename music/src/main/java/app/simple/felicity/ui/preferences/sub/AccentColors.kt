package app.simple.felicity.ui.preferences.sub

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import app.simple.felicity.adapters.preference.AdapterAccentColors
import app.simple.felicity.databinding.FragmentGenericRecyclerViewBinding
import app.simple.felicity.extensions.fragments.MediaFragment
import app.simple.felicity.repository.models.Audio

class AccentColors : MediaFragment() {

    private lateinit var binding: FragmentGenericRecyclerViewBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentGenericRecyclerViewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        postponeEnterTransition()
        requireHiddenMiniPlayer()

        binding.recyclerView.adapter = AdapterAccentColors()

        view.startTransitionOnPreDraw()
    }

    override fun getTransitionType(): TransitionType {
        return TransitionType.SLIDE
    }

    override fun onAudio(audio: Audio) {
        super.onAudio(audio)
        (binding.recyclerView.adapter as? AdapterAccentColors)?.reloadAlbumArt()
    }

    override val wantsMiniPlayerVisible: Boolean
        get() = false

    companion object {
        fun newInstance(): AccentColors {
            return AccentColors()
        }

        const val TAG = "AccentColors"
    }
}