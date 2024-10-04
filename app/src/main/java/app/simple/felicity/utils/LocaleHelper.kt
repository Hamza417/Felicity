package app.simple.felicity.utils

import android.content.res.Resources
import android.view.View
import app.simple.felicity.models.normal.Locales
import java.util.Locale

object LocaleHelper {

    private var appLocale = Locale.getDefault()

    /**
     * Code for russian locale
     */
    private const val russianLocale = "ru-RU"

    fun getSystemLanguageCode(): String {
        return Resources.getSystem().configuration.locales[0].language
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

    /**
     * List of languages currently supported by
     * the app.
     *
     * Do not include incomplete translations.
     *
     * Use Java 8's [Locale] codes and not Android's:
     * https://www.oracle.com/java/technologies/javase/jdk8-jre8-suported-locales.html
     */
    val localeList = arrayListOf(
            // Auto detect language (default)
            Locales("autoSystemLanguageString" /* Placeholder */, "default"),
            // English (United States)
            Locales("English (US)", "en-US"),
            // Traditional Chinese (Taiwan)
            Locales("繁體中文 (Traditional Chinese)", "zh-TW"),
            // Simplified Chinese (China)
            Locales("简体中文 (Simplified Chinese)", "zh-CN"),
            // Russian
            Locales("Русский (Russian)", "ru-RU"),
            // Italian
            Locales("Italiano (Italian)", "it-IT"),
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

    fun Resources.isRTL(): Boolean {
        return configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
    }

    fun isAppRussianLocale(): Boolean {
        return app.simple.felicity.preferences.ConfigurationPreferences.getAppLanguage() == russianLocale
    }
}
