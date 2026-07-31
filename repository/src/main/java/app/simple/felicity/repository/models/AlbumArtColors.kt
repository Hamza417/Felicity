package app.simple.felicity.repository.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Holds the pre-decoded palette colors for a single track so the app never has to
 * re-extract a bitmap just to get theme colors when the song changes.
 *
 * Every color field stores a packed ARGB integer (the same format Android uses natively),
 * so loading them back is instant — no bitmap decoding, no palette computation, just a
 * simple database read.
 *
 * The row is keyed by [audioHash], which is the XXHash64 fingerprint computed during
 * the library scan. This intentionally mirrors the same pattern used by [AudioStat]:
 * no foreign key constraint, so cached colors survive even if the track is temporarily
 * removed from the library and are re-associated the moment the file is rescanned.
 *
 * Light-mode colors use tone names without a suffix, dark-mode counterparts end in [Dark].
 *
 * @author Hamza417
 */
@Entity(
        tableName = "album_art_colors",
        indices = [Index(value = ["audioHash"], unique = true)]
)
data class AlbumArtColors(

        @PrimaryKey
        val audioHash: Long,

        // Light theme colors
        @ColumnInfo(name = "heading_text_color")
        val headingTextColor: Int,

        @ColumnInfo(name = "primary_text_color")
        val primaryTextColor: Int,

        @ColumnInfo(name = "secondary_text_color")
        val secondaryTextColor: Int,

        @ColumnInfo(name = "tertiary_text_color")
        val tertiaryTextColor: Int,

        @ColumnInfo(name = "quaternary_text_color")
        val quaternaryTextColor: Int,

        @ColumnInfo(name = "background")
        val background: Int,

        @ColumnInfo(name = "highlight_background")
        val highlightBackground: Int,

        @ColumnInfo(name = "selected_background")
        val selectedBackground: Int,

        @ColumnInfo(name = "divider_background")
        val dividerBackground: Int,

        @ColumnInfo(name = "spot_color")
        val spotColor: Int,

        @ColumnInfo(name = "switch_off_color")
        val switchOffColor: Int,

        @ColumnInfo(name = "regular_icon_color")
        val regularIconColor: Int,

        @ColumnInfo(name = "secondary_icon_color")
        val secondaryIconColor: Int,

        @ColumnInfo(name = "disabled_icon_color")
        val disabledIconColor: Int,

        // Dark theme colors
        @ColumnInfo(name = "heading_text_color_dark")
        val headingTextColorDark: Int,

        @ColumnInfo(name = "primary_text_color_dark")
        val primaryTextColorDark: Int,

        @ColumnInfo(name = "secondary_text_color_dark")
        val secondaryTextColorDark: Int,

        @ColumnInfo(name = "tertiary_text_color_dark")
        val tertiaryTextColorDark: Int,

        @ColumnInfo(name = "quaternary_text_color_dark")
        val quaternaryTextColorDark: Int,

        @ColumnInfo(name = "background_dark")
        val backgroundDark: Int,

        @ColumnInfo(name = "highlight_background_dark")
        val highlightBackgroundDark: Int,

        @ColumnInfo(name = "selected_background_dark")
        val selectedBackgroundDark: Int,

        @ColumnInfo(name = "divider_background_dark")
        val dividerBackgroundDark: Int,

        @ColumnInfo(name = "spot_color_dark")
        val spotColorDark: Int,

        @ColumnInfo(name = "switch_off_color_dark")
        val switchOffColorDark: Int,

        @ColumnInfo(name = "regular_icon_color_dark")
        val regularIconColorDark: Int,

        @ColumnInfo(name = "secondary_icon_color_dark")
        val secondaryIconColorDark: Int,

        @ColumnInfo(name = "disabled_icon_color_dark")
        val disabledIconColorDark: Int,

        /**
         * The two raw accent tones from the MonetPalette that drive the album art accent color.
         * Storing them here means we can restore the accent chip without touching the bitmap again.
         */
        @ColumnInfo(name = "accent1_500")
        val accent1_500: Int,

        @ColumnInfo(name = "accent1_300")
        val accent1_300: Int,
)

