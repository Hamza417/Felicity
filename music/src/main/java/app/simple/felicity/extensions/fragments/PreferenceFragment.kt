package app.simple.felicity.extensions.fragments

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.format.DateUtils
import android.view.View
import android.widget.TextView
import androidx.core.net.toUri
import app.simple.felicity.BuildConfig
import app.simple.felicity.R
import app.simple.felicity.decorations.seekbars.FelicitySeekbar
import app.simple.felicity.decorations.toggles.FelicityButtonGroup
import app.simple.felicity.decorations.toggles.FelicityButtonGroup.Companion.Button
import app.simple.felicity.decorations.toggles.FelicitySwitch
import app.simple.felicity.decorations.views.PopupMenuItem
import app.simple.felicity.decorations.views.SharedScrollViewPopup
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
import app.simple.felicity.preferences.LibraryPreferences
import app.simple.felicity.preferences.ShufflePreferences
import app.simple.felicity.preferences.UserInterfacePreferences
import app.simple.felicity.repository.services.AudioDatabaseService
import app.simple.felicity.ui.preferences.sub.AccentColors
import app.simple.felicity.ui.preferences.sub.BlacklistFolders
import app.simple.felicity.ui.preferences.sub.Language
import app.simple.felicity.ui.preferences.sub.Themes
import app.simple.felicity.ui.preferences.sub.TypeFaces
import java.util.Locale
import java.util.function.Supplier

