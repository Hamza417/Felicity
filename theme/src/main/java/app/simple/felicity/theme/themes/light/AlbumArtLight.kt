package app.simple.felicity.theme.themes.light

import app.simple.felicity.theme.data.AlbumArtData
import app.simple.felicity.theme.models.IconTheme
import app.simple.felicity.theme.models.SwitchTheme
import app.simple.felicity.theme.models.TextViewTheme
import app.simple.felicity.theme.models.Theme
import app.simple.felicity.theme.models.ViewGroupTheme

/**
 * A light theme whose colors are derived from the dominant hue of the current album art.
 * All values are sourced from [AlbumArtData], which must be populated via
 * [AlbumArtData.populate] before this theme is applied.
 *
 * Falls back to values matching [LightTheme] until a palette has been extracted.
 *
 * @author Hamza417
 */
class AlbumArtLight : Theme() {
    init {
        setTextViewTheme(
                TextViewTheme(
                        AlbumArtData.headingTextColor,
                        AlbumArtData.primaryTextColor,
                        AlbumArtData.secondaryTextColor,
                        AlbumArtData.tertiaryTextColor,
                        AlbumArtData.quaternaryTextColor
                )
        )

        setViewGroupTheme(
                ViewGroupTheme(
                        AlbumArtData.background,
                        AlbumArtData.highlightBackground,
                        AlbumArtData.selectedBackground,
                        AlbumArtData.dividerBackground,
                        AlbumArtData.spotColor
                )
        )

        setSwitchTheme(SwitchTheme(AlbumArtData.switchOffColor))

        setIconTheme(
                IconTheme(
                        AlbumArtData.regularIconColor,
                        AlbumArtData.secondaryIconColor,
                        AlbumArtData.disabledIconColor
                )
        )
    }
}

