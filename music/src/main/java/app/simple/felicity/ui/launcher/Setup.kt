package app.simple.felicity.ui.launcher

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import app.simple.felicity.databinding.FragmentSetupBinding
import app.simple.felicity.extensions.fragments.MediaFragment

class Setup : MediaFragment() {

    private var binding: FragmentSetupBinding? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentSetupBinding.inflate(inflater, container, false)



        return binding?.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireHiddenMiniPlayer()
    }

    companion object {
        fun newInstance(): Setup {
            val args = Bundle()
            val fragment = Setup()
            fragment.arguments = args
            return fragment
        }

        const val TAG = "Setup"
    }
}