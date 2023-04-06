package app.simple.felicity.utils

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import android.os.LocaleList
import java.util.*

open class ContextUtils(context: Context) : ContextWrapper(context) {
    companion object {
        /**
         * Android does not have a default method to change app locale
         * at runtime like changing theme. This method at first only
         * applicable before the app starts, second is not solution
         * rather a work around, third uses deprecated methods for
         * older APIs which can cause issues in some phones.
         *
         * @param baseContext is base context
         * @param languageCode is code of the language e.g. en for English
         */
        fun updateLocale(baseContext: Context, languageCode: String): ContextWrapper {
            val localeToSwitchTo = if (languageCode == "default") {
                if (LocaleHelper.isOneOfTraditionalChinese()) {
                    Locale.forLanguageTag("zh-TW")
                } else {
                    Locale.forLanguageTag(LocaleHelper.getSystemLanguageCode())
                }
            } else {
                Locale.forLanguageTag(languageCode)
            }

            var context = baseContext
            val resources: Resources = context.resources
            val configuration: Configuration = resources.configuration

            val localeList = LocaleList(localeToSwitchTo)
            LocaleList.setDefault(localeList)
            configuration.setLocales(localeList)

            context = context.createConfigurationContext(configuration)

            return ContextUtils(context)
        }
    }
}
