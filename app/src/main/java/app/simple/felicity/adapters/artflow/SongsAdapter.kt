package app.simple.felicity.adapters.artflow

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import app.simple.felicity.R
import app.simple.felicity.decorations.ripple.DynamicRippleConstraintLayout
import app.simple.felicity.decorations.typeface.TypeFaceTextView
import app.simple.felicity.glide.pathcover.Utils.loadFromPath
import app.simple.felicity.repository.models.normal.Audio

class SongsAdapter(private val context: Context, private val audio: ArrayList<Audio>) : BaseAdapter() {

    class Holder {
        lateinit var art: ImageView
        lateinit var title: TypeFaceTextView
        lateinit var artist: TypeFaceTextView
        lateinit var details: TypeFaceTextView
        lateinit var container: DynamicRippleConstraintLayout
    }

    override fun getCount(): Int {
        return audio.size
    }

    override fun getItem(position: Int): Audio {
        return audio[position]
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val holder: Holder
        val view: View

        if (convertView == null) {
            view = LayoutInflater.from(context).inflate(R.layout.adapter_songs_flow, parent, false)
            holder = Holder()
            holder.art = view.findViewById(R.id.album_art)
            holder.title = view.findViewById(R.id.title)
            holder.artist = view.findViewById(R.id.artist)
            holder.details = view.findViewById(R.id.details)
            holder.container = view.findViewById(R.id.container)
            view.tag = holder
        } else {
            view = convertView
            holder = convertView.tag as Holder
        }

        val audio = getItem(position)

        holder.title.text = audio.title
        holder.artist.text = audio.artist
        holder.details.text = audio.album

        holder.art.loadFromPath(audio.path)

        return view
    }
}
