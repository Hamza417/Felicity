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
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import app.simple.felicity.callbacks.GeneralAdapterCallbacks
import app.simple.felicity.databinding.AdapterPlayingQueueBinding
import app.simple.felicity.decorations.overscroll.VerticalListViewHolder
import app.simple.felicity.decorations.utils.TextViewUtils.setTextOrUnknown
import app.simple.felicity.glide.util.AudioCoverUtils.loadArtCoverWithPayload
import app.simple.felicity.repository.managers.MediaManager
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.shared.utils.ColorUtils.changeAlpha
import app.simple.felicity.theme.managers.ThemeManager
import app.simple.felicity.utils.AdapterUtils.addAudioQualityIcon
import com.bumptech.glide.Glide

class AdapterPlayingQueue(initial: List<Audio>) : RecyclerView.Adapter<AdapterPlayingQueue.QueueHolder>() {

    private var generalAdapterCallbacks: GeneralAdapterCallbacks? = null
    private var itemTouchHelper: ItemTouchHelper? = null
    private var onItemMovedCallback: ((fromPosition: Int, toPosition: Int) -> Unit)? = null
    private var onItemSwipedCallback: ((position: Int) -> Unit)? = null

    // Plain mutable list — single source of truth, mutated directly on the main thread.
    // No AsyncListDiffer: async diffs race with ItemTouchHelper and cause wrong highlights.
    private val songs: MutableList<Audio> = mutableListOf()

    // True while a drag gesture is in progress — defers external list updates.
    private var isDragInProgress = false
    private var pendingList: List<Audio>? = null


    init {
        setHasStableIds(true)
        songs.addAll(initial)
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

    /**
     * Apply a new list from outside (ViewModel / MediaManager flow).
     * Deferred while a drag is in progress to avoid visual conflicts.
     * Uses synchronous DiffUtil — queue sizes are always small.
     */
    fun updateSongs(newSongs: List<Audio>) {
        if (isDragInProgress) {
            pendingList = newSongs.toList()
            return
        }
        applyListWithDiff(newSongs)
    }

    private fun applyListWithDiff(newSongs: List<Audio>) {
        val oldSongs = songs.toList()
        val result = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = oldSongs.size
            override fun getNewListSize() = newSongs.size
            override fun areItemsTheSame(o: Int, n: Int) = oldSongs[o].id == newSongs[n].id
            override fun areContentsTheSame(o: Int, n: Int): Boolean {
                val a = oldSongs[o];
                val b = newSongs[n]
                return a.title == b.title && a.artist == b.artist &&
                        a.album == b.album && a.duration == b.duration && a.path == b.path
            }
        })
        songs.clear()
        songs.addAll(newSongs)
        result.dispatchUpdatesTo(this)
    }

    internal fun onDragStarted() {
        isDragInProgress = true
        pendingList = null
    }

    internal fun onDragEnded() {
        isDragInProgress = false
        val pending = pendingList
        pendingList = null
        if (pending != null) {
            applyListWithDiff(pending)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    inner class QueueHolder(val binding: AdapterPlayingQueueBinding) : VerticalListViewHolder(binding.root) {

        fun bindSelectionState(audio: Audio) {
            binding.container.setAudioID(audio.id)
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

    private inner class DragShimmerCallback(
            context: Context,
            @ColorInt private val accentColor: Int
    ) : ItemTouchHelper.Callback() {

        private val density = context.resources.displayMetrics.density
        private val shimmerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val shimmerRect = RectF()
        private val cornerRadius = 12f * density

        private var shimmerFraction = 0f
        private var shimmerAlpha = 0f
        private var shimmerAnimator: ValueAnimator? = null
        private var releaseAnimator: ValueAnimator? = null

        private var dragFromPosition = -1
        private var dragToPosition = -1
        private var isDragging = false

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
            if (from < 0 || to < 0 || from >= songs.size || to >= songs.size) return false

            if (dragFromPosition == -1) dragFromPosition = from
            dragToPosition = to

            // Mutate backing list directly — no DiffUtil, no async, no races.
            songs.add(to, songs.removeAt(from))
            // Tell RecyclerView exactly what moved — ItemTouchHelper owns the animation.
            notifyItemMoved(from, to)
            // Mirror the move in MediaManager silently (no songPositionFlow, no ExoPlayer call).
            MediaManager.moveQueueItemSilently(from, to)

            return true
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
            val position = viewHolder.bindingAdapterPosition
            if (position >= 0) {
                onItemSwipedCallback?.invoke(position)
            }
        }

        override fun isLongPressDragEnabled(): Boolean = false

        override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
            super.onSelectedChanged(viewHolder, actionState)
            if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                onDragStarted()
                startShimmer(viewHolder?.itemView?.width?.toFloat() ?: 0f)
            }
        }

        override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
            super.clearView(recyclerView, viewHolder)

            // MediaManager's list and ExoPlayer queue are already fully synced by the
            // incremental moveQueueItemSilently calls in onMove — no extra callback needed.

            dragFromPosition = -1
            dragToPosition = -1
            isDragging = false
            shimmerAlpha = 0f

            onDragEnded()
        }

        override fun onChildDraw(
                c: Canvas,
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                dX: Float, dY: Float,
                actionState: Int,
                isCurrentlyActive: Boolean
        ) {
            super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)

            if (isCurrentlyActive && !isDragging) {
                isDragging = true
            } else if (!isCurrentlyActive && isDragging) {
                isDragging = false
                stopShimmerAndFadeOut(viewHolder.itemView)
            }

            if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && shimmerAlpha > 0f) {
                val view = viewHolder.itemView
                val left = view.left.toFloat()
                val top = view.top.toFloat() + dY
                val right = view.right.toFloat()
                val bottom = view.bottom.toFloat() + dY
                val width = right - left
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

        private fun startShimmer(itemWidth: Float) {
            releaseAnimator?.cancel()
            shimmerAlpha = 1f
            shimmerAnimator?.cancel()
            shimmerAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 900
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.RESTART
                interpolator = DecelerateInterpolator(0.7f)
                addUpdateListener { shimmerFraction = it.animatedValue as Float }
                start()
            }
        }

        private fun stopShimmerAndFadeOut(itemView: android.view.View) {
            shimmerAnimator?.cancel()
            shimmerAnimator = null
            releaseAnimator?.cancel()
            val capturedFraction = shimmerFraction
            releaseAnimator = ValueAnimator.ofFloat(1f, 0f).apply {
                duration = 250
                interpolator = DecelerateInterpolator()
                addUpdateListener {
                    shimmerAlpha = it.animatedValue as Float
                    shimmerFraction = capturedFraction + (1f - capturedFraction) * (1f - shimmerAlpha)
                    itemView.invalidate()
                }
                start()
            }
        }
    }

    companion object {
    }
}