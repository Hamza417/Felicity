package app.simple.felicity.decorations.pager

import android.content.Context
import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.opengl.Matrix
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.OverScroller
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.Future
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * FelicityPager
 * OpenGL based lightweight pager with recycling & auto sliding banners.
 * It renders bitmaps (supplied by adapter) on textured quads with simple horizontal translation.
 */
class FelicityPager @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null
) : GLSurfaceView(context, attrs), GestureDetector.OnGestureListener, Runnable {

    // ----- Public adapter + listeners -----
    interface Adapter {
        fun getCount(): Int

        /** Load (potentially heavy) bitmap for [position]. Runs off GL thread. Return null to skip. */
        fun loadBitmap(position: Int): Bitmap?

        /** Optional stable ids to improve cache retention */
        fun getItemId(position: Int): Long = position.toLong()
    }

    interface OnPageChangeListener {
        fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}
        fun onPageSelected(position: Int) {}
        fun onPageScrollStateChanged(state: Int) {}
    }

    companion object {
        const val SCROLL_STATE_IDLE = 0
        const val SCROLL_STATE_DRAGGING = 1
        const val SCROLL_STATE_SETTLING = 2
    }

    private val pageChangeListeners = CopyOnWriteArrayList<OnPageChangeListener>()

    fun addOnPageChangeListener(l: OnPageChangeListener) {
        pageChangeListeners.add(l)
    }

    fun removeOnPageChangeListener(l: OnPageChangeListener) {
        pageChangeListeners.remove(l)
    }

    fun clearOnPageChangeListeners() {
        pageChangeListeners.clear()
    }

    // ----- Rendering / GL -----
    private val renderer: PagerRenderer

    // ----- Scrolling state -----
    @Volatile
    private var scrollOffset = 0f // 0 .. (count-1)
    private var currentPage = 0
    private var scrollState = SCROLL_STATE_IDLE
    private val scroller = OverScroller(context)
    private val gestureDetector = GestureDetector(context, this)
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var isBeingDragged = false
    private var lastMotionX = 0f

    // ----- Auto slide -----
    private val mainHandler = Handler(Looper.getMainLooper())
    private var autoSlideInterval = 0L
    private val autoSlideRunnable = object : Runnable {
        override fun run() {
            if (autoSlideInterval > 0 && adapter?.getCount()?.let { it > 1 } == true && scrollState != SCROLL_STATE_DRAGGING) {
                val next = (currentPage + 1) % max(1, adapter!!.getCount())
                setCurrentItem(next, smoothScroll = true)
                mainHandler.postDelayed(this, autoSlideInterval)
            }
        }
    }

    fun startAutoSlide(intervalMs: Long) {
        autoSlideInterval = intervalMs
        mainHandler.removeCallbacks(autoSlideRunnable)
        if (intervalMs > 0) mainHandler.postDelayed(autoSlideRunnable, intervalMs)
    }

    fun stopAutoSlide() {
        autoSlideInterval = 0
        mainHandler.removeCallbacks(autoSlideRunnable)
    }

    // ----- Adapter -----
    private var adapter: Adapter? = null

    private fun maxLastPage(): Int = (adapter?.getCount() ?: 1) - 1
    private fun maxLastOffset(): Float = maxLastPage().toFloat()

    fun setAdapter(adapter: Adapter?) {
        queueEvent { renderer.clearAllTextures() }
        this.adapter = adapter
        scrollOffset = 0f
        currentPage = 0
        requestRender()
    }

    fun notifyDataSetChanged() {
        queueEvent { renderer.clearAllTextures() }
        if (scrollOffset > maxLastOffset()) scrollOffset = maxLastOffset()
        requestRender()
    }

    fun getCurrentItem(): Int = currentPage

    fun setCurrentItem(item: Int, smoothScroll: Boolean = false) {
        val bounded = item.coerceIn(0, maxLastPage())
        if (!smoothScroll) {
            scrollOffset = bounded.toFloat()
            scroller.abortAnimation()
            renderer.setScrollOffset(scrollOffset)
            dispatchOnPageScrolled()
            dispatchPageSelected(bounded)
            dispatchOnScrollStateChanged(SCROLL_STATE_IDLE)
            requestRender()
        } else {
            smoothScrollTo(bounded.toFloat())
        }
    }

    // Scrolling helpers
    private fun performDrag(deltaPixels: Float) {
        val widthPx = width.takeIf { it > 0 } ?: return
        val pageDelta = deltaPixels / widthPx
        scrollOffset = (scrollOffset + pageDelta).coerceIn(0f, maxLastOffset())
        renderer.setScrollOffset(scrollOffset)
        dispatchOnPageScrolled()
        requestRender()
    }

    private fun finishDrag() {
        val target = scrollOffset.roundToInt().coerceIn(0, maxLastPage())
        smoothScrollTo(target.toFloat())
        isBeingDragged = false
    }

    private fun smoothScrollTo(target: Float) {
        val start = scrollOffset
        if (start == target) {
            dispatchPageSelected(target.toInt())
            dispatchOnScrollStateChanged(SCROLL_STATE_IDLE)
            return
        }
        dispatchOnScrollStateChanged(SCROLL_STATE_SETTLING)
        val widthPx = width.takeIf { it > 0 } ?: 1
        scroller.startScroll((start * widthPx).toInt(), 0, ((target - start) * widthPx).toInt(), 0, 400)
        queueAnimationTick()
    }

    private var animPosted = false
    private fun queueAnimationTick() {
        if (!animPosted) {
            animPosted = true
            mainHandler.post(this)
        }
    }

    override fun run() { // animation frame
        animPosted = false
        if (scroller.computeScrollOffset()) {
            val widthPx = width.takeIf { it > 0 } ?: 1
            scrollOffset = (scroller.currX.toFloat() / widthPx).coerceIn(0f, maxLastOffset())
            renderer.setScrollOffset(scrollOffset)
            dispatchOnPageScrolled()
            requestRender()
            queueAnimationTick()
        } else {
            scrollOffset = scrollOffset.roundToInt().toFloat()
            renderer.setScrollOffset(scrollOffset)
            dispatchOnPageScrolled()
            dispatchPageSelected(scrollOffset.toInt())
            dispatchOnScrollStateChanged(SCROLL_STATE_IDLE)
            requestRender()
        }
    }

    private fun dispatchOnPageScrolled() {
        val pos = scrollOffset.toInt().coerceAtMost(maxLastPage())
        val offset = (scrollOffset - pos).coerceIn(0f, 1f)
        val px = (offset * width).toInt()
        pageChangeListeners.forEach { it.onPageScrolled(pos, offset, px) }
    }

    private fun dispatchPageSelected(position: Int) {
        if (position != currentPage) {
            currentPage = position
            pageChangeListeners.forEach { it.onPageSelected(position) }
        }
    }

    private fun dispatchOnScrollStateChanged(newState: Int) {
        if (scrollState != newState) {
            scrollState = newState
            pageChangeListeners.forEach { it.onPageScrollStateChanged(newState) }
        }
    }

    // ----- Init -----
    init {
        setEGLContextClientVersion(2)
        renderer = PagerRenderer()
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY // for smooth animations
    }

    // ----- Gesture handling -----
    override fun onTouchEvent(event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!scroller.isFinished) scroller.abortAnimation()
                lastMotionX = event.x
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - lastMotionX
                if (!isBeingDragged && abs(dx) > touchSlop) {
                    isBeingDragged = true
                    dispatchOnScrollStateChanged(SCROLL_STATE_DRAGGING)
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
                if (isBeingDragged) performDrag(-dx)
                lastMotionX = event.x
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isBeingDragged) finishDrag() else if (event.actionMasked == MotionEvent.ACTION_UP) performClick()
                isBeingDragged = false
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        return super.performClick()
    }

    // ----- GestureDetector callbacks -----
    override fun onDown(e: MotionEvent): Boolean = true
    override fun onShowPress(e: MotionEvent) {}
    override fun onSingleTapUp(e: MotionEvent): Boolean {
        performClick()
        return true
    }

    override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean = false
    override fun onLongPress(e: MotionEvent) {}
    override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
        val widthPx = width.takeIf { it > 0 } ?: return false
        val velocityPagesPerSec = velocityX / widthPx
        var target = (scrollOffset - velocityPagesPerSec * 0.25f).roundToInt()
        target = target.coerceIn(0, maxLastPage())
        smoothScrollTo(target.toFloat())
        return true
    }

    // ----- Lifecycle -----
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopAutoSlide()
        queueEvent { renderer.clearAllTextures(); renderer.release() }
    }

    // ----- View <-> Renderer interaction -----
    private inner class PagerRenderer : Renderer {

        // Matrices
        private val proj = FloatArray(16)
        private val viewM = FloatArray(16)
        private val model = FloatArray(16)
        private val mvp = FloatArray(16)

        // Geometry quad
        private val quadVerts = floatArrayOf(
                -0.5f, 0.5f, 0f,
                -0.5f, -0.5f, 0f,
                0.5f, -0.5f, 0f,
                0.5f, 0.5f, 0f
        )
        private val quadUV = floatArrayOf(
                0f, 0f,
                0f, 1f,
                1f, 1f,
                1f, 0f
        )
        private val quadInd = shortArrayOf(0, 1, 2, 0, 2, 3)
        private lateinit var vb: java.nio.FloatBuffer
        private lateinit var tb: java.nio.FloatBuffer
        private lateinit var ib: java.nio.ShortBuffer

        // Program
        private var program = 0
        private var aPos = 0
        private var aUV = 0
        private var uMVP = 0
        private var uTex = 0
        private var uAlpha = 0

        // Textures & decode
        private val textures = ConcurrentHashMap<Long, Int>() // id -> tex
        private val positionToId = ConcurrentHashMap<Int, Long>()
        private val inFlight = ConcurrentHashMap<Int, Boolean>()
        private val decodeExecutor = Executors.newFixedThreadPool(2)
        private val futures = ConcurrentHashMap<Int, Future<*>>()

        // Radii
        private var visibleRadius = 1 // one each side
        private var keepRadius = 2

        // Scroll offset copy used in GL thread
        @Volatile
        private var glScrollOffset = 0f

        fun setScrollOffset(offset: Float) {
            glScrollOffset = offset
        }

        fun clearAllTextures() {
            textures.values.forEach { id -> GLES20.glDeleteTextures(1, intArrayOf(id), 0) }
            textures.clear()
            positionToId.clear()
            inFlight.clear()
            futures.values.forEach { it.cancel(true) }
            futures.clear()
        }

        fun release() {
            futures.values.forEach { it.cancel(true) }
            decodeExecutor.shutdownNow()
        }

        override fun onSurfaceCreated(unused: javax.microedition.khronos.opengles.GL10?, config: javax.microedition.khronos.egl.EGLConfig?) {
            GLES20.glClearColor(0f, 0f, 0f, 1f)
            GLES20.glEnable(GLES20.GL_BLEND)
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
            // Buffers
            vb = ByteBuffer.allocateDirect(quadVerts.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(quadVerts); position(0) }
            tb = ByteBuffer.allocateDirect(quadUV.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(quadUV); position(0) }
            ib = ByteBuffer.allocateDirect(quadInd.size * 2).order(ByteOrder.nativeOrder()).asShortBuffer().apply { put(quadInd); position(0) }
            buildProgram()
            Matrix.setLookAtM(viewM, 0, 0f, 0f, 2.5f, 0f, 0f, 0f, 0f, 1f, 0f)
        }

        override fun onSurfaceChanged(unused: javax.microedition.khronos.opengles.GL10?, width: Int, height: Int) {
            GLES20.glViewport(0, 0, width, height)
            val aspect = width.toFloat() / height
            Matrix.frustumM(proj, 0, -aspect, aspect, -1f, 1f, 2f, 10f)
        }

        override fun onDrawFrame(unused: javax.microedition.khronos.opengles.GL10?) {
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
            // Preload visible pages
            preloadAround(glScrollOffset)
            val count = adapter?.getCount() ?: 0
            if (count == 0) return
            val center = glScrollOffset
            val first = max(0, center.toInt() - visibleRadius)
            val last = min(count - 1, center.toInt() + visibleRadius + 1)
            for (i in first..last) {
                drawPage(i, center - i)
            }
            recycleFar(center)
        }

        private fun buildProgram() {
            val vs = """
                attribute vec3 aPos; attribute vec2 aUV; uniform mat4 uMVP; varying vec2 vUV; void main(){vUV=aUV; gl_Position = uMVP*vec4(aPos,1.0);}"""
            val fs = """
                precision mediump float; varying vec2 vUV; uniform sampler2D uTex; uniform float uAlpha; void main(){ vec4 c=texture2D(uTex,vUV); gl_FragColor=vec4(c.rgb, c.a*uAlpha);}"""
            val vId = compileShader(GLES20.GL_VERTEX_SHADER, vs)
            val fId = compileShader(GLES20.GL_FRAGMENT_SHADER, fs)
            program = GLES20.glCreateProgram()
            GLES20.glAttachShader(program, vId)
            GLES20.glAttachShader(program, fId)
            GLES20.glLinkProgram(program)
            aPos = GLES20.glGetAttribLocation(program, "aPos")
            aUV = GLES20.glGetAttribLocation(program, "aUV")
            uMVP = GLES20.glGetUniformLocation(program, "uMVP")
            uTex = GLES20.glGetUniformLocation(program, "uTex")
            uAlpha = GLES20.glGetUniformLocation(program, "uAlpha")
        }

        private fun compileShader(type: Int, src: String): Int {
            val id = GLES20.glCreateShader(type)
            GLES20.glShaderSource(id, src)
            GLES20.glCompileShader(id)
            return id
        }

        private fun drawPage(position: Int, offsetFromPage: Float) {
            val count = adapter?.getCount() ?: return
            if (position < 0 || position >= count) return
            val id = adapter?.getItemId(position) ?: position.toLong()
            val tex = textures[id] ?: return // not loaded yet
            val scale = 1.6f // base scale to fill height
            Matrix.setIdentityM(model, 0)
            Matrix.translateM(model, 0, -offsetFromPage * scale * 0.65f, 0f, 0f)
            Matrix.scaleM(model, 0, scale, scale, 1f)
            Matrix.multiplyMM(mvp, 0, viewM, 0, model, 0)
            Matrix.multiplyMM(mvp, 0, proj, 0, mvp, 0)
            GLES20.glUseProgram(program)
            GLES20.glUniformMatrix4fv(uMVP, 1, false, mvp, 0)
            GLES20.glUniform1f(uAlpha, 1f - min(1f, abs(offsetFromPage)))
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex)
            GLES20.glUniform1i(uTex, 0)
            GLES20.glEnableVertexAttribArray(aPos)
            GLES20.glEnableVertexAttribArray(aUV)
            GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, 0, vb)
            GLES20.glVertexAttribPointer(aUV, 2, GLES20.GL_FLOAT, false, 0, tb)
            GLES20.glDrawElements(GLES20.GL_TRIANGLES, 6, GLES20.GL_UNSIGNED_SHORT, ib)
            GLES20.glDisableVertexAttribArray(aPos)
            GLES20.glDisableVertexAttribArray(aUV)
        }

        private fun preloadAround(center: Float) {
            val count = adapter?.getCount() ?: return
            val start = max(0, center.toInt() - visibleRadius - 1)
            val end = min(count - 1, center.toInt() + visibleRadius + 1)
            for (i in start..end) maybeLoad(i)
        }

        private fun recycleFar(center: Float) {
            val count = adapter?.getCount() ?: return
            val lowKeep = max(0, center.toInt() - keepRadius)
            val highKeep = min(count - 1, center.toInt() + keepRadius)
            val it = textures.entries.iterator()
            while (it.hasNext()) {
                val e = it.next()
                val pos = positionToId.entries.firstOrNull { p -> p.value == e.key }?.key
                if (pos == null || pos < lowKeep || pos > highKeep) {
                    GLES20.glDeleteTextures(1, intArrayOf(e.value), 0)
                    it.remove()
                }
            }
        }

        private fun maybeLoad(position: Int) {
            if (inFlight[position] == true) return
            val id = adapter?.getItemId(position) ?: position.toLong()
            if (textures.containsKey(id)) return
            val ad = adapter ?: return
            inFlight[position] = true
            futures[position] = decodeExecutor.submit {
                val bmp = try {
                    ad.loadBitmap(position)
                } catch (_: Throwable) {
                    null
                }
                if (bmp != null) {
                    val cropped = centerCrop(bmp)
                    queueEvent {
                        val texId = createTexture(cropped)
                        textures[id] = texId
                        positionToId[position] = id
                    }
                }
                inFlight.remove(position)
            }
        }

        private fun centerCrop(src: Bitmap): Bitmap {
            val w = src.width;
            val h = src.height
            if (w == h) return src
            return if (w > h) {
                val x = (w - h) / 2
                Bitmap.createBitmap(src, x, 0, h, h)
            } else {
                val y = (h - w) / 2
                Bitmap.createBitmap(src, 0, y, w, w)
            }
        }

        private fun createTexture(bitmap: Bitmap): Int {
            val ids = IntArray(1)
            GLES20.glGenTextures(1, ids, 0)
            val id = ids[0]
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, id)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
            bitmap.recycle()
            return id
        }
    }
}