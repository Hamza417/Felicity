package app.simple.felicity.ui.preferences.sub

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import app.simple.felicity.adapters.preference.AdapterTypeface
import app.simple.felicity.databinding.FragmentGenericRecyclerViewBinding
import app.simple.felicity.extensions.fragments.MediaFragment

class TypeFaces : MediaFragment() {

    private lateinit var binding: FragmentGenericRecyclerViewBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentGenericRecyclerViewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        postponeEnterTransition()

        binding.recyclerView.adapter = AdapterTypeface()
        binding.recyclerView.scheduleLayoutAnimation()

        view.startTransitionOnPreDraw()
        requireHiddenMiniPlayer()
    }

    override fun getTransitionType(): TransitionType {
        return TransitionType.SLIDE
    }

    companion object {
        fun newInstance(): TypeFaces {
            val args = Bundle()
            val fragment = TypeFaces()
            fragment.arguments = args
            return fragment
        }

        const val TAG = "TypeFaces"
    }
}