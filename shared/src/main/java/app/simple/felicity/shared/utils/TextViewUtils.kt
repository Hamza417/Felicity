package app.simple.felicity.shared.utils

import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.text.Html
import android.text.Selection
import android.text.Spannable
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.TextWatcher
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.View
import android.widget.TextView
import androidx.appcompat.widget.AppCompatEditText
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.graphics.toColorInt
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import app.simple.felicity.shared.R
import app.simple.felicity.shared.utils.TextViewUtils.setFade
import app.simple.felicity.shared.utils.TextViewUtils.setSlide
import app.simple.felicity.shared.utils.TextViewUtils.setTextWithEffect
import app.simple.felicity.shared.utils.TextViewUtils.setTypeWriting
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.WeakHashMap

object TextViewUtils {

    private val UNKNOWN_VALUES = arrayOf("unknown", "null", "", "0")

    fun AppCompatTextView.setStartDrawable(resourceId: Int) {
        this.setCompoundDrawablesWithIntrinsicBounds(resourceId, 0, 0, 0)
    }

    fun TextView.makeLinks(vararg links: Pair<String, View.OnClickListener>) {
        val spannableString = SpannableString(this.text)
        var startIndexOfLink = -1
        for (link in links) {
            val clickableSpan = object : ClickableSpan() {
                override fun updateDrawState(textPaint: TextPaint) {
                    /**
                     * use this to change the link color
                     */
                    textPaint.color = "#2e86c1".toColorInt()

                    /**
                     * Toggle below value to enable/disable
                     * the underline shown below the clickable text
                     */
                    textPaint.isUnderlineText = true
                }

                override fun onClick(view: View) {
                    Selection.setSelection((view as TextView).text as Spannable, 0)
                    view.invalidate()
                    link.second.onClick(view)
                }
            }
            startIndexOfLink = this.text.toString().indexOf(link.first, startIndexOfLink + 1)
            // if(startIndexOfLink == -1) continue // if you want to verify your texts contains links text
            spannableString.setSpan(
                    clickableSpan, startIndexOfLink, startIndexOfLink + link.first.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        this.movementMethod =
            LinkMovementMethod.getInstance() // without LinkMovementMethod, link can not click
        this.setText(spannableString, TextView.BufferType.SPANNABLE)
    }

    fun TextView.makeClickable(vararg links: Pair<String, View.OnClickListener>) {
        val spannableString = SpannableString(this.text)
        var startIndexOfLink = -1
        for (link in links) {
            val clickableSpan = object : ClickableSpan() {
                override fun updateDrawState(textPaint: TextPaint) {
                    /**
                     * use this to change the link color
                     */
                    textPaint.color = this@makeClickable.currentTextColor

                    /**
                     * Toggle below value to enable/disable
                     * the underline shown below the clickable text
                     */
                    textPaint.isUnderlineText = true
                }

                override fun onClick(view: View) {
                    Selection.setSelection((view as TextView).text as Spannable, 0)
                    view.invalidate()
                    link.second.onClick(view)
                }
            }
            startIndexOfLink = this.text.toString().indexOf(link.first, startIndexOfLink + 1)
            // if(startIndexOfLink == -1) continue // if you want to verify your texts contains links text
            spannableString.setSpan(
                    clickableSpan, startIndexOfLink, startIndexOfLink + link.first.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        this.movementMethod =
            LinkMovementMethod.getInstance() // without LinkMovementMethod, link can not click

        this.setText(spannableString, TextView.BufferType.SPANNABLE)
    }

    fun String.toHtmlSpanned(): Spanned {
        return Html.fromHtml(this, Html.FROM_HTML_SEPARATOR_LINE_BREAK_HEADING)
    }

    fun TextView.setDrawableTint(color: Int) {
        for (drawable in this.compoundDrawablesRelative) {
            drawable?.mutate()
            drawable?.colorFilter = PorterDuffColorFilter(
                    color, PorterDuff.Mode.SRC_IN
            )
        }
    }

    fun AppCompatEditText.setDrawableTint(color: Int) {
        for (drawable in this.compoundDrawablesRelative) {
            drawable?.mutate()
            drawable?.colorFilter = PorterDuffColorFilter(
                    color, PorterDuff.Mode.SRC_IN
            )
        }
    }

    inline fun TextView.doOnTextChanged(
            crossinline action: (
                    text: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int
            ) -> Unit
    ): TextWatcher = addTextChangedListener(onTextChanged = action)

    fun TextView.setTextOrUnknown(text: String?) {
        if (text.isNullOrBlank()) {
            this.text = context.getString(R.string.unknown)
        } else {
            if (UNKNOWN_VALUES.contains(text.lowercase())) {
                this.text = text
            } else {
                this.text = text
            }
        }
    }

    private val activeAnimationJobs = WeakHashMap<TextView, Job>()

    /**
     * Set texts with a flip/typewriter delay up to a certain character limit.
     * Once the limit is reached, the remaining characters are set instantly in one go.
     */
    fun TextView.setTypeWriting(text: String, delayTime: Long = 10L, animateLimit: Int? = null) {
        activeAnimationJobs[this]?.cancel()

        val scope = this.findViewTreeLifecycleOwner()?.lifecycleScope ?: return

        if (this.text.toString() == text) return // No need to animate if the text is already the same

        activeAnimationJobs[this] = scope.launch {
            val current = this@setTypeWriting.text.toString()
            val builder = StringBuilder(current)

            val limit = animateLimit ?: Int.MAX_VALUE
            var flipCount = 0 // Tracks actual UI mutations rather than the string index
            var limitReached = false

            // 1. Flip & Typewriter Phase
            for (i in text.indices) {
                if (flipCount >= limit) {
                    limitReached = true
                    break // Stop animating, jump to the end
                }

                val isAppending = i >= builder.length
                val isDifferent = !isAppending && builder[i] != text[i]

                // OPTIMIZATION: If the character exists and is identical, skip the delay and UI update
                if (!isAppending && !isDifferent) {
                    continue
                }

                // Animate the difference
                delay(delayTime)

                if (isAppending) {
                    builder.append(text[i])
                } else {
                    builder.setCharAt(i, text[i])
                }

                this@setTypeWriting.text = builder.toString()
                flipCount++ // Only increment the limit counter when an actual change happens
            }

            // 2. Cleanup Phase (Delete Extra Characters)
            if (!limitReached && builder.length > text.length) {
                val charsToDelete = builder.length - text.length

                for (i in 0 until charsToDelete) {
                    if (flipCount >= limit) {
                        limitReached = true
                        break // Stop deleting one-by-one, jump to the end
                    }

                    delay(delayTime)
                    builder.deleteCharAt(builder.lastIndex)
                    this@setTypeWriting.text = builder.toString()
                    flipCount++
                }
            }

            // 3. Finalization
            if (limitReached) {
                // Instantly apply the full target text, bypassing any further delays
                this@setTypeWriting.text = text
            }
        }
    }

    /**
     * Animates a text change with a cross-fade: fades the current text out, swaps the
     * content, then fades it back in. Has no visible effect when the new text equals the
     * current text.
     *
     * @param text The new text to display.
     * @param fadeDuration Half-duration of each fade phase in milliseconds.
     */
    fun TextView.setFade(text: String, fadeDuration: Long = 150L) {
        activeAnimationJobs[this]?.cancel()

        val scope = this.findViewTreeLifecycleOwner()?.lifecycleScope ?: return

        if (this.text.toString() == text) return

        activeAnimationJobs[this] = scope.launch {
            // Fade out
            animate()
                .alpha(0f)
                .setDuration(fadeDuration)
                .withEndAction {
                    this@setFade.text = text
                    // Fade in
                    animate()
                        .alpha(1f)
                        .setDuration(fadeDuration)
                        .start()
                }
                .start()
        }
    }

    /**
     * Animates a text change with a directional slide.
     *
     * When [isForward] is `true` (next song), the label slides out to the left and the new
     * text enters from the right, matching the direction of a forward swipe.
     * When [isForward] is `false` (previous song), the directions are reversed.
     *
     * @param text       The new text to display.
     * @param isForward  `true` to animate forward (next), `false` to animate backward (previous).
     * @param slideDuration Duration of each slide phase in milliseconds.
     */
    fun TextView.setSlide(text: String, isForward: Boolean, slideDuration: Long = 200L) {
        activeAnimationJobs[this]?.cancel()

        val scope = this.findViewTreeLifecycleOwner()?.lifecycleScope ?: return

        if (this.text.toString() == text) return

        // Slide out: forward → exit left (negative X), backward → exit right (positive X)
        val slideOutX = if (isForward) -width.toFloat().coerceAtLeast(200f) else width.toFloat().coerceAtLeast(200f)
        val slideInX = -slideOutX

        activeAnimationJobs[this] = scope.launch {
            // Slide out current text
            animate()
                .translationX(slideOutX)
                .alpha(0f)
                .setDuration(slideDuration)
                .withEndAction {
                    this@setSlide.text = text
                    translationX = slideInX
                    // Slide in new text from the opposite edge
                    animate()
                        .translationX(0f)
                        .alpha(1f)
                        .setDuration(slideDuration)
                        .start()
                }
                .start()
        }
    }

    /**
     * Central dispatcher that applies a text change animation based on [effect].
     *
     * Use this function in the player UI so that the chosen animation style is applied
     * consistently across all text labels without scattering effect constants through the UI layer.
     *
     * Effect constants are defined in `BehaviourPreferences`:
     *  - `TEXT_EFFECT_NONE`        (0) — instant text swap, no animation.
     *  - `TEXT_EFFECT_FADE`        (1) — cross-fade via [setFade].
     *  - `TEXT_EFFECT_SLIDE`       (2) — directional slide via [setSlide].
     *  - `TEXT_EFFECT_TYPEWRITING` (3) — character-by-character reveal via [setTypeWriting].
     *
     * @param text       The new text to display.
     * @param effect     The animation style constant (see above).
     * @param isForward  Navigation direction used only when [effect] is `TEXT_EFFECT_SLIDE`.
     *                   `true` = forward (next song), `false` = backward (previous song).
     */
    fun TextView.setTextWithEffect(text: String, effect: Int, isForward: Boolean = true) {
        when (effect) {
            TEXT_EFFECT_FADE -> setFade(text)
            TEXT_EFFECT_SLIDE -> setSlide(text, isForward)
            TEXT_EFFECT_TYPEWRITING -> setTypeWriting(text)
            else -> this.text = text // TEXT_EFFECT_NONE or any unknown value
        }
    }

    /** @see setTextWithEffect */
    const val TEXT_EFFECT_NONE = 0

    /** @see setTextWithEffect */
    const val TEXT_EFFECT_FADE = 1

    /** @see setTextWithEffect */
    const val TEXT_EFFECT_SLIDE = 2

    /** @see setTextWithEffect */
    const val TEXT_EFFECT_TYPEWRITING = 3
}