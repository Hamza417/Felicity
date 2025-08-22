package app.simple.felicity.ui.main.preferences

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import app.simple.felicity.R
import app.simple.felicity.adapters.preference.AdapterPreference
import app.simple.felicity.adapters.preference.AdapterPreference.Companion.AdapterPreferenceCallbacks
import app.simple.felicity.databinding.FragmentPreferencesBinding
import app.simple.felicity.databinding.HeaderPreferencesBinding
import app.simple.felicity.decorations.itemdecorations.SpacingItemDecoration
import app.simple.felicity.extensions.fragments.ScopedFragment
import app.simple.felicity.viewmodels.main.preferences.PreferencesViewModel
import app.simple.felicity.viewmodels.main.preferences.PreferencesViewModel.Companion.Preference

class Preferences : ScopedFragment() {

    private lateinit var binding: FragmentPreferencesBinding
    private lateinit var headerBinding: HeaderPreferencesBinding

    private var adapter: AdapterPreference? = null

    private val preferencesViewModel: PreferencesViewModel by viewModels({ requireActivity() })

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentPreferencesBinding.inflate(inflater, container, false)
        headerBinding = HeaderPreferencesBinding.inflate(inflater, binding.recyclerView, false)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.recyclerView.addItemDecoration(SpacingItemDecoration(48, true))

        preferencesViewModel.getPreferences().observe(viewLifecycleOwner) { preferences ->
            adapter = AdapterPreference(preferences)
            binding.recyclerView.adapter = adapter

            adapter?.setAdapterPreferenceCallbacks(object : AdapterPreferenceCallbacks {
                override fun onPreferenceClicked(preference: Preference, position: Int, view: View) {
                    when (preference.title) {
                        R.string.appearance -> {
                            openFragment(Appearance.newInstance(), Appearance.TAG)
                        }
                        R.string.user_interface -> {
                            openFragment(UserInterface.newInstance(), UserInterface.TAG)
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