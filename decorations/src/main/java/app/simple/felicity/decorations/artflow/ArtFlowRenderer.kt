package app.simple.felicity.decorations.artflow

import android.content.Context
import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.opengl.Matrix
import android.os.Handler
import android.os.Looper
import android.util.Log
import app.simple.felicity.preferences.CarouselPreferences
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

class ArtFlowRenderer(
        private val glView: GLSurfaceView,
        private val context: Context
) : GLSurfaceView.Renderer {

    // Public state
    @Volatile
    var scrollOffset = 0f
        private set

    // Global fade alpha (0..1) applied to every drawn cover
    @Volatile
    private var globalAlpha = 1f

    fun setGlobalAlpha(alpha: Float) {
        val a = alpha.coerceIn(0f, 1f)
        if (a != globalAlpha) {
            globalAlpha = a
            glView.requestRender()
        }
    }

    fun setZSpread(spread: Float) {
        // limit spread to prevent excessive parallax
        zSpread = spread.coerceIn(0f, 1f)
    }

    @Suppress("PrivatePropertyName")
    private val SCROLL_SENSITIVITY = 2.5f // faster scrolling

    // Orientation
    @Volatile
    private var verticalOrientation = false

    // Camera distances
    private val cameraZLandscape = 2.1f
    private val cameraZPortrait = 2.1f

    // Scaling variants
    private var baseScaleLandscape = 1.4f
    private var baseScalePortrait = 0.8f // reduced further so portrait items look smaller
    private var currentBaseScale = baseScaleLandscape

    // Reflection enable flag (disabled in portrait)
    @Volatile
    private var reflectionEnabled = true

    fun updateCamera() {
        val z = if (verticalOrientation) cameraZPortrait else cameraZLandscape
        val y = if (verticalOrientation) 0f else CarouselPreferences.getEyeY()

        // eye at (0,0,z) looking at origin
        Matrix.setLookAtM(/* rm = */ view,
                          /* rmOffset = */ 0,
                          /* eyeX = */ 0f,
                          /* eyeY = */ y,
                          /* eyeZ = */ z,
                          /* centerX = */ 0f,
                          /* centerY = */ 0f,
                          /* centerZ = */ 0f,
                          /* upX = */ 0f,
                          /* upY = */ 1F,
                          /* upZ = */ 0f)
    }

    fun setVerticalOrientation(vertical: Boolean) {
        if (verticalOrientation != vertical) {
            verticalOrientation = vertical
            if (verticalOrientation) {
                currentBaseScale = baseScalePortrait
                currentSpacing = spacingPortrait
                reflectionEnabled = false
            } else {
                currentBaseScale = baseScaleLandscape
                currentSpacing = spacingLandscape
                reflectionEnabled = true
            }
            updateCamera()
        }
    }

    // New snap state
    @Volatile
    private var snapTarget: Float? = null

    // Overscroll effect
    // Gives a satisfying bounce when you scroll past the edges.
    // When the user drags beyond the first or last item, we let the content
    // follow their finger but with increasing resistance.
    // When they let go, it snaps back smoothly to the valid bounds.

    private val maxOverscroll = 1.5f        // how far past the edge you can drag (in items)
    private val overscrollResistance = 3f   // makes it harder to drag the further you go
    private val overscrollBounceLambda = 12f // controls how fast it snaps back (higher = snappier)

    @Volatile
    private var overscrollAmount = 0f       // tracks how far past the edge we are
    // negative = scrolled before first item
    // positive = scrolled past last item

    @Volatile
    private var isBouncing = false          // true when we're animating the snap back

    // Layout knobs
    private val spacingLandscape = 1.2f
    private val spacingPortrait = 0.65f // tighter spacing in portrait
    private var currentSpacing = spacingLandscape
    private val maxRotation = 55f
    private var sideScale = 0.75f
    private var zSpread = 0.35f
    private var depthParallaxEnabled = true

    // Reflection parameters
    private var reflectionGap = 0.05f          // vertical gap below main cover (in scaled quad units)
    private val reflectionScale = 0.65f        // relative height of reflection
    private val reflectionStrength = 0.55f     // max brightness/alpha of reflection

    // Click scale animation
    @Volatile
    private var clickScaleActive = false
    private var clickScaleProgress = 0f  // 0 to 1
    private val clickScaleDuration = 200f // milliseconds
    private val clickScaleAmount = 0.92f  // scale down to 92% of original size

    // Texture management
    private val targetMaxDim = 512 // px max dimension for covers

    // Radii - reduced to save memory
    private var visibleRadius = 5        // items each side to actively draw
    private var prefetchRadius = 7       // items each side to ensure decoded (>= visibleRadius)
    private var keepRadius = 8           // items each side to retain before recycling (>= prefetchRadius)

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


    // Picking structures
    private data class CoverPick(val index: Int, val minX: Float, val maxX: Float, val minY: Float, val maxY: Float)

    private val framePicks = ArrayList<CoverPick>()
    private var viewWidth = 0
    private var viewHeight = 0

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

    // Data provider
    @Volatile
    private var dataProvider: ArtFlowDataProvider? = null

    // Public API
    fun setDataProvider(provider: ArtFlowDataProvider) {
        dataProvider = provider
        queueGL { deleteAllTextures() }
        notifyScrollChanged(force = true)
        requestPrefetch(scrollOffset)
    }


    fun configureRadii(visible: Int? = null, prefetch: Int? = null, keep: Int? = null) {
        visible?.let { visibleRadius = max(1, it) }
        prefetch?.let { prefetchRadius = max(visibleRadius, it) }
        keep?.let { keepRadius = max(prefetchRadius, it) }
    }

    // Helper methods to get item count from provider
    private fun getItemCount(): Int {
        return dataProvider?.getItemCount() ?: 0
    }

    private fun isValidIndex(index: Int): Boolean {
        val count = getItemCount()
        return index in 0 until count
    }

    fun centeredIndex(): Int = scrollOffset.roundToInt().coerceIn(0, max(0, getItemCount() - 1))

    // Request a snap (does not jump immediately; animated in onDrawFrame)
    fun snapToNearest() {
        if (getItemCount() == 0) return
        val tgt = scrollOffset.roundToInt().coerceIn(0, max(0, getItemCount() - 1)).toFloat()
        snapTarget = tgt
        snappingNotified = false
    }

    fun scrollBy(dxItems: Float) {
        if (getItemCount() == 0) return
        snapTarget = null
        isBouncing = false  // user is actively dragging, so stop any bounce animation

        val delta = dxItems * SCROLL_SENSITIVITY
        val minBound = 0f
        val maxBound = (getItemCount() - 1).toFloat()

        // If we're already in overscroll territory, handle it specially
        // This is the key to making the overscroll feel natural
        if (overscrollAmount != 0f) {
            val newOverscroll = overscrollAmount + delta

            // Check if user is dragging back toward the valid content
            val pullingBack = (overscrollAmount < 0f && delta > 0f) || (overscrollAmount > 0f && delta < 0f)

            if (pullingBack) {
                // User is pulling back toward the content - let them!
                // Check if they've pulled all the way back into bounds
                if ((overscrollAmount < 0f && newOverscroll >= 0f) || (overscrollAmount > 0f && newOverscroll <= 0f)) {
                    // They crossed back into valid territory
                    overscrollAmount = 0f
                    val remainingDelta = newOverscroll // leftover movement goes to normal scroll
                    scrollOffset = (scrollOffset + remainingDelta).coerceIn(minBound, maxBound)
                } else {
                    // Still in overscroll, but getting closer to bounds
                    overscrollAmount = newOverscroll.coerceIn(-maxOverscroll, maxOverscroll)
                }
            } else {
                // User is pushing even further past the edge
                // Apply resistance - the more they push, the harder it gets
                val resistance = 1f + abs(overscrollAmount) * overscrollResistance
                val resistedDelta = delta / resistance
                overscrollAmount = (overscrollAmount + resistedDelta).coerceIn(-maxOverscroll, maxOverscroll)
            }
        } else {
            // Normal scrolling - no overscroll active
            val newScrollOffset = scrollOffset + delta

            when {
                // Trying to scroll before the first item
                newScrollOffset < minBound -> {
                    scrollOffset = minBound
                    // Start the rubber band effect
                    val overDelta = newScrollOffset - minBound
                    overscrollAmount = overDelta.coerceIn(-maxOverscroll, 0f)
                }
                // Trying to scroll past the last item
                newScrollOffset > maxBound -> {
                    scrollOffset = maxBound
                    // Start the rubber band effect
                    val overDelta = newScrollOffset - maxBound
                    overscrollAmount = overDelta.coerceIn(0f, maxOverscroll)
                }
                // Normal case - within bounds
                else -> {
                    scrollOffset = newScrollOffset
                }
            }
        }

        val center = centeredIndex().toFloat()
        requestPrefetch(center)
        queueGL { recycleFarTexturesFloat(scrollOffset) }
    }

    /**
     * Call this when the user lifts their finger to trigger the snap-back animation.
     * The overscroll will spring back to the edge smoothly.
     */
    fun endScroll() {
        if (overscrollAmount != 0f) {
            isBouncing = true
        }
    }

    /**
     * Check if we're currently stretched past the edges.
     * Used by the view to detect when a fling should stop early.
     */
    fun isOverscrolling(): Boolean = overscrollAmount != 0f

    private var placeholderTex = 0

    fun release() {
        decodeExecutor.shutdownNow()
        queueGL { deleteAllTextures() }
    }

    // GLSurfaceView.Renderer
    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0f, 0f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        setupBuffers()
        buildProgram()
        updateCamera()
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
        viewWidth = width
        viewHeight = height
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

    // Vertical drag (carousel pitch & offset)
    private val maxDragYOffset = 0.35f
    private val maxDragPitchDeg = 10f

    @Volatile
    private var targetDragYOffset = 0f

    @Volatile
    private var targetDragPitch = 0f

    @Volatile
    private var currentDragYOffset = 0f

    @Volatile
    private var currentDragPitch = 0f

    @Volatile
    private var userDraggingVertically = false
    private val dragEasingLambda = 14f

    fun setDragVertical(normalized: Float) {
        val n = normalized.coerceIn(-1f, 1f)
        userDraggingVertically = true
        targetDragYOffset = n * maxDragYOffset
        targetDragPitch = n * maxDragPitchDeg
        currentDragYOffset = targetDragYOffset
        currentDragPitch = targetDragPitch
    }

    fun endVerticalDrag() {
        userDraggingVertically = false
        targetDragYOffset = 0f
        targetDragPitch = 0f
    }

    // Allow configuring reflection gap at runtime
    fun setReflectionGap(gap: Float) {
        val g = gap.coerceAtLeast(0f)
        if (g != reflectionGap) {
            reflectionGap = g
            glView.requestRender()
        }
    }

    fun setDepthParallaxEnabled(enabled: Boolean) {
        depthParallaxEnabled = enabled
    }

    /**
     * Triggers a click scale animation on the center cover.
     * The cover will briefly shrink and then return to normal size.
     */
    fun triggerClickScale() {
        clickScaleProgress = 0f
        clickScaleActive = true
        glView.requestRender()
    }

    // Drawing
    private fun drawItem(index: Int, tex: Int, offsetFromCenter: Float) {
        val absOff = abs(offsetFromCenter)
        val rotEase = smoothstep(0f, 0.18f, absOff)
        val depthFactor = if (depthParallaxEnabled) -absOff * zSpread * rotEase else 0f
        val sideFactor = (1f - (1f - sideScale) * min(1f, absOff))
        var scale = currentBaseScale * sideFactor

        // Apply click scale animation to center item
        if (clickScaleActive && absOff < 0.5f) {
            // Use a sine curve for smooth press-and-release effect
            // Progress 0->0.5: scale down, 0.5->1: scale back up
            val animScale = if (clickScaleProgress < 0.5f) {
                // First half: scale down
                1f - (1f - clickScaleAmount) * (clickScaleProgress * 2f)
            } else {
                // Second half: scale back up
                clickScaleAmount + (1f - clickScaleAmount) * ((clickScaleProgress - 0.5f) * 2f)
            }
            scale *= animScale
        }

        val brightness = globalAlpha // apply global alpha as brightness multiplier

        Matrix.setIdentityM(model, 0)
        if (currentDragPitch != 0f) Matrix.rotateM(model, 0, currentDragPitch, 1f, 0f, 0f)

        if (verticalOrientation) {
            // In vertical mode we want higher indices to appear below the center item, so invert translation.
            val y = -offsetFromCenter * currentSpacing
            val rotX = (-offsetFromCenter * maxRotation * rotEase).coerceIn(-maxRotation, maxRotation)
            Matrix.translateM(model, 0, 0f, currentDragYOffset + y, depthFactor)
            if (rotEase > 0f) Matrix.rotateM(model, 0, rotX, 1f, 0f, 0f)
        } else {
            val x = offsetFromCenter * currentSpacing
            val rotY = (-offsetFromCenter * maxRotation * rotEase).coerceIn(-maxRotation, maxRotation)
            Matrix.translateM(model, 0, x, currentDragYOffset, depthFactor)
            if (rotEase > 0f) Matrix.rotateM(model, 0, rotY, 0f, 1f, 0f)
        }
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

        capturePickBoundsForIndex(index)

        if (reflectionEnabled) {
            Matrix.setIdentityM(model, 0)
            if (currentDragPitch != 0f) Matrix.rotateM(model, 0, currentDragPitch, 1f, 0f, 0f)
            // Correct center-to-center offset: half of main height + half of reflection height + optional gap
            val down = 0.5f * (scale + scale * reflectionScale) + reflectionGap
            if (verticalOrientation) {
                // Skip drawing reflection in portrait (reflectionEnabled should be false anyway)
            } else {
                val x = offsetFromCenter * currentSpacing
                val rotY = (-offsetFromCenter * maxRotation * rotEase).coerceIn(-maxRotation, maxRotation)
                Matrix.translateM(model, 0, x, currentDragYOffset - down, depthFactor)
                if (rotEase > 0f) Matrix.rotateM(model, 0, rotY, 0f, 1f, 0f)
                Matrix.scaleM(model, 0, scale, -scale * reflectionScale, 1f)
                Matrix.multiplyMM(mvp, 0, view, 0, model, 0)
                Matrix.multiplyMM(mvp, 0, proj, 0, mvp, 0)
                GLES20.glUniformMatrix4fv(uMVP, 1, false, mvp, 0)
                GLES20.glUniform1f(uAlpha, brightness)
                GLES20.glUniform1f(uReflection, 1f)
                GLES20.glUniform1f(uReflectStrength, reflectionStrength)
                GLES20.glDrawElements(GLES20.GL_TRIANGLES, 6, GLES20.GL_UNSIGNED_SHORT, quadIB)
            }
        }

        GLES20.glDisableVertexAttribArray(aPos)
        GLES20.glDisableVertexAttribArray(aUV)
    }

    @Suppress("SameParameterValue")
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
                vec2 uv = vUV - 0.5; 
                float vignette = 1.0 - dot(uv, uv)*0.65; 
                c.rgb *= clamp(vignette, 0.5, 1.0);
                if (uReflection > 0.5) {
                    float fade = vUV.y; 
                    float oa = fade * uReflectStrength * uAlpha; // overall alpha for reflection
                    c.rgb *= oa;
                    c.a = oa;
                } else {
                    c.rgb *= uAlpha;
                    c.a = uAlpha; // make main cover transparent based on global alpha
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

    // Prefetch & Recycling
    private fun requestPrefetch(centerF: Float) {
        schedulePrefetch(centerF)
    }

    private fun enqueueLoad(index: Int) {
        if (!isValidIndex(index)) return
        if (textures.containsKey(index)) return
        if (inFlight.putIfAbsent(index, true) == null) {
            decodeExecutor.execute {
                var bmpCopy: Bitmap? = null
                try {
                    val bmp = dataProvider?.loadArtwork(index, targetMaxDim)
                    @Suppress("SENSELESS_COMPARISON")
                    if (bmp != null && !bmp.isRecycled) {
                        // Create a mutable copy of the bitmap for OpenGL upload
                        // This allows the cache to manage the original independently
                        bmpCopy = bmp.copy(bmp.config ?: Bitmap.Config.ARGB_8888, false)

                        queueGL {
                            try {
                                // Only upload if the copy is still valid
                                if (bmpCopy != null && !bmpCopy.isRecycled) {
                                    val texId = createTextureFromBitmap(bmpCopy)
                                    textures[index] = texId
                                }
                            } finally {
                                // Recycle the copy immediately after GL upload
                                // The texture is now in GPU memory, we don't need the bitmap anymore
                                bmpCopy?.recycle()
                            }
                        }
                    }
                } catch (t: Throwable) {
                    Log.w("CoverFlow", "Decode failed for index=$index: ${t.message}")
                    bmpCopy?.recycle()
                } finally {
                    inFlight.remove(index)
                }
            }
        }
    }

    private fun recycleFarTexturesFloat(centerF: Float) {
        val cutoff = keepRadius + 0.25f // reduced buffer for more aggressive cleanup
        val it = textures.entries.iterator()
        while (it.hasNext()) {
            val (idx, texId) = it.next()
            if (abs(idx - centerF) > cutoff) {
                deleteTexture(texId)
                it.remove()
            }
        }
    }

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

    // GL texture helpers (run on GL thread)
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
        if (getItemCount() == 0) return
        val clamped = offset.coerceIn(0f, (getItemCount() - 1).toFloat())
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
        if (force || off.isNaN().not() && (lastNotifiedOffset.isNaN() || abs(off - lastNotifiedOffset) > 0.0005f)) {
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

    // GLSurfaceView.Renderer
    override fun onDrawFrame(unused: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        if (getItemCount() == 0) return
        framePicks.clear()
        val prevOffset = scrollOffset
        val hadSnapTarget = snapTarget != null
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
        if (!userDraggingVertically) {
            val dy = targetDragYOffset - currentDragYOffset
            val dp = targetDragPitch - currentDragPitch
            if (abs(dy) > 0.0001f) currentDragYOffset += dy * (1f - exp(-dragEasingLambda * dt)) else currentDragYOffset = targetDragYOffset
            if (abs(dp) > 0.0001f) currentDragPitch += dp * (1f - exp(-dragEasingLambda * dt)) else currentDragPitch = targetDragPitch
        }

        // Animate the overscroll snap-back
        // When the user lets go while overscrolled, this smoothly springs back to the edge
        // Uses exponential decay for that natural, physics-y feel
        if (isBouncing && overscrollAmount != 0f) {
            val bounceFactor = 1f - exp(-overscrollBounceLambda * dt)
            overscrollAmount -= overscrollAmount * bounceFactor
            if (abs(overscrollAmount) < 0.001f) {
                // Close enough to zero - snap to exactly zero and stop bouncing
                overscrollAmount = 0f
                isBouncing = false
            } else {
                // Keep the animation going until we're done
                glView.requestRender()
            }
        }

        // Animate click scale effect
        if (clickScaleActive) {
            val dtMs = dt * 1000f
            clickScaleProgress += dtMs / clickScaleDuration
            if (clickScaleProgress >= 1f) {
                clickScaleProgress = 1f
                clickScaleActive = false
            } else {
                // Keep animating
                glView.requestRender()
            }
        }

        if (scrollOffset != prevOffset) notifyScrollChanged()
        // The visual position includes the overscroll offset
        // This is what makes the content appear to stretch past the edges
        val centerF = scrollOffset + overscrollAmount
        schedulePrefetch(scrollOffset) // but prefetch based on actual position, not the visual offset
        val lastIndex = getItemCount() - 1
        val visStart = max(0, floor(centerF - visibleRadius).toInt())
        val visEnd = min(lastIndex, ceil(centerF + visibleRadius).toInt())
        for (i in visStart..visEnd) {
            val centerIdx = centerF.roundToInt()
            if (abs(i - centerIdx) <= prefetchRadius && !textures.containsKey(i) && !inFlight.containsKey(i)) enqueueLoad(i)
            val tex = textures[i] ?: placeholderTex
            val offset = i - centerF
            drawItem(i, tex, offset)
        }
        queueGL { recycleFarTexturesFloat(centerF) }
    }

    private fun schedulePrefetch(centerF: Float) {
        if (getItemCount() == 0) return
        val lastIndex = getItemCount() - 1
        val preStart = max(0, floor(centerF - prefetchRadius).toInt())
        val preEnd = min(lastIndex, ceil(centerF + prefetchRadius).toInt())
        if (preEnd < preStart) return
        val toLoad = mutableListOf<Int>()
        for (i in preStart..preEnd) {
            if (!textures.containsKey(i) && !inFlight.containsKey(i)) {
                toLoad.add(i)
            }
        }
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

    private fun capturePickBoundsForIndex(index: Int) {
        if (!isValidIndex(index)) return
        val corners = floatArrayOf(
                -0.5f, 0.5f, 0f, 1f,
                -0.5f, -0.5f, 0f, 1f,
                0.5f, 0.5f, 0f, 1f,
                0.5f, -0.5f, 0f, 1f
        )
        var minX = 10f
        var maxX = -10f
        var minY = 10f
        var maxY = -10f
        for (i in 0 until 4) {
            val bi = i * 4
            val x = corners[bi]
            val y = corners[bi + 1]
            val z = corners[bi + 2]
            val w = corners[bi + 3]
            val vx = mvp[0] * x + mvp[4] * y + mvp[8] * z + mvp[12] * w
            val vy = mvp[1] * x + mvp[5] * y + mvp[9] * z + mvp[13] * w
            val vw = mvp[3] * x + mvp[7] * y + mvp[11] * z + mvp[15] * w
            if (vw != 0f) {
                val ndcX = vx / vw
                val ndcY = vy / vw
                if (ndcX < minX) minX = ndcX
                if (ndcX > maxX) maxX = ndcX
                if (ndcY < minY) minY = ndcY
                if (ndcY > maxY) maxY = ndcY
            }
        }

        if (minX <= maxX && minY <= maxY) {
            synchronized(framePicks) {
                framePicks.add(CoverPick(index, minX, maxX, minY, maxY))
            }
        }
    }

    fun getItemIdAt(index: Int): Any? {
        return if (isValidIndex(index)) {
            dataProvider?.getItemId(index)
        } else {
            null
        }
    }

    fun pickIndexAtScreenX(x: Float): Int? {
        if (viewWidth == 0) return null
        val nx = (x / viewWidth.toFloat()) * 2f - 1f
        var best: CoverPick? = null
        synchronized(framePicks) {
            for (p in framePicks) {
                if (nx >= p.minX && nx <= p.maxX) {
                    if (best == null || (p.maxX - p.minX) < (best.maxX - best.minX)) best = p
                }
            }
        }
        return best?.index
    }

    fun pickIndexAtScreenY(y: Float): Int? {
        if (viewHeight == 0) return null
        val ny = 1f - (y / viewHeight.toFloat()) * 2f
        var best: CoverPick? = null
        synchronized(framePicks) {
            for (p in framePicks) {
                if (ny >= p.minY && ny <= p.maxY) {
                    if (best == null || (p.maxY - p.minY) < (best.maxY - best.minY)) best = p
                }
            }
        }
        return best?.index
    }

    fun setSideScale(scale: Float) {
        val s = scale.coerceIn(0.1f, 1f)
        if (s != sideScale) {
            sideScale = s
            glView.requestRender()
        }
    }
}
