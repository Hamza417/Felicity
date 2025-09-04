package app.simple.felicity.ui.preferences.sub

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import app.simple.felicity.adapters.preference.AdapterTheme
import app.simple.felicity.databinding.FragmentGenericRecyclerViewBinding
import app.simple.felicity.extensions.fragments.MediaFragment
import app.simple.felicity.preferences.AppearancePreferences
import app.simple.felicity.theme.managers.ThemeUtils

class Themes : MediaFragment() {

    private lateinit var binding: FragmentGenericRecyclerViewBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentGenericRecyclerViewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        postponeEnterTransition()
        binding.recyclerView.adapter = AdapterTheme()
        view.startTransitionOnPreDraw()
        requireHiddenMiniPlayer()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        when (key) {
            AppearancePreferences.THEME -> {
                handler.postDelayed({ ThemeUtils.setAppTheme(resources) }, 25)
            }
        }
    }

    override fun getTransitionType(): TransitionType {
        return TransitionType.SLIDE
    }

    companion object {
        fun newInstance(): Themes {
            val args = Bundle()
            val fragment = Themes()
            fragment.arguments = args
            return fragment
        }

        const val TAG = "Theme"
    }
}