package app.simple.felicity.ui.main.preferences

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import app.simple.felicity.adapters.preference.AdapterTheme
import app.simple.felicity.databinding.FragmentGenericRecyclerViewBinding
import app.simple.felicity.extensions.fragments.ScopedFragment
import app.simple.felicity.preferences.AppearancePreferences
import app.simple.felicity.theme.managers.ThemeUtils

class Theme : ScopedFragment() {

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
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        when (key) {
            AppearancePreferences.THEME -> {
                handler.postDelayed({ ThemeUtils.setAppTheme(resources) }, 25)
            }
        }
    }

    companion object {
        fun newInstance(): Theme {
            val args = Bundle()
            val fragment = Theme()
            fragment.arguments = args
            return fragment
        }

        const val TAG = "Theme"
    }
}