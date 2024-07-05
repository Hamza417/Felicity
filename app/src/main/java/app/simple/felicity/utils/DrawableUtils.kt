package app.simple.felicity.utils

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat

object DrawableUtils {

    /**
     * Converts drawable resource to drawable
     * @param context is the context
     * @return drawable
     * @receiver drawable resource
     */
    fun @receiver:DrawableRes Int.toDrawable(context: Context): Drawable {
        return ContextCompat.getDrawable(context, this)!!
    }
}
