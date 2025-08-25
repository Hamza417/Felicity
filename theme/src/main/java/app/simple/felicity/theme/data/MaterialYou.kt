package app.simple.felicity.theme.data

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat

object MaterialYou {

    @RequiresApi(Build.VERSION_CODES.S)
    const val MATERIAL_YOU_PRIMARY_ACCENT_RES_ID = android.R.color.system_accent1_500

    @RequiresApi(Build.VERSION_CODES.S)
    const val MATERIAL_YOU_SECONDARY_ACCENT_RES_ID = android.R.color.system_accent1_300
    const val MATERIAL_YOU_ADAPTER_INDEX = 1

    var headingTextColor = 0
    var primaryTextColor = 0
    var secondaryTextColor = 0
    var tertiaryTextColor = 0
    var quaternaryTextColor = 0
    var background = 0
    var viewerBackground = 0
    var highlightBackground = 0
    var selectedBackground = 0
    var dividerBackground = 0
    var spotColor = 0
    var switchOffColor = 0
    var regularIconColor = 0
    var secondaryIconColor = 0

    var headingTextColorDark = 0
    var primaryTextColorDark = 0
    var secondaryTextColorDark = 0
    var tertiaryTextColorDark = 0
    var quaternaryTextColorDark = 0
    var backgroundDark = 0
    var viewerBackgroundDark = 0
    var highlightBackgroundDark = 0
    var selectedBackgroundDark = 0
    var dividerBackgroundDark = 0
    var spotColorDark = 0
    var switchOffColorDark = 0
    var regularIconColorDark = 0
    var secondaryIconColorDark = 0

    var primaryAccentColor = 0
    var secondaryAccentColor = 0

    @RequiresApi(Build.VERSION_CODES.S)
    fun Context.presetMaterialYouDynamicColors() {
        setLightColors()
        setDarkColors()
        setAccentColors()
    }

    @RequiresApi(Build.VERSION_CODES.S)
    fun Context.setAccentColors() {
        primaryAccentColor = ContextCompat.getColor(this, MATERIAL_YOU_PRIMARY_ACCENT_RES_ID)
        secondaryAccentColor = ContextCompat.getColor(this, MATERIAL_YOU_SECONDARY_ACCENT_RES_ID)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun Context.setLightColors() {
        headingTextColor = ContextCompat.getColor(this, android.R.color.system_neutral1_1000)
        primaryTextColor = ContextCompat.getColor(this, android.R.color.system_neutral1_900)
        secondaryTextColor = ContextCompat.getColor(this, android.R.color.system_neutral1_700)
        tertiaryTextColor = ContextCompat.getColor(this, android.R.color.system_neutral1_500)
        quaternaryTextColor = ContextCompat.getColor(this, android.R.color.system_neutral1_300)

        background = ContextCompat.getColor(this, android.R.color.system_neutral1_50)
        viewerBackground = ContextCompat.getColor(this, android.R.color.system_neutral1_100)
        highlightBackground = ContextCompat.getColor(this, android.R.color.system_neutral1_100)
        selectedBackground = ContextCompat.getColor(this, android.R.color.system_neutral1_200)
        dividerBackground = ContextCompat.getColor(this, android.R.color.system_accent3_300)
        spotColor = ContextCompat.getColor(this, android.R.color.system_accent1_600)

        switchOffColor = ContextCompat.getColor(this, android.R.color.system_neutral1_100)

        regularIconColor = ContextCompat.getColor(this, android.R.color.system_neutral1_900)
        secondaryIconColor = ContextCompat.getColor(this, android.R.color.system_neutral1_400)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun Context.setDarkColors() {
        headingTextColorDark = ContextCompat.getColor(this, android.R.color.system_neutral1_10)
        primaryTextColorDark = ContextCompat.getColor(this, android.R.color.system_neutral1_100)
        secondaryTextColorDark = ContextCompat.getColor(this, android.R.color.system_neutral1_200)
        tertiaryTextColorDark = ContextCompat.getColor(this, android.R.color.system_neutral1_300)
        quaternaryTextColorDark = ContextCompat.getColor(this, android.R.color.system_neutral1_400)

        backgroundDark = ContextCompat.getColor(this, android.R.color.system_neutral1_900)
        viewerBackgroundDark = ContextCompat.getColor(this, android.R.color.system_neutral1_800)
        highlightBackgroundDark = ContextCompat.getColor(this, android.R.color.system_accent1_700)
        selectedBackgroundDark = ContextCompat.getColor(this, android.R.color.system_accent2_600)
        dividerBackgroundDark = ContextCompat.getColor(this, android.R.color.system_accent3_200)
        spotColorDark = ContextCompat.getColor(this, android.R.color.system_accent1_500)

        switchOffColorDark = ContextCompat.getColor(this, android.R.color.system_neutral1_800)

        regularIconColorDark = ContextCompat.getColor(this, android.R.color.system_neutral1_50)
        secondaryIconColorDark = ContextCompat.getColor(this, android.R.color.system_neutral1_200)
    }
}