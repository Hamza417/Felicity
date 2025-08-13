package app.simple.felicity.decorations.views

import android.animation.Animator
import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.animation.DecelerateInterpolator
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import app.simple.felicity.decorations.typeface.TypeFaceTextView

class AnimatedTextView @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0
) : HorizontalScrollView(context, attrs, defStyleAttr) {

    private val linearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
    }

    private var marqueeAnimator: ValueAnimator? = null
    private var scrollAnimator: ValueAnimator? = null
    private var duration = 0L

    init {
        isHorizontalScrollBarEnabled = false
        isVerticalScrollBarEnabled = false
        isHorizontalFadingEdgeEnabled = true
        setFadingEdgeLength((16 * resources.displayMetrics.density).toInt()) // fading edge length
        addView(linearLayout, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
    }

    fun setAnimatedText(text: String) {
        // Stop any ongoing marquee scrolling
        marqueeAnimator?.cancel()

        linearLayout.removeAllViews()

        text.forEachIndexed { index, c ->
            val charView = TypeFaceTextView(context).apply {
                this.text = c.toString()
                alpha = 0f
                scaleY = 0.5f
                scaleX = 0.5f
                fontStyle = TypeFaceTextView.BLACK
                textSize = 12f * resources.displayMetrics.density
            }

            linearLayout.addView(charView)

            if (!c.isWhitespace()) {
                charView.animate()
                    .alpha(1f)
                    .scaleY(1f)
                    .scaleX(1f)
                    .setStartDelay((index * 50).toLong()) // staggered delay per char
                    .setDuration(150)
                    .withEndAction {
                        // When last character animation completes, start marquee
                        if (index == text.length - 1) {
                            startMarquee()
                        }
                    }
                    .start()

                duration += 500L // Increment duration for each character
            } else {
                charView.alpha = 1f
                charView.scaleY = 1f
                charView.scaleX = 1f

                // If last character is whitespace still start marquee after animation delay
                if (index == text.length - 1) {
                    postDelayed({ startMarquee() }, (index * 100 + 150).toLong())
                }
            }
        }
    }

    private fun startMarquee() {
        val totalWidth = linearLayout.width
        val scrollWidth = totalWidth - width
        if (scrollWidth <= 0) return  // No need to scroll if content fits

        marqueeAnimator?.cancel()

        marqueeAnimator = ValueAnimator.ofInt(0, totalWidth).apply {
            duration = this@AnimatedTextView.duration
            addUpdateListener { animation ->
                val scrollX = animation.animatedValue as Int
                scrollTo(scrollX, 0)
            }
            addListener(object : Animator.AnimatorListener {
                override fun onAnimationStart(animation: Animator) {

                }

                override fun onAnimationEnd(animation: Animator) {
                    // When marquee ends, scroll back to start
                    scrollToStart()
                }

                override fun onAnimationCancel(animation: Animator) {

                }

                override fun onAnimationRepeat(animation: Animator) {

                }
            })
            start()
        }
    }

    private fun scrollToStart() {
        scrollAnimator?.cancel()
        marqueeAnimator?.cancel()

        scrollAnimator = ValueAnimator.ofInt(scrollX, 0).apply {
            duration = this@AnimatedTextView.duration.div(4) // Duration for scrolling back to start
            interpolator = DecelerateInterpolator(1.5F)
            addUpdateListener { animation ->
                val scrollX = animation.animatedValue as Int
                scrollTo(scrollX, 0)
            }
            addListener(object : Animator.AnimatorListener {
                override fun onAnimationStart(animation: Animator) {

                }

                override fun onAnimationEnd(animation: Animator) {
                    // Restart marquee after scrolling back
                    startMarquee()
                }

                override fun onAnimationCancel(animation: Animator) {

                }

                override fun onAnimationRepeat(animation: Animator) {

                }
            })
            start()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        marqueeAnimator?.cancel() // Clean up animator to avoid leaks
    }
}

