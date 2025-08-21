package app.simple.felicity.ui.main.preferences

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import app.simple.felicity.databinding.FragmentPreferenceAppearanceBinding
import app.simple.felicity.extensions.fragments.PreferenceFragment

class Appearance : PreferenceFragment() {

    private lateinit var binding: FragmentPreferenceAppearanceBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentPreferenceAppearanceBinding.inflate(inflater, container, false)
        return binding.root
    }

    companion object {
        fun newInstance(): Appearance {
            val args = Bundle()
            val fragment = Appearance()
            fragment.arguments = args
            return fragment
        }

        const val TAG = "Appearance"
    }
}