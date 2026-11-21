package app.simple.felicity.decorations.weather

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.Choreographer
import android.view.View
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.withTranslation
import app.simple.felicity.manager.SharedPreferences.registerSharedPreferenceChangeListener
import app.simple.felicity.manager.SharedPreferences.unregisterSharedPreferenceChangeListener
import app.simple.felicity.preferences.UserInterfacePreferences
import app.simple.felicity.theme.interfaces.ThemeChangedListener
import app.simple.felicity.theme.managers.ThemeManager
import app.simple.felicity.theme.models.Accent
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class WeatherEffectView @JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr),
    Choreographer.FrameCallback,
    SharedPreferences.OnSharedPreferenceChangeListener,
    ThemeChangedListener {

    enum class Season {
        WINTER, AUTUMN, SUMMER, NONE
    }

    var season: Season = Season.NONE
        set(value) {
            if (field != value) {
                field = value
                if (value != Season.NONE) {
                    if (activeSeason == Season.NONE) {
                        activeSeason = value
                        isStopping = false
                        initParticles()
                        invalidate()
                    } else {
                        // When transitioning, set activeSeason without re-initializing all particles.
                        // As old particles fall off-screen, they will respawn as the new season.
                        activeSeason = value
                        isStopping = false
                    }
                } else {
                    isStopping = true
                }
            }
        }

    private var activeSeason: Season = Season.NONE
    private var isStopping = false

    private val particles = Array(40) {
        Particle()
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val leafPath = Path().apply {
        moveTo(-1f, 0f)
        quadTo(0f, -1f, 1f, 0f)
        quadTo(0f, 1f, -1f, 0f)
        close()
    }

    private val autumnVeinPathCenter = Path().apply {
        moveTo(-0.9f, 0f)
        lineTo(0.8f, 0f)
    }

    private val autumnVeinPathSides = Path().apply {
        moveTo(-0.4f, 0f)
        lineTo(0f, -0.3f)
        moveTo(-0.4f, 0f)
        lineTo(0f, 0.3f)
        moveTo(0.1f, 0f)
        lineTo(0.4f, -0.2f)
        moveTo(0.1f, 0f)
        lineTo(0.4f, 0.2f)
    }

    private val winterPath = Path().apply {
        val branchStart = 0.5f
        val branchDx = 0.35f
        val branchDy = 0.35f
        for (i in 0 until 6) {
            val angle = Math.toRadians((i * 60).toDouble())
            val cosA = cos(angle).toFloat()
            val sinA = sin(angle).toFloat()

            fun rx(x: Float, y: Float) = x * cosA - y * sinA
            fun ry(x: Float, y: Float) = x * sinA + y * cosA

            moveTo(0f, 0f)
            lineTo(rx(1f, 0f), ry(1f, 0f))

            moveTo(rx(branchStart, 0f), ry(branchStart, 0f))
            lineTo(rx(branchStart + branchDx, branchDy), ry(branchStart + branchDx, branchDy))

            moveTo(rx(branchStart, 0f), ry(branchStart, 0f))
            lineTo(rx(branchStart + branchDx, -branchDy), ry(branchStart + branchDx, -branchDy))
        }
    }

    private val tempHsl = FloatArray(3)
    private val tempParticleHsl = FloatArray(3)

    init {
        updateSeasonFromPreferences()
    }

    private fun updateSeasonFromPreferences() {
        if (isInEditMode) {
            season = Season.AUTUMN
            return
        }

        val pref = UserInterfacePreferences.getWeatherEffect()
        season = when (pref) {
            UserInterfacePreferences.WEATHER_EFFECT_OFF -> Season.NONE
            UserInterfacePreferences.WEATHER_EFFECT_WINTER -> Season.WINTER
            UserInterfacePreferences.WEATHER_EFFECT_AUTUMN -> Season.AUTUMN
            UserInterfacePreferences.WEATHER_EFFECT_SUMMER -> Season.SUMMER
            else -> {
                // Auto
                val month = Calendar.getInstance().get(Calendar.MONTH)
                when (month) {
                    Calendar.DECEMBER, Calendar.JANUARY, Calendar.FEBRUARY -> {
                        Season.WINTER
                    }
                    Calendar.SEPTEMBER, Calendar.OCTOBER, Calendar.NOVEMBER -> {
                        Season.AUTUMN
                    }
                    Calendar.MAY, Calendar.JUNE, Calendar.JULY, Calendar.AUGUST -> {
                        Season.SUMMER
                    }
                    else -> {
                        Season.NONE
                    }
                }
            }
        }
    }

    private fun initParticles() {
        for (particle in particles) {
            particle.type = activeSeason
            resetParticle(particle)
            // Distribute initially across the screen for a gradual start
            val spreadY = 2.5f
            val spreadX = 1.2f

            if (activeSeason == Season.SUMMER) {
                // Summer dandelion seeds start distributed across the screen
                particle.y = Random.nextFloat() * 1.2f - 0.1f
                particle.x = Random.nextFloat() * spreadX - 0.1f
            } else {
                particle.y = -(Random.nextFloat() * spreadY + 0.1f)
            }
        }
    }

    private fun resetParticle(particle: Particle) {
        particle.type = activeSeason
        particle.x = Random.nextFloat()

        if (particle.type == Season.SUMMER) {
            // Summer seeds respawn at the bottom
            particle.y = 1.1f
        } else {
            // Winter/Autumn respawn at the top
            particle.y = -0.1f
        }

        particle.wobblePhase = Random.nextFloat() * (Math.PI.toFloat() * 2)

        val accentColor = ThemeManager.accent.primaryAccentColor
        ColorUtils.colorToHSL(accentColor, tempHsl)

        when (particle.type) {
            Season.WINTER -> {
                // Slower speed, gentle falling
                particle.speedY = Random.nextFloat() * 0.001f + 0.0005f
                particle.speedX = Random.nextFloat() * 0.0005f - 0.00025f
                particle.size = Random.nextFloat() * 10f + 6f // Slightly larger to see complex snowflake details
                tempHsl[2] = Random.nextFloat() * 0.4f + 0.5f // Lightness from 0.5 to 0.9
                particle.color = ColorUtils.HSLToColor(tempHsl)
                particle.alpha = Random.nextInt(100, 255)
                particle.wobbleSpeed = Random.nextFloat() * 0.015f
                particle.wobbleAmount = Random.nextFloat() * 0.01f

                // 3D Rotation params
                particle.rotation = Random.nextFloat() * 360f
                particle.rotationSpeed = Random.nextFloat() * 1f - 0.5f
                particle.rotationX = Random.nextFloat() * 360f
                particle.rotationXSpeed = Random.nextFloat() * 2f - 1f
                particle.rotationY = Random.nextFloat() * 360f
                particle.rotationYSpeed = Random.nextFloat() * 2f - 1f
            }
            Season.AUTUMN -> {
                // Top-right to bottom-left diagonal motion (slower)
                particle.speedY = Random.nextFloat() * 0.0015f + 0.001f
                particle.speedX = -(Random.nextFloat() * 0.001f + 0.0005f) // Moving leftwards
                particle.size = Random.nextFloat() * 12f + 6f
                // Autumn colors: Darker shades of accent color
                tempHsl[2] = Random.nextFloat() * 0.4f + 0.1f // Lightness from 0.1 to 0.5
                particle.color = ColorUtils.HSLToColor(tempHsl)
                particle.alpha = Random.nextInt(150, 255)
                particle.wobbleSpeed = Random.nextFloat() * 0.02f
                particle.wobbleAmount = Random.nextFloat() * 0.02f

                // 3D Rotation params
                particle.rotation = Random.nextFloat() * 360f
                particle.rotationSpeed = Random.nextFloat() * 2f - 1f
                particle.rotationX = Random.nextFloat() * 360f
                particle.rotationXSpeed = Random.nextFloat() * 3f - 1.5f
                particle.rotationY = Random.nextFloat() * 360f
                particle.rotationYSpeed = Random.nextFloat() * 3f - 1.5f
            }
            Season.SUMMER -> {
                // Gentle floating up/diagonally like dandelion seeds in a breeze
                particle.speedY = -(Random.nextFloat() * 0.001f + 0.0005f)
                particle.speedX = Random.nextFloat() * 0.001f - 0.0005f
                particle.size = Random.nextFloat() * 20f + 15f // Need to be large enough to see the lines

                // Summer colors: Very light, almost white accent color shades
                tempHsl[2] = Random.nextFloat() * 0.2f + 0.7f // Lightness from 0.7 to 0.9
                particle.color = ColorUtils.HSLToColor(tempHsl)
                particle.alpha = Random.nextInt(150, 255) // Less transparent

                // Drift/Wobble
                particle.wobbleSpeed = Random.nextFloat() * 0.005f
                particle.wobbleAmount = Random.nextFloat() * 0.03f

                // No complex 3D rotation, just a slight 2D spin
                particle.rotation = Random.nextFloat() * 360f
                particle.rotationSpeed = Random.nextFloat() * 0.5f - 0.25f
                particle.rotationX = 0f
                particle.rotationXSpeed = 0f
                particle.rotationY = 0f
                particle.rotationYSpeed = 0f
            }
            Season.NONE -> {
                particle.alpha = 0
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        ThemeManager.addListener(this)
        updateSeasonFromPreferences()
        Choreographer.getInstance().postFrameCallback(this)

        if (isInEditMode.not()) {
            registerSharedPreferenceChangeListener()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        ThemeManager.removeListener(this)
        unregisterSharedPreferenceChangeListener()
        Choreographer.getInstance().removeFrameCallback(this)
    }

    override fun onAccentChanged(accent: Accent) {
        val accentColor = accent.primaryAccentColor
        ColorUtils.colorToHSL(accentColor, tempHsl)

        for (particle in particles) {
            ColorUtils.colorToHSL(particle.color, tempParticleHsl)

            // Keep the randomized lightness, only change hue and saturation
            tempHsl[2] = tempParticleHsl[2]
            particle.color = ColorUtils.HSLToColor(tempHsl)
        }

        invalidate()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        when (key) {
            UserInterfacePreferences.WEATHER_EFFECT -> {
                updateSeasonFromPreferences()
            }
        }
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        when (visibility) {
            VISIBLE -> {
                isStopping = false
                updateSeasonFromPreferences()
                if (activeSeason == Season.NONE && season != Season.NONE) {
                    activeSeason = season
                }
                if (activeSeason != Season.NONE) {
                    initParticles() // Start gradually when app is reopened
                }
            }
            else -> {
                isStopping = true // Drain when hidden
            }
        }
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (activeSeason != Season.NONE) {
            updateParticles()
            invalidate()
        }

        Choreographer.getInstance().postFrameCallback(this)
    }

    private fun updateParticles() {
        var hasActiveParticles = false
        for (p in particles) {
            val isOffScreen = if (p.type == Season.SUMMER) {
                p.y < -0.1f // Floating up, off-screen at the top
            } else {
                p.y > 1.1f // Falling down, off-screen at the bottom
            }

            if (isOffScreen) {
                if (!isStopping) {
                    resetParticle(p)
                    p.x = Random.nextFloat() // randomize X again when resetting
                }
            } else {
                p.y += p.speedY
                p.x += p.speedX
                p.wobblePhase += p.wobbleSpeed

                if (p.type == Season.AUTUMN || p.type == Season.WINTER) {
                    p.rotation += p.rotationSpeed
                    p.rotationX += p.rotationXSpeed
                    p.rotationY += p.rotationYSpeed
                } else if (p.type == Season.SUMMER) {
                    p.rotation += p.rotationSpeed
                }

                // Wrap around X (if going off-screen to the left or right)
                if (p.x < -0.2f) {
                    p.x = 1.2f
                } else if (p.x > 1.2f) {
                    p.x = -0.2f
                }
            }

            val isActive = if (p.type == Season.SUMMER) {
                p.y >= -0.1f
            } else {
                p.y <= 1.1f
            }

            if (isActive) {
                hasActiveParticles = true
            }
        }

        if (isStopping && !hasActiveParticles) {
            activeSeason = Season.NONE
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (activeSeason == Season.NONE) return

        val w = width.toFloat()
        val h = height.toFloat()

        for (particle in particles) {
            val isOffScreen = if (particle.type == Season.SUMMER) {
                particle.y < -0.1f
            } else {
                particle.y > 1.1f
            }

            if (isOffScreen) continue // don't draw particles off-screen

            val drawX = (particle.x + sin(particle.wobblePhase.toDouble()).toFloat() * particle.wobbleAmount) * w
            val drawY = particle.y * h

            paint.color = particle.color
            paint.alpha = particle.alpha

            when (particle.type) {
                Season.WINTER -> {
                    paint.strokeWidth = 1f / 6f // Thinner strokes for complex shape
                    paint.strokeCap = Paint.Cap.ROUND
                    paint.style = Paint.Style.STROKE

                    canvas.withTranslation(drawX, drawY) {
                        // Simulate 3D rotation using Canvas Scaling
                        val scaleX = cos(Math.toRadians(particle.rotationY.toDouble())).toFloat()
                        val scaleY = cos(Math.toRadians(particle.rotationX.toDouble())).toFloat()
                        scale(scaleX * particle.size, scaleY * particle.size)
                        rotate(particle.rotation)

                        drawPath(winterPath, paint)
                    }

                    paint.style = Paint.Style.FILL // restore to default fill for other things if needed
                }
                Season.AUTUMN -> {
                    canvas.withTranslation(drawX, drawY) {
                        // Simulate 3D rotation using Canvas Scaling
                        val scaleX = cos(Math.toRadians(particle.rotationY.toDouble())).toFloat()
                        val scaleY = cos(Math.toRadians(particle.rotationX.toDouble())).toFloat()
                        scale(scaleX * particle.size, scaleY * particle.size)
                        rotate(particle.rotation)

                        // Draw a leafy shape
                        paint.style = Paint.Style.FILL
                        drawPath(leafPath, paint)

                        // Draw some texture/veins
                        val origColor = paint.color
                        paint.color = Color.argb((paint.alpha * 0.3f).toInt(), 0, 0, 0)
                        paint.style = Paint.Style.STROKE
                        paint.strokeJoin = Paint.Join.ROUND
                        paint.strokeCap = Paint.Cap.ROUND

                        // Center vein
                        paint.strokeWidth = 1f / 8f
                        drawPath(autumnVeinPathCenter, paint)

                        // Side veins
                        paint.strokeWidth = 1f / 12f
                        drawPath(autumnVeinPathSides, paint)

                        paint.color = origColor
                        paint.style = Paint.Style.FILL
                    }
                }
                Season.SUMMER -> {
                    canvas.withTranslation(drawX, drawY) {
                        rotate(particle.rotation)
                        scale(particle.size, particle.size)

                        // The main plant stalk (curved for natural variation)
                        paint.style = Paint.Style.STROKE
                        paint.strokeWidth = 1f / 30f
                        paint.strokeCap = Paint.Cap.ROUND
                        canvas.drawPath(particle.summerStemPath, paint)

                        // The dark central core of the flower head
                        paint.style = Paint.Style.FILL
                        drawCircle(0f, 0f, 0.08f, paint)

                        // The 360-degree puffball (lollipop)
                        paint.style = Paint.Style.STROKE
                        paint.strokeWidth = 1f / 60f
                        canvas.drawPath(particle.summerFluffPath, paint)
                    }

                    paint.style = Paint.Style.FILL
                }
                Season.NONE -> {

                }
            }
        }
    }

    private class Particle {
        var type: Season = Season.NONE

        var x: Float = 0f
        var y: Float = 0f
        var speedX: Float = 0f
        var speedY: Float = 0f
        var size: Float = 0f
        var color: Int = 0
        var alpha: Int = 0

        var wobblePhase: Float = 0f
        var wobbleSpeed: Float = 0f
        var wobbleAmount: Float = 0f

        // For autumn and winter (3D simulated tumbling)
        var rotation: Float = 0f
        var rotationSpeed: Float = 0f

        var rotationX: Float = 0f
        var rotationXSpeed: Float = 0f

        var rotationY: Float = 0f
        var rotationYSpeed: Float = 0f

        val summerStemPath = Path()
        val summerFluffPath = Path()

        init {
            val stemLength = 1.25f
            val bendFactor = ((hashCode() % 100) / 50f) - 1f
            val controlPointX = bendFactor * 0.6f
            val endPointX = bendFactor * 0.2f

            summerStemPath.moveTo(0f, 0f)
            summerStemPath.quadTo(controlPointX, stemLength / 2f, endPointX, stemLength)

            val fluffCount = 24
            val baseFluffLength = 0.45f
            val angleStep = 360f / fluffCount.toFloat()

            for (i in 0 until fluffCount) {
                val angle = Math.toRadians((i * angleStep).toDouble())
                val strandRand = abs((hashCode() xor (i * 31)) % 100) / 100f
                val currentFluffLength = baseFluffLength * (0.75f + 0.25f * strandRand)

                val endX = (cos(angle) * currentFluffLength).toFloat()
                val endY = (sin(angle) * currentFluffLength).toFloat()

                summerFluffPath.moveTo(0f, 0f)
                summerFluffPath.lineTo(endX, endY)

                val branchDist = currentFluffLength * 0.75f
                val branchX = (cos(angle) * branchDist).toFloat()
                val branchY = (sin(angle) * branchDist).toFloat()
                val branchLength = 0.06f

                val bAngle1 = angle - 0.5
                summerFluffPath.moveTo(branchX, branchY)
                summerFluffPath.lineTo(
                        /* x = */ branchX + (cos(bAngle1) * branchLength).toFloat(),
                        /* y = */ branchY + (sin(bAngle1) * branchLength).toFloat()
                )

                val bAngle2 = angle + 0.5
                summerFluffPath.moveTo(branchX, branchY)
                summerFluffPath.lineTo(
                        /* x = */ branchX + (cos(bAngle2) * branchLength).toFloat(),
                        /* y = */ branchY + (sin(bAngle2) * branchLength).toFloat()
                )
            }
        }
    }
}
