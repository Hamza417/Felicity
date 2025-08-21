package app.simple.felicity.ui.main.preferences

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.activity.addCallback
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.viewModels
import app.simple.felicity.adapters.preference.AdapterPreference
import app.simple.felicity.adapters.preference.AdapterPreference.Companion.AdapterPreferenceCallbacks
import app.simple.felicity.core.R
import app.simple.felicity.core.utils.ViewUtils.visible
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

    private val backStackChangedListener = FragmentManager.OnBackStackChangedListener {
        val hasChild = childFragmentManager.backStackEntryCount > 0
        if (hasChild) {
            hideTitles()
        } else {
            showTitles()
        }
        updateWeights(hasChild)
    }

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
                    createPreferencePane(preference)
                }
            })
        }

        childFragmentManager.addOnBackStackChangedListener(backStackChangedListener)

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            if (childFragmentManager.backStackEntryCount > 0) {
                childFragmentManager.popBackStack()
            } else {
                popBackStack()
            }
        }
    }

    private fun createPreferencePane(preference: Preference) {
        when (preference.title) {
            R.string.appearance -> {
                childFragmentManager.beginTransaction()
                    .replace(binding.childContainer.id, Appearance.newInstance(), Appearance.TAG)
                    .setCustomAnimations(app.simple.felicity.decoration.R.anim.zoom_in,
                                         app.simple.felicity.decoration.R.anim.zoom_out,
                                         app.simple.felicity.decoration.R.anim.zoom_in,
                                         app.simple.felicity.decoration.R.anim.zoom_out)
                    .addToBackStack(Appearance.TAG)
                    .commit()
            }

            else -> {
                // Handle other preferences or show a message
            }
        }
    }

    private fun hideTitles() {
        adapter?.setTitlesVisible(false)
        binding.divider.visible(true)
    }

    private fun showTitles() {
        adapter?.setTitlesVisible(true)
        binding.divider.visible(false)
    }

    private fun updateWeights(hasChild: Boolean) {
        val optionsPane = binding.recyclerView
        val preferencePane = binding.childContainer

        val optionsParams = optionsPane.layoutParams as LinearLayout.LayoutParams
        val preferenceParams = preferencePane.layoutParams as LinearLayout.LayoutParams

        if (hasChild) {
            optionsParams.weight = 2f // 20%
            preferenceParams.weight = 8f // 80%
            preferencePane.visibility = View.VISIBLE
        } else {
            optionsParams.weight = 1f // 100%
            preferenceParams.weight = 0f // hidden
            preferencePane.visibility = View.GONE
        }

        optionsPane.layoutParams = optionsParams
        preferencePane.layoutParams = preferenceParams

        optionsPane.requestLayout()
        preferencePane.requestLayout()
    }

    override fun onDestroyView() {
        childFragmentManager.removeOnBackStackChangedListener(backStackChangedListener)
        super.onDestroyView()
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