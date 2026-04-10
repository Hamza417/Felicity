package app.simple.felicity.preferences

import android.annotation.SuppressLint
import androidx.core.content.edit
import app.simple.felicity.manager.SharedPreferences
import app.simple.felicity.shared.utils.CalendarUtils
import java.util.Date

object TrialPreferences {

    private const val MAX_TRIAL_DAYS = 0xF

    private const val FIRST_LAUNCH = "first_launch_"
    private const val IS_APP_FULL_VERSION_ENABLED = "is_full_version_"
    private const val LAST_VERIFICATION_DATE = "last_verification_date_"
    private const val IS_EARLY_ACCESS_USER = "is_early_access_user_"
    private const val IS_SUPPORTER = "is_supporter_"

    const val HAS_LICENSE_KEY = "has_license_key"

    // ---------------------------------------------------------------------------------------------------------- //

    fun setFirstLaunchDate(date: Long) {
        SharedPreferences.getEncryptedSharedPreferences().edit { putLong(FIRST_LAUNCH, date) }
    }

    fun getFirstLaunchDate(): Long {
        return SharedPreferences.getEncryptedSharedPreferences().getLong(FIRST_LAUNCH, -1)
    }

    // ---------------------------------------------------------------------------------------------------------- //

    fun getDaysLeft(): Int {
        return kotlin.runCatching {
            MAX_TRIAL_DAYS - CalendarUtils.getDaysBetweenTwoDates(Date(getFirstLaunchDate()), CalendarUtils.getToday())
                .coerceAtLeast(0).coerceAtMost(MAX_TRIAL_DAYS)
        }.getOrElse {
            -1
        }
    }

    fun getMaxDays(): Int {
        return MAX_TRIAL_DAYS
    }

    // ---------------------------------------------------------------------------------------------------------- //

    @SuppressLint("UseKtx")
    fun setFullVersion(value: Boolean): Boolean {
        return SharedPreferences.getEncryptedSharedPreferences().edit().putBoolean(IS_APP_FULL_VERSION_ENABLED, value).commit()
    }

    fun isAppFullVersionEnabled(): Boolean {
        return SharedPreferences.getEncryptedSharedPreferences().getBoolean(IS_APP_FULL_VERSION_ENABLED, false) ||
                CalendarUtils.getDaysBetweenTwoDates(Date(getFirstLaunchDate()), CalendarUtils.getToday()) <= MAX_TRIAL_DAYS
    }

    fun isWithinTrialPeriod(): Boolean {
        return CalendarUtils.getDaysBetweenTwoDates(Date(getFirstLaunchDate()), CalendarUtils.getToday()) <= MAX_TRIAL_DAYS
    }

    fun isTrialWithoutFull(): Boolean {
        return CalendarUtils.getDaysBetweenTwoDates(Date(getFirstLaunchDate()), CalendarUtils.getToday()) <= MAX_TRIAL_DAYS
                && !isAppFullVersionEnabled()
    }

    fun isFullVersion(): Boolean {
        return SharedPreferences.getEncryptedSharedPreferences().getBoolean(IS_APP_FULL_VERSION_ENABLED, false)
    }

    // ---------------------------------------------------------------------------------------------------------- //

    fun reset() {
        setFirstLaunchDate(-1)
        setFullVersion(false)
    }

    // ---------------------------------------------------------------------------------------------------------- //

    fun setHasLicenceKey(hasLicence: Boolean) {
        SharedPreferences.getEncryptedSharedPreferences().edit { putBoolean(HAS_LICENSE_KEY, hasLicence) }
    }

    fun hasLicenceKey(): Boolean {
        return SharedPreferences.getEncryptedSharedPreferences().getBoolean(HAS_LICENSE_KEY, false)
    }

    // ---------------------------------------------------------------------------------------------------------- //

    fun setLastVerificationDate(date: Long) {
        SharedPreferences.getEncryptedSharedPreferences().edit { putLong(LAST_VERIFICATION_DATE, date) }
    }

    fun getLastVerificationDate(): Long {
        return SharedPreferences.getEncryptedSharedPreferences().getLong(LAST_VERIFICATION_DATE, -1L)
    }

    // ---------------------------------------------------------------------------------------------------------- //

    fun setIsEarlyAccessUser(isEarlyAccessUser: Boolean): Boolean {
        return SharedPreferences.getEncryptedSharedPreferences()
            .edit().putBoolean(IS_EARLY_ACCESS_USER, isEarlyAccessUser).commit()
    }

    fun isEarlyAccessUser(): Boolean {
        return SharedPreferences.getEncryptedSharedPreferences()
            .getBoolean(IS_EARLY_ACCESS_USER, false)
    }

    // ---------------------------------------------------------------------------------------------------------- //

    fun setIsSupporter(isSupporter: Boolean): Boolean {
        return SharedPreferences.getEncryptedSharedPreferences()
            .edit().putBoolean(IS_SUPPORTER, isSupporter).commit()
    }

    fun isSupporter(): Boolean {
        return SharedPreferences.getEncryptedSharedPreferences()
            .getBoolean(IS_SUPPORTER, false)
    }
}