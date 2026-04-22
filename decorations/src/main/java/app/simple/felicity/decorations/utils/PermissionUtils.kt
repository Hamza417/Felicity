package app.simple.felicity.decorations.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import app.simple.felicity.preferences.SAFPreferences

object PermissionUtils {

    fun Context.isPostNotificationsPermissionGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Not required below Android 13
        }
    }

    fun Fragment.isPostNotificationsPermissionGranted(): Boolean {
        return requireContext().isPostNotificationsPermissionGranted()
    }

    /**
     * Returns true when the user has granted access to at least one folder via SAF.
     * This is the check we use to decide whether the app is ready to scan music.
     */
    fun Context.isSAFAccessGranted(): Boolean {
        return SAFPreferences.hasAnyTreeUri()
    }

    fun Fragment.isSAFAccessGranted(): Boolean {
        return requireContext().isSAFAccessGranted()
    }

    /**
     * Checks whether the READ_MEDIA_AUDIO permission has been granted.
     *
     * On Android 13 (Tiramisu) and above this is a real runtime permission that the user
     * has to accept. On older versions MediaStore audio access is always available so we
     * just return true — no need to bug the user about something they already have.
     */
    fun Context.isReadMediaAudioPermissionGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.READ_MEDIA_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Already accessible on Android 12 and below — lucky us!
        }
    }

    fun Fragment.isReadMediaAudioPermissionGranted(): Boolean {
        return requireContext().isReadMediaAudioPermissionGranted()
    }
}