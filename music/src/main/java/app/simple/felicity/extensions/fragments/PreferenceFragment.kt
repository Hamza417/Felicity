package app.simple.felicity.extensions.fragments

import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.format.DateUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import app.simple.felicity.BuildConfig
import app.simple.felicity.R
import app.simple.felicity.adapters.preference.GenericPreferencesAdapter
import app.simple.felicity.databinding.FragmentPreferenceAppearanceBinding
import app.simple.felicity.databinding.HeaderPreferencesGenericBinding
import app.simple.felicity.decorations.popups.PopupMenuItem
import app.simple.felicity.decorations.popups.SharedScrollViewPopup
import app.simple.felicity.decorations.seekbars.FelicitySeekbar
import app.simple.felicity.decorations.toggles.FelicityButtonGroup
import app.simple.felicity.decorations.toggles.FelicityButtonGroup.Companion.Button
import app.simple.felicity.decorations.toggles.FelicitySwitch
import app.simple.felicity.decorations.views.AppHeader
import app.simple.felicity.dialogs.app.StatusBarLyricsColorPicker
import app.simple.felicity.enums.PreferenceType
import app.simple.felicity.models.ButtonGroupState
import app.simple.felicity.models.Preference
import app.simple.felicity.models.SeekbarState
import app.simple.felicity.preferences.AccessibilityPreferences
import app.simple.felicity.preferences.AlbumArtPreferences
import app.simple.felicity.preferences.AppearancePreferences
import app.simple.felicity.preferences.AudioPreferences
import app.simple.felicity.preferences.BehaviourPreferences
import app.simple.felicity.preferences.ConfigurationPreferences
import app.simple.felicity.preferences.EqualizerPreferences
import app.simple.felicity.preferences.LibraryPreferences
import app.simple.felicity.preferences.PlayerPreferences
import app.simple.felicity.preferences.StatusBarLyricsPreferences
import app.simple.felicity.preferences.UserInterfacePreferences
import app.simple.felicity.repository.repositories.SongStatRepository
import app.simple.felicity.repository.services.AudioDatabaseService
import app.simple.felicity.statusbar.StatusBarLyricsService
import app.simple.felicity.theme.accents.AlbumArt
import app.simple.felicity.ui.preferences.sub.AccentColors
import app.simple.felicity.ui.preferences.sub.ArtistWhitelist
import app.simple.felicity.ui.preferences.sub.Language
import app.simple.felicity.ui.preferences.sub.MusicFolders
import app.simple.felicity.ui.preferences.sub.PanelVisibility
import app.simple.felicity.ui.preferences.sub.StatusBarLyrics
import app.simple.felicity.ui.preferences.sub.Themes
import app.simple.felicity.ui.preferences.sub.TypeFaces
import com.bumptech.glide.Glide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.function.Supplier

@Suppress("unused")
abstract class PreferenceFragment : MediaFragment() {

    private var _binding: FragmentPreferenceAppearanceBinding? = null
    protected val binding: FragmentPreferenceAppearanceBinding
        get() = _binding!!

    private var _headerBinding: HeaderPreferencesGenericBinding? = null
    protected val headerBinding: HeaderPreferencesGenericBinding
        get() = _headerBinding!!

    protected open val titleRes: Int = 0
    protected open val iconRes: Int = 0

    /**
     * Switch that was flipped on before the overlay permission existed. When the user
     * returns from the system permission screen, [onResume] completes the toggle for them
     * instead of making them tap the switch a second time.
     */
    private var pendingStatusBarLyricsSwitch: FelicitySwitch? = null

    override val wantsMiniPlayerVisible: Boolean
        get() = false

    protected open fun getPreferencesList(): List<Preference> = emptyList()

