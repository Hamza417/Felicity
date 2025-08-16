package app.simple.felicity.decorations.coverflow

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.opengl.Matrix
import android.util.Log
import android.util.Size
import com.google.android.material.math.MathUtils.lerp
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

class CoverFlowRenderer(
        private val glView: GLSurfaceView,
        private val context: Context
) : GLSurfaceView.Renderer {

    // ----- Public state -----
    @Volatile
    var scrollOffset = 0f
        private set

    // Layout knobs
    private val spacing = 1.2f
    private val maxRotation = 55f
    private val sideScale = 0.75f
    private val zSpread = 0.35f
    private val fadeSide = 0.35f

    // New: base scale to enlarge items (center item ~1.0 * baseScale)
    private val baseScale = 1.4f

    // Texture management
    private val targetMaxDim = 512 // px max dimension for covers
    private var prefetchRadius = 2  // decode/upload around current index
    private var keepRadius = 3      // keep textures around current index; recycle others

    // GL program/attribs
    private var program = 0
    private var aPos = 0
    private var aUV = 0
    private var uMVP = 0
    private var uAlpha = 0
    private var uTex = 0

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

    // ----- Public API -----
    fun setUris(list: List<Uri>) {
        uris.clear()
        uris.addAll(list)
        // Clear textures; they will be requested lazily
        queueGL {
            deleteAllTextures()
        }
        requestPrefetch(centeredIndex())
    }

    fun setPrefetchRadius(radius: Int) {
        prefetchRadius = max(0, radius)
    }

    fun setKeepRadius(radius: Int) {
        keepRadius = max(1, radius)
    }

    fun centeredIndex(): Int = scrollOffset.roundToInt().coerceIn(0, max(0, uris.size - 1))

    fun snapToNearest() {
        val target = scrollOffset.roundToInt().toFloat()
        scrollOffset = lerp(scrollOffset, target, 0.25f)
        requestPrefetch(centeredIndex())
        queueGL { recycleFarTextures(centeredIndex()) }
    }

    fun scrollBy(dxItems: Float) {
        if (uris.isEmpty()) return
        scrollOffset += dxItems * 2.2f
        scrollOffset = scrollOffset.coerceIn(0f, (uris.size - 1).toFloat())
        val center = centeredIndex()
        requestPrefetch(center)
        queueGL { recycleFarTextures(center) }
    }

    fun release() {
        decodeExecutor.shutdownNow()
        queueGL { deleteAllTextures() }
    }

    // ----- GLSurfaceView.Renderer -----
    override fun onSurfaceCreated(unused: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        setupBuffers()
        buildProgram()
        // Fix: correct lookAt parameters - camera at (0,0,3.1), looking at origin, up is Y
        Matrix.setLookAtM(view, 0, 0f, 0f, 3.1f, 0f, 0f, 0f, 0f, 1f, 0f)
    }

    override fun onSurfaceChanged(unused: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        val aspect = width.toFloat() / height
        Matrix.frustumM(proj, 0, -aspect, aspect, -1f, 1f, 2f, 10f)
    }

    override fun onDrawFrame(unused: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        if (uris.isEmpty()) return

        // Small auto-settle
        if (abs(scrollOffset - scrollOffset.roundToInt()) < 0.01f) {
            snapToNearest()
        }

        val centerIdx = centeredIndex()
        val centerFloat = scrollOffset

        // Define a drawing window. We draw a little beyond keepRadius so that fade feels smooth
        val drawRadius = max(keepRadius, prefetchRadius + 1)
        val start = max(0, centerIdx - drawRadius)
        val end = min(uris.size - 1, centerIdx + drawRadius)

        // Draw only the window range to avoid triggering loads for every item each frame
        for (i in start..end) {
            val offset = i - centerFloat
            val tex = textures[i] ?: run {
                // Queue load only if within prefetch radius
                if (abs(i - centerIdx) <= prefetchRadius) enqueueLoad(i)
                continue
            }
            drawItem(tex, offset)
        }

        // Recycle outside keep radius after drawing (in case center changed mid-frame)
        queueGL { recycleFarTextures(centeredIndex()) }
    }

    // ----- Drawing -----
    private fun drawItem(tex: Int, offsetFromCenter: Float) {
        val x = offsetFromCenter * spacing
        val rotY = max(-maxRotation, min(maxRotation, -offsetFromCenter * maxRotation))
        val z = -abs(offsetFromCenter) * zSpread
        val sideFactor = (1f - (1f - sideScale) * min(1f, abs(offsetFromCenter)))
        val scale = baseScale * sideFactor
        val alpha = 1f - (1f - fadeSide) * min(1f, abs(offsetFromCenter).pow(0.9f))

        Matrix.setIdentityM(model, 0)
        Matrix.translateM(model, 0, x, 0f, z)
        Matrix.rotateM(model, 0, rotY, 0f, 1f, 0f)
        Matrix.scaleM(model, 0, scale, scale, 1f)

        Matrix.multiplyMM(mvp, 0, view, 0, model, 0)
        Matrix.multiplyMM(mvp, 0, proj, 0, mvp, 0)

        GLES20.glUseProgram(program)
        GLES20.glUniformMatrix4fv(uMVP, 1, false, mvp, 0)
        GLES20.glUniform1f(uAlpha, alpha)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex)
        GLES20.glUniform1i(uTex, 0)

        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glEnableVertexAttribArray(aUV)
        GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, 0, quadVB)
        GLES20.glVertexAttribPointer(aUV, 2, GLES20.GL_FLOAT, false, 0, quadTB)
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, 6, GLES20.GL_UNSIGNED_SHORT, quadIB)
        GLES20.glDisableVertexAttribArray(aPos)
        GLES20.glDisableVertexAttribArray(aUV)
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
            void main(){
                vec4 c = texture2D(uTex, vUV);
                vec2 uv = vUV - 0.5; 
                float vignette = 1.0 - dot(uv, uv)*0.65; 
                c.rgb *= clamp(vignette, 0.5, 1.0);
                gl_FragColor = vec4(c.rgb, c.a * uAlpha);
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
    private fun requestPrefetch(center: Int) {
        val start = max(0, center - prefetchRadius)
        val end = min(uris.size - 1, center + prefetchRadius)
        for (i in start..end) enqueueLoad(i)
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

    private fun recycleFarTextures(center: Int) {
        val it = textures.entries.iterator()
        while (it.hasNext()) {
            val (idx, texId) = it.next()
            if (abs(idx - center) > keepRadius) {
                deleteTexture(texId)
                it.remove()
            }
        }
    }

    private fun deleteAllTextures() {
        val ids = textures.values.toIntArray()
        if (ids.isNotEmpty()) GLES20.glDeleteTextures(ids.size, ids, 0)
        textures.clear()
    }

    // ----- Bitmap decode helpers -----
    @SuppressLint("NewApi")
    private fun decodeScaled(uri: Uri, maxDim: Int): Bitmap? {
        return context.contentResolver.loadThumbnail(uri, Size(maxDim, maxDim), null)
    }

    private fun computeInSampleSize(w: Int, h: Int, maxDim: Int): Int {
        var size = 1
        val largest = max(w, h)
        while (largest / size > maxDim) size = size shl 1
        return size
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
}