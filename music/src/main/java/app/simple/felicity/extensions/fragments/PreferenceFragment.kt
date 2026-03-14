package app.simple.felicity.extensions.fragments

import android.os.Build
import android.os.Bundle
import android.text.format.DateUtils
import android.view.View
import android.widget.TextView
import app.simple.felicity.R
import app.simple.felicity.decorations.seekbars.FelicitySeekbar
import app.simple.felicity.decorations.toggles.FelicitySwitch
import app.simple.felicity.decorations.views.SharedScrollViewPopup
import app.simple.felicity.enums.PreferenceType
import app.simple.felicity.models.Preference
import app.simple.felicity.models.SeekbarState
import app.simple.felicity.popups.home.PopupHomeInterfaceMenu
import app.simple.felicity.preferences.AccessibilityPreferences
import app.simple.felicity.preferences.AlbumArtPreferences
import app.simple.felicity.preferences.AppearancePreferences
import app.simple.felicity.preferences.AudioPreferences
import app.simple.felicity.preferences.BehaviourPreferences
import app.simple.felicity.preferences.HomePreferences
import app.simple.felicity.preferences.LibraryPreferences
import app.simple.felicity.preferences.ShufflePreferences
import app.simple.felicity.preferences.UserInterfacePreferences
import app.simple.felicity.ui.preferences.sub.AccentColors
import app.simple.felicity.ui.preferences.sub.Themes
import app.simple.felicity.ui.preferences.sub.TypeFaces
import java.util.Locale
import java.util.function.Supplier