    override fun onResume() {
        super.onResume()

        val pendingSwitch = pendingStatusBarLyricsSwitch ?: return
        val context = context ?: return

        if (Settings.canDrawOverlays(context)) {
            StatusBarLyricsPreferences.setEnabled(true)
            pendingSwitch.setChecked(true, false)
            StatusBarLyricsService.startIfEnabled(context)
            Toast.makeText(context, R.string.status_bar_lyrics_enabled, Toast.LENGTH_SHORT).show()
        }

        pendingStatusBarLyricsSwitch = null
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        if (titleRes != 0) {
            _binding = FragmentPreferenceAppearanceBinding.inflate(inflater, container, false)
            _headerBinding = HeaderPreferencesGenericBinding.inflate(inflater, container, false)
            return _binding!!.root
        }
        return super.onCreateView(inflater, container, savedInstanceState)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireHiddenMiniPlayer()

        if (titleRes != 0) {
            _headerBinding?.title?.text = getString(titleRes)
            _headerBinding?.icon?.setImageResource(iconRes)
            _binding?.header?.setContentView(_headerBinding!!.root)
            _binding?.recyclerView?.setHasFixedSize(false)
            _binding?.recyclerView?.adapter = GenericPreferencesAdapter(getPreferencesList())
            _binding?.header?.attachTo(_binding!!.recyclerView, AppHeader.ScrollMode.HIDE_ON_SCROLL)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        _headerBinding = null
    }

    /**
     * Bookmarks:
     * Appearance Preferences: [createAppearancePanel]
     * User Interface Preferences: [createUserInterfacePanel]
     * Configuration Preferences: [createConfigurationPreferences]
     * Behavior Preferences: [createBehaviorPanel]
     * Engine Preferences: [createEnginePanel]
     * Library Preferences: [createLibraryPanel]
     * Accessibility Preferences: [createAccessibilityPanel]
     * About Preferences: [createAboutPanel]
     */

    protected fun createAppearancePanel(): List<Preference> {
        val preferences = mutableListOf<Preference>()

        val colors = Preference(type = PreferenceType.SUB_HEADER, title = R.string.colors)

        val themes = Preference(
                title = R.string.theme,
                summary = R.string.theme_summary,
                icon = R.drawable.ic_invert,
                type = PreferenceType.PANEL,
                onPreferenceAction = { view, callback ->
                    openFragment(Themes.newInstance(), Themes.TAG)
                }
        )

        val accentColor = Preference(
                title = R.string.accent_color,
                summary = R.string.accent_color_summary,
                icon = R.drawable.ic_palette,
                type = PreferenceType.PANEL,
                onPreferenceAction = { view, callback ->
                    openFragment(AccentColors.newInstance(), AccentColors.TAG)
                }
        )

        val albumArtPaletteStyle = Preference(
                title = R.string.album_art_palette_style,
                summary = R.string.album_art_palette_style_summary,
                icon = R.drawable.ic_tonality,
                type = PreferenceType.POPUP,
                valueProvider = {
                    when (AlbumArtPreferences.getPaletteStyle()) {
                        AlbumArtPreferences.PALETTE_STYLE_PASTEL -> getString(R.string.album_art_palette_style_pastel)
                        AlbumArtPreferences.PALETTE_STYLE_CHIC -> getString(R.string.album_art_palette_style_chic)
                        else -> getString(R.string.album_art_palette_style_retro)
                    }
                },
                onPreferenceAction = { view, callback ->
                    SharedScrollViewPopup(
                            container = requireContainerView(),
                            anchorView = view,
                            menuItems = listOf(
                                    PopupMenuItem(title = R.string.album_art_palette_style_pastel),
                                    PopupMenuItem(title = R.string.album_art_palette_style_chic),
                                    PopupMenuItem(title = R.string.album_art_palette_style_retro)
                            ),
                            onMenuItemClick = {
                                when (it) {
                                    R.string.album_art_palette_style_pastel -> {
                                        AlbumArtPreferences.setPaletteStyle(AlbumArtPreferences.PALETTE_STYLE_PASTEL)
                                        (view as TextView).text = getString(R.string.album_art_palette_style_pastel)
                                    }
                                    R.string.album_art_palette_style_chic -> {
                                        AlbumArtPreferences.setPaletteStyle(AlbumArtPreferences.PALETTE_STYLE_CHIC)
                                        (view as TextView).text = getString(R.string.album_art_palette_style_chic)
                                    }
                                    R.string.album_art_palette_style_retro -> {
                                        AlbumArtPreferences.setPaletteStyle(AlbumArtPreferences.PALETTE_STYLE_RETRO)
                                        (view as TextView).text = getString(R.string.album_art_palette_style_retro)
                                    }
                                }
                            }
                    ).show()
                }
        )

        val font = Preference(
                title = R.string.font,
                type = PreferenceType.SUB_HEADER,
        )

        val typeface = Preference(
                title = R.string.typeface,
                summary = R.string.typeface_summary,
                icon = R.drawable.ic_text_fields,
                type = PreferenceType.PANEL,
                onPreferenceAction = { view, callback ->
                    openFragment(TypeFaces.newInstance(), TypeFaces.TAG)
                }
        )

        val header = Preference(type = PreferenceType.SUB_HEADER, title = R.string.appearance)

        val cornerRadius = Preference(
                title = R.string.corner_radius,
                summary = R.string.corner_radius_summary,
                icon = R.drawable.ic_corner,
                type = PreferenceType.SLIDER,
                onPreferenceAction = { view, callback ->
                    AppearancePreferences.setCornerRadius((view as FelicitySeekbar).getProgress())
                },
                valueProvider = Supplier {
                    SeekbarState(
                            position = AppearancePreferences.getCornerRadius(),
                            max = AppearancePreferences.MAX_CORNER_RADIUS,
                            min = 0F,
                            default = AppearancePreferences.DEFAULT_CORNER_RADIUS,
                            leftLabel = false,
                            rightLabel = true,
                            rightLabelProvider = { progress, _, _ ->
                                String.format(Locale.getDefault(), "%.1f px", progress)
                            },
                    )
                }
        )

        val spacing = Preference(
                title = R.string.vertical_spacing,
                summary = R.string.spacing_summary,
                icon = R.drawable.ic_spacing,
                type = PreferenceType.SLIDER,
                onPreferenceAction = { view, callback ->
                    AppearancePreferences.setListSpacing((view as FelicitySeekbar).getProgress())
                },
                valueProvider = Supplier {
                    SeekbarState(
                            position = AppearancePreferences.getListSpacing(),
                            max = AppearancePreferences.MAX_SPACING,
                            min = 0F,
                            default = AppearancePreferences.DEFAULT_SPACING,
                            leftLabel = false,
                            rightLabel = true,
                            rightLabelProvider = { progress, _, _ ->
                                String.format(Locale.getDefault(), "%.1f px", progress)
                            },
                    )
                }
        )

        val effects = Preference(type = PreferenceType.SUB_HEADER, title = R.string.effects)

        val shadowEffectToggle = Preference(
                title = R.string.shadow_effect,
                summary = R.string.shadow_effect_summary,
                icon = R.drawable.ic_shadow,
                type = PreferenceType.SWITCH,
                onPreferenceAction = { view, callback ->
                    AppearancePreferences.setShadowEffect((view as FelicitySwitch).isChecked)
                },
                valueProvider = Supplier {
                    AppearancePreferences.isShadowEffectOn()
                }
        )

        val albumArt = Preference(type = PreferenceType.SUB_HEADER, title = R.string.album_art)

        val albumArtShadows = Preference(
                title = R.string.shadows,
                summary = R.string.album_art_shadows_summary,
                icon = R.drawable.ic_shadow,
                type = PreferenceType.SWITCH,
                onPreferenceAction = { view, callback ->
                    AlbumArtPreferences.setShadowEnabled((view as FelicitySwitch).isChecked)
                },
                valueProvider = Supplier {
                    AlbumArtPreferences.isShadowEnabled()
                }
        )

        val albumArtCorners = Preference(
                title = R.string.rounded_corners,
                summary = R.string.album_art_rounded_corners_summary,
                icon = R.drawable.ic_corner,
                type = PreferenceType.SWITCH,
                onPreferenceAction = { view, callback ->
                    AlbumArtPreferences.setRoundedCornersEnabled((view as FelicitySwitch).isChecked)
                },
                valueProvider = Supplier {
                    AlbumArtPreferences.isRoundedCornersEnabled()
                }
        )

        val albumArtCrop = Preference(
                title = R.string.crop,
                summary = R.string.album_art_crop_summary,
                icon = R.drawable.ic_crop,
                type = PreferenceType.SWITCH,
                onPreferenceAction = { view, callback ->
                    AlbumArtPreferences.setCropEnabled((view as FelicitySwitch).isChecked)
                },
                valueProvider = Supplier {
                    AlbumArtPreferences.isCropEnabled()
                }
        )

        val albumArtGreyscale = Preference(
                title = R.string.greyscale,
                summary = R.string.album_art_greyscale_summary,
                icon = R.drawable.ic_invert,
                type = PreferenceType.SWITCH,
                onPreferenceAction = { view, callback ->
                    AlbumArtPreferences.setGreyscaleEnabled((view as FelicitySwitch).isChecked)
                },
                valueProvider = Supplier {
                    AlbumArtPreferences.isGreyscaleEnabled()
                }
        )

        preferences.add(colors)
        preferences.add(themes)
        preferences.add(accentColor)
        if (AppearancePreferences.getAccentColorName() == AlbumArt.IDENTIFIER) {
            preferences.add(albumArtPaletteStyle)
        }
        preferences.add(font)
        preferences.add(typeface)
        preferences.add(header)
        preferences.add(cornerRadius)
        preferences.add(spacing)
        preferences.add(effects)
        preferences.add(shadowEffectToggle)
        preferences.add(albumArt)
        preferences.add(albumArtShadows)
        preferences.add(albumArtCorners)
        preferences.add(albumArtCrop)
        preferences.add(albumArtGreyscale)

        return preferences
    }

    protected fun createUserInterfacePanel(): List<Preference> {
        val preferences = mutableListOf<Preference>()

        val homeHeader = Preference(type = PreferenceType.SUB_HEADER, title = R.string.home)

        val panelVisibility = Preference(
                title = R.string.panel_visibility,
                summary = R.string.panel_visibility_summary,
                icon = R.drawable.ic_carousel,
                type = PreferenceType.PANEL,
                onPreferenceAction = { _, _ ->
                    openFragment(PanelVisibility.newInstance(), PanelVisibility.TAG)
                }
        )

        val homeInterface = Preference(
                title = R.string.change_home_interface,
                summary = R.string.change_home_interface_summary,
                icon = R.drawable.ic_home,
                type = PreferenceType.POPUP,
                valueProvider = {
                    when (UserInterfacePreferences.getHomeInterface()) {
                        UserInterfacePreferences.HOME_INTERFACE_DASHBOARD -> getString(R.string.dashboard)
                        UserInterfacePreferences.HOME_INTERFACE_ARTFLOW -> getString(R.string.artflow)
                        UserInterfacePreferences.HOME_INTERFACE_TILED -> getString(R.string.tiled)
                        UserInterfacePreferences.HOME_INTERFACE_SIMPLE -> getString(R.string.simple)
                        else -> getString(R.string.simple)
                    }
                },
                onPreferenceAction = { view, callback ->
                    SharedScrollViewPopup(
                            container = requireContainerView(),
                            anchorView = view,
                            menuItems = listOf(
                                    PopupMenuItem(title = R.string.dashboard, summary = getString(R.string.dashboard_desc), isExperimental = true),
                                    PopupMenuItem(title = R.string.tiled, summary = getString(R.string.tiled_desc)),
                                    PopupMenuItem(title = R.string.artflow, summary = getString(R.string.artflow_desc), isExperimental = true),
                                    PopupMenuItem(title = R.string.simple, summary = getString(R.string.simple_desc))
                            ),
                            onMenuItemClick = {
                                when (it) {
                                    R.string.dashboard -> {
                                        UserInterfacePreferences.setHomeInterface(UserInterfacePreferences.HOME_INTERFACE_DASHBOARD)
                                        (view as TextView).text = getString(R.string.dashboard)
                                    }
                                    R.string.tiled -> {
                                        UserInterfacePreferences.setHomeInterface(UserInterfacePreferences.HOME_INTERFACE_TILED)
                                        (view as TextView).text = getString(R.string.tiled)
                                    }
                                    R.string.artflow -> {
                                        UserInterfacePreferences.setHomeInterface(UserInterfacePreferences.HOME_INTERFACE_ARTFLOW)
                                        (view as TextView).text = getString(R.string.artflow)
                                    }
                                    R.string.simple -> {
                                        UserInterfacePreferences.setHomeInterface(UserInterfacePreferences.HOME_INTERFACE_SIMPLE)
                                        (view as TextView).text = getString(R.string.simple)
                                    }
                                }
                            },
                            onDismiss = {

                            }
                    ).show()
                }
        )

        val miniPlayerHeader = Preference(type = PreferenceType.SUB_HEADER, title = R.string.miniplayer)

        val miniplayerWidth = Preference(
                title = R.string.miniplayer_width,
                summary = R.string.miniplayer_width_summary,
                icon = -1,
                type = PreferenceType.SLIDER,
                onPreferenceAction = { view, callback ->
                    UserInterfacePreferences.setMiniplayerWidth((view as FelicitySeekbar).getProgress())
                },
                valueProvider = Supplier {
                    SeekbarState(
                            position = UserInterfacePreferences.getMiniplayerWidth(),
                            max = UserInterfacePreferences.MAX_LANDSCAPE_MINIPLAYER_WIDTH,
                            min = UserInterfacePreferences.MIN_LANDSCAPE_MINIPLAYER_WIDTH,
                            default = UserInterfacePreferences.DEFAULT_LANDSCAPE_MINIPLAYER_WIDTH,
                            leftLabel = false,
                            rightLabel = false,
                    )
                }
        )

        val marginAroundMiniplayerToggle = Preference(
                title = R.string.margin_around_miniplayer,
                summary = R.string.margin_around_miniplayer_summary,
                icon = R.drawable.ic_border_outer,
                type = PreferenceType.SWITCH,
                onPreferenceAction = { view, callback ->
                    UserInterfacePreferences.setMarginAroundMiniplayer((view as FelicitySwitch).isChecked)
                },
                valueProvider = Supplier {
                    UserInterfacePreferences.isMarginAroundMiniplayer()
                }
        )

        val playerHeader = Preference(type = PreferenceType.SUB_HEADER, title = R.string.player)

        val playerInterface = Preference(
                title = R.string.change_player_interface,
                summary = R.string.change_player_interface_summary,
                icon = R.drawable.ic_radio,
                type = PreferenceType.POPUP,
                valueProvider = {
                    when (UserInterfacePreferences.getPlayerInterface()) {
                        UserInterfacePreferences.PLAYER_INTERFACE_FADED -> getString(R.string.faded)
                        UserInterfacePreferences.PLAYER_INTERFACE_DEFAULT -> getString(R.string.simple)
                        UserInterfacePreferences.PLAYER_INTERFACE_CAROUSEL -> getString(R.string.carousel)
                        else -> getString(R.string.simple)
                    }
                },
                onPreferenceAction = { view, callback ->
                    SharedScrollViewPopup(
                            container = requireContainerView(),
                            anchorView = view,
                            menuItems = listOf(
                                    PopupMenuItem(title = R.string.simple, summary = getString(R.string.simple_ui_desc)),
                                    PopupMenuItem(title = R.string.faded, summary = getString(R.string.faded_ui_desc)),
                                    PopupMenuItem(title = R.string.carousel, summary = getString(R.string.carousel_ui_desc))
                            ),
                            onMenuItemClick = {
                                when (it) {
                                    R.string.simple -> {
                                        UserInterfacePreferences.setPlayerInterface(UserInterfacePreferences.PLAYER_INTERFACE_DEFAULT)
                                        (view as TextView).text = getString(R.string.simple)
                                    }
                                    R.string.faded -> {
                                        UserInterfacePreferences.setPlayerInterface(UserInterfacePreferences.PLAYER_INTERFACE_FADED)
                                        (view as TextView).text = getString(R.string.faded)
                                    }
                                    R.string.carousel -> {
                                        UserInterfacePreferences.setPlayerInterface(UserInterfacePreferences.PLAYER_INTERFACE_CAROUSEL)
                                        (view as TextView).text = getString(R.string.carousel)
                                    }
                                }
                            },
                            onDismiss = {}
                    ).show()
                }
        )

        val lyricsToggle = Preference(
                title = R.string.lyrics,
                summary = R.string.show_lyrics_summary,
                icon = R.drawable.ic_lyrics,
                type = PreferenceType.SWITCH,
                onPreferenceAction = { view, callback ->
                    PlayerPreferences.setShowLyrics((view as FelicitySwitch).isChecked)
                },
                valueProvider = Supplier {
                    PlayerPreferences.isShowLyrics()
                }
        )

        val statusBarLyrics = Preference(
                title = R.string.status_bar_lyrics,
                summary = R.string.status_bar_lyrics_summary,
                icon = R.drawable.ic_lyrics,
                type = PreferenceType.PANEL,
                onPreferenceAction = { _, _ ->
                    openFragment(StatusBarLyrics.newInstance(), StatusBarLyrics.TAG)
                }
        )

        val stackMediaControlsToggle = Preference(
                title = R.string.stack_media_controls,
                summary = R.string.stack_media_controls_summary,
                icon = R.drawable.ic_play,
                type = PreferenceType.SWITCH,
                onPreferenceAction = { view, callback ->
                    UserInterfacePreferences.setStackMediaControls((view as FelicitySwitch).isChecked)
                },
                valueProvider = Supplier {
                    UserInterfacePreferences.isStackMediaControls()
                }
        )

        val showForwardAndRewindButtons = Preference(
                title = R.string.show_forward_rewind_buttons,
                summary = R.string.show_forward_rewind_buttons_summary,
                icon = R.drawable.ic_fast_forward,
                type = PreferenceType.SWITCH,
                onPreferenceAction = { view, callback ->
                    UserInterfacePreferences.setForwardRewindButtons((view as FelicitySwitch).isChecked)
                },
                valueProvider = Supplier {
                    UserInterfacePreferences.isForwardRewindButtons()
                }
        )

        val immersiveMode = Preference(
                title = R.string.immersive_mode,
                summary = R.string.immersive_mode_summary,
                icon = R.drawable.ic_fullscreen,
                type = PreferenceType.SWITCH,
                onPreferenceAction = { view, callback ->
                    UserInterfacePreferences.setImmersiveMode((view as FelicitySwitch).isChecked)
                },
                valueProvider = Supplier {
                    UserInterfacePreferences.isImmersiveMode()
                }
        )

        val pagesHeader = Preference(type = PreferenceType.SUB_HEADER, title = R.string.pages)

        val songsFirstToggle = Preference(
                title = R.string.show_songs_first,
                summary = R.string.show_songs_first_summary,
                icon = R.drawable.ic_sort,
                type = PreferenceType.SWITCH,
                onPreferenceAction = { view, _ ->
                    UserInterfacePreferences.setSongsFirstInPages((view as FelicitySwitch).isChecked)
                },
                valueProvider = Supplier {
                    UserInterfacePreferences.isSongsFirstInPages()
                }
        )

        val applicationHeader = Preference(type = PreferenceType.SUB_HEADER, title = R.string.application)

        val likeButton = Preference(
                title = R.string.like_icon,
                summary = R.string.like_icon_summary,
                icon = R.drawable.ic_thumb_up,
                type = PreferenceType.SWITCH,
                onPreferenceAction = { view, callback ->
                    UserInterfacePreferences.setLikeIconInsteadOfThumb((view as FelicitySwitch).isChecked)
                },
                valueProvider = Supplier {
                    UserInterfacePreferences.isLikeIconInsteadOfThumb()
                }
        )

        val volumeControls = Preference(
                title = R.string.volume_controls,
                summary = R.string.volume_controls_summary,
                icon = R.drawable.ic_volume_up,
                type = PreferenceType.SWITCH,
                onPreferenceAction = { view, callback ->
                    UserInterfacePreferences.setVolumeControls((view as FelicitySwitch).isChecked)
                },
                valueProvider = Supplier {
                    UserInterfacePreferences.isVolumeControls()
                }
        )

        val volumeKnobPosition = Preference(
                title = R.string.volume_knob_position,
                summary = R.string.volume_knob_position_summary,
                icon = R.drawable.ic_align_center,
                type = PreferenceType.POPUP,
                valueProvider = {
                    when (UserInterfacePreferences.getVolumeKnobPosition()) {
                        UserInterfacePreferences.VOLUME_KNOB_POSITION_LEFT -> getString(R.string.left)
                        UserInterfacePreferences.VOLUME_KNOB_POSITION_CENTER -> getString(R.string.center)
                        UserInterfacePreferences.VOLUME_KNOB_POSITION_RIGHT -> getString(R.string.right)
                        else -> getString(R.string.center)
                    }
                },
                onPreferenceAction = { view, _ ->
                    SharedScrollViewPopup(
                            container = requireContainerView(),
                            anchorView = view,
                            menuItems = listOf(
                                    PopupMenuItem(title = R.string.left),
                                    PopupMenuItem(title = R.string.center),
                                    PopupMenuItem(title = R.string.right)
                            ),
                            onMenuItemClick = {
                                when (it) {
                                    R.string.left -> {
                                        UserInterfacePreferences.setVolumeKnobPosition(
                                                UserInterfacePreferences.VOLUME_KNOB_POSITION_LEFT)
                                        (view as TextView).text = getString(R.string.left)
                                    }
                                    R.string.center -> {
                                        UserInterfacePreferences.setVolumeKnobPosition(
                                                UserInterfacePreferences.VOLUME_KNOB_POSITION_CENTER)
                                        (view as TextView).text = getString(R.string.center)
                                    }
                                    R.string.right -> {
                                        UserInterfacePreferences.setVolumeKnobPosition(
                                                UserInterfacePreferences.VOLUME_KNOB_POSITION_RIGHT)
                                        (view as TextView).text = getString(R.string.right)
                                    }
                                }
                            },
                            onDismiss = {}
                    ).show()
                }
        )

        val weatherEffect = Preference(
                title = R.string.weather_effect,
                summary = R.string.weather_effect_summary,
                icon = R.drawable.ic_cloud,
                type = PreferenceType.POPUP,
                valueProvider = {
                    when (UserInterfacePreferences.getWeatherEffect()) {
                        UserInterfacePreferences.WEATHER_EFFECT_OFF -> getString(R.string.off)
                        UserInterfacePreferences.WEATHER_EFFECT_AUTO -> getString(R.string.auto)
                        UserInterfacePreferences.WEATHER_EFFECT_WINTER -> getString(R.string.winter)
                        UserInterfacePreferences.WEATHER_EFFECT_AUTUMN -> getString(R.string.autumn)
                        UserInterfacePreferences.WEATHER_EFFECT_SUMMER -> getString(R.string.summer)
                        else -> getString(R.string.auto)
                    }
                },
                onPreferenceAction = { view, _ ->
                    SharedScrollViewPopup(
                            container = requireContainerView(),
                            anchorView = view,
                            menuItems = listOf(
                                    PopupMenuItem(title = R.string.off),
                                    PopupMenuItem(title = R.string.auto),
                                    PopupMenuItem(title = R.string.winter),
                                    PopupMenuItem(title = R.string.autumn),
                                    PopupMenuItem(title = R.string.summer)
                            ),
                            onMenuItemClick = {
                                when (it) {
                                    R.string.off -> {
                                        UserInterfacePreferences.setWeatherEffect(UserInterfacePreferences.WEATHER_EFFECT_OFF)
                                        (view as TextView).text = getString(R.string.off)
                                    }
                                    R.string.auto -> {
                                        UserInterfacePreferences.setWeatherEffect(UserInterfacePreferences.WEATHER_EFFECT_AUTO)
                                        (view as TextView).text = getString(R.string.auto)
                                    }
                                    R.string.winter -> {
                                        UserInterfacePreferences.setWeatherEffect(UserInterfacePreferences.WEATHER_EFFECT_WINTER)
                                        (view as TextView).text = getString(R.string.winter)
                                    }
                                    R.string.autumn -> {
                                        UserInterfacePreferences.setWeatherEffect(UserInterfacePreferences.WEATHER_EFFECT_AUTUMN)
                                        (view as TextView).text = getString(R.string.autumn)
                                    }
                                    R.string.summer -> {
                                        UserInterfacePreferences.setWeatherEffect(UserInterfacePreferences.WEATHER_EFFECT_SUMMER)
                                        (view as TextView).text = getString(R.string.summer)
                                    }
                                }
                            },
                            onDismiss = {}
                    ).show()
                }
        )

        preferences.add(homeHeader)
        preferences.add(panelVisibility)
        preferences.add(homeInterface)
        preferences.add(playerHeader)
        preferences.add(playerInterface)
        preferences.add(lyricsToggle)
        preferences.add(statusBarLyrics)
        preferences.add(stackMediaControlsToggle)
        preferences.add(showForwardAndRewindButtons)
        preferences.add(immersiveMode)
        preferences.add(pagesHeader)
        preferences.add(songsFirstToggle)
        preferences.add(miniPlayerHeader)
        preferences.add(miniplayerWidth)
        preferences.add(marginAroundMiniplayerToggle)
        preferences.add(applicationHeader)
        preferences.add(likeButton)
        preferences.add(volumeControls)
        preferences.add(volumeKnobPosition)
        preferences.add(weatherEffect)

        return preferences
    }

    /**
     * Status bar lyrics panel.
     *
     * Everything here is deliberately live: the overlay service listens to the same
     * preference keys, so dragging a slider or picking a colour updates the floating
     * window immediately while music is playing.
     */
    protected fun createStatusBarLyricsPanel(): List<Preference> {
        val preferences = mutableListOf<Preference>()

        val enable = Preference(
                title = R.string.status_bar_lyrics,
                summary = R.string.status_bar_lyrics_enable_summary,
                icon = R.drawable.ic_lyrics,
                type = PreferenceType.SWITCH,
                valueProvider = Supplier {
                    StatusBarLyricsPreferences.isEnabled()
                },
                onPreferenceAction = { view, _ ->
                    val switch = view as FelicitySwitch
                    val context = requireContext()

                    if (switch.isChecked) {
                        if (!Settings.canDrawOverlays(context)) {
                            switch.setChecked(false, false)
                            pendingStatusBarLyricsSwitch = switch
                            Toast.makeText(
                                    context,
                                    R.string.status_bar_lyrics_permission_missing,
                                    Toast.LENGTH_LONG
                            ).show()
                            openStatusBarLyricsPermissionSettings()
                        } else {
                            StatusBarLyricsPreferences.setEnabled(true)
                            StatusBarLyricsService.startIfEnabled(context)
                        }
                    } else {
                        StatusBarLyricsPreferences.setEnabled(false)
                        StatusBarLyricsService.stop(context)
                    }
                }
        )

        val note = Preference(
                title = R.string.status_bar_lyrics_how_it_works,
                summary = R.string.status_bar_lyrics_note,
                icon = -1,
                type = PreferenceType.INFO
        )

        val permission = Preference(
                title = R.string.status_bar_lyrics_permission,
                summary = R.string.status_bar_lyrics_permission_summary,
                icon = R.drawable.ic_locked,
                type = PreferenceType.NORMAL,
                onPreferenceAction = { _, _ ->
                    openStatusBarLyricsPermissionSettings()
                }
        )

        val appearanceHeader = Preference(
                title = R.string.status_bar_lyrics_appearance,
                type = PreferenceType.SUB_HEADER
        )

        val width = Preference(
                title = R.string.status_bar_lyrics_width,
                summary = R.string.status_bar_lyrics_width_summary,
                icon = R.drawable.ic_status_bar_lyrics_width,
                type = PreferenceType.SLIDER,
                onPreferenceAction = { view, _ ->
                    StatusBarLyricsPreferences.setWidth((view as FelicitySeekbar).getProgress().toInt())
                },
                valueProvider = Supplier {
                    SeekbarState(
                            position = StatusBarLyricsPreferences.getWidth().toFloat(),
                            max = StatusBarLyricsPreferences.MAX_WIDTH.toFloat(),
                            min = StatusBarLyricsPreferences.MIN_WIDTH.toFloat(),
                            default = StatusBarLyricsPreferences.DEFAULT_WIDTH.toFloat(),
                            leftLabel = true,
                            leftLabelProvider = { progress, _, _ -> "${progress.toInt()} dp" }
                    )
                }
        )

        val lineCount = Preference(
                title = R.string.status_bar_lyrics_line_count,
                summary = R.string.status_bar_lyrics_line_count_summary,
                icon = R.drawable.ic_status_bar_lyrics_line_count,
                type = PreferenceType.SLIDER,
                onPreferenceAction = { view, _ ->
                    StatusBarLyricsPreferences.setLineCount((view as FelicitySeekbar).getProgress().toInt())
                },
                valueProvider = Supplier {
                    SeekbarState(
                            position = StatusBarLyricsPreferences.getLineCount().toFloat(),
                            max = StatusBarLyricsPreferences.MAX_LINE_COUNT.toFloat(),
                            min = StatusBarLyricsPreferences.MIN_LINE_COUNT.toFloat(),
                            default = StatusBarLyricsPreferences.DEFAULT_LINE_COUNT.toFloat(),
                            leftLabel = true,
                            leftLabelProvider = { progress, _, _ -> "${progress.toInt()}" }
                    )
                }
        )

        val textSize = Preference(
                title = R.string.status_bar_lyrics_text_size,
                summary = R.string.status_bar_lyrics_text_size_summary,
                icon = R.drawable.ic_alphabet,
                type = PreferenceType.SLIDER,
                onPreferenceAction = { view, _ ->
                    StatusBarLyricsPreferences.setTextSize((view as FelicitySeekbar).getProgress())
                },
                valueProvider = Supplier {
                    SeekbarState(
                            position = StatusBarLyricsPreferences.getTextSize(),
                            max = StatusBarLyricsPreferences.MAX_TEXT_SIZE,
                            min = StatusBarLyricsPreferences.MIN_TEXT_SIZE,
                            default = StatusBarLyricsPreferences.DEFAULT_TEXT_SIZE,
                            leftLabel = true,
                            leftLabelProvider = { progress, _, _ -> "${progress.toInt()} sp" }
                    )
                }
        )

        // Opens the HSV colour wheel instead of a preset drop-down. The dialog writes
        // the chosen colour to the preference live, so the overlay repaints while the
        // wheel is being dragged; the adapter callback refreshes this row's value text.
        // Only the hex value is shown — with a free-form wheel, colour names are
        // meaningless for arbitrary picks.
        val textColor = Preference(
                title = R.string.status_bar_lyrics_text_color,
                summary = R.string.status_bar_lyrics_text_color_summary,
                icon = R.drawable.ic_palette,
                type = PreferenceType.POPUP,
                valueProvider = {
                    StatusBarLyricsPreferences.colorHex(StatusBarLyricsPreferences.getTextColor())
                },
                onPreferenceAction = { _, refresh ->
                    StatusBarLyricsColorPicker.newInstance(StatusBarLyricsColorPicker.Target.TEXT) { refresh(null) }
                            .show(childFragmentManager, StatusBarLyricsColorPicker.TAG)
                }
        )

        val stroke = Preference(
                title = R.string.status_bar_lyrics_stroke,
                summary = R.string.status_bar_lyrics_stroke_summary,
                icon = R.drawable.ic_text_fields,
                type = PreferenceType.SWITCH,
                onPreferenceAction = { view, _ ->
                    StatusBarLyricsPreferences.setStroke((view as FelicitySwitch).isChecked)
                },
                valueProvider = Supplier {
                    StatusBarLyricsPreferences.isStroke()
                }
        )

        // Outline colour uses the same HSV wheel as the text colour, but writes to a
        // different preference. The dialog reads/writes by target and refreshes this row.
        val strokeColor = Preference(
                title = R.string.status_bar_lyrics_stroke_color,
                summary = R.string.status_bar_lyrics_stroke_color_summary,
                icon = R.drawable.ic_palette,
                type = PreferenceType.POPUP,
                valueProvider = {
                    StatusBarLyricsPreferences.colorHex(StatusBarLyricsPreferences.getStrokeColor())
                },
                onPreferenceAction = { _, refresh ->
                    StatusBarLyricsColorPicker.newInstance(StatusBarLyricsColorPicker.Target.STROKE) { refresh(null) }
                            .show(childFragmentManager, StatusBarLyricsColorPicker.TAG)
                }
        )

        val strokeWidth = Preference(
                title = R.string.status_bar_lyrics_stroke_width,
                summary = R.string.status_bar_lyrics_stroke_width_summary,
                icon = R.drawable.ic_status_bar_lyrics_stroke_width,
                type = PreferenceType.SLIDER,
                onPreferenceAction = { view, _ ->
                    StatusBarLyricsPreferences.setStrokeWidth((view as FelicitySeekbar).getProgress())
                },
                valueProvider = Supplier {
                    SeekbarState(
                            position = StatusBarLyricsPreferences.getStrokeWidth(),
                            max = StatusBarLyricsPreferences.MAX_STROKE_WIDTH,
                            min = StatusBarLyricsPreferences.MIN_STROKE_WIDTH,
                            default = StatusBarLyricsPreferences.DEFAULT_STROKE_WIDTH,
                            leftLabel = true,
                            leftLabelProvider = { progress, _, _ -> String.format("%.1f dp", progress) }
                    )
                }
        )

        val alignment = Preference(
                title = R.string.status_bar_lyrics_alignment,
                summary = R.string.status_bar_lyrics_alignment_summary,
                icon = R.drawable.ic_align_center,
                type = PreferenceType.BUTTON_GROUP,
                valueProvider = Supplier {
                    ButtonGroupState(
                            buttons = listOf(
                                    Button(iconResId = R.drawable.ic_align_left_12dp),
                                    Button(iconResId = R.drawable.ic_align_center_12dp),
                                    Button(iconResId = R.drawable.ic_align_right_12dp)
                            ),
                            selectedIndex = StatusBarLyricsPreferences.getAlignment()
                    )
                },
                onPreferenceAction = { view, _ ->
                    val index = (view as FelicityButtonGroup).getSelectedIndex()
                    StatusBarLyricsPreferences.setAlignment(index)
                }
        )

        val hideTranslation = Preference(
                title = R.string.status_bar_lyrics_hide_translation,
                summary = R.string.status_bar_lyrics_hide_translation_summary,
                icon = R.drawable.ic_translate,
                type = PreferenceType.SWITCH,
                onPreferenceAction = { view, _ ->
                    StatusBarLyricsPreferences.setHideTranslation((view as FelicitySwitch).isChecked)
                },
                valueProvider = Supplier {
                    StatusBarLyricsPreferences.isHideTranslation()
                }
        )

        val positionHeader = Preference(
                title = R.string.status_bar_lyrics_position,
                type = PreferenceType.SUB_HEADER
        )

        val horizontalPosition = Preference(
                title = R.string.status_bar_lyrics_horizontal_position,
                summary = R.string.status_bar_lyrics_horizontal_position_summary,
                icon = R.drawable.ic_align_center,
                type = PreferenceType.SLIDER,
                onPreferenceAction = { view, _ ->
                    StatusBarLyricsPreferences.setHorizontalBias(
                            (view as FelicitySeekbar).getProgress() / 100f
                    )
                },
                valueProvider = Supplier {
                    SeekbarState(
                            position = StatusBarLyricsPreferences.getHorizontalBias() * 100f,
                            max = 100f,
                            min = 0f,
                            default = StatusBarLyricsPreferences.DEFAULT_HORIZONTAL_BIAS * 100f,
                            leftLabel = true,
                            leftLabelProvider = { progress, _, _ -> "${progress.toInt()} %" }
                    )
                }
        )

        val verticalPosition = Preference(
                title = R.string.status_bar_lyrics_vertical_position,
                summary = R.string.status_bar_lyrics_vertical_position_summary,
                icon = R.drawable.ic_vertical_align_center,
                type = PreferenceType.SLIDER,
                onPreferenceAction = { view, _ ->
                    StatusBarLyricsPreferences.setVerticalOffset(
                            (view as FelicitySeekbar).getProgress().toInt()
                    )
                },
                valueProvider = Supplier {
                    SeekbarState(
                            position = StatusBarLyricsPreferences.getVerticalOffset().toFloat(),
                            max = StatusBarLyricsPreferences.MAX_VERTICAL_OFFSET.toFloat(),
                            min = StatusBarLyricsPreferences.MIN_VERTICAL_OFFSET.toFloat(),
                            default = StatusBarLyricsPreferences.DEFAULT_VERTICAL_OFFSET.toFloat(),
                            leftLabel = true,
                            leftLabelProvider = { progress, _, _ ->
                                val value = progress.toInt()
                                when {
                                    value <= StatusBarLyricsPreferences.MIN_VERTICAL_OFFSET -> getString(R.string.status_bar_lyrics_position_top)
                                    value >= StatusBarLyricsPreferences.MAX_VERTICAL_OFFSET -> getString(R.string.status_bar_lyrics_position_bottom)
                                    else -> "${(progress * 100f / StatusBarLyricsPreferences.MAX_VERTICAL_OFFSET).toInt()} %"
                                }
                            }
                    )
                }
        )

        val hideOnPlayer = Preference(
                title = R.string.status_bar_lyrics_hide_on_player,
                summary = R.string.status_bar_lyrics_hide_on_player_summary,
                icon = R.drawable.ic_fullscreen,
                type = PreferenceType.SWITCH,
                onPreferenceAction = { view, _ ->
                    StatusBarLyricsPreferences.setHideOnPlayerScreen((view as FelicitySwitch).isChecked)
                },
                valueProvider = Supplier {
                    StatusBarLyricsPreferences.isHideOnPlayerScreen()
                }
        )

        val hideOnLyrics = Preference(
                title = R.string.status_bar_lyrics_hide_on_lyrics,
                summary = R.string.status_bar_lyrics_hide_on_lyrics_summary,
                icon = R.drawable.ic_lyrics,
                type = PreferenceType.SWITCH,
                onPreferenceAction = { view, _ ->
                    StatusBarLyricsPreferences.setHideOnLyricsScreen((view as FelicitySwitch).isChecked)
                },
                valueProvider = Supplier {
                    StatusBarLyricsPreferences.isHideOnLyricsScreen()
                }
        )

        val resetPosition = Preference(
                title = R.string.status_bar_lyrics_reset_position,
                summary = R.string.status_bar_lyrics_reset_position_summary,
                icon = R.drawable.ic_refresh,
                type = PreferenceType.NORMAL,
                onPreferenceAction = { _, _ ->
                    StatusBarLyricsPreferences.resetPosition()
                    Toast.makeText(requireContext(), R.string.status_bar_lyrics_position_reset, Toast.LENGTH_SHORT).show()
                    binding.recyclerView.adapter?.notifyDataSetChanged()
                }
        )

        preferences.add(enable)
        preferences.add(note)
        preferences.add(permission)
        preferences.add(appearanceHeader)
        preferences.add(width)
        preferences.add(lineCount)
        preferences.add(textSize)
        preferences.add(textColor)
        preferences.add(stroke)
        preferences.add(strokeColor)
        preferences.add(strokeWidth)
        preferences.add(alignment)
        preferences.add(hideTranslation)
        preferences.add(positionHeader)
        preferences.add(horizontalPosition)
        preferences.add(verticalPosition)
        preferences.add(hideOnPlayer)
        preferences.add(hideOnLyrics)
        preferences.add(resetPosition)

        return preferences
    }

    /**
     * Opens the system screen that controls the "display over other apps" permission.
     * Some OEMs rename or replace it, so fall back to the generic action if needed.
     */
    private fun openStatusBarLyricsPermissionSettings() {
        val context = requireContext()
        val packageUri = Uri.parse("package:${context.packageName}")

        runCatching {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, packageUri))
        }.onFailure {
            runCatching {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
            }.onFailure {
                Log.w("PreferenceFragment", "Unable to open overlay permission settings", it)
            }
        }
    }

    protected fun createConfigurationPreferences(): List<Preference> {
        val preferences = mutableListOf<Preference>()

        val applicationHeader = Preference(type = PreferenceType.SUB_HEADER, title = R.string.application)

        val keepScreenOn = Preference(
                title = R.string.keep_screen_on,
                summary = R.string.keep_screen_on_summary,
                icon = R.drawable.ic_keep_screen_on,
                type = PreferenceType.SWITCH,
                onPreferenceAction = { view, callback ->
                    ConfigurationPreferences.setKeepScreenOn((view as FelicitySwitch).isChecked)
                },
                valueProvider = Supplier {
                    ConfigurationPreferences.isKeepScreenOn()
                }
        )

        val language = Preference(
                title = R.string.language,
                summary = R.string.language_summary,
                icon = R.drawable.ic_translate,
                type = PreferenceType.PANEL,
                onPreferenceAction = { view, callback ->
                    openFragment(Language.newInstance(), Language.TAG)
                },
        )

        preferences.add(applicationHeader)
        preferences.add(keepScreenOn)
        preferences.add(language)

        return preferences
    }

    protected fun createBehaviorPanel(): List<Preference> {
        val preferences = mutableListOf<Preference>()

        // List Preference
        val listHeader = Preference(type = PreferenceType.SUB_HEADER, title = R.string.list)

        val fastScrollBehavior = Preference(
                title = R.string.fast_scroll_behavior,
                summary = R.string.fast_scroll_behavior_summary,
                icon = R.drawable.ic_swipe_vertical,
                type = PreferenceType.POPUP,
                valueProvider = {
                    when (BehaviourPreferences.getFastScrollBehavior()) {
                        BehaviourPreferences.HIDE_FAST_SCROLLBAR -> getString(R.string.hide)
                        BehaviourPreferences.FADE_FAST_SCROLLBAR -> getString(R.string.fade)
                        else -> getString(R.string.fade)
                    }
                },
                onPreferenceAction = { view, _ ->
                    SharedScrollViewPopup(
                            container = requireContainerView(),
                            anchorView = view,
                            menuItems = listOf(
                                    PopupMenuItem(title = R.string.hide),
                                    PopupMenuItem(title = R.string.fade)
                            ),
                            onMenuItemClick = {
                                when (it) {
                                    R.string.hide -> {
                                        BehaviourPreferences.setFastScrollBehavior(BehaviourPreferences.HIDE_FAST_SCROLLBAR)
                                        (view as TextView).text = getString(R.string.hide)
                                    }
                                    R.string.fade -> {
                                        BehaviourPreferences.setFastScrollBehavior(BehaviourPreferences.FADE_FAST_SCROLLBAR)
                                        (view as TextView).text = getString(R.string.fade)
                                    }
                                }
                            },
                            onDismiss = {}
                    ).show()
                }
        )

        val applicationHeader = Preference(type = PreferenceType.SUB_HEADER, title = R.string.application)

        val predictiveBackToggle = Preference(
                title = R.string.predictive_back,
                summary = R.string.predictive_back_summary,
                icon = R.drawable.ic_back_gesture,
                type = PreferenceType.SWITCH,
                valueProvider = {
                    BehaviourPreferences.isPredictiveBackEnabled()
                },
                onPreferenceAction = { view, callback ->
                    val isChecked = (view as FelicitySwitch).isChecked
                    BehaviourPreferences.setPredictiveBack(isChecked)
                }
        )

        val hapticToggle = Preference(
                title = R.string.haptic_feedback,
                summary = R.string.haptic_feedback_summary,
                icon = R.drawable.ic_vibration,
                type = PreferenceType.SWITCH,
                valueProvider = {
                    BehaviourPreferences.isHapticFeedbackEnabled()
                },
                onPreferenceAction = { view, callback ->
                    val isChecked = (view as FelicitySwitch).isChecked
                    BehaviourPreferences.setHapticFeedback(isChecked)
                }
        )

        val fragmentTransition = Preference(
                title = R.string.transition,
                summary = R.string.transition_summary,
                icon = -1,
                type = PreferenceType.POPUP,
                valueProvider = {
                    when (BehaviourPreferences.getFragmentTransition()) {
                        BehaviourPreferences.TRANSITION_Z -> getString(R.string.depth)
                        BehaviourPreferences.TRANSITION_X -> getString(R.string.drift)
                        else -> getString(R.string.fade)
                    }
                },
                onPreferenceAction = { view, _ ->
                    SharedScrollViewPopup(
                            container = requireContainerView(),
                            anchorView = view,
                            menuItems = listOf(
                                    PopupMenuItem(title = R.string.fade),
                                    PopupMenuItem(title = R.string.depth),
                                    PopupMenuItem(title = R.string.drift)
                            ),
                            onMenuItemClick = {
                                when (it) {
                                    R.string.fade -> {
                                        BehaviourPreferences.setFragmentTransition(BehaviourPreferences.TRANSITION_FADE)
                                        (view as TextView).text = getString(R.string.fade)
                                    }
                                    R.string.depth -> {
                                        BehaviourPreferences.setFragmentTransition(BehaviourPreferences.TRANSITION_Z)
                                        (view as TextView).text = getString(R.string.depth)
                                    }
                                    R.string.drift -> {
                                        BehaviourPreferences.setFragmentTransition(BehaviourPreferences.TRANSITION_X)
                                        (view as TextView).text = getString(R.string.drift)
                                    }
                                }
                            },
                            onDismiss = {}
                    ).show()
                }
        )

        val miniplayerHeader = Preference(type = PreferenceType.SUB_HEADER, title = R.string.miniplayer)

        val miniPlayerVisibilityToggle = Preference(
                title = R.string.keep_mini_player_visible,
                summary = R.string.keep_mini_player_visible_summary,
                icon = -1,
                type = PreferenceType.SWITCH,
                valueProvider = {
                    BehaviourPreferences.isMiniplayerAlwaysVisible()
                },
                onPreferenceAction = { view, callback ->
                    val isChecked = (view as FelicitySwitch).isChecked
                    BehaviourPreferences.setMiniplayerAlwaysVisible(isChecked)
                }
        )

        val playerHeader = Preference(type = PreferenceType.SUB_HEADER, title = R.string.player)

        val textChangeEffect = Preference(
                title = R.string.text_change_effect,
                summary = R.string.text_change_effect_summary,
                icon = -1,
                type = PreferenceType.POPUP,
                valueProvider = {
                    when (BehaviourPreferences.getTextChangeEffect()) {
                        BehaviourPreferences.TEXT_EFFECT_NONE -> getString(R.string.no_effect)
                        BehaviourPreferences.TEXT_EFFECT_FADE -> getString(R.string.fade)
                        BehaviourPreferences.TEXT_EFFECT_SLIDE -> getString(R.string.slide)
                        BehaviourPreferences.TEXT_EFFECT_TYPEWRITING -> getString(R.string.typewriting)
                        else -> getString(R.string.typewriting)
                    }
                },
                onPreferenceAction = { view, callback ->
                    SharedScrollViewPopup(
                            container = requireContainerView(),
                            anchorView = view,
                            menuItems = listOf(
                                    PopupMenuItem(title = R.string.no_effect),
                                    PopupMenuItem(title = R.string.fade),
                                    PopupMenuItem(title = R.string.slide),
                                    PopupMenuItem(title = R.string.typewriting)
                            ),
                            onMenuItemClick = {
                                val effect = when (it) {
                                    R.string.no_effect -> BehaviourPreferences.TEXT_EFFECT_NONE
                                    R.string.fade -> BehaviourPreferences.TEXT_EFFECT_FADE
                                    R.string.slide -> BehaviourPreferences.TEXT_EFFECT_SLIDE
                                    else -> BehaviourPreferences.TEXT_EFFECT_TYPEWRITING
                                }
                                BehaviourPreferences.setTextChangeEffect(effect)
                                (view as TextView).text = getString(it)
                            },
                            onDismiss = {}
                    ).show()
                }
        )

        preferences.add(listHeader)
        preferences.add(fastScrollBehavior)
        preferences.add(applicationHeader)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            preferences.add(predictiveBackToggle)
        }
        preferences.add(hapticToggle)
        preferences.add(fragmentTransition)
        preferences.add(miniplayerHeader)
        preferences.add(miniPlayerVisibilityToggle)
        preferences.add(playerHeader)
        preferences.add(textChangeEffect)

        return preferences
    }

    protected fun createEnginePanel(): List<Preference> {
        val preferences = mutableListOf<Preference>()

        val decoderHeader = Preference(type = PreferenceType.SUB_HEADER, title = R.string.decoder)

        val currentDecoder = Preference(
                title = R.string.decoder,
                summary = R.string.audio_pipeline_summary,
                icon = R.drawable.ic_memory,
                type = PreferenceType.POPUP,
                valueProvider = {
                    when (AudioPreferences.getAudioDecoder()) {
                        AudioPreferences.LOCAL_DECODER -> getString(R.string.system_hardware)
                        AudioPreferences.FFMPEG -> getString(R.string.ffmpeg)
                        else -> getString(R.string.system_hardware)
                    }
                },
                onPreferenceAction = { view, callback ->
                    SharedScrollViewPopup(
                            container = requireContainerView(),
                            anchorView = view,
                            menuItems = listOf(
                                    PopupMenuItem(title = R.string.system_hardware),
                                    PopupMenuItem(title = R.string.ffmpeg)
                            ),
                            onMenuItemClick = {
                                when (it) {
                                    R.string.system_hardware -> {
                                        AudioPreferences.setAudioDecoder(AudioPreferences.LOCAL_DECODER)
                                        (view as TextView).text = getString(R.string.system_hardware)
                                    }
                                    R.string.ffmpeg -> {
                                        AudioPreferences.setAudioDecoder(AudioPreferences.FFMPEG)
                                        (view as TextView).text = getString(R.string.ffmpeg)
                                    }
                                }
                            },
                            onDismiss = {

                            }
                    ).show()
                }
        )

        val fallbackToSWToggle = Preference(
                title = R.string.fallback_to_software_decoder,
                summary = R.string.fallback_to_software_decoder_summary,
                icon = R.drawable.ic_warning_12dp,
                type = PreferenceType.SWITCH,
                onPreferenceAction = { view, callback ->
                    AudioPreferences.setFallbackToSoftwareDecoder((view as FelicitySwitch).isChecked)
                },
                valueProvider = Supplier {
                    AudioPreferences.isFallbackToSoftwareDecoderEnabled()
                }
        )

        val outputHeader = Preference(type = PreferenceType.SUB_HEADER, title = R.string.output)

        /**
         * Popup that lets the user choose which low-level audio API writes the final PCM
         * to the hardware. AudioTrack is the safe default; AAudio bypasses the mixer for
         * lower latency; Oboe picks the best available API automatically at runtime.
         */
        val sinkPopup = Preference(
                title = R.string.output_sink,
                summary = R.string.output_sink_summary,
                icon = R.drawable.ic_speaker,
                type = PreferenceType.POPUP,
                valueProvider = {
                    when (AudioPreferences.getOutputSink()) {
                        AudioPreferences.SINK_AAUDIO -> getString(R.string.aaudio_enabled)
                        AudioPreferences.SINK_OBOE -> getString(R.string.oboe)
                        else -> getString(R.string.audiotrack)
                    }
                },
                onPreferenceAction = { view, _ ->
                    SharedScrollViewPopup(
                            container = requireContainerView(),
                            anchorView = view,
                            menuItems = listOf(
                                    PopupMenuItem(title = R.string.audiotrack, summary = getString(R.string.audiotrack_desc)),
                                    PopupMenuItem(title = R.string.aaudio_enabled, summary = getString(R.string.aaudio_desc)),
                                    PopupMenuItem(title = R.string.oboe, summary = getString(R.string.oboe_desc))
                            ),
                            onMenuItemClick = {
                                when (it) {
                                    R.string.aaudio_enabled -> {
                                        AudioPreferences.setOutputSink(AudioPreferences.SINK_AAUDIO)
                                        (view as TextView).text = getString(R.string.aaudio_enabled)
                                    }
                                    R.string.oboe -> {
                                        AudioPreferences.setOutputSink(AudioPreferences.SINK_OBOE)
                                        (view as TextView).text = getString(R.string.oboe)
                                    }
                                    else -> {
                                        AudioPreferences.setOutputSink(AudioPreferences.SINK_AUDIO_TRACK)
                                        (view as TextView).text = getString(R.string.audiotrack)
                                    }
                                }
                            },
                            onDismiss = {}
                    ).show()
                }
        )

        val usbDacControl = Preference(
                title = R.string.usb_dac,
                summary = R.string.usb_dac_control_summary,
                icon = R.drawable.ic_usb,
                type = PreferenceType.SWITCH,
                onPreferenceAction = { view, callback ->
                    AudioPreferences.setUsbDac((view as FelicitySwitch).isChecked)
                },
                valueProvider = Supplier {
                    AudioPreferences.isUsbDacEnabled()
                }
        )

        val hiresToggle = Preference(
                title = R.string.high_resolution_output,
                summary = R.string.high_resolution_output_summary,
                icon = R.drawable.ic_hires_12dp,
                type = PreferenceType.SWITCH,
                onPreferenceAction = { view, callback ->
                    AudioPreferences.setHiresOutput((view as FelicitySwitch).isChecked)
                },
                valueProvider = Supplier {
                    AudioPreferences.isHiresOutputEnabled()
                }
        )

        val crossfadeHeader = Preference(type = PreferenceType.SUB_HEADER, title = R.string.crossfade)

        val crossfadeToggle = Preference(
                title = R.string.crossfade,
                summary = R.string.crossfade_summary,
                icon = R.drawable.ic_join_right,
                type = PreferenceType.SWITCH,
                onPreferenceAction = { view, callback ->
                    AudioPreferences.setCrossfadeEnabled((view as FelicitySwitch).isChecked)
                },
                valueProvider = Supplier {
                    AudioPreferences.isCrossfadeEnabled()
                }
        )

        val crossfadeManualSkipToggle = Preference(
                title = R.string.crossfade_manual_skips,
                summary = R.string.crossfade_manual_skips_summary,
                icon = -1,
                type = PreferenceType.SWITCH,
                onPreferenceAction = { view, callback ->
                    AudioPreferences.setCrossfadeManualSkip((view as FelicitySwitch).isChecked)
                },
                valueProvider = Supplier {
                    AudioPreferences.isCrossfadeManualSkipEnabled()
                }
        )

        val crossfadeDurationSlider = Preference(
                title = R.string.crossfade_duration,
                summary = R.string.crossfade_duration_summary,
                icon = R.drawable.ic_timelapse,
                type = PreferenceType.SLIDER,
                valueProvider = {
                    Log.d(TAG, AudioPreferences.getCrossfadeDuration().toString())
                    SeekbarState(
                            position = AudioPreferences.getCrossfadeDuration().toFloat(),
                            max = 10000F,
                            min = 1000F,
                            stepSize = 500F,
                            default = AudioPreferences.DEFAULT_CROSSFADE_DURATION.toFloat(),
                            leftLabel = false,
                            rightLabel = true,
                            rightLabelProvider = { progress, _, _ ->
                                String.format(Locale.getDefault(), "%.1f s", progress / 1000f)
                            },
                    )
                },
                onPreferenceAction = { view, _ ->
                    AudioPreferences.setCrossfadeDuration((view as FelicitySeekbar).getProgress().toLong())
                }
        )

        val visualizerHeader = Preference(type = PreferenceType.SUB_HEADER, title = R.string.visualizer)

        val fftResolutionPopup = Preference(
                title = R.string.fft_resolution,
                summary = R.string.fft_resolution_summary,
                icon = R.drawable.ic_graphic_eq,
                type = PreferenceType.POPUP,
                valueProvider = {
                    when (AudioPreferences.getFftResolution()) {
                        AudioPreferences.FFT_RESOLUTION_VERY_LOW -> getString(R.string.fft_resolution_very_low)
                        AudioPreferences.FFT_RESOLUTION_LOW -> getString(R.string.fft_resolution_low)
                        AudioPreferences.FFT_RESOLUTION_HIGH -> getString(R.string.fft_resolution_high)
                        AudioPreferences.FFT_RESOLUTION_ULTRA -> getString(R.string.fft_resolution_ultra)
                        else -> getString(R.string.fft_resolution_normal)
                    }
                },
                onPreferenceAction = { view, _ ->
                    SharedScrollViewPopup(
                            container = requireContainerView(),
                            anchorView = view,
                            menuItems = listOf(
                                    PopupMenuItem(title = R.string.fft_resolution_very_low),
                                    PopupMenuItem(title = R.string.fft_resolution_low),
                                    PopupMenuItem(title = R.string.fft_resolution_normal),
                                    PopupMenuItem(title = R.string.fft_resolution_high),
                                    PopupMenuItem(title = R.string.fft_resolution_ultra)
                            ),
                            onMenuItemClick = {
                                when (it) {
                                    R.string.fft_resolution_very_low -> {
                                        AudioPreferences.setFftResolution(AudioPreferences.FFT_RESOLUTION_VERY_LOW)
                                        (view as TextView).text = getString(R.string.fft_resolution_very_low)
                                    }
                                    R.string.fft_resolution_low -> {
                                        AudioPreferences.setFftResolution(AudioPreferences.FFT_RESOLUTION_LOW)
                                        (view as TextView).text = getString(R.string.fft_resolution_low)
                                    }
                                    R.string.fft_resolution_high -> {
                                        AudioPreferences.setFftResolution(AudioPreferences.FFT_RESOLUTION_HIGH)
                                        (view as TextView).text = getString(R.string.fft_resolution_high)
                                    }
                                    R.string.fft_resolution_ultra -> {
                                        AudioPreferences.setFftResolution(AudioPreferences.FFT_RESOLUTION_ULTRA)
                                        (view as TextView).text = getString(R.string.fft_resolution_ultra)
                                    }
                                    else -> {
                                        AudioPreferences.setFftResolution(AudioPreferences.FFT_RESOLUTION_NORMAL)
                                        (view as TextView).text = getString(R.string.fft_resolution_normal)
                                    }
                                }
                            },
                            onDismiss = {}
                    ).show()
                }
        )

        val playbackHeader = Preference(type = PreferenceType.SUB_HEADER, title = R.string.playback)

        val stereoDownmixing = Preference(
                title = R.string.force_stereo_downmixing,
                summary = R.string.force_stereo_downmixing_summary,
                icon = R.drawable.ic_headset,
                type = PreferenceType.SWITCH,
                onPreferenceAction = { view, callback ->
                    AudioPreferences.setIsStereoDownmixForced((view as FelicitySwitch).isChecked)
                },
                valueProvider = Supplier {
                    AudioPreferences.isStereoDownmixForced()
                }
        )

        val gaplessToggle = Preference(
                title = R.string.gapless_playback,
                summary = R.string.gapless_playback_summary,
                icon = R.drawable.ic_join,
                type = PreferenceType.SWITCH,
                onPreferenceAction = { view, callback ->
                    AudioPreferences.setGaplessPlayback((view as FelicitySwitch).isChecked)
                },
                valueProvider = Supplier {
                    AudioPreferences.isGaplessPlaybackEnabled()
                }
        )

        val skipSilenceToggle = Preference(
                title = R.string.skip_silence,
                summary = R.string.skip_silence_summary,
                icon = R.drawable.ic_skip,
                type = PreferenceType.SWITCH,
                onPreferenceAction = { view, callback ->
                    AudioPreferences.setSkipSilence((view as FelicitySwitch).isChecked)
                },
                valueProvider = Supplier {
                    AudioPreferences.isSkipSilenceEnabled()
                }
        )

        val replayGainHeader = Preference(type = PreferenceType.SUB_HEADER, title = R.string.replay_gain)

        /**
         * Master switch for tag-based ReplayGain. When turned on, the player reads the
         * REPLAYGAIN_TRACK_GAIN or REPLAYGAIN_ALBUM_GAIN tag from each file and applies
         * the encoded gain automatically every time a new track starts. The manual preamp
         * knob on the equalizer screen still works on top of this.
         */
        val autoReplayGainToggle = Preference(
                title = R.string.auto_replay_gain,
                summary = R.string.auto_replay_gain_summary,
                icon = R.drawable.ic_volume_up,
                type = PreferenceType.SWITCH,
                onPreferenceAction = { view, _ ->
                    EqualizerPreferences.setAutoReplayGainEnabled((view as FelicitySwitch).isChecked)
                },
                valueProvider = Supplier {
                    EqualizerPreferences.isAutoReplayGainEnabled()
                }
        )

        /**
         * Selects which embedded tag to read when auto ReplayGain is active.
         * Track mode is the safer default for shuffle playlists because each
         * song was analyzed independently. Album mode keeps the loudness relationship
         * between tracks intact when you listen to a full album in order.
         */
        val replayGainMode = Preference(
                title = R.string.replay_gain_mode,
                summary = R.string.replay_gain_mode_summary,
                icon = -1,
                type = PreferenceType.POPUP,
                valueProvider = {
                    when (EqualizerPreferences.getReplayGainMode()) {
                        EqualizerPreferences.REPLAY_GAIN_MODE_ALBUM -> getString(R.string.replay_gain_album)
                        else -> getString(R.string.replay_gain_track)
                    }
                },
                onPreferenceAction = { view, _ ->
                    SharedScrollViewPopup(
                            container = requireContainerView(),
                            anchorView = view,
                            menuItems = listOf(
                                    PopupMenuItem(title = R.string.replay_gain_track, summary = getString(R.string.replay_gain_track_summary)),
                                    PopupMenuItem(title = R.string.replay_gain_album, summary = getString(R.string.replay_gain_album_summary))
                            ),
                            onMenuItemClick = {
                                when (it) {
                                    R.string.replay_gain_track -> {
                                        EqualizerPreferences.setReplayGainMode(EqualizerPreferences.REPLAY_GAIN_MODE_TRACK)
                                        (view as TextView).text = getString(R.string.replay_gain_track)
                                    }
                                    R.string.replay_gain_album -> {
                                        EqualizerPreferences.setReplayGainMode(EqualizerPreferences.REPLAY_GAIN_MODE_ALBUM)
                                        (view as TextView).text = getString(R.string.replay_gain_album)
                                    }
                                }
                            },
                            onDismiss = {}
                    ).show()
                }
        )

        preferences.add(decoderHeader)
        preferences.add(currentDecoder)
        preferences.add(fallbackToSWToggle)
        preferences.add(outputHeader)
        preferences.add(sinkPopup)
        if (BuildConfig.DEBUG) {
            preferences.add(usbDacControl)
        }
        preferences.add(hiresToggle)
        preferences.add(crossfadeHeader)
        preferences.add(crossfadeToggle)
        preferences.add(crossfadeDurationSlider)
        preferences.add(crossfadeManualSkipToggle)
        preferences.add(visualizerHeader)
        preferences.add(fftResolutionPopup)
        preferences.add(playbackHeader)
        preferences.add(stereoDownmixing)
        preferences.add(gaplessToggle)
        preferences.add(skipSilenceToggle)
        preferences.add(replayGainHeader)
        preferences.add(autoReplayGainToggle)
        preferences.add(replayGainMode)

        return preferences
    }

    protected fun createLibraryPanel(): List<Preference> {
        val preferences = mutableListOf<Preference>()

        val folders = Preference(
                title = R.string.manage_music_folders,
                summary = R.string.manage_music_folders_desc,
                icon = R.drawable.ic_folder_open_outline,
                type = PreferenceType.PANEL,
                onPreferenceAction = { view, callback ->
                    openFragment(MusicFolders.newInstance(), MusicFolders.TAG)
                }
        )

        val scanLibrary = Preference(
                title = R.string.scan_library,
                summary = R.string.scan_library_summary,
                icon = R.drawable.ic_search,
                type = PreferenceType.NORMAL,
                onPreferenceAction = { view, callback ->
                    withSureDialog {
                        if (it) {
                            AudioDatabaseService.refreshScan(requireContext())
                        }
                    }
                }
        )

        val refreshLibrary = Preference(
                title = R.string.refresh_library,
                summary = R.string.refresh_library_summary,
                icon = R.drawable.ic_refresh,
                type = PreferenceType.NORMAL,
                onPreferenceAction = { view, callback ->
                    withSureDialog {
                        if (it) {
                            AudioDatabaseService.wipeScan(requireContext())
                        }
                    }
                }
        )

        val scannerOnResumeToggle = Preference(
                title = R.string.scan_on_resume,
                summary = R.string.scan_on_resume_summary,
                icon = -1,
                type = PreferenceType.SWITCH,
                onPreferenceAction = { view, callback ->
                    LibraryPreferences.setScannerOnResumeEnabled((view as FelicitySwitch).isChecked)
                },
                valueProvider = Supplier {
                    LibraryPreferences.isScannerOnResumeEnabled()
                }
        )

        val activityHeader = Preference(type = PreferenceType.SUB_HEADER, title = R.string.activity)

        val pauseActivityToggle = Preference(
                title = R.string.pause_activity,
                summary = R.string.pause_activity_summary,
                icon = R.drawable.ic_pause,
                type = PreferenceType.SWITCH,
                onPreferenceAction = { view, callback ->
                    LibraryPreferences.setActivityPaused((view as FelicitySwitch).isChecked)
                },
                valueProvider = Supplier {
                    LibraryPreferences.isActivityPaused()
                }
        )

        val clearPlaybackStats = Preference(
                title = R.string.clear_playback_stats,
                summary = R.string.clear_playback_stats_summary,
                icon = -1,
                type = PreferenceType.NORMAL,
                onPreferenceAction = { view, callback ->
                    withSureDialog {
                        if (it) {
                            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Default) {
                                SongStatRepository(requireContext()).wipeStats()

                                withContext(Dispatchers.Main) {
                                    showWarning(R.string.done)
                                }
                            }
                        }
                    }
                }
        )

        val queueManagementHeader = Preference(type = PreferenceType.SUB_HEADER, title = R.string.queue_management)

        val confirmQueueReplacementToggle = Preference(
                title = R.string.confirm_queue_replacement,
                summary = R.string.confirm_queue_replacement_summary,
                icon = R.drawable.ic_queue,
                type = PreferenceType.SWITCH,
                onPreferenceAction = { view, callback ->
                    LibraryPreferences.setConfirmQueueReplacementEnabled((view as FelicitySwitch).isChecked)
                },
                valueProvider = Supplier {
                    LibraryPreferences.isConfirmQueueReplacementEnabled()
                }
        )

        val metadataHeader = Preference(type = PreferenceType.SUB_HEADER, title = R.string.metadata)

        val albumArtistsInsteadOfArtists = Preference(
                title = R.string.show_album_artists,
                summary = R.string.show_album_artists_summary,
                icon = R.drawable.ic_artist,
                type = PreferenceType.SWITCH,
                onPreferenceAction = { view, callback ->
                    LibraryPreferences.setAlbumArtistOverArtist((view as FelicitySwitch).isChecked)
                },
                valueProvider = Supplier {
                    LibraryPreferences.isAlbumArtistOverArtist()
                }
        )

        val artistWhitelist = Preference(
                title = R.string.artist_whitelist,
                summary = R.string.artist_whitelist_desc,
                icon = R.drawable.ic_artist,
                type = PreferenceType.PANEL,
                onPreferenceAction = { view, callback ->
                    openFragment(ArtistWhitelist.newInstance(), ArtistWhitelist.TAG)
                }
        )

        val musicBrainz = Preference(
                title = R.string.musicbrainz_enabled,
                summary = R.string.musicbrainz_enabled_summary,
                icon = R.drawable.ic_file_info,
                type = PreferenceType.SWITCH,
                onPreferenceAction = { view, callback ->
                    LibraryPreferences.setMusicBrainzEnabled((view as FelicitySwitch).isChecked)
                },
                valueProvider = Supplier {
                    LibraryPreferences.isMusicBrainzEnabled()
                }
        )

        val albumArtHeader = Preference(type = PreferenceType.SUB_HEADER, title = R.string.album_art)

        val mediaStoreArt = Preference(
                title = R.string.prefer_media_store_art,
                summary = R.string.prefer_media_store_art_summary,
                icon = R.drawable.ic_image,
                type = PreferenceType.SWITCH,
                onPreferenceAction = { view, callback ->
                    LibraryPreferences.setUseMediaStoreArtwork((view as FelicitySwitch).isChecked)
                },
                valueProvider = Supplier {
                    LibraryPreferences.isUseMediaStoreArtwork()
                }
        )

        val scannerHeader = Preference(type = PreferenceType.SUB_HEADER, title = R.string.scanner)

        val minimumAudioLength = Preference(
                title = R.string.minimum_audio_length,
                summary = R.string.minimum_audio_length_summary,
                icon = R.drawable.ic_hourglass,
                type = PreferenceType.SLIDER,
                onPreferenceAction = { view, callback ->
                    LibraryPreferences.setMinimumAudioLength((view as FelicitySeekbar).getProgress().toInt())
                },
                valueProvider = Supplier {
                    SeekbarState(
                            position = LibraryPreferences.getMinimumAudioLength().toFloat(),
                            max = 600F, // 10 minutes max
                            min = 0F,
                            default = 0F,
                            leftLabel = false,
                            rightLabel = true,
                            rightLabelProvider = { progress, _, _ ->
                                DateUtils.formatElapsedTime(progress.toLong())
                            },
                    )
                }
        )

        val minimumAudioSize = Preference(
                title = R.string.minimum_audio_size,
                summary = R.string.minimum_audio_size_summary,
                icon = R.drawable.ic_filter,
                type = PreferenceType.SLIDER,
                onPreferenceAction = { view, callback ->
                    LibraryPreferences.setMinimumAudioSize((view as FelicitySeekbar).getProgress().toInt())
                },
                valueProvider = Supplier {
                    SeekbarState(
                            position = LibraryPreferences.getMinimumAudioSize().toFloat(),
                            max = 1024 * 20F, // 20 MB max
                            min = 0F,
                            default = 0F,
                            leftLabel = false,
                            rightLabel = true,
                            rightLabelProvider = { progress, _, _ ->
                                String.format(Locale.getDefault(), "%d MB", (progress / 1024).toInt())
                            },
                    )
                }
        )

        val filtersHeader = Preference(type = PreferenceType.SUB_HEADER, title = R.string.filters)

        val skipHiddenFilesToggle = Preference(
                title = R.string.skip_hidden_files,
                summary = R.string.skip_hidden_files_summary,
                icon = R.drawable.ic_dot_16dp,
                type = PreferenceType.SWITCH,
                onPreferenceAction = { view, callback ->
                    LibraryPreferences.setSkipHiddenFiles((view as FelicitySwitch).isChecked)
                },
                valueProvider = Supplier {
                    LibraryPreferences.isSkipHiddenFiles()
                }
        )

        val skipHiddenFoldersToggle = Preference(
                title = R.string.skip_hidden_folders,
                summary = R.string.skip_hidden_folders_summary,
                icon = R.drawable.ic_folder,
                type = PreferenceType.SWITCH,
                onPreferenceAction = { view, callback ->
                    LibraryPreferences.setSkipHiddenFolders((view as FelicitySwitch).isChecked)
                },
                valueProvider = Supplier {
                    LibraryPreferences.isSkipHiddenFolders()
                }
        )

        val cacheHeader = Preference(type = PreferenceType.SUB_HEADER, title = R.string.cache)

        val clearImageCache = Preference(
                title = R.string.clear_image_cache,
                summary = R.string.clear_image_cache_summary,
                icon = R.drawable.ic_image,
                type = PreferenceType.NORMAL,
                onPreferenceAction = { view, callback ->
                    withSureDialog {
                        if (it) {
                            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Default) {
                                Glide.get(requireContext()).clearDiskCache() // Requires bg thread

                                withContext(Dispatchers.Main) {
                                    Glide.get(requireContext()).clearMemory() // Requires main thread
                                    showWarning(R.string.done) // Obviously main thread as well
                                }
                            }
                        }
                    }
                }
        )

        preferences.add(folders)
        preferences.add(scanLibrary)
        preferences.add(refreshLibrary)
        preferences.add(scannerOnResumeToggle)
        preferences.add(activityHeader)
        preferences.add(pauseActivityToggle)
        preferences.add(clearPlaybackStats)
        preferences.add(queueManagementHeader)
        preferences.add(confirmQueueReplacementToggle)
        preferences.add(metadataHeader)
        preferences.add(albumArtistsInsteadOfArtists)
        preferences.add(artistWhitelist)
        preferences.add(musicBrainz)
        preferences.add(albumArtHeader)
        preferences.add(mediaStoreArt)
        preferences.add(scannerHeader)
        preferences.add(minimumAudioLength)
        preferences.add(minimumAudioSize)
        preferences.add(filtersHeader)
        preferences.add(skipHiddenFilesToggle)
        preferences.add(skipHiddenFoldersToggle)
        preferences.add(cacheHeader)
        preferences.add(clearImageCache)

        return preferences
    }

    protected fun createAccessibilityPanel(): List<Preference> {
        val preferences = mutableListOf<Preference>()

        val userInterfaceHeader = Preference(type = PreferenceType.SUB_HEADER, title = R.string.user_interface)

        val divider = Preference(
                title = R.string.dividers,
                summary = R.string.dividers_summary,
                icon = R.drawable.ic_divider,
                type = PreferenceType.SWITCH,
                onPreferenceAction = { view, callback ->
                    AccessibilityPreferences.setDivider((view as FelicitySwitch).isChecked)
                },
                valueProvider = Supplier {
                    AccessibilityPreferences.isDividerEnabled()
                }
        )

        val highlightClickedElement = Preference(
                title = R.string.highlight_clicked_element,
                summary = R.string.highlight_clicked_element_summary,
                icon = R.drawable.ic_highlight,
                type = PreferenceType.SWITCH,
                onPreferenceAction = { view, callback ->
                    AccessibilityPreferences.setHighlightMode((view as FelicitySwitch).isChecked)
                },
                valueProvider = Supplier {
                    AccessibilityPreferences.isHighlightMode()
                }
        )

        val outlineClickableElements = Preference(
                title = R.string.outline_clickable_elements,
                summary = R.string.outline_clickable_elements_summary,
                icon = R.drawable.ic_border_outer,
                type = PreferenceType.SWITCH,
                onPreferenceAction = { view, callback ->
                    AccessibilityPreferences.setHighlightStroke((view as FelicitySwitch).isChecked)
                },
                valueProvider = Supplier {
                    AccessibilityPreferences.isHighlightStroke()
                }
        )

        val miniPlayerHeader = Preference(type = PreferenceType.SUB_HEADER, title = R.string.miniplayer)

        val strokeAroundMiniplayer = Preference(
                title = R.string.stroke_around_miniplayer,
                summary = R.string.stroke_around_miniplayer_summary,
                icon = R.drawable.ic_border_outer,
                type = PreferenceType.SWITCH,
                onPreferenceAction = { view, callback ->
                    AccessibilityPreferences.setStrokeAroundMiniplayer((view as FelicitySwitch).isChecked)
                },
                valueProvider = Supplier {
                    AccessibilityPreferences.isStrokeAroundMiniplayerOn()
                }
        )

        val darkerMiniplayerShadow = Preference(
                title = R.string.darker_miniplayer_shadow,
                summary = R.string.darker_miniplayer_shadow_summary,
                icon = R.drawable.ic_opacity,
                type = PreferenceType.SWITCH,
                onPreferenceAction = { view, _ ->
                    AccessibilityPreferences.setDarkerMiniplayerShadow((view as FelicitySwitch).isChecked)
                },
                valueProvider = Supplier {
                    AccessibilityPreferences.isDarkerMiniplayerShadow()
                }
        )

        val animationsHeader = Preference(type = PreferenceType.SUB_HEADER, title = R.string.animations)

        val reduceAnimations = Preference(
                title = R.string.reduce_animations,
                summary = R.string.reduce_animations_summary,
                icon = R.drawable.ic_animation,
                type = PreferenceType.SWITCH,
                onPreferenceAction = { view, callback ->
                    AccessibilityPreferences.setReduceAnimations((view as FelicitySwitch).isChecked)
                },
                valueProvider = Supplier {
                    AccessibilityPreferences.isAnimationReduced()
                }
        )

        preferences.add(userInterfaceHeader)
        preferences.add(divider)
        preferences.add(highlightClickedElement)
        preferences.add(outlineClickableElements)
        preferences.add(miniPlayerHeader)
        preferences.add(strokeAroundMiniplayer)
        preferences.add(darkerMiniplayerShadow)
        preferences.add(animationsHeader)
        preferences.add(reduceAnimations)

        return preferences
    }

    protected fun createAboutPanel(): List<Preference> {
        val preferences = mutableListOf<Preference>()

        val appHeader = Preference(
                title = R.string.application,
                type = PreferenceType.SUB_HEADER
        )

        val appVersion = Preference(
                title = R.string.version,
                summary = BuildConfig.VERSION_NAME,
                icon = -1,
                type = PreferenceType.INFO
        )

        val socials = Preference(
                title = R.string.socials,
                type = PreferenceType.SUB_HEADER
        )

        val telegram = Preference(
                title = R.string.telegram_channel,
                summary = R.string.telegram_channel_summary,
                icon = R.drawable.ic_telegram,
                type = PreferenceType.LINK,
                onPreferenceAction = { view, callback ->
                    val intent = Intent(Intent.ACTION_VIEW, "https://t.me/felicity_music_player".toUri())
                    startActivity(intent)
                }
        )

        preferences.add(appHeader)
        preferences.add(appVersion)
        preferences.add(socials)
        preferences.add(telegram)

        return preferences
    }

    companion object {
        private const val TAG = "PreferenceFragment"
    }
}
