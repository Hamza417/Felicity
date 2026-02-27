package app.simple.felicity.ui.preferences.main

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import app.simple.felicity.R
import app.simple.felicity.adapters.preference.GenericPreferencesAdapter
import app.simple.felicity.databinding.FragmentPreferenceAppearanceBinding
import app.simple.felicity.databinding.HeaderPreferencesGenericBinding
import app.simple.felicity.decorations.views.AppHeader
import app.simple.felicity.extensions.fragments.PreferenceFragment
import app.simple.felicity.preferences.LibraryPreferences
import app.simple.felicity.repository.services.AudioDatabaseService

class Library : PreferenceFragment() {

    private lateinit var binding: FragmentPreferenceAppearanceBinding
    private lateinit var headerBinding: HeaderPreferencesGenericBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentPreferenceAppearanceBinding.inflate(inflater, container, false)
        headerBinding = HeaderPreferencesGenericBinding.inflate(inflater, container, false)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        headerBinding.title.text = getString(R.string.library)
        headerBinding.icon.setImageResource(R.drawable.ic_library)
        binding.header.setContentView(headerBinding.root)
        binding.recyclerView.setHasFixedSize(false)
        binding.recyclerView.adapter = GenericPreferencesAdapter(createLibraryPanel())
        binding.header.attachTo(binding.recyclerView, AppHeader.ScrollMode.HIDE_ON_SCROLL)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        super.onSharedPreferenceChanged(sharedPreferences, key)
        when (key) {
            LibraryPreferences.MINIMUM_AUDIO_SIZE,
            LibraryPreferences.MINIMUM_AUDIO_LENGTH,
            LibraryPreferences.SKIP_NOMEDIA,
            LibraryPreferences.SKIP_HIDDEN_FILES,
            LibraryPreferences.SKIP_HIDDEN_FOLDERS -> {
                // Any scanner-related preference change requires a fresh scan
                AudioDatabaseService.refreshScan(requireContext())
            }
        }
    }

    companion object {
        fun newInstance(): Library {
            val args = Bundle()
            val fragment = Library()
            fragment.arguments = args
            return fragment
        }

        const val TAG = "Appearance"
    }
}

