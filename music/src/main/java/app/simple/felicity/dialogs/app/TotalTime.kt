package app.simple.felicity.dialogs.app

import android.icu.text.MeasureFormat
import android.icu.util.Measure
import android.icu.util.MeasureUnit
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import app.simple.felicity.R
import app.simple.felicity.databinding.DialogTotalTimeBinding
import app.simple.felicity.extensions.dialogs.ScopedBottomSheetFragment
import app.simple.felicity.repository.constants.BundleConstants
import java.util.Locale
import java.util.concurrent.TimeUnit

class TotalTime : ScopedBottomSheetFragment() {

    private lateinit var binding: DialogTotalTimeBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = DialogTotalTimeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val totalTime = requireArguments().getLong(BundleConstants.TOTAL_TIME)
        val count = requireArguments().getInt(BundleConstants.COUNT)

        binding.text.text = buildString {
            append(getString(R.string.x_songs, count))
            append(" (")
            append(totalTime.toLocalizedExactDuration())
            append(")")
        }
    }

    /**
     * Converts milliseconds into a clean, macro-level readable string.
     * Example: "3 days 7 hours" or "2 hours 15 mins"
     */
    /**
     * Converts milliseconds into an exact localized string.
     * Example: "3d 7h 16m 44s" or "3 days 7 hours 16 mins 44 secs"
     */
    fun Long.toLocalizedExactDuration(): String {
        val days = TimeUnit.MILLISECONDS.toDays(this)
        val hours = TimeUnit.MILLISECONDS.toHours(this) % 24
        val minutes = TimeUnit.MILLISECONDS.toMinutes(this) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(this) % 60

        // FormatWidth.NARROW gives "3d", FormatWidth.SHORT gives "3 days" or "3 hrs"
        val formatter = MeasureFormat.getInstance(Locale.getDefault(), MeasureFormat.FormatWidth.WIDE)
        val parts = mutableListOf<String>()

        if (days > 0) parts.add(formatter.format(Measure(days, MeasureUnit.DAY)))
        if (hours > 0) parts.add(formatter.format(Measure(hours, MeasureUnit.HOUR)))
        if (minutes > 0) parts.add(formatter.format(Measure(minutes, MeasureUnit.MINUTE)))

        // Always add seconds if it's the only unit we have, or if we want absolute precision
        if (seconds > 0 || parts.isEmpty()) {
            parts.add(formatter.format(Measure(seconds, MeasureUnit.SECOND)))
        }

        return parts.joinToString(" ")
    }

    companion object {
        fun newInstance(totalTime: Long, count: Int): TotalTime {
            val args = Bundle()
            args.putLong(BundleConstants.TOTAL_TIME, totalTime)
            args.putInt(BundleConstants.COUNT, count)
            val fragment = TotalTime()
            fragment.arguments = args
            return fragment
        }

        fun FragmentManager.showTotalTime(totalTime: Long, count: Int) {
            val dialog = newInstance(totalTime, count)
            dialog.show(this, TAG)
        }

        private const val TAG = "TotalTimeDialog"
    }
}