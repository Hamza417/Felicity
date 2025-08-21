package app.simple.felicity.ui.main.preferences

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.activity.addCallback
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.viewModels
import app.simple.felicity.R
import app.simple.felicity.adapters.preference.AdapterPreference
import app.simple.felicity.adapters.preference.AdapterPreference.Companion.AdapterPreferenceCallbacks
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

    private var lastOpenedTag: String? = null

    private val backStackChangedListener = FragmentManager.OnBackStackChangedListener {
        val hasChild = childFragmentManager.backStackEntryCount > 0
        if (hasChild) {
            hideTitles()
        } else {
            showTitles()
            // Reset lastOpenedTag when all children are popped so user can reopen
            lastOpenedTag = null
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

            childFragmentManager.addOnBackStackChangedListener(backStackChangedListener)

            // Apply correct weights immediately (e.g., after rotation) based on current back stack
            val hasChild = childFragmentManager.backStackEntryCount > 0
            updateWeights(hasChild)
            if (hasChild) {
                hideTitles()
                lastOpenedTag = childFragmentManager.fragments.lastOrNull { it.isVisible }?.tag
            } else {
                showTitles()
            }
        }

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            if (childFragmentManager.backStackEntryCount > 0) {
                childFragmentManager.popBackStack()
            } else {
                popBackStack()
            }
        }
    }

    private fun createPreferencePane(preference: Preference) {
        // Map preference to fragment tag & instance
        val tag = when (preference.title) {
            R.string.appearance -> Appearance.TAG
            else -> return // Unsupported for now
        }

        // Guard against rapid double taps launching same fragment while transaction pending
        if (lastOpenedTag == tag) return

        // If the current visible fragment already matches this tag, do nothing
        val currentVisible = childFragmentManager.fragments.lastOrNull { it.isVisible }
        if (currentVisible?.tag == tag) return

        // If a back stack entry with same name exists anywhere, pop back to it instead of adding duplicate
        for (i in 0 until childFragmentManager.backStackEntryCount) {
            if (childFragmentManager.getBackStackEntryAt(i).name == tag) {
                childFragmentManager.popBackStack(tag, 0)
                lastOpenedTag = tag
                return
            }
        }

        // Reuse existing fragment instance if already added (but not in back stack), else create new
        val fragment = childFragmentManager.findFragmentByTag(tag) ?: when (preference.title) {
            R.string.appearance -> Appearance.newInstance()
            else -> null
        } ?: return

        lastOpenedTag = tag

        // Perform a replace so only one child fragment occupies the container. Add to back stack with tag name.
        childFragmentManager.beginTransaction()
            .setReorderingAllowed(true)
            .setCustomAnimations(
                    R.anim.enter_from_right, // enter
                    R.anim.exit_to_right, // exit
                    R.anim.enter_from_right, // popEnter
                    R.anim.exit_to_right // popExit
            )
            .replace(binding.childContainer.id, fragment, tag)
            .addToBackStack(tag)
            .commit()
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
            // Options list shrinks to wrap its content; child pane takes remaining space
            optionsParams.width = LinearLayout.LayoutParams.WRAP_CONTENT
            optionsParams.weight = 0f
            preferenceParams.width = 0 // width controlled by weight
            preferenceParams.weight = 1f
            preferencePane.visibility = View.VISIBLE
        } else {
            // Options list expands to full width without relying on weight distribution
            optionsParams.width = LinearLayout.LayoutParams.MATCH_PARENT
            optionsParams.weight = 0f
            preferenceParams.width = 0
            preferenceParams.weight = 0f
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