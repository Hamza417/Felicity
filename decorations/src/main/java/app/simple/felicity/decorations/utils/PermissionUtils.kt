package app.simple.felicity.decorations.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment

object PermissionUtils {

    fun Context.isReadMediaAudioPermissionGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.READ_MEDIA_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

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

    fun Fragment.isReadMediaAudioPermissionGranted(): Boolean {
        return requireContext().isReadMediaAudioPermissionGranted()
    }

    fun Fragment.isPostNotificationsPermissionGranted(): Boolean {
        return requireContext().isPostNotificationsPermissionGranted()
    }
}