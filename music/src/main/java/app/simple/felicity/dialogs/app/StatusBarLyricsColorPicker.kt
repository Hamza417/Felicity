package app.simple.felicity.dialogs.app

import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import app.simple.felicity.R
import app.simple.felicity.databinding.DialogStatusBarLyricsColorPickerBinding
import app.simple.felicity.extensions.dialogs.ScopedDialogFragment
import app.simple.felicity.preferences.StatusBarLyricsPreferences
import app.simple.felicity.theme.managers.ThemeManager
import app.simple.inure.decorations.colorpicker.ColorPickerView
import kotlin.math.roundToInt

/**
 * Colour editor for the floating status bar lyrics. Can target either the lyric
 * text colour or the outline colour; the target is stored in arguments so it survives
 * process recreation.
 *
 * The [ColorPickerView] wheel only provides hue and saturation (its HSV value is
 * hard-coded to 1), so two sliders supply the missing brightness and alpha. Brightness
 * 0 gives true black and alpha 0 gives full transparency, neither of which the wheel
 * can reach on its own. Every change writes straight to [StatusBarLyricsPreferences]
 * so the overlay service, which observes the same preference, repaints the lyric live.
 */
class StatusBarLyricsColorPicker : ScopedDialogFragment() {

    private var _binding: DialogStatusBarLyricsColorPickerBinding? = null
    private val binding get() = _binding!!

    private var onColorChanged: ((Int) -> Unit)? = null

    private val target: Target by lazy {
        Target.fromValue(arguments?.getInt(ARG_TARGET, Target.TEXT.value) ?: Target.TEXT.value)
    }

    // Current colour kept as HSV + alpha because the wheel only reports hue/saturation.
    private var hue = 0f
    private var saturation = 0f
    private var brightness = 1f
    private var alpha = 255

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogStatusBarLyricsColorPickerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.colorTitle.setText(target.titleRes)

        val accentTint = ColorStateList.valueOf(ThemeManager.accent.primaryAccentColor)
        binding.brightnessSeekBar.progressTintList = accentTint
        binding.brightnessSeekBar.thumbTintList = accentTint
        binding.opacitySeekBar.progressTintList = accentTint
        binding.opacitySeekBar.thumbTintList = accentTint

        syncFromColor(readColor())

        // Seed the wheel with a full-brightness version: its pointer only depends on
        // hue/saturation, and setColor() does not fire the listener (no premature write).
        binding.colorPicker.setColor(Color.HSVToColor(floatArrayOf(hue, saturation, 1f)), false)

        binding.colorPicker.setColorListener { color, _, fromUser ->
            if (fromUser) {
                val hsv = FloatArray(3)
                Color.colorToHSV(color, hsv)
                hue = hsv[0]
                saturation = hsv[1]
                commitColor()
            }
        }

        binding.brightnessSeekBar.setOnSeekBarChangeListener(simpleSeekBarListener { progress ->
            brightness = progress / 100f
            updateValueLabels()
            commitColor()
        })

        binding.opacitySeekBar.setOnSeekBarChangeListener(simpleSeekBarListener { progress ->
            alpha = (progress * 255f / 100f).roundToInt().coerceIn(0, 255)
            updateValueLabels()
            commitColor()
        })

        binding.colorDone.setOnClickListener { dismiss() }
    }

    /** Rebuilds all four components from a stored ARGB colour without firing listeners. */
    private fun syncFromColor(color: Int) {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        hue = hsv[0]
        saturation = hsv[1]
        brightness = hsv[2]
        alpha = Color.alpha(color)

        binding.brightnessSeekBar.progress = (brightness * 100f).roundToInt()
        binding.opacitySeekBar.progress = (alpha * 100f / 255f).roundToInt()
        updateValueLabels()
        render(color)
    }

    private fun updateValueLabels() {
        binding.brightnessValue.text = "${(brightness * 100f).roundToInt()} %"
        binding.opacityValue.text = "${(alpha * 100f / 255f).roundToInt()} %"
    }

    private fun commitColor() {
        val color = Color.HSVToColor(alpha, floatArrayOf(hue, saturation, brightness))
        writeColor(color)
        render(color)
        onColorChanged?.invoke(color)
    }

    private fun readColor(): Int = when (target) {
        Target.TEXT -> StatusBarLyricsPreferences.getTextColor()
        Target.STROKE -> StatusBarLyricsPreferences.getStrokeColor()
    }

    private fun writeColor(color: Int) = when (target) {
        Target.TEXT -> StatusBarLyricsPreferences.setTextColor(color)
        Target.STROKE -> StatusBarLyricsPreferences.setStrokeColor(color)
    }

    private fun relevantKey(key: String?): Boolean = when (target) {
        Target.TEXT -> key == StatusBarLyricsPreferences.TEXT_COLOR
        Target.STROKE -> key == StatusBarLyricsPreferences.STROKE_COLOR
    }

    private fun render(color: Int) {
        binding.colorPreview.setBackgroundColor(color)
        binding.colorHex.text = StatusBarLyricsPreferences.colorHex(color)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (relevantKey(key) && _binding != null) {
            syncFromColor(readColor())
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.colorPicker.setColorListener { _, _, _ -> }
        binding.brightnessSeekBar.setOnSeekBarChangeListener(null)
        binding.opacitySeekBar.setOnSeekBarChangeListener(null)
        _binding = null
    }

    private fun simpleSeekBarListener(onProgress: (Int) -> Unit): SeekBar.OnSeekBarChangeListener {
        return object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) onProgress(progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        }
    }

    enum class Target(val value: Int, val titleRes: Int) {
        TEXT(0, R.string.status_bar_lyrics_text_color),
        STROKE(1, R.string.status_bar_lyrics_stroke_color);

        companion object {
            fun fromValue(value: Int): Target = entries.firstOrNull { it.value == value } ?: TEXT
        }
    }

    companion object {
        private const val ARG_TARGET = "target"

        fun newInstance(target: Target = Target.TEXT, onColorChanged: ((Int) -> Unit)? = null): StatusBarLyricsColorPicker {
            return StatusBarLyricsColorPicker().apply {
                arguments = Bundle().apply { putInt(ARG_TARGET, target.value) }
                this.onColorChanged = onColorChanged
            }
        }

        const val TAG = "StatusBarLyricsColorPicker"
    }
}
