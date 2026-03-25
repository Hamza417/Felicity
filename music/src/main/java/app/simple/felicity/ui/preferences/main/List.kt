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
import app.simple.felicity.preferences.ListPreferences
import app.simple.felicity.repository.utils.AudioUtils

class List : PreferenceFragment() {

    private lateinit var binding: FragmentPreferenceAppearanceBinding
    private lateinit var headerBinding: HeaderPreferencesGenericBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentPreferenceAppearanceBinding.inflate(inflater, container, false)
        headerBinding = HeaderPreferencesGenericBinding.inflate(inflater, container, false)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        headerBinding.title.text = getString(R.string.list)
        headerBinding.icon.setImageResource(R.drawable.ic_list)
        binding.header.setContentView(headerBinding.root)
        binding.recyclerView.setHasFixedSize(false)
        binding.recyclerView.adapter = GenericPreferencesAdapter(createListPanel())
        binding.header.attachTo(binding.recyclerView, AppHeader.ScrollMode.HIDE_ON_SCROLL)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        super.onSharedPreferenceChanged(sharedPreferences, key)
        when (key) {
            ListPreferences.ALBUM_ARTIST_OVER_ARTIST -> {
                AudioUtils.albumArtistOverArtist = ListPreferences.isAlbumArtistOverArtist()
            }
            else -> {
            }
        }
    }

    override val wantsMiniPlayerVisible: Boolean
        get() = false

    companion object {
        fun newInstance(): List {
            val args = Bundle()
            val fragment = List()
            fragment.arguments = args
            return fragment
        }

        const val TAG = "List"
    }
}