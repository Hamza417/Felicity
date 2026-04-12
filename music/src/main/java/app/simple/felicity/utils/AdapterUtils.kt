package app.simple.felicity.utils

import android.content.res.ColorStateList
import android.graphics.Typeface
import android.text.Spannable
import android.text.style.TextAppearanceSpan
import android.widget.TextView
import androidx.core.text.toSpannable
import app.simple.felicity.R
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.repository.models.Playlist
import app.simple.felicity.theme.managers.ThemeManager
import java.util.Locale

object AdapterUtils {
    fun TextView.addAudioQualityIcon(audio: Audio) {
        when (audio.audioQuality) {
            Audio.AUDIO_QUALITY_LQ -> {
                // setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, R.drawable.ic_lq_12dp, 0)
                setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0)
            }
            Audio.AUDIO_QUALITY_HQ -> {
                setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, R.drawable.ic_hq_12dp, 0)
            }
            Audio.AUDIO_QUALITY_LOSSLESS -> {
                setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, R.drawable.ic_lossless_12dp, 0)
            }
            Audio.AUDIO_QUALITY_HI_RES -> {
                setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, R.drawable.ic_hires_12dp, 0)
            }
            else -> {
                setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0)
            }
        }
    }

    fun TextView.addPlaylistTypeIcon(playlist: Playlist) {
        when {
            playlist.isM3UPlaylist -> {
                setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, R.drawable.ic_m3u_16dp, 0)
            }
            else -> {
                setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0)
            }
        }
    }

    /**
     * Highlights every case-insensitive occurrence of [searchKeyword] inside [textView]
     * using the current accent color in bold.
     *
     * Uses [String.indexOf] for substring scanning instead of regex compilation, which is
     * significantly faster when called repeatedly inside a RecyclerView adapter.
     * The [ColorStateList] is created once per call rather than once per match so that
     * object allocation stays constant regardless of how many occurrences are found.
     *
     * @param textView     the view whose current text will be spanned.
     * @param searchKeyword the substring to highlight; no-op when blank.
     */
    fun searchHighlighter(textView: TextView, searchKeyword: String) {
        if (searchKeyword.isBlank()) return
        val original = textView.text.toString()
        if (original.isEmpty()) return

        val spannable = original.toSpannable()
        val lowerOriginal = original.lowercase(Locale.getDefault())
        val lowerKeyword = searchKeyword.lowercase(Locale.getDefault())
        val accentColor = ColorStateList(arrayOf(intArrayOf()), intArrayOf(ThemeManager.accent.primaryAccentColor))

        var index = lowerOriginal.indexOf(lowerKeyword)
        while (index >= 0) {
            spannable.setSpan(
                    TextAppearanceSpan(null, Typeface.BOLD, -1, accentColor, null),
                    index, index + lowerKeyword.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            index = lowerOriginal.indexOf(lowerKeyword, index + lowerKeyword.length)
        }

        textView.text = spannable
    }

    /**
     * Highlights every occurrence of [searchKeyword] inside [textView] using the current
     * accent color in bold.
     *
     * When [ignoreCasing] is `true` the match is case-insensitive. Uses [String.indexOf]
     * for substring scanning and creates [ColorStateList] once per call rather than per match.
     *
     * @param textView      the view whose current text will be spanned.
     * @param searchKeyword the substring to highlight; no-op when blank.
     * @param ignoreCasing  whether the match should ignore letter case.
     */
    fun searchHighlighter(textView: TextView, searchKeyword: String, ignoreCasing: Boolean) {
        if (searchKeyword.isBlank()) return
        val original = textView.text.toString()
        if (original.isEmpty()) return

        val searchIn = if (ignoreCasing) original.lowercase(Locale.getDefault()) else original
        val searchFor = if (ignoreCasing) searchKeyword.lowercase(Locale.getDefault()) else searchKeyword

        val spannable = original.toSpannable()
        val accentColor = ColorStateList(arrayOf(intArrayOf()), intArrayOf(ThemeManager.accent.primaryAccentColor))

        var index = searchIn.indexOf(searchFor)
        while (index >= 0) {
            spannable.setSpan(
                    TextAppearanceSpan(null, Typeface.BOLD, -1, accentColor, null),
                    index, index + searchFor.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            index = searchIn.indexOf(searchFor, index + searchFor.length)
        }

        textView.text = spannable
    }

    fun TextView.setSelectedIndicator(isSelected: Boolean) {
        if (isSelected) {
            setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, R.drawable.ic_ring_12dp, 0)
        } else {
            setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0)
        }
    }
}