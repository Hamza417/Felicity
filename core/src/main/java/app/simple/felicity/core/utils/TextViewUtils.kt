package app.simple.felicity.core.utils

import android.graphics.Color
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
import androidx.core.widget.addTextChangedListener
import app.simple.felicity.core.R

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
                    textPaint.color = Color.parseColor("#2e86c1")

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
}