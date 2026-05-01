package app.simple.felicity.ui.preferences.sub

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import app.simple.felicity.R
import app.simple.felicity.adapters.preference.AdapterPanelVisibility
import app.simple.felicity.databinding.FragmentGenericRecyclerViewBinding
import app.simple.felicity.decorations.toggles.FelicitySwitch
import app.simple.felicity.enums.PreferenceType
import app.simple.felicity.extensions.fragments.PreferenceFragment
import app.simple.felicity.models.Preference
import app.simple.felicity.preferences.UserInterfacePreferences
import java.util.function.Supplier

/**
 * A preferences screen that lets the user decide which panels appear in the
 * Dashboard browse grid and the Simple Home list. Think of it as a wardrobe
 * for your home screen — keep what you love, hide the rest.
 *
 * Songs, Albums, and Artists are always visible and therefore don't show up here.
 * They're the non-negotiable core of any music app.
 *
 * @author Hamza417
 */
class PanelVisibility : PreferenceFragment() {

    private lateinit var binding: FragmentGenericRecyclerViewBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentGenericRecyclerViewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Hand the full list (header included) straight to the recycler — no external
        // AppHeader needed here, the first item in the list is the header itself.
        binding.recyclerView.setHasFixedSize(false)
        binding.recyclerView.adapter = AdapterPanelVisibility(buildPanelVisibilityList())
    }

    /**
     * Builds the list of toggle preferences, one for each panel that can be hidden.
     * Songs, Albums, and Artists are excluded because they are always shown.
     */
    private fun buildPanelVisibilityList(): List<Preference> {
        val prefs = mutableListOf<Preference>()

        // The header lives right inside the list, just like AccentColors does it —
        // no separate AppHeader widget floating above the recycler.
        prefs.add(Preference(type = PreferenceType.HEADER, title = R.string.panel_visibility))

        // Library subsection — the optional library panels that sit below the always-visible three.
        prefs.add(Preference(type = PreferenceType.SUB_HEADER, title = R.string.library))
        prefs.add(makePanelToggle(R.string.album_artists, R.drawable.ic_artist, UserInterfacePreferences.PANEL_VISIBLE_ALBUM_ARTISTS))
        prefs.add(makePanelToggle(R.string.genres, R.drawable.ic_piano, UserInterfacePreferences.PANEL_VISIBLE_GENRES))
        prefs.add(makePanelToggle(R.string.year, R.drawable.ic_date_range, UserInterfacePreferences.PANEL_VISIBLE_YEAR))
        prefs.add(makePanelToggle(R.string.playlists, R.drawable.ic_list, UserInterfacePreferences.PANEL_VISIBLE_PLAYLISTS))

        // Activity subsection — things you've done with your music lately.
        prefs.add(Preference(type = PreferenceType.SUB_HEADER, title = R.string.activity))
        prefs.add(makePanelToggle(R.string.playing_queue, R.drawable.ic_queue, UserInterfacePreferences.PANEL_VISIBLE_PLAYING_QUEUE))
        prefs.add(makePanelToggle(R.string.recently_added, R.drawable.ic_recently_added, UserInterfacePreferences.PANEL_VISIBLE_RECENTLY_ADDED))
        prefs.add(makePanelToggle(R.string.recently_played, R.drawable.ic_history, UserInterfacePreferences.PANEL_VISIBLE_RECENTLY_PLAYED))
        prefs.add(makePanelToggle(R.string.most_played, R.drawable.ic_equalizer, UserInterfacePreferences.PANEL_VISIBLE_MOST_PLAYED))
        prefs.add(makePanelToggle(R.string.favorites, R.drawable.ic_favorite_filled, UserInterfacePreferences.PANEL_VISIBLE_FAVORITES))

        // Files subsection — folder-based navigation options.
        prefs.add(Preference(type = PreferenceType.SUB_HEADER, title = R.string.files))
        prefs.add(makePanelToggle(R.string.folders, R.drawable.ic_folder, UserInterfacePreferences.PANEL_VISIBLE_FOLDERS))
        prefs.add(makePanelToggle(R.string.folders_hierarchy, R.drawable.ic_tree, UserInterfacePreferences.PANEL_VISIBLE_FOLDERS_HIERARCHY))
        prefs.add(makePanelToggle(R.string.always_skipped, R.drawable.ic_skip_16dp, UserInterfacePreferences.PANEL_VISIBLE_ALWAYS_SKIPPED))

        return prefs
    }

    /**
     * Creates a single SWITCH preference that reads and writes a panel visibility setting.
     *
     * @param titleRes  String resource for the panel name shown next to the toggle.
     * @param iconRes   Drawable resource for the panel icon shown to the left of the title.
     * @param prefKey   The shared preference key from [UserInterfacePreferences] that backs this toggle.
     */
    private fun makePanelToggle(titleRes: Int, iconRes: Int, prefKey: String): Preference {
        return Preference(
                title = titleRes,
                summary = "",
                icon = iconRes,
                type = PreferenceType.SWITCH,
                onPreferenceAction = { view, _ ->
                    UserInterfacePreferences.setPanelVisible(prefKey, (view as FelicitySwitch).isChecked)
                },
                valueProvider = Supplier {
                    UserInterfacePreferences.isPanelVisible(prefKey)
                }
        )
    }

    override val wantsMiniPlayerVisible: Boolean
        get() = false

    override fun getTransitionType(): TransitionType {
        return TransitionType.DRIFT
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        super.onSharedPreferenceChanged(sharedPreferences, key)
        // No need to rebuild the list here; the GenericPreferencesAdapter switches handle
        // their own state through their valueProvider, so they stay in sync automatically.
    }

    companion object {
        /**
         * Creates a new instance of [PanelVisibility].
         *
         * @return A fresh [PanelVisibility] fragment ready to let the user declutter their home.
         */
        fun newInstance(): PanelVisibility {
            val args = Bundle()
            val fragment = PanelVisibility()
            fragment.arguments = args
            return fragment
        }

        const val TAG = "PanelVisibility"
    }
}

