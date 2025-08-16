package app.simple.felicity.decorations.coverflow

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.opengl.Matrix
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Size
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class CoverFlowRenderer(
        private val glView: GLSurfaceView,
        private val context: Context
) : GLSurfaceView.Renderer {

    // ----- Public state -----
    @Volatile
    var scrollOffset = 0f
        private set

    // New snap state
    @Volatile
    private var snapTarget: Float? = null

    // Layout knobs
    private val spacing = 1.2f
    private val maxRotation = 55f
    private val sideScale = 0.75f
    private val zSpread = 0.35f
    private var depthParallaxEnabled = true

    // Reflection parameters
    private val reflectionGap = 0.04f          // vertical gap below main cover
    private val reflectionScale = 0.85f        // relative height of reflection
    private val reflectionStrength = 0.55f     // max brightness/alpha of reflection

    // New: base scale to enlarge items (center item ~1.0 * baseScale)
    private val baseScale = 1.4f

    // Texture management
    private val targetMaxDim = 512 // px max dimension for covers

    // Radii
    private var visibleRadius = 5        // items each side to actively draw
    private var prefetchRadius = 8       // items each side to ensure decoded (>= visibleRadius)
    private var keepRadius = 11          // items each side to retain before recycling (>= prefetchRadius)

    // GL program/attribs
    private var program = 0
    private var aPos = 0
    private var aUV = 0
    private var uMVP = 0
    private var uAlpha = 0
    private var uTex = 0
    private var uReflection = 0
    private var uReflectStrength = 0

    // Geometry
    private lateinit var quadVB: FloatBuffer
    private lateinit var quadTB: FloatBuffer
    private lateinit var quadIB: ShortBuffer

    // Data
    private val uris = mutableListOf<Uri>()

    // Matrices
    private val proj = FloatArray(16)
    private val view = FloatArray(16)
    private val model = FloatArray(16)
    private val mvp = FloatArray(16)

    // Background decode
    private val decodeExecutor = Executors.newFixedThreadPool(2)

    // Removed unused requestQueue (was never used) to avoid confusion
    // private val requestQueue = LinkedBlockingQueue<Int>() // indices to load
    private val inFlight = ConcurrentHashMap<Int, Boolean>()

    // Texture cache by index
    private val textures = ConcurrentHashMap<Int, Int>() // index -> GL texId
    private var glGeneration = 0

    // ----- Public API -----
    fun setUris(list: List<Uri>) {
        uris.clear()
        uris.addAll(list)
        queueGL { deleteAllTextures() }
        notifyScrollChanged(force = true)
        requestPrefetch(scrollOffset)
    }

    fun configureRadii(visible: Int? = null, prefetch: Int? = null, keep: Int? = null) {
        visible?.let { visibleRadius = max(1, it) }
        prefetch?.let { prefetchRadius = max(visibleRadius, it) }
        keep?.let { keepRadius = max(prefetchRadius, it) }
    }

    fun centeredIndex(): Int = scrollOffset.roundToInt().coerceIn(0, max(0, uris.size - 1))

    // Request a snap (does not jump immediately; animated in onDrawFrame)
    fun snapToNearest() {
        if (uris.isEmpty()) return
        val tgt = scrollOffset.roundToInt().coerceIn(0, max(0, uris.size - 1)).toFloat()
        snapTarget = tgt
        snappingNotified = false
    }

    fun scrollBy(dxItems: Float) {
        if (uris.isEmpty()) return
        // Cancel any ongoing snap because user is interacting
        snapTarget = null
        scrollOffset += dxItems
        scrollOffset = scrollOffset.coerceIn(0f, (uris.size - 1).toFloat())
        val center = centeredIndex().toFloat()
        requestPrefetch(center)
        queueGL { recycleFarTexturesFloat(scrollOffset) }
    }

    private var placeholderTex = 0

    fun release() {
        decodeExecutor.shutdownNow()
        queueGL { deleteAllTextures() }
    }

    // ----- GLSurfaceView.Renderer -----
    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        setupBuffers()
        buildProgram()
        Matrix.setLookAtM(view, 0, 0f, 0f, 2.1f, 0f, 0f, 0f, 0f, 1f, 0f)
        // GL context likely recreated: purge stale texture IDs so they reload
        glGeneration++
        textures.clear()
        inFlight.clear()
        placeholderTex = 0
        ensurePlaceholderTexture()
        // Re-request current window so covers appear immediately
        requestPrefetch(scrollOffset)
        notifyScrollChanged(force = true)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        val aspect = width.toFloat() / height
        Matrix.frustumM(proj, 0, -aspect, aspect, -1f, 1f, 2f, 10f)
    }

    private var lastFrameNanos = 0L
    private val snapLambda = 10f // higher -> faster snap (per second rate)

    private val mainHandler = Handler(Looper.getMainLooper())
    private val scrollListeners = CopyOnWriteArrayList<ScrollListener>()
    private var lastNotifiedOffset = Float.NaN
    private var lastNotifiedCenteredIndex = -1
    private var snappingNotified = false

    // ----- Drawing -----
    private fun drawItem(tex: Int, offsetFromCenter: Float) {
        val x = offsetFromCenter * spacing
        val absOff = abs(offsetFromCenter)
        val rotEase = smoothstep(0f, 0.18f, absOff)
        val rotY = (-offsetFromCenter * maxRotation * rotEase).coerceIn(-maxRotation, maxRotation)
        val depthFactor = if (depthParallaxEnabled) -absOff * zSpread * rotEase else 0f
        val z = depthFactor
        val sideFactor = (1f - (1f - sideScale) * min(1f, absOff))
        val scale = baseScale * sideFactor
        val brightness = 1f

        // Main cover
        Matrix.setIdentityM(model, 0)
        Matrix.translateM(model, 0, x, 0f, z)
        if (rotEase > 0f) Matrix.rotateM(model, 0, rotY, 0f, 1f, 0f)
        Matrix.scaleM(model, 0, scale, scale, 1f)
        Matrix.multiplyMM(mvp, 0, view, 0, model, 0)
        Matrix.multiplyMM(mvp, 0, proj, 0, mvp, 0)
        GLES20.glUseProgram(program)
        GLES20.glUniformMatrix4fv(uMVP, 1, false, mvp, 0)
        GLES20.glUniform1f(uAlpha, brightness)
        GLES20.glUniform1f(uReflection, 0f)
        GLES20.glUniform1f(uReflectStrength, reflectionStrength)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex)
        GLES20.glUniform1i(uTex, 0)
        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glEnableVertexAttribArray(aUV)
        GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, 0, quadVB)
        GLES20.glVertexAttribPointer(aUV, 2, GLES20.GL_FLOAT, false, 0, quadTB)
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, 6, GLES20.GL_UNSIGNED_SHORT, quadIB)

        // Reflection pass (draw after main)
        Matrix.setIdentityM(model, 0)
        // move down by cover height * scale + gap * scale
        val down = scale + reflectionGap
        Matrix.translateM(model, 0, x, -down, z)
        if (rotEase > 0f) Matrix.rotateM(model, 0, rotY, 0f, 1f, 0f)
        // mirror by negative Y scale and shrink
        Matrix.scaleM(model, 0, scale, -scale * reflectionScale, 1f)
        Matrix.multiplyMM(mvp, 0, view, 0, model, 0)
        Matrix.multiplyMM(mvp, 0, proj, 0, mvp, 0)
        GLES20.glUniformMatrix4fv(uMVP, 1, false, mvp, 0)
        GLES20.glUniform1f(uAlpha, brightness)
        GLES20.glUniform1f(uReflection, 1f)
        GLES20.glUniform1f(uReflectStrength, reflectionStrength)
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, 6, GLES20.GL_UNSIGNED_SHORT, quadIB)

        GLES20.glDisableVertexAttribArray(aPos)
        GLES20.glDisableVertexAttribArray(aUV)
    }

    private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
        if (edge0 == edge1) return 1f
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private fun setupBuffers() {
        val verts = floatArrayOf(
                -0.5f, 0.5f, 0f,
                -0.5f, -0.5f, 0f,
                0.5f, -0.5f, 0f,
                0.5f, 0.5f, 0f,
        )
        val uvs = floatArrayOf(
                0f, 0f,
                0f, 1f,
                1f, 1f,
                1f, 0f,
        )
        val inds = shortArrayOf(0, 1, 2, 0, 2, 3)
        quadVB = ByteBuffer.allocateDirect(verts.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(verts); position(0) }
        quadTB = ByteBuffer.allocateDirect(uvs.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(uvs); position(0) }
        quadIB = ByteBuffer.allocateDirect(inds.size * 2).order(ByteOrder.nativeOrder()).asShortBuffer().apply { put(inds); position(0) }
    }

    private fun buildProgram() {
        val vs = """
            attribute vec3 aPos; 
            attribute vec2 aUV; 
            uniform mat4 uMVP; 
            varying vec2 vUV; 
            void main(){
                vUV = aUV; 
                gl_Position = uMVP * vec4(aPos, 1.0);
            }
        """
        val fs = """
            precision mediump float; 
            varying vec2 vUV; 
            uniform sampler2D uTex; 
            uniform float uAlpha; 
            uniform float uReflection; // 0 = main, 1 = reflection
            uniform float uReflectStrength; 
            void main(){
                vec4 c = texture2D(uTex, vUV);
                // optional vignette (retain subtle shading)
                vec2 uv = vUV - 0.5; 
                float vignette = 1.0 - dot(uv, uv)*0.65; 
                c.rgb *= clamp(vignette, 0.5, 1.0);
                if (uReflection > 0.5) {
                    // reflection fade: seam (top after mirror) has vUV.y ~1
                    float fade = vUV.y; // 1 at seam, 0 at bottom
                    c.rgb *= fade * uReflectStrength * uAlpha;
                    c.a = fade * uReflectStrength;
                } else {
                    c.rgb *= uAlpha;
                    c.a = 1.0;
                }
                gl_FragColor = c;
            }
        """
        val vsId = compileShader(GLES20.GL_VERTEX_SHADER, vs)
        val fsId = compileShader(GLES20.GL_FRAGMENT_SHADER, fs)
        program = linkProgram(vsId, fsId)
        aPos = GLES20.glGetAttribLocation(program, "aPos")
        aUV = GLES20.glGetAttribLocation(program, "aUV")
        uMVP = GLES20.glGetUniformLocation(program, "uMVP")
        uAlpha = GLES20.glGetUniformLocation(program, "uAlpha")
        uTex = GLES20.glGetUniformLocation(program, "uTex")
        uReflection = GLES20.glGetUniformLocation(program, "uReflection")
        uReflectStrength = GLES20.glGetUniformLocation(program, "uReflectStrength")
    }

    private fun compileShader(type: Int, src: String): Int {
        val id = GLES20.glCreateShader(type)
        GLES20.glShaderSource(id, src)
        GLES20.glCompileShader(id)
        val status = IntArray(1)
        GLES20.glGetShaderiv(id, GLES20.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES20.glGetShaderInfoLog(id)
            GLES20.glDeleteShader(id)
            throw RuntimeException("Shader compile error: $log")
        }
        return id
    }

    private fun linkProgram(vs: Int, fs: Int): Int {
        val id = GLES20.glCreateProgram()
        GLES20.glAttachShader(id, vs)
        GLES20.glAttachShader(id, fs)
        GLES20.glLinkProgram(id)
        val status = IntArray(1)
        GLES20.glGetProgramiv(id, GLES20.GL_LINK_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES20.glGetProgramInfoLog(id)
            GLES20.glDeleteProgram(id)
            throw RuntimeException("Program link error: $log")
        }
        return id
    }

    // ----- Prefetch & Recycling -----
    private fun requestPrefetch(centerF: Float) {
        schedulePrefetch(centerF)
    }

    private fun enqueueLoad(index: Int) {
        if (index !in uris.indices) return
        if (textures.containsKey(index)) return
        if (inFlight.putIfAbsent(index, true) == null) {
            decodeExecutor.execute {
                try {
                    val bmp = decodeScaled(uris[index], targetMaxDim)
                    if (bmp != null) {
                        queueGL {
                            val texId = createTextureFromBitmap(bmp)
                            textures[index] = texId
                            bmp.recycle()
                        }
                    }
                } catch (t: Throwable) {
                    Log.w("CoverFlow", "Decode failed for index=$index: ${t.message}")
                } finally {
                    inFlight.remove(index)
                }
            }
        }
    }

    private fun recycleFarTexturesFloat(centerF: Float) {
        val cutoff = keepRadius + 0.75f // small buffer to prevent rapid thrash
        val it = textures.entries.iterator()
        while (it.hasNext()) {
            val (idx, texId) = it.next()
            if (abs(idx - centerF) > cutoff) {
                deleteTexture(texId)
                it.remove()
            }
        }
    }

    // keep legacy call
    private fun recycleFarTextures(center: Int) = recycleFarTexturesFloat(center.toFloat())

    // Placeholder texture (2x2 neutral gray gradient) to avoid gaps
    private fun ensurePlaceholderTexture() {
        if (placeholderTex != 0) return
        val pixels = intArrayOf(
                0xFF3A3A3A.toInt(), 0xFF444444.toInt(),
                0xFF444444.toInt(), 0xFF3A3A3A.toInt()
        )
        val bb = ByteBuffer.allocateDirect(pixels.size * 4).order(ByteOrder.nativeOrder())
        for (p in pixels) bb.putInt(p)
        bb.position(0)
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        placeholderTex = ids[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, placeholderTex)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, 2, 2, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, bb)
    }

    private fun deleteAllTextures() {
        val ids = textures.values.toIntArray()
        if (ids.isNotEmpty()) GLES20.glDeleteTextures(ids.size, ids, 0)
        textures.clear()
        if (placeholderTex != 0) {
            GLES20.glDeleteTextures(1, intArrayOf(placeholderTex), 0)
            placeholderTex = 0
        }
    }

    // ----- Bitmap decode helpers -----
    @SuppressLint("NewApi")
    private fun decodeScaled(uri: Uri, maxDim: Int): Bitmap? {
        return context.contentResolver.loadThumbnail(uri, Size(maxDim, maxDim), null)
    }

    // ----- GL texture helpers (run on GL thread) -----
    private fun createTextureFromBitmap(bmp: Bitmap): Int {
        val texIdArr = IntArray(1)
        GLES20.glGenTextures(1, texIdArr, 0)
        val texId = texIdArr[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0)
        return texId
    }

    private fun deleteTexture(texId: Int) {
        val arr = intArrayOf(texId)
        GLES20.glDeleteTextures(1, arr, 0)
    }

    private inline fun queueGL(crossinline block: () -> Unit) {
        glView.queueEvent { block() }
        glView.requestRender()
    }

    fun setDepthParallaxEnabled(enabled: Boolean) {
        depthParallaxEnabled = enabled
    }

    interface ScrollListener {
        fun onScrollOffsetChanged(offset: Float) {}
        fun onCenteredIndexChanged(index: Int) {}
        fun onSnapStarted(targetIndex: Int) {}
        fun onSnapFinished(finalIndex: Int) {}
    }

    fun addScrollListener(listener: ScrollListener) {
        scrollListeners.add(listener)
        // send initial
        mainHandler.post {
            listener.onScrollOffsetChanged(scrollOffset)
            listener.onCenteredIndexChanged(centeredIndex())
        }
    }

    fun removeScrollListener(listener: ScrollListener) {
        scrollListeners.remove(listener)
    }

    fun clearScrollListeners() {
        scrollListeners.clear()
    }

    // Programmatic setters
    fun setScrollOffset(offset: Float, smooth: Boolean = false) {
        if (uris.isEmpty()) return
        val clamped = offset.coerceIn(0f, (uris.size - 1).toFloat())
        if (smooth) {
            snapTarget = clamped
            snappingNotified = false
            notifyScrollChanged(force = true)
        } else {
            snapTarget = null
            if (scrollOffset != clamped) {
                scrollOffset = clamped
                notifyScrollChanged(force = true)
            }
        }
        requestPrefetch(clamped)
    }

    fun scrollToIndex(index: Int, smooth: Boolean = true) = setScrollOffset(index.toFloat(), smooth)

    private fun notifyScrollChanged(force: Boolean = false) {
        val off = scrollOffset
        if (force || off.isNaN().not() && (lastNotifiedOffset.isNaN() || kotlin.math.abs(off - lastNotifiedOffset) > 0.0005f)) {
            lastNotifiedOffset = off
            mainHandler.post {
                for (l in scrollListeners) l.onScrollOffsetChanged(off)
            }
        }
        val centered = centeredIndex()
        if (force || centered != lastNotifiedCenteredIndex) {
            lastNotifiedCenteredIndex = centered
            mainHandler.post {
                for (l in scrollListeners) l.onCenteredIndexChanged(centered)
            }
        }
    }

    private fun notifySnapLifecycle(started: Boolean, finished: Boolean) {
        if (started && !snappingNotified) {
            val targetIdx = snapTarget?.roundToInt() ?: return
            snappingNotified = true
            mainHandler.post { scrollListeners.forEach { it.onSnapStarted(targetIdx) } }
        }
        if (finished) {
            val idx = centeredIndex()
            mainHandler.post { scrollListeners.forEach { it.onSnapFinished(idx) } }
        }
    }

    // ----- GLSurfaceView.Renderer -----
    override fun onDrawFrame(unused: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        if (uris.isEmpty()) return

        val prevOffset = scrollOffset
        val hadSnapTarget = snapTarget != null
        // Time step for frame-rate independent easing
        val now = System.nanoTime()
        if (lastFrameNanos == 0L) lastFrameNanos = now
        val dt = ((now - lastFrameNanos).coerceAtMost(100_000_000L)) / 1_000_000_000f
        lastFrameNanos = now
        snapTarget?.let { target ->
            val delta = target - scrollOffset
            val ad = abs(delta)
            if (ad < 0.00008f) {
                scrollOffset = target
                snapTarget = null
                notifySnapLifecycle(started = false, finished = true)
            } else {
                val factor = 1f - exp(-snapLambda * dt)
                scrollOffset += delta * factor
                notifySnapLifecycle(started = hadSnapTarget, finished = false)
            }
        }

        if (scrollOffset != prevOffset) notifyScrollChanged()

        val centerF = scrollOffset

        // Prefetch scheduling (prioritize closest missing within prefetchRadius)
        schedulePrefetch(centerF)
        val lastIndex = uris.lastIndex
        val visStart = max(0, floor(centerF - visibleRadius).toInt())
        val visEnd = min(lastIndex, ceil(centerF + visibleRadius).toInt())
        for (i in visStart..visEnd) {
            val centerIdx = centerF.roundToInt()
            if (abs(i - centerIdx) <= prefetchRadius && !textures.containsKey(i) && !inFlight.containsKey(i)) enqueueLoad(i)
            val tex = textures[i] ?: placeholderTex
            val offset = i - centerF
            drawItem(tex, offset)
        }
        queueGL { recycleFarTexturesFloat(centerF) }
    }

    private fun schedulePrefetch(centerF: Float) {
        if (uris.isEmpty()) return
        val lastIndex = uris.lastIndex
        val preStart = max(0, floor(centerF - prefetchRadius).toInt())
        val preEnd = min(lastIndex, ceil(centerF + prefetchRadius).toInt())
        if (preEnd < preStart) return
        val toLoad = mutableListOf<Int>()
        for (i in preStart..preEnd) if (!textures.containsKey(i) && !inFlight.containsKey(i)) toLoad.add(i)
        toLoad.sortBy { abs(it - centerF) }
        toLoad.forEach { enqueueLoad(it) }
    }

    // Force reload API (optional external call)
    fun forceReloadAll() {
        queueGL {
            textures.clear()
            inFlight.clear()
            placeholderTex = 0
            ensurePlaceholderTexture()
            requestPrefetch(scrollOffset)
            notifyScrollChanged(force = true)
        }
    }
}