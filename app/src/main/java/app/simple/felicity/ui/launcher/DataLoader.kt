package app.simple.felicity.ui.launcher

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import app.simple.felicity.databinding.FragmentLoaderBinding
import app.simple.felicity.extensions.fragments.ScopedFragment

class DataLoader : ScopedFragment() {

    private var binding: FragmentLoaderBinding? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentLoaderBinding.inflate(inflater, container, false)
        return binding?.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        startPostponedEnterTransition()

    }

    companion object {
        fun newInstance(): DataLoader {
            val args = Bundle()
            val fragment = DataLoader()
            fragment.arguments = args
            return fragment
        }
    }
}
