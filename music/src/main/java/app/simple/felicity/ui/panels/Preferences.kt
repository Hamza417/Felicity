package app.simple.felicity.ui.panels

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import app.simple.felicity.R
import app.simple.felicity.adapters.preference.AdapterPreference
import app.simple.felicity.databinding.FragmentPreferencesBinding
import app.simple.felicity.databinding.HeaderPreferencesBinding
import app.simple.felicity.extensions.fragments.MediaFragment
import app.simple.felicity.ui.preferences.main.Appearance
import app.simple.felicity.ui.preferences.main.Behavior
import app.simple.felicity.ui.preferences.main.Engine
import app.simple.felicity.ui.preferences.main.Library
import app.simple.felicity.ui.preferences.main.UserInterface
import app.simple.felicity.viewmodels.panels.PreferencesViewModel

class Preferences : MediaFragment() {

    private lateinit var binding: FragmentPreferencesBinding
    private lateinit var headerBinding: HeaderPreferencesBinding

    private var adapter: AdapterPreference? = null

    private val preferencesViewModel: PreferencesViewModel by viewModels({ requireActivity() })

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentPreferencesBinding.inflate(inflater, container, false)
        headerBinding = HeaderPreferencesBinding.inflate(inflater, binding.recyclerView, false)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireHiddenMiniPlayer()

        preferencesViewModel.getPreferences().observe(viewLifecycleOwner) { preferences ->
            adapter = AdapterPreference(preferences)
            binding.recyclerView.adapter = adapter

            adapter?.setAdapterPreferenceCallbacks(object : AdapterPreference.Companion.AdapterPreferenceCallbacks {
                override fun onPreferenceClicked(preference: PreferencesViewModel.Companion.Preference, position: Int, view: View) {
                    when (preference.title) {
                        R.string.appearance -> {
                            openFragment(Appearance.newInstance(), Appearance.TAG)
                        }
                        R.string.user_interface -> {
                            openFragment(UserInterface.newInstance(), UserInterface.TAG)
                        }
                        R.string.behavior -> {
                            openFragment(Behavior.newInstance(), Behavior.TAG)
                        }
                        R.string.audio -> {
                            openFragment(Engine.newInstance(), Engine.TAG)
                        }
                        R.string.library -> {
                            openFragment(Library.newInstance(), Library.TAG)
                        }
                    }
                }
            })
        }
    }

    companion object {
        fun newInstance(): Preferences {
            val args = Bundle()
            val fragment = Preferences()
            fragment.arguments = args
            return fragment
        }

        const val TAG = "Preferences"
    }
}