open class PreferenceFragment : MediaFragment() {

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
                    true
                }
        )

        val accentColor = Preference(
                title = R.string.accent_color,
                summary = R.string.accent_color_summary,
                icon = R.drawable.ic_palette,
                type = PreferenceType.PANEL,
                onPreferenceAction = { view, callback ->
                    openFragment(AccentColors.newInstance(), AccentColors.TAG)
                    true
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
                    true
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
                    true
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
                    true
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

        val thumbShape = Preference(
                title = R.string.thumb_shape,
                summary = R.string.thumb_shape_summary,
                icon = -1,
                type = PreferenceType.POPUP,
                valueProvider = {
                    when (AppearancePreferences.getSeekbarThumbStyle()) {
                        AppearancePreferences.SEEKBAR_THUMB_PILL -> getString(R.string.pill)
                        AppearancePreferences.SEEKBAR_THUMB_CIRCLE -> getString(R.string.circle)
                        else -> getString(R.string.pill)
                    }
                },
                onPreferenceAction = { view, callback ->
                    SharedScrollViewPopup(
                            container = requireContainerView(),
                            anchorView = view,
                            menuItems = listOf(R.string.pill, R.string.circle),
                            onMenuItemClick = {
                                when (it) {
                                    R.string.pill -> {
                                        AppearancePreferences.setSeekbarThumbStyle(AppearancePreferences.SEEKBAR_THUMB_PILL)
                                        (view as TextView).text = getString(R.string.pill)
                                    }
                                    R.string.circle -> {
                                        AppearancePreferences.setSeekbarThumbStyle(AppearancePreferences.SEEKBAR_THUMB_CIRCLE)
                                        (view as TextView).text = getString(R.string.circle)
                                    }
                                }
                            },
                            onDismiss = {
                                callback
                            }
                    ).show()
                }
        )

        val knobStyle = Preference(
                title = R.string.knob_style,
                summary = R.string.knob_style_summary,
                icon = -1,
                type = PreferenceType.POPUP,
                valueProvider = {
                    when (AppearancePreferences.getKnobStyle()) {
                        AppearancePreferences.KNOB_STYLE_DEFAULT -> getString(R.string.flat)
                        AppearancePreferences.KNOB_STYLE_NEU -> getString(R.string.neumorphic)
                        else -> getString(R.string.flat)
                    }
                },
                onPreferenceAction = { view, callback ->
                    SharedScrollViewPopup(
                            container = requireContainerView(),
                            anchorView = view,
                            menuItems = listOf(R.string.flat, R.string.neumorphic),
                            onMenuItemClick = {
                                when (it) {
                                    R.string.flat -> {
                                        AppearancePreferences.setKnobStyle(AppearancePreferences.KNOB_STYLE_DEFAULT)
                                        (view as TextView).text = getString(R.string.flat)
                                    }
                                    R.string.neumorphic -> {
                                        AppearancePreferences.setKnobStyle(AppearancePreferences.KNOB_STYLE_NEU)
                                        (view as TextView).text = getString(R.string.neumorphic)
                                    }
                                }
                            },
                            onDismiss = {
                                callback
                            }
                    ).show()
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
                    true
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
                    true
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
                    true
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
                    true
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
                    true
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
        preferences.add(thumbShape)
        preferences.add(knobStyle)
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
                icon = R.drawable.ic_felicity_full,
                type = PreferenceType.POPUP,
                valueProvider = {
                    when (HomePreferences.getHomeInterface()) {
                        HomePreferences.HOME_INTERFACE_DASHBOARD -> getString(R.string.dashboard)
                        HomePreferences.HOME_INTERFACE_ARTFLOW -> getString(R.string.artflow)
                        HomePreferences.HOME_INTERFACE_SPANNED -> getString(R.string.spanned)
                        HomePreferences.HOME_INTERFACE_SIMPLE -> getString(R.string.simple)
                        else -> getString(R.string.app_name)
                    }
                },
                onPreferenceAction = { view, callback ->
                    PopupHomeInterfaceMenu(
                            container = requireContainerView(),
                            anchorView = view,
                            menuItems = listOf(R.string.dashboard,
                                               R.string.spanned,
                                               R.string.artflow,
                                               R.string.simple),
                            menuIcons = listOf(R.drawable.ic_dot_16dp,
                                               R.drawable.ic_spanned_16dp,
                                               R.drawable.ic_flow_16dp,
                                               R.drawable.ic_list_16dp),
                            onMenuItemClick = {
                                when (it) {
                                    R.string.dashboard -> {
                                        HomePreferences.setHomeInterface(HomePreferences.HOME_INTERFACE_DASHBOARD)
                                        (view as TextView).text = getString(R.string.dashboard)
                                    }
                                    R.string.spanned -> {
                                        HomePreferences.setHomeInterface(HomePreferences.HOME_INTERFACE_SPANNED)
                                        (view as TextView).text = getString(R.string.spanned)
                                    }
                                    R.string.artflow -> {
                                        HomePreferences.setHomeInterface(HomePreferences.HOME_INTERFACE_ARTFLOW)
                                        (view as TextView).text = getString(R.string.artflow)
                                    }
                                    R.string.simple -> {
                                        HomePreferences.setHomeInterface(HomePreferences.HOME_INTERFACE_SIMPLE)
                                        (view as TextView).text = getString(R.string.simple)
                                    }
                                }
                            },
                            onDismiss = {
                                callback
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
                    true
                },
                valueProvider = Supplier {
                    UserInterfacePreferences.isMarginAroundMiniplayer()
                }
        )

        preferences.add(homeHeader)
        preferences.add(homeInterface)
        preferences.add(miniPlayerHeader)
        preferences.add(marginAroundMiniplayerToggle)

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
                        else -> getString(R.string.app_name)
                    }
                },
                onPreferenceAction = { view, callback ->
                    SharedScrollViewPopup(
                            container = requireContainerView(),
                            anchorView = view,
                            menuItems = listOf(R.string.hide, R.string.fade),
                            menuIcons = listOf(R.drawable.ic_hide_16dp, R.drawable.ic_opacity_16dp),
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
                            onDismiss = {

                            }
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
                    true
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
                    true
                }
        )

        preferences.add(listHeader)
        preferences.add(fastScrollBehavior)
        preferences.add(applicationHeader)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            preferences.add(predictiveBackToggle)
        }
        preferences.add(hapticToggle)

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
                            menuItems = listOf(R.string.system_hardware, R.string.ffmpeg),
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
                    true
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
                    true
                },
                valueProvider = Supplier {
                    AudioPreferences.isHiresOutputEnabled()
                }
        )

        val stereoDownmixing = Preference(
                title = R.string.force_stereo_downmixing,
                summary = R.string.force_stereo_downmixing_summary,
                icon = R.drawable.ic_speaker,
                type = PreferenceType.SWITCH,
                onPreferenceAction = { view, callback ->
                    AudioPreferences.setIsStereoDownmixForced((view as FelicitySwitch).isChecked)
                    true
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
                    true
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
                    true
                },
                valueProvider = Supplier {
                    AudioPreferences.isSkipSilenceEnabled()
                }
        )

        preferences.add(decoderHeader)
        preferences.add(currentDecoder)
        preferences.add(fallbackToSWToggle)
        preferences.add(playbackHeader)
        preferences.add(hiresToggle)
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
                            menuItems = listOf(R.string.fisher_yates, R.string.miller),
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

        val scannerHeader = Preference(type = PreferenceType.SUB_HEADER, title = R.string.scanner)

        val minimumAudioLength = Preference(
                title = R.string.minimum_audio_length,
                summary = R.string.minimum_audio_length_summary,
                icon = R.drawable.ic_hourglass,
                type = PreferenceType.SLIDER,
                onPreferenceAction = { view, callback ->
                    LibraryPreferences.setMinimumAudioLength((view as FelicitySeekbar).getProgress().toInt())
                    true
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
                    true
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
                    true
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
                    true
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
                    true
                },
                valueProvider = Supplier {
                    LibraryPreferences.isSkipHiddenFolders()
                }
        )

        preferences.add(shuffleHeader)
        preferences.add(currentShuffle)
        preferences.add(scannerHeader)
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

        val strokeAroundMiniplayer = Preference(
                title = R.string.stroke_around_miniplayer,
                summary = R.string.stroke_around_miniplayer_summary,
                icon = R.drawable.ic_border_outer,
                type = PreferenceType.SWITCH,
                onPreferenceAction = { view, callback ->
                    AccessibilityPreferences.setStrokeAroundMiniplayer((view as FelicitySwitch).isChecked)
                    true
                },
                valueProvider = Supplier {
                    AccessibilityPreferences.isStrokeAroundMiniplayerOn()
                }
        )

        preferences.add(userInterfaceHeader)
        preferences.add(strokeAroundMiniplayer)

        return preferences
    }
}
