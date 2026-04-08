package app.simple.felicity.theme.themes.dark

import app.simple.felicity.theme.data.AlbumArtData
import app.simple.felicity.theme.models.IconTheme
import app.simple.felicity.theme.models.SwitchTheme
import app.simple.felicity.theme.models.TextViewTheme
import app.simple.felicity.theme.models.Theme
import app.simple.felicity.theme.models.ViewGroupTheme

/**
 * A dark theme whose colors are derived from the dominant hue of the current album art.
 * All values are sourced from [AlbumArtData], which must be populated via
 * [AlbumArtData.populate] before this theme is applied.
 *
 * Falls back to values matching [DarkTheme] until a palette has been extracted.
 *
 * @author Hamza417
 */
class AlbumArtDark : Theme() {
    init {
        setTextViewTheme(
                TextViewTheme(
                        AlbumArtData.headingTextColorDark,
                        AlbumArtData.primaryTextColorDark,
                        AlbumArtData.secondaryTextColorDark,
                        AlbumArtData.tertiaryTextColorDark,
                        AlbumArtData.quaternaryTextColorDark
                )
        )

        setViewGroupTheme(
                ViewGroupTheme(
                        AlbumArtData.backgroundDark,
                        AlbumArtData.highlightBackgroundDark,
                        AlbumArtData.selectedBackgroundDark,
                        AlbumArtData.dividerBackgroundDark,
                        AlbumArtData.spotColorDark
                )
        )

        setSwitchTheme(SwitchTheme(AlbumArtData.switchOffColorDark))

        setIconTheme(
                IconTheme(
                        AlbumArtData.regularIconColorDark,
                        AlbumArtData.secondaryIconColorDark,
                        AlbumArtData.disabledIconColorDark
                )
        )
    }
}

