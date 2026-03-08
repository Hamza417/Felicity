package app.simple.felicity.adapters.home.sub

import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import app.simple.felicity.decorations.pager.FelicityPager
import app.simple.felicity.glide.util.AudioCoverUtils.loadArtCover
import app.simple.felicity.models.ArtFlowData
import com.bumptech.glide.Glide

/**
 * A [FelicityPager.PageAdapter] that drives a [app.simple.felicity.decorations.pager.FelicitySlider]
 * from an [ArtFlowData] source, loading artwork via Glide (through [loadArtCover]).
 *
 * Replaces [ArtistArtFlowAdapter] (which was tied to the third-party
 * `com.smarteist:Android-Image-Slider` library) wherever a [FelicitySlider] is used.
 */
class ArtFlowSliderAdapter(private var data: ArtFlowData<Any>) : FelicityPager.PageAdapter {

    override fun getCount(): Int = data.items.size.coerceAtMost(12)

    override fun getItemId(position: Int): Long = position.toLong()

    override fun onCreateView(position: Int, parent: ViewGroup): View {
        return ImageView(parent.context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
    }

    override fun onBindView(position: Int, view: View) {
        val iv = view as ImageView
        if (data.items.isNotEmpty()) {
            iv.loadArtCover(
                    item = data.items[position],
                    roundedCorners = false,
                    blur = false,
                    skipCache = false,
                    crop = true
            )
        }
    }

    override fun onRecycleView(position: Int, view: View) {
        val iv = view as ImageView
        Glide.with(iv.context).clear(iv)
        iv.setImageDrawable(null)
    }

    /** Updates the underlying data. Call [app.simple.felicity.decorations.pager.FelicitySlider.notifyDataSetChanged] afterwards. */
    fun updateData(newData: ArtFlowData<Any>) {
        data = newData
    }
}

