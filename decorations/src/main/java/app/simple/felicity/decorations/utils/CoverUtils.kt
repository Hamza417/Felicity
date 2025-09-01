package app.simple.felicity.decorations.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.Log
import android.util.Size
import androidx.annotation.DrawableRes
import app.simple.felicity.core.helpers.ImageHelper.toBitmap
import app.simple.felicity.decoration.R

object CoverUtils {
    fun getAlbumArtBitmap(context: Context, uri: Uri, dimension: Int, @DrawableRes defaultArt: Int = R.drawable.ic_felicity_full_art): Bitmap {
        try {
            return (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.contentResolver.loadThumbnail(uri, Size(dimension, dimension), null)
            } else {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    BitmapFactory.decodeFileDescriptor(pfd.fileDescriptor)
                }
            })!!
        } catch (e: Exception) {
            Log.w("ArtFlow", "Decode failed for $uri: ${e.message}")
            return defaultArt.toBitmap(context)
        }
    }
}