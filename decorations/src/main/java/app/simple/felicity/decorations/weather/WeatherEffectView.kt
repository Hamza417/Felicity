package app.simple.felicity.decorations.weather

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.Choreographer
import android.view.View
import androidx.core.graphics.withTranslation
import app.simple.felicity.manager.SharedPreferences.registerSharedPreferenceChangeListener
import app.simple.felicity.manager.SharedPreferences.unregisterSharedPreferenceChangeListener
import app.simple.felicity.preferences.UserInterfacePreferences
import java.util.Calendar
import kotlin.math.sin
import kotlin.random.Random

class WeatherEffectView @JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), Choreographer.FrameCallback, SharedPreferences.OnSharedPreferenceChangeListener {

    enum class Season {
        WINTER, AUTUMN, NONE
    }

    var season: Season = Season.NONE
        set(value) {
            if (field != value) {
                field = value
                if (value != Season.NONE) {
                    activeSeason = value
                    isStopping = false
                    initParticles()
                    invalidate()
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

    init {
        updateSeasonFromPreferences()
    }

    private fun updateSeasonFromPreferences() {
        val pref = UserInterfacePreferences.getWeatherEffect()
        season = when (pref) {
            UserInterfacePreferences.WEATHER_EFFECT_OFF -> Season.NONE
            UserInterfacePreferences.WEATHER_EFFECT_WINTER -> Season.WINTER
            UserInterfacePreferences.WEATHER_EFFECT_AUTUMN -> Season.AUTUMN
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
                    else -> {
                        Season.NONE
                    }
                }
            }
        }
    }

    private fun initParticles() {
        for (particle in particles) {
            resetParticle(particle)
            // Distribute initially above the screen for a gradual start
            val spread = 2.5f
            particle.y = -(Random.nextFloat() * spread + 0.1f)
        }
    }

    private fun resetParticle(particle: Particle) {
        particle.x = Random.nextFloat()
        particle.y = -0.1f
        particle.wobblePhase = Random.nextFloat() * (Math.PI.toFloat() * 2)

        when (activeSeason) {
            Season.WINTER -> {
                // Slower speed, gentle falling
                particle.speedY = Random.nextFloat() * 0.001f + 0.0005f
                particle.speedX = Random.nextFloat() * 0.0005f - 0.00025f
                particle.size = Random.nextFloat() * 8f + 4f
                particle.color = Color.WHITE
                particle.alpha = Random.nextInt(100, 255)
                particle.wobbleSpeed = Random.nextFloat() * 0.015f
                particle.wobbleAmount = Random.nextFloat() * 0.01f
            }
            Season.AUTUMN -> {
                // Top-right to bottom-left diagonal motion (slower)
                particle.speedY = Random.nextFloat() * 0.0015f + 0.001f
                particle.speedX = -(Random.nextFloat() * 0.001f + 0.0005f) // Moving leftwards
                particle.size = Random.nextFloat() * 12f + 6f
                // Autumn colors: Orange, Red, Yellow, Brown
                val colors = intArrayOf(0xFFD2691E.toInt(), 0xFFFF8C00.toInt(), 0xFFB22222.toInt(), 0xFFFFD700.toInt())
                particle.color = colors[Random.nextInt(colors.size)]
                particle.alpha = Random.nextInt(150, 255)
                particle.rotation = Random.nextFloat() * 360f
                particle.rotationSpeed = Random.nextFloat() * 2f - 1f
                particle.wobbleSpeed = Random.nextFloat() * 0.02f
                particle.wobbleAmount = Random.nextFloat() * 0.02f
            }
            Season.NONE -> {
                particle.alpha = 0
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        registerSharedPreferenceChangeListener()
        updateSeasonFromPreferences()
        Choreographer.getInstance().postFrameCallback(this)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        unregisterSharedPreferenceChangeListener()
        Choreographer.getInstance().removeFrameCallback(this)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == UserInterfacePreferences.WEATHER_EFFECT) {
            updateSeasonFromPreferences()
        }
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == VISIBLE) {
            isStopping = false
            updateSeasonFromPreferences()
            if (activeSeason != Season.NONE) {
                initParticles() // Start gradually when app is reopened
            }
        } else {
            isStopping = true // Drain when hidden
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
            if (p.y > 1.1f) {
                if (!isStopping) {
                    resetParticle(p)
                    p.x = Random.nextFloat() // randomize X again when resetting at top
                }
            } else {
                p.y += p.speedY
                p.x += p.speedX
                p.wobblePhase += p.wobbleSpeed

                if (activeSeason == Season.AUTUMN) {
                    p.rotation += p.rotationSpeed
                }

                // Wrap around X (if going off-screen to the left)
                if (p.x < -0.2f) {
                    p.x = 1.2f
                } else if (p.x > 1.2f) {
                    p.x = -0.2f
                }
            }

            if (p.y <= 1.1f) {
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

        for (p in particles) {
            if (p.y > 1.1f) continue // don't draw particles off-screen

            val drawX = (p.x + sin(p.wobblePhase.toDouble()).toFloat() * p.wobbleAmount) * w
            val drawY = p.y * h

            paint.color = p.color
            paint.alpha = p.alpha

            when (activeSeason) {
                Season.WINTER -> {
                    canvas.drawCircle(drawX, drawY, p.size, paint)
                }
                Season.AUTUMN -> {
                    canvas.withTranslation(drawX, drawY) {
                        rotate(p.rotation)
                        // Draw a simple leaf shape (oval)
                        drawOval(-p.size, -p.size / 2f, p.size, p.size / 2f, paint)
                    }
                }
                Season.NONE -> {}
            }
        }
    }

    private class Particle {
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

        // For autumn
        var rotation: Float = 0f
        var rotationSpeed: Float = 0f
    }
}
