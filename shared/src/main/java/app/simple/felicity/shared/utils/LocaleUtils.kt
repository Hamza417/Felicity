package app.simple.felicity.shared.utils

import android.content.res.Resources
import android.view.View
import app.simple.felicity.shared.models.Lang
import java.util.Locale

object LocaleUtils {

    private var appLocale = Locale.getDefault()

    /**
     * List of languages currently supported by
     * the app.
     *
     * Do not include incomplete translations.
     *
     * Use Java 8's [Locale] codes and not Android's:
     * https://www.oracle.com/java/technologies/javase/jdk8-jre8-suported-locales.html
     */
    val langLists = arrayListOf(
            // Auto detect language (default)
            Lang("autoSystemLanguageString" /* Placeholder */, "default"),
            // English (United States)
            Lang("English (US)", "en-US"),
    )

    fun getAppLocale(): Locale {
        return synchronized(this) {
            appLocale
        }
    }

    fun setAppLocale(value: Locale) {
        synchronized(this) {
            appLocale = value
        }
    }

    fun isOneOfTraditionalChinese(): Boolean {
        return with(getSystemLanguageCode()) {
            this == "zh" ||
                    this == "zh-HK" ||
                    this == "zh-MO" ||
                    this == "zh-TW" ||
                    this == "zh-Hant" ||
                    this == "zh-Hant-HK" ||
                    this == "zh-Hant-MO" ||
                    this == "zh-Hant-TW" ||
                    this == "zh-Hant-CN" ||
                    this == "zh-Hant-SG"
        }
    }

    fun getSystemLanguageCode(): String {
        return Resources.getSystem().configuration.locales[0].language
    }

    fun Resources.isRTL(): Boolean {
        return configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
    }
}
