package app.simple.felicity.adapters.ui.lists

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import androidx.annotation.ColorInt
import androidx.recyclerview.widget.AsyncDifferConfig
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.ListUpdateCallback
import androidx.recyclerview.widget.RecyclerView
import app.simple.felicity.callbacks.GeneralAdapterCallbacks
import app.simple.felicity.databinding.AdapterPlayingQueueBinding
import app.simple.felicity.decorations.overscroll.VerticalListViewHolder
import app.simple.felicity.glide.util.AudioCoverUtils.loadArtCoverWithPayload
import app.simple.felicity.repository.managers.MediaManager
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.shared.utils.ColorUtils.changeAlpha
import app.simple.felicity.shared.utils.TextViewUtils.setTextOrUnknown
import app.simple.felicity.theme.managers.ThemeManager
import app.simple.felicity.utils.AdapterUtils.addAudioQualityIcon
import com.bumptech.glide.Glide

class AdapterPlayingQueue(initial: List<Audio>) : RecyclerView.Adapter<AdapterPlayingQueue.QueueHolder>() {

    private var generalAdapterCallbacks: GeneralAdapterCallbacks? = null
    private var itemTouchHelper: ItemTouchHelper? = null
    private var onItemMovedCallback: ((fromPosition: Int, toPosition: Int) -> Unit)? = null
    private var onItemSwipedCallback: ((position: Int) -> Unit)? = null
    private var previousIndex = -1

    private val listUpdateCallback = object : ListUpdateCallback {
        @SuppressLint("NotifyDataSetChanged")
        override fun onInserted(position: Int, count: Int) {
            if (count > 100) notifyDataSetChanged() else notifyItemRangeInserted(position, count)
        }

        @SuppressLint("NotifyDataSetChanged")
        override fun onRemoved(position: Int, count: Int) {
            if (count > 100) notifyDataSetChanged() else notifyItemRangeRemoved(position, count)
        }

        override fun onMoved(fromPosition: Int, toPosition: Int) {
            notifyItemMoved(fromPosition, toPosition)
        }

        override fun onChanged(position: Int, count: Int, payload: Any?) {
            notifyItemRangeChanged(position, count, payload)
        }
    }