@Suppress("unused")
abstract class PreferenceFragment : MediaFragment() {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireHiddenMiniPlayer()
    }

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

        val homeInterface = Preference(
                title = R.string.change_home_interface,
                summary = R.string.change_home_interface_summary,
                icon = R.drawable.ic_home,
                type = PreferenceType.POPUP,
                valueProvider = {
                    when (UserInterfacePreferences.getHomeInterface()) {
                        UserInterfacePreferences.HOME_INTERFACE_DASHBOARD -> getString(R.string.dashboard)
                        UserInterfacePreferences.HOME_INTERFACE_ARTFLOW -> getString(R.string.artflow)
                        UserInterfacePreferences.HOME_INTERFACE_SPANNED -> getString(R.string.spanned)
                        UserInterfacePreferences.HOME_INTERFACE_SIMPLE -> getString(R.string.simple)
                        else -> getString(R.string.app_name)
                    }
                },
                onPreferenceAction = { view, callback ->
                    SharedScrollViewPopup(
                            container = requireContainerView(),
                            anchorView = view,
                            menuItems = listOf(
                                    PopupMenuItem(title = R.string.dashboard, isExperimental = true),
                                    PopupMenuItem(title = R.string.spanned, isExperimental = true),
                                    PopupMenuItem(title = R.string.artflow, isExperimental = true),
                                    PopupMenuItem(title = R.string.simple)
                            ),
                            onMenuItemClick = {
                                when (it) {
                                    R.string.dashboard -> {
                                        UserInterfacePreferences.setHomeInterface(UserInterfacePreferences.HOME_INTERFACE_DASHBOARD)
                                        (view as TextView).text = getString(R.string.dashboard)
                                    }
                                    R.string.spanned -> {
                                        UserInterfacePreferences.setHomeInterface(UserInterfacePreferences.HOME_INTERFACE_SPANNED)
                                        (view as TextView).text = getString(R.string.spanned)
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
                        else -> getString(R.string.simple)
                    }
                },
                onPreferenceAction = { view, callback ->
                    SharedScrollViewPopup(
                            container = requireContainerView(),
                            anchorView = view,
                            menuItems = listOf(
                                    PopupMenuItem(title = R.string.simple, summary = getString(R.string.simple_ui_desc)),
                                    PopupMenuItem(title = R.string.faded, summary = getString(R.string.faded_ui_desc))
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
                                }
                            },
                            onDismiss = {}
                    ).show()
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

        preferences.add(homeHeader)
        preferences.add(homeInterface)
        preferences.add(playerHeader)
        preferences.add(playerInterface)
        preferences.add(miniPlayerHeader)
        preferences.add(marginAroundMiniplayerToggle)
        preferences.add(applicationHeader)
        preferences.add(likeButton)

        return preferences
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
                type = PreferenceType.BUTTON_GROUP,
                valueProvider = {
                    ButtonGroupState(
                            buttons = listOf(
                                    Button(textResId = R.string.hide),
                                    Button(textResId = R.string.fade),
                            ),
                            selectedIndex = when (BehaviourPreferences.getFastScrollBehavior()) {
                                BehaviourPreferences.HIDE_FAST_SCROLLBAR -> 0
                                else -> 1
                            }
                    )
                },
                onPreferenceAction = { view, _ ->
                    val behavior = when ((view as FelicityButtonGroup).getSelectedIndex()) {
                        0 -> BehaviourPreferences.HIDE_FAST_SCROLLBAR
                        else -> BehaviourPreferences.FADE_FAST_SCROLLBAR
                    }
                    BehaviourPreferences.setFastScrollBehavior(behavior)
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
                type = PreferenceType.BUTTON_GROUP,
                valueProvider = {
                    ButtonGroupState(
                            buttons = listOf(
                                    Button(textResId = R.string.depth),
                                    Button(textResId = R.string.drift),
                                    Button(textResId = R.string.fade),
                            ),
                            selectedIndex = when (BehaviourPreferences.getFragmentTransition()) {
                                BehaviourPreferences.TRANSITION_Z -> 0
                                BehaviourPreferences.TRANSITION_X -> 1
                                else -> 2
                            }
                    )
                },
                onPreferenceAction = { view, _ ->
                    val transition = when ((view as FelicityButtonGroup).getSelectedIndex()) {
                        0 -> BehaviourPreferences.TRANSITION_Z
                        1 -> BehaviourPreferences.TRANSITION_X
                        else -> BehaviourPreferences.TRANSITION_FADE
                    }
                    BehaviourPreferences.setFragmentTransition(transition)
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
                title = R.string.audio_pipeline,
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

        val playbackHeader = Preference(type = PreferenceType.SUB_HEADER, title = R.string.playback)

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

        val hiresWarning = Preference(
                title = R.string.hires_warning,
                type = PreferenceType.WARN
        )

        val stereoDownmixing = Preference(
                title = R.string.force_stereo_downmixing,
                summary = R.string.force_stereo_downmixing_summary,
                icon = R.drawable.ic_speaker,
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

        val outputHeader = Preference(type = PreferenceType.SUB_HEADER, title = R.string.output)

        /**
         * Toggle that routes the post-DSP float32 PCM stream through the AAudio
         * direct-to-HAL path instead of the standard AudioTrack pipeline.
         * Requires API 26 (Android 8.0) or higher.
         */
        val aaudioToggle = Preference(
                title = R.string.aaudio_enabled,
                summary = R.string.aaudio_enabled_summary,
                icon = R.drawable.ic_timer,
                type = PreferenceType.SWITCH,
                onPreferenceAction = { view, callback ->
                    AudioPreferences.setAaudioEnabled((view as FelicitySwitch).isChecked)
                },
                valueProvider = Supplier {
                    AudioPreferences.isAaudioEnabled()
                }
        )

        val aaudioWarning = Preference(
                title = R.string.aaudio_warning,
                type = PreferenceType.WARN
        )

        preferences.add(decoderHeader)
        preferences.add(currentDecoder)
        preferences.add(fallbackToSWToggle)
        preferences.add(outputHeader)
        preferences.add(aaudioToggle)
        preferences.add(aaudioWarning)
        preferences.add(playbackHeader)
        preferences.add(hiresToggle)
        preferences.add(hiresWarning)
        preferences.add(stereoDownmixing)
        preferences.add(gaplessToggle)
        preferences.add(skipSilenceToggle)

        return preferences
    }

    protected fun createLibraryPanel(): List<Preference> {
        val preferences = mutableListOf<Preference>()

        val shuffleHeader = Preference(type = PreferenceType.SUB_HEADER, title = R.string.shuffle)

        val currentShuffle = Preference(
                title = R.string.shuffle,
                summary = R.string.shuffle_algorithm_summary,
                icon = R.drawable.ic_shuffle,
                type = PreferenceType.POPUP,
                valueProvider = {
                    when (ShufflePreferences.getShuffleAlgorithm()) {
                        ShufflePreferences.ALGORITHM_FISHER_YATES -> getString(R.string.fisher_yates)
                        ShufflePreferences.ALGORITHM_MILLER -> getString(R.string.miller)
                        else -> getString(R.string.fisher_yates)
                    }
                },
                onPreferenceAction = { view, callback ->
                    SharedScrollViewPopup(
                            container = requireContainerView(),
                            anchorView = view,
                            menuItems = listOf(
                                    PopupMenuItem(title = R.string.fisher_yates),
                                    PopupMenuItem(title = R.string.miller)
                            ),
                            onMenuItemClick = {
                                when (it) {
                                    R.string.fisher_yates -> {
                                        ShufflePreferences.setShuffleAlgorithm(ShufflePreferences.ALGORITHM_FISHER_YATES)
                                        (view as TextView).text = getString(R.string.fisher_yates)
                                    }
                                    R.string.miller -> {
                                        ShufflePreferences.setShuffleAlgorithm(ShufflePreferences.ALGORITHM_MILLER)
                                        (view as TextView).text = getString(R.string.miller)
                                    }
                                }
                            },
                            onDismiss = {

                            }
                    ).show()
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

        val excludedFolders = Preference(
                title = R.string.excluded_folders,
                summary = R.string.excluded_folders_summary,
                icon = R.drawable.ic_folder,
                type = PreferenceType.PANEL,
                onPreferenceAction = { view, callback ->
                    openFragment(BlacklistFolders.newInstance(), BlacklistFolders.TAG)
                },
        )

        val refreshLibrary = Preference(
                title = R.string.refresh_library,
                summary = R.string.refresh_library_summary,
                icon = R.drawable.ic_refresh,
                type = PreferenceType.NORMAL,
                onPreferenceAction = { view, callback ->
                    withSureDialog {
                        if (it) {
                            AudioDatabaseService.refreshScan(requireContext())
                        }
                    }
                }
        )

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

        val skipNomediaToggle = Preference(
                title = R.string.skip_nomedia_folders,
                summary = R.string.skip_nomedia_folders_summary,
                icon = R.drawable.ic_hide,
                type = PreferenceType.SWITCH,
                onPreferenceAction = { view, callback ->
                    LibraryPreferences.setSkipNomedia((view as FelicitySwitch).isChecked)
                },
                valueProvider = Supplier {
                    LibraryPreferences.isSkipNomedia()
                }
        )

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

        preferences.add(refreshLibrary)
        preferences.add(shuffleHeader)
        preferences.add(currentShuffle)
        preferences.add(metadataHeader)
        preferences.add(albumArtistsInsteadOfArtists)
        preferences.add(albumArtHeader)
        preferences.add(mediaStoreArt)
        preferences.add(scannerHeader)
        preferences.add(excludedFolders)
        preferences.add(minimumAudioLength)
        preferences.add(minimumAudioSize)
        preferences.add(filtersHeader)
        preferences.add(skipNomediaToggle)
        preferences.add(skipHiddenFilesToggle)
        preferences.add(skipHiddenFoldersToggle)

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

        preferences.add(userInterfaceHeader)
        preferences.add(divider)
        preferences.add(strokeAroundMiniplayer)
        preferences.add(darkerMiniplayerShadow)

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
}
