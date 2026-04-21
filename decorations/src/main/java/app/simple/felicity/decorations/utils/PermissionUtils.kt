package app.simple.felicity.decorations.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
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

    fun Context.isManageExternalStoragePermissionGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun Fragment.isPostNotificationsPermissionGranted(): Boolean {
        return requireContext().isPostNotificationsPermissionGranted()
    }

    fun Fragment.isManageExternalStoragePermissionGranted(): Boolean {
        return requireContext().isManageExternalStoragePermissionGranted()
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
}