    private val diffCallback = object : DiffUtil.ItemCallback<Audio>() {
        override fun areItemsTheSame(oldItem: Audio, newItem: Audio) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Audio, newItem: Audio): Boolean {
            return oldItem.title == newItem.title &&
                    oldItem.artist == newItem.artist &&
                    oldItem.album == newItem.album &&
                    oldItem.duration == newItem.duration &&
                    oldItem.path == newItem.path
        }
    }

    private val differ = AsyncListDiffer(
            listUpdateCallback,
            AsyncDifferConfig.Builder(diffCallback).build()
    )

    private val songs: List<Audio> get() = differ.currentList

    init {
        setHasStableIds(true)
        differ.submitList(initial.toList())
    }

    var currentlyPlayingSong: Audio? = null
        set(value) {
            val oldIndex = previousIndex
            field = value
            val newIndex = value?.let { v -> songs.indexOfFirst { it.id == v.id } } ?: -1
            if (newIndex != -1) notifyItemChanged(newIndex, PAYLOAD_PLAYBACK_STATE)
            if (oldIndex != -1 && oldIndex != newIndex) notifyItemChanged(oldIndex, PAYLOAD_PLAYBACK_STATE)
            previousIndex = newIndex
        }

    override fun getItemId(position: Int): Long = songs[position].id

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        val accentColor = ThemeManager.accent.primaryAccentColor
        val callback = DragShimmerCallback(recyclerView.context, accentColor)
        itemTouchHelper = ItemTouchHelper(callback)
        itemTouchHelper?.attachToRecyclerView(recyclerView)
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        itemTouchHelper?.attachToRecyclerView(null)
        itemTouchHelper = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QueueHolder {
        return QueueHolder(
                AdapterPlayingQueueBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )
    }

    override fun onBindViewHolder(holder: QueueHolder, position: Int) {
        holder.bind(songs[position], isLightBind = false)
    }

    override fun onBindViewHolder(holder: QueueHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains(PAYLOAD_PLAYBACK_STATE)) {
            holder.bindSelectionState(songs[position])
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    override fun getItemCount(): Int = songs.size

    override fun onViewRecycled(holder: QueueHolder) {
        holder.itemView.clearAnimation()
        super.onViewRecycled(holder)
        Glide.with(holder.binding.cover).clear(holder.binding.cover)
    }

    fun setGeneralAdapterCallbacks(callbacks: GeneralAdapterCallbacks) {
        this.generalAdapterCallbacks = callbacks
    }

    fun setOnItemMovedCallback(callback: (fromPosition: Int, toPosition: Int) -> Unit) {
        this.onItemMovedCallback = callback
    }

    fun setOnItemSwipedCallback(callback: (position: Int) -> Unit) {
        this.onItemSwipedCallback = callback
    }

    fun updateSongs(newSongs: List<Audio>) {
        differ.submitList(newSongs.toList())
    }

    @SuppressLint("ClickableViewAccessibility")
    inner class QueueHolder(val binding: AdapterPlayingQueueBinding) : VerticalListViewHolder(binding.root) {

        fun bindSelectionState(song: Audio) {
            binding.container.isSelected = MediaManager.getCurrentSongId() == song.id
        }

        fun bind(audio: Audio, isLightBind: Boolean) {
            binding.title.setTextOrUnknown(audio.title)
            binding.secondaryDetail.setTextOrUnknown(audio.artist)
            binding.tertiaryDetail.setTextOrUnknown(audio.album)
            binding.title.addAudioQualityIcon(audio)
            bindSelectionState(audio)

            binding.dragHandle.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    itemTouchHelper?.startDrag(this)
                }
                false
            }

            if (isLightBind) return
            binding.cover.loadArtCoverWithPayload(audio)

            binding.container.setOnLongClickListener {
                generalAdapterCallbacks?.onSongLongClicked(songs, bindingAdapterPosition, binding.cover)
                true
            }

            binding.container.setOnClickListener {
                generalAdapterCallbacks?.onSongClicked(songs, bindingAdapterPosition, it)
            }
        }
    }

    // Custom ItemTouchHelper.Callback that draws a shimmer effect
    // on the dragged item and notifies the adapter of moves and swipes.
    private inner class DragShimmerCallback(
            context: Context,
            @ColorInt private val accentColor: Int
    ) : ItemTouchHelper.Callback() {

        private val density = context.resources.displayMetrics.density

        // Paint for the traveling shimmer band
        private val shimmerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val shimmerRect = RectF()
        private val cornerRadius = 12f * density

        // Shimmer sweep fraction [0..1] driven by a looping animator while dragging
        private var shimmerFraction = 0f
        private var shimmerAlpha = 0f                     // 0 = invisible, 1 = fully visible
        private var shimmerAnimator: ValueAnimator? = null // looping sweep
        private var releaseAnimator: ValueAnimator? = null // fade-out after drop

        // Drag tracking
        private var dragFromPosition = -1
        private var dragToPosition = -1

        override fun getMovementFlags(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder
        ): Int = makeMovementFlags(
                ItemTouchHelper.UP or ItemTouchHelper.DOWN,
                ItemTouchHelper.START or ItemTouchHelper.END
        )

        override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
        ): Boolean {
            val from = viewHolder.bindingAdapterPosition
            val to = target.bindingAdapterPosition
            if (from == RecyclerView.NO_ID.toInt() || to == RecyclerView.NO_ID.toInt()) return false

            if (dragFromPosition == -1) dragFromPosition = from
            dragToPosition = to

            val currentList = songs.toMutableList()
            val moved = currentList.removeAt(from)
            currentList.add(to, moved)
            differ.submitList(currentList)
            return true
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
            val position = viewHolder.bindingAdapterPosition
            if (position != RecyclerView.NO_ID.toInt()) {
                onItemSwipedCallback?.invoke(position)
            }
        }

        override fun isLongPressDragEnabled(): Boolean = false

        // Suppress the default grey elevation shadow by returning 0
        // override fun getDefaultUIUtil() = super.getDefaultUIUtil()

        // Start the shimmer when an item is selected for dragging, and stop it when released.

        override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
            super.onSelectedChanged(viewHolder, actionState)
            if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                startShimmer(viewHolder?.itemView?.width?.toFloat() ?: 0f)
            }
        }

        override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
            super.clearView(recyclerView, viewHolder)

            // Commit the reorder
            if (dragFromPosition != -1 && dragToPosition != -1 && dragFromPosition != dragToPosition) {
                onItemMovedCallback?.invoke(dragFromPosition, dragToPosition)
            }
            dragFromPosition = -1
            dragToPosition = -1

            // Stop the looping sweep and fade the shimmer out
            stopShimmerAndFadeOut(viewHolder.itemView)
        }

        // Draw the shimmer effect over the dragged item. The shimmer is a sweeping gradient band
        // that moves across the item while dragging, and then fades out after release.
        override fun onChildDraw(
                c: Canvas,
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                dX: Float,
                dY: Float,
                actionState: Int,
                isCurrentlyActive: Boolean
        ) {
            super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)

            if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && isCurrentlyActive && shimmerAlpha > 0f) {
                val view = viewHolder.itemView
                val left = view.left.toFloat()
                val top = view.top.toFloat() + dY
                val right = view.right.toFloat()
                val bottom = view.bottom.toFloat() + dY
                val width = right - left

                // Shimmer band: a 30 % wide feathered band sweeping left → right
                val bandWidth = width * 0.35f
                val center = left + shimmerFraction * (width + bandWidth) - bandWidth * 0.5f

                val solidColor = changeAlpha(accentColor, (shimmerAlpha * 90).toInt())
                val edgeColor = changeAlpha(accentColor, 0)

                shimmerPaint.shader = LinearGradient(
                        center - bandWidth * 0.5f, top,
                        center + bandWidth * 0.5f, top,
                        intArrayOf(edgeColor, solidColor, edgeColor),
                        floatArrayOf(0f, 0.5f, 1f),
                        Shader.TileMode.CLAMP
                )

                shimmerRect.set(left, top, right, bottom)
                c.drawRoundRect(shimmerRect, cornerRadius, cornerRadius, shimmerPaint)
            }
        }

        // Helpers

        private fun startShimmer(itemWidth: Float) {
            releaseAnimator?.cancel()
            shimmerAlpha = 1f

            shimmerAnimator?.cancel()
            shimmerAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 900
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.RESTART
                interpolator = DecelerateInterpolator(0.7f)
                addUpdateListener {
                    shimmerFraction = it.animatedValue as Float
                }
                start()
            }
        }

        private fun stopShimmerAndFadeOut(itemView: android.view.View) {
            shimmerAnimator?.cancel()
            shimmerAnimator = null

            releaseAnimator?.cancel()
            val capturedFraction = shimmerFraction
            releaseAnimator = ValueAnimator.ofFloat(1f, 0f).apply {
                duration = 420
                interpolator = DecelerateInterpolator()
                addUpdateListener {
                    shimmerAlpha = it.animatedValue as Float
                    // Keep band at the last position so it "shimmers away" from the drop point
                    shimmerFraction = capturedFraction + (1f - capturedFraction) * (1f - shimmerAlpha)
                    itemView.invalidate()
                }
                start()
            }
        }
    }

    companion object {
        const val PAYLOAD_PLAYBACK_STATE = "payload_playing_state"
    }
}
