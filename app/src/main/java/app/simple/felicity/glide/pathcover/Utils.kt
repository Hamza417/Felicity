package app.simple.felicity.glide.pathcover

import android.widget.ImageView
import com.bumptech.glide.Glide

object Utils {

    fun ImageView.loadFromPath(path: String) {
        Glide.with(context)
            .load(PathCoverModel(context, path))
            .into(this)
    }
}
