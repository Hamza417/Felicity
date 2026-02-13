package app.simple.felicity.utils

import android.widget.TextView
import app.simple.felicity.R
import app.simple.felicity.repository.models.Audio

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
                setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0)
            }
            Audio.AUDIO_QUALITY_HI_RES -> {
                setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, R.drawable.ic_hires_12dp, 0)
            }
            else -> {
                setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0)
            }
        }
    }
}