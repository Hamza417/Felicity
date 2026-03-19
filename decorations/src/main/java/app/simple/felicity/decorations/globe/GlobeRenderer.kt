package app.simple.felicity.decorations.globe

import android.content.Context
import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.opengl.Matrix
import android.util.Log
import app.simple.felicity.decorations.artflow.ArtFlowDataProvider
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * OpenGL ES 2.0 renderer that places album-art quads on the surface of a sphere using a
 * UV-sphere grid (latitude rows with variable longitude columns). Each quad is sized to
 * exactly fill its angular cell, producing edge-to-edge tiling with no overlap in any
 * direction. Supports:
 *  - Free rotation via an accumulated rotation matrix (trackball-style)
 *  - Pinch-to-zoom via sphere object scaling (camera and frustum remain fixed)
 *  - Back-to-front depth sorting (painter's algorithm) for correct transparency
 *  - NDC-bounds picking to detect which album was tapped
 *
 * @author Hamza417
 */
class GlobeRenderer(
        private val glView: GLSurfaceView,
        @Suppress("unused") private val context: Context
) : GLSurfaceView.Renderer {

    /**
     * Represents a single album-art item on the UV sphere surface.
     *
     * @property x          Unit-sphere X coordinate in the local (pre-rotation) frame.
     * @property y          Unit-sphere Y coordinate in the local (pre-rotation) frame.
     * @property z          Unit-sphere Z coordinate in the local (pre-rotation) frame.
     * @property index      Data-provider index used for texture look-ups and pick-testing.
     * @property halfWidth  Half arc-width of the quad at [sphereRadius], east direction (unscaled).
     * @property halfHeight Half arc-height of the quad at [sphereRadius], north direction (unscaled).
     * @property ex         Local-frame east unit-vector X component: -sin(φ).
     * @property ey         Local-frame east unit-vector Y component: always 0.
     * @property ez         Local-frame east unit-vector Z component: cos(φ).
     */
    private data class GlobeItem(
            val x: Float,
            val y: Float,
            val z: Float,
            val index: Int,
            val halfWidth: Float,
            val halfHeight: Float,
            val ex: Float,
            val ey: Float,
            val ez: Float
    )

    /** NDC bounding rectangle captured during the last draw call for hit-testing. */
    private data class CoverPick(
            val index: Int,
            val minX: Float,
            val maxX: Float,
            val minY: Float,
            val maxY: Float
    )

    /** Radius of the sphere in world-space units. */
    private val sphereRadius = 3.2f

    /** Target max dimension for texture bitmaps in pixels. */
    private val targetMaxDim = 512

    /**
     * Z position of the camera eye, must match [buildViewMatrix].
     * Used for the perspective-correct front-face visibility threshold.
     */
    private val cameraEyeZ = 8f

    /** Number of items in the sphere. Set once data is provided. */
    @Volatile
    private var itemCount = 0

    /** Pre-computed sphere item positions. */
    private val items = ArrayList<GlobeItem>()

    /** Accumulated rotation matrix applied to every item. Identity on init. */
    private val rotationMatrix = FloatArray(16).also { Matrix.setIdentityM(it, 0) }

    /**
     * Current sphere scale factor for zoom; adjusted by pinch-to-zoom.
     * The sphere itself grows or shrinks while the camera remains fixed.
     */
    @Volatile
    private var sphereScale = 1.0f

    /** Viewport dimensions for NDC conversions. */
    private var viewWidth = 0
    private var viewHeight = 0

    /** OpenGL matrices. */
    private val proj = FloatArray(16)
    private val view = FloatArray(16)
    private val model = FloatArray(16)
    private val mvp = FloatArray(16)

    /** NDC-bounds list rebuilt each frame. */
    private val framePicks = ArrayList<CoverPick>()

    /** Shader handles. */
    private var program = 0
    private var aPos = 0
    private var aUV = 0
    private var uMVP = 0
    private var uAlpha = 0
    private var uTex = 0

    /** Geometry buffers. */
    private lateinit var quadVB: FloatBuffer
    private lateinit var quadTB: FloatBuffer
    private lateinit var quadIB: ShortBuffer

    /** Background decode executor. */
    private val decodeExecutor = Executors.newFixedThreadPool(2)
    private val inFlight = ConcurrentHashMap<Int, Boolean>()
    private val textures = ConcurrentHashMap<Int, Int>()

    /** Placeholder texture ID (gray square). */
    private var placeholderTex = 0

    /** Incremented when GL context is recreated so stale texture IDs are discarded. */
    private var glGeneration = 0

    /** Data source provided by the host fragment. */
    @Volatile
    private var dataProvider: ArtFlowDataProvider? = null

    /** Returns the data-provider item-id for the given index. */
    fun getItemIdAt(index: Int): Any? =
        if (index >= 0 && index < itemCount) dataProvider?.getItemId(index) else null

    /** Apply a new rotation delta encoded as a 4x4 column-major matrix. */
    fun applyRotationDelta(deltaMatrix: FloatArray) {
        val tmp = FloatArray(16)
        Matrix.multiplyMM(tmp, 0, deltaMatrix, 0, rotationMatrix, 0)
        System.arraycopy(tmp, 0, rotationMatrix, 0, 16)
    }

    /**
     * Updates the sphere scale factor to produce a zoom-in/zoom-out effect.
     * The sphere itself grows or shrinks anchored at the origin; the camera and
     * frustum remain fixed.
     *
     * @param scale Desired scale, clamped to [0.3, 2.8].
     */
    fun setScale(scale: Float) {
        sphereScale = scale.coerceIn(0.3f, 2.8f)
    }

    /** Replace the data source. Safe to call from any thread. */
    fun setDataProvider(provider: ArtFlowDataProvider) {
        dataProvider = provider
        val count = provider.getItemCount()
        itemCount = count
        buildSpherePositions(count)
        queueGL { deleteAllTextures() }
        schedulePrefetch()
    }

    /** Release all GL and background resources. */
    fun release() {
        decodeExecutor.shutdownNow()
        queueGL { deleteAllTextures() }
    }

    /**
     * Perform a hit-test against the last captured NDC bounds.
     *
     * @param ndcX Normalized device x in [-1, 1].
     * @param ndcY Normalized device y in [-1, 1].
     * @return The data-provider index of the hit item, or null if nothing was hit.
     */
    fun pickAt(ndcX: Float, ndcY: Float): Int? {
        var bestIndex: Int? = null
        var bestArea = Float.MAX_VALUE
        synchronized(framePicks) {
            for (pick in framePicks) {
                if (ndcX >= pick.minX && ndcX <= pick.maxX && ndcY >= pick.minY && ndcY <= pick.maxY) {
                    val area = (pick.maxX - pick.minX) * (pick.maxY - pick.minY)
                    if (area < bestArea) {
                        bestArea = area
                        bestIndex = pick.index
                    }
                }
            }
        }
        return bestIndex
    }

    /** Convert a screen-pixel coordinate to NDC, then delegate to [pickAt]. */
    fun pickAtScreen(screenX: Float, screenY: Float): Int? {
        if (viewWidth == 0 || viewHeight == 0) return null
        val ndcX = (screenX / viewWidth) * 2f - 1f
        val ndcY = 1f - (screenY / viewHeight) * 2f
        return pickAt(ndcX, ndcY)
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        // Cull back faces so quads on the far hemisphere are not rendered.
        GLES20.glEnable(GLES20.GL_CULL_FACE)
        GLES20.glCullFace(GLES20.GL_BACK)
        setupBuffers()
        buildProgram()
        buildViewMatrix()
        glGeneration++
        textures.clear()
        inFlight.clear()
        placeholderTex = 0
        ensurePlaceholderTexture()
        schedulePrefetch()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        viewWidth = width
        viewHeight = height
        rebuildProjection()
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        if (itemCount == 0) return

        synchronized(framePicks) { framePicks.clear() }
        schedulePrefetch()

        // Scale the base sphere radius by the current zoom factor so the sphere object
        // itself grows/shrinks while the camera and projection stay fixed.
        val effectiveRadius = sphereRadius * sphereScale

        // Rotate every unit-sphere position and its local east axis through the accumulated
        // rotation matrix, then scale the position to world space. Rotating east preserves
        // the UV-grid alignment of each quad after any user-applied rotation.
        data class Transformed(
                val item: GlobeItem,
                val worldX: Float,
                val worldY: Float,
                val worldZ: Float,
                val worldEastX: Float,
                val worldEastY: Float,
                val worldEastZ: Float
        )

        val transformed = items.map { item ->
            val wx = rotationMatrix[0] * item.x + rotationMatrix[4] * item.y + rotationMatrix[8] * item.z
            val wy = rotationMatrix[1] * item.x + rotationMatrix[5] * item.y + rotationMatrix[9] * item.z
            val wz = rotationMatrix[2] * item.x + rotationMatrix[6] * item.y + rotationMatrix[10] * item.z
            val wex = rotationMatrix[0] * item.ex + rotationMatrix[4] * item.ey + rotationMatrix[8] * item.ez
            val wey = rotationMatrix[1] * item.ex + rotationMatrix[5] * item.ey + rotationMatrix[9] * item.ez
            val wez = rotationMatrix[2] * item.ex + rotationMatrix[6] * item.ey + rotationMatrix[10] * item.ez
            Transformed(item, wx * effectiveRadius, wy * effectiveRadius, wz * effectiveRadius, wex, wey, wez)
        }

        // Painter's algorithm: draw farthest items first (most negative world-Z).
        val sorted = transformed.sortedBy { it.worldZ }

        // Horizon threshold: for a perspective camera at (0, 0, cameraEyeZ), a point on
        // the sphere is visible only when worldZ > effectiveRadius² / cameraEyeZ.
        // Skip items deeper than one radius behind the horizon to save draw calls.
        val horizonZ = effectiveRadius * effectiveRadius / cameraEyeZ

        GLES20.glUseProgram(program)
        for (entry in sorted) {
            if (entry.worldZ < -horizonZ) continue
            val tex = textures[entry.item.index] ?: placeholderTex
            drawItem(
                    entry.item, tex,
                    entry.worldX, entry.worldY, entry.worldZ,
                    entry.worldEastX, entry.worldEastY, entry.worldEastZ,
                    effectiveRadius
            )
        }
    }

    /**
     * Draws a single album-art quad tangent to the sphere surface at the given world-space
     * position. The model matrix is built from the UV-grid's own east and north axes so
     * quads tile edge-to-edge regardless of sphere rotation. Independent X/Y scaling from
     * [GlobeItem.halfWidth] and [GlobeItem.halfHeight] ensures each quad exactly fills its
     * latitude-row cell with no overlap and no gap.
     *
     * @param item            Source item carrying per-cell arc extents and the local east vector.
     * @param tex             OpenGL texture ID to bind.
     * @param wx              World X position on the sphere surface.
     * @param wy              World Y position on the sphere surface.
     * @param wz              World Z position on the sphere surface.
     * @param wex             World-space east unit-vector X, already rotated by [rotationMatrix].
     * @param wey             World-space east unit-vector Y.
     * @param wez             World-space east unit-vector Z.
     * @param effectiveRadius Current sphere radius including zoom scale; used to normalize the outward normal.
     */
    private fun drawItem(
            item: GlobeItem,
            tex: Int,
            wx: Float, wy: Float, wz: Float,
            wex: Float, wey: Float, wez: Float,
            effectiveRadius: Float
    ) {
        // Scale the stored (unscaled) arc extents by the current zoom factor.
        val hw = item.halfWidth * sphereScale
        val hh = item.halfHeight * sphereScale

        // Outward unit normal — divide by the SCALED radius so the vector is truly unit length.
        val ox = wx / effectiveRadius
        val oy = wy / effectiveRadius
        val oz = wz / effectiveRadius

        // North = east × outward.
        // Both vectors are unit-length and mutually perpendicular so the cross-product is
        // already normalized — no extra sqrt required.
        val northX = wey * oz - wez * oy
        val northY = wez * ox - wex * oz
        val northZ = wex * oy - wey * ox

        // Build a column-major 4×4 model matrix:
        //   column 0 → -east  * halfWidth  * 2  (local X axis; negated so winding is CCW/front-face)
        //   column 1 →  north * halfHeight * 2  (local Y axis, scaled to arc height)
        //   column 2 →  outward normal           (local Z axis, unscaled face normal)
        //   column 3 →  world position           (translation to sphere surface)
        val scaleX = hw * 2f
        val scaleY = hh * 2f
        model[0] = -wex * scaleX; model[1] = -wey * scaleX; model[2] = -wez * scaleX; model[3] = 0f
        model[4] = northX * scaleY; model[5] = northY * scaleY; model[6] = northZ * scaleY; model[7] = 0f
        model[8] = ox; model[9] = oy; model[10] = oz; model[11] = 0f
        model[12] = wx; model[13] = wy; model[14] = wz; model[15] = 1f

        Matrix.multiplyMM(mvp, 0, view, 0, model, 0)
        Matrix.multiplyMM(mvp, 0, proj, 0, mvp, 0)

        GLES20.glUniformMatrix4fv(uMVP, 1, false, mvp, 0)
        GLES20.glUniform1f(uAlpha, 1f)
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

        capturePickBounds(item.index)
    }

    /** Captures NDC bounding rectangle of the current MVP quad for hit-testing. */
    private fun capturePickBounds(index: Int) {
        val localCorners = floatArrayOf(
                -0.5f, 0.5f, 0f, 1f,
                -0.5f, -0.5f, 0f, 1f,
                0.5f, 0.5f, 0f, 1f,
                0.5f, -0.5f, 0f, 1f
        )
        var minX = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE

        for (i in 0 until 4) {
            val bi = i * 4
            val lx = localCorners[bi]
            val ly = localCorners[bi + 1]
            val lz = localCorners[bi + 2]
            val lw = localCorners[bi + 3]
            val cx = mvp[0] * lx + mvp[4] * ly + mvp[8] * lz + mvp[12] * lw
            val cy = mvp[1] * lx + mvp[5] * ly + mvp[9] * lz + mvp[13] * lw
            val cw = mvp[3] * lx + mvp[7] * ly + mvp[11] * lz + mvp[15] * lw
            if (cw != 0f) {
                val nx = cx / cw
                val ny = cy / cw
                if (nx < minX) minX = nx
                if (nx > maxX) maxX = nx
                if (ny < minY) minY = ny
                if (ny > maxY) maxY = ny
            }
        }

        if (minX <= maxX && minY <= maxY) {
            synchronized(framePicks) {
                framePicks.add(CoverPick(index, minX, maxX, minY, maxY))
            }
        }
    }

    /**
     * Distributes [count] album-art quads on a UV sphere using a latitude-row / longitude-column
     * grid.  Items in the same row share identical arc-height; items in the same row also share
     * identical arc-width, sized so the column count fills the full latitude circumference exactly.
     * This guarantees edge-to-edge tiling with zero overlap in both directions.
     *
     * The row count is chosen so the total slot count approximates [count].  Items are placed
     * in row-major order (north pole first); any unfilled trailing slots are simply omitted.
     */
    private fun buildSpherePositions(count: Int) {
        items.clear()
        if (count == 0) return

        // Total UV-sphere slots ≈ 4·nRows²/π  →  nRows ≈ sqrt(count·π/4).
        val nRows = maxOf(1, sqrt(count * PI / 4.0).roundToInt())
        val angularHeight = PI / nRows

        // Half arc-height is the same for every row.
        val halfH = (sphereRadius * angularHeight / 2.0).toFloat()

        var globalIndex = 0
        for (row in 0 until nRows) {
            if (globalIndex >= count) break

            // Colatitude of the row center (0 = north pole, π = south pole).
            val theta = PI * (row + 0.5) / nRows
            val sinTheta = sin(theta)
            val cosTheta = cos(theta)

            // Number of columns for this row: round the circumference / arc-height ratio.
            val nCols = maxOf(1, (2.0 * nRows * sinTheta).roundToInt())

            // Half arc-width = half of (circumference / nCols) = sphereRadius · sinθ · π / nCols.
            val halfW = (sphereRadius * sinTheta * PI / nCols).toFloat()

            for (col in 0 until nCols) {
                if (globalIndex >= count) break
                val phi = 2.0 * PI * col / nCols
                items.add(
                        GlobeItem(
                                x = (sinTheta * cos(phi)).toFloat(),
                                y = cosTheta.toFloat(),
                                z = (sinTheta * sin(phi)).toFloat(),
                                index = globalIndex,
                                halfWidth = halfW,
                                halfHeight = halfH,
                                // East unit vector: d/dφ of the unit-sphere position, normalized.
                                ex = (-sin(phi)).toFloat(),
                                ey = 0f,
                                ez = cos(phi).toFloat()
                        )
                )
                globalIndex++
            }
        }
    }

    /**
     * Rebuilds the perspective projection matrix with a fixed 45° FOV.
     * Zoom is provided entirely by scaling the sphere object, so the frustum
     * never changes. A near plane of 0.1 gives enough headroom for the maximum
     * zoom scale without clipping the front face of the sphere.
     */
    private fun rebuildProjection() {
        if (viewWidth == 0 || viewHeight == 0) return
        val aspect = viewWidth.toFloat() / viewHeight.toFloat()
        Matrix.perspectiveM(proj, 0, 45f, aspect, 0.1f, 50f)
    }

    /** Builds a static look-at view matrix with eye at (0, 0, 8). */
    private fun buildViewMatrix() {
        Matrix.setLookAtM(view, 0,
                          0f, 0f, 8f,
                          0f, 0f, 0f,
                          0f, 1f, 0f)
    }

    /** Creates the quad vertex/UV/index buffers. */
    private fun setupBuffers() {
        val verts = floatArrayOf(
                -0.5f, 0.5f, 0f,
                -0.5f, -0.5f, 0f,
                0.5f, -0.5f, 0f,
                0.5f, 0.5f, 0f
        )
        val uvs = floatArrayOf(
                0f, 0f,
                0f, 1f,
                1f, 1f,
                1f, 0f
        )
        val inds = shortArrayOf(0, 1, 2, 0, 2, 3)
        quadVB = ByteBuffer.allocateDirect(verts.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
            .apply { put(verts); position(0) }
        quadTB = ByteBuffer.allocateDirect(uvs.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
            .apply { put(uvs); position(0) }
        quadIB = ByteBuffer.allocateDirect(inds.size * 2)
            .order(ByteOrder.nativeOrder()).asShortBuffer()
            .apply { put(inds); position(0) }
    }

    /** Compiles and links the GLSL shader program. */
    private fun buildProgram() {
        val vs = """
            attribute vec3 aPos;
            attribute vec2 aUV;
            uniform mat4 uMVP;
            varying vec2 vUV;
            void main() {
                vUV = aUV;
                gl_Position = uMVP * vec4(aPos, 1.0);
            }
        """.trimIndent()

        val fs = """
            precision mediump float;
            varying vec2 vUV;
            uniform sampler2D uTex;
            uniform float uAlpha;
            void main() {
                vec4 c = texture2D(uTex, vUV);
                vec2 uv = vUV - 0.5;
                float vignette = 1.0 - dot(uv, uv) * 0.8;
                c.rgb *= clamp(vignette, 0.4, 1.0);
                c.a *= uAlpha;
                gl_FragColor = c;
            }
        """.trimIndent()

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
            throw RuntimeException("Globe shader compile error: $log")
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
            throw RuntimeException("Globe program link error: $log")
        }
        return id
    }

    /** Enqueues background texture loads for all items if not already loaded/loading. */
    private fun schedulePrefetch() {
        val count = itemCount
        if (count == 0) return
        repeat(count) { i ->
            if (!textures.containsKey(i) && !inFlight.containsKey(i)) {
                enqueueLoad(i)
            }
        }
    }

    private fun enqueueLoad(index: Int) {
        if (index < 0 || index >= itemCount) return
        if (textures.containsKey(index)) return
        if (inFlight.putIfAbsent(index, true) != null) return

        decodeExecutor.execute {
            var copy: Bitmap? = null
            try {
                val bmp = dataProvider?.loadArtwork(index, targetMaxDim)
                if (bmp != null && !bmp.isRecycled) {
                    copy = bmp.copy(bmp.config ?: Bitmap.Config.ARGB_8888, false)
                    queueGL {
                        try {
                            if (copy != null && !copy.isRecycled) {
                                textures[index] = createTextureFromBitmap(copy)
                            }
                        } finally {
                            copy?.recycle()
                        }
                    }
                }
            } catch (t: Throwable) {
                Log.w("GlobeRenderer", "Decode failed for index=$index: ${t.message}")
                copy?.recycle()
            } finally {
                inFlight.remove(index)
            }
        }
    }

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

    private fun createTextureFromBitmap(bmp: Bitmap): Int {
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        val id = ids[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, id)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0)
        return id
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

    private inline fun queueGL(crossinline block: () -> Unit) {
        glView.queueEvent { block() }
        glView.requestRender()
    }
}

