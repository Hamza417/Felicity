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

        if (this.text == text) return // No need to animate if the text is already the same

        activeAnimationJobs[this] = scope.launch {
            val current = this@setTypeWriting.text.toString()
            val builder = StringBuilder(current)

            // If no limit is provided, treat it as virtually infinite
            val limit = animateLimit ?: Int.MAX_VALUE
            var limitReached = false

            // Flip & Typewriter
            for (i in text.indices) {
                if (i >= limit) {
                    limitReached = true
                    break // Stop animating, jump to the end
                }

                delay(delayTime)

                if (i < builder.length) {
                    builder.setCharAt(i, text[i])
                } else {
                    builder.append(text[i])
                }
                this@setTypeWriting.text = builder.toString()
            }

            // Cleanup (Delete Extra Characters)
            if (!limitReached && current.length > text.length) {
                for (i in text.length until current.length) {
                    if (i >= limit) {
                        limitReached = true
                        break // Stop deleting one-by-one, jump to the end
                    }

                    delay(delayTime)

                    if (builder.isNotEmpty()) {
                        builder.deleteCharAt(builder.lastIndex)
                        this@setTypeWriting.text = builder.toString()
                    }
                }
            }

            // Set final text if we exited early due to limit
            if (limitReached) {
                // Instantly apply the full target text, bypassing any further delays
                this@setTypeWriting.text = text
            }
        }
    }
}