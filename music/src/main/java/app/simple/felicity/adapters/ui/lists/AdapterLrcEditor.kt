package app.simple.felicity.adapters.ui.lists

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import app.simple.felicity.databinding.AdapterLrcEditorItemBinding
import app.simple.felicity.decorations.overscroll.VerticalListViewHolder
import app.simple.felicity.models.LrcEntryModel

/**
 * RecyclerView adapter for the LRC editor panel.
 *
 * Renders a list of [LrcEntryModel] entries, each as a row containing:
 *  - A clock stamp button that assigns the current player position as the timestamp.
 *  - An editable timestamp field displayed in `MM:SS.mmm` format.
 *  - An editable lyric text field that fills the remaining row width.
 *  - A delete button that removes the row from the list.
 *
 * All in-place text edits are reflected immediately in the underlying [LrcEntryModel]
 * objects so that the caller's list reference stays current without requiring full
 * list re-submissions. Structural changes (stamp, delete) should be dispatched back
 * to the adapter via [notifyItemChanged] or [notifyItemRemoved] from the host fragment.
 *
 * @param entries       The mutable list of entries being edited.
 * @param onStamp       Invoked when the clock button of the row at [Int] is tapped.
 * @param onDelete      Invoked when the delete button of the row at [Int] is tapped.
 *
 * @author Hamza417
 */
class AdapterLrcEditor(
        private val entries: MutableList<LrcEntryModel>,
        private val onStamp: (index: Int) -> Unit,
        private val onDelete: (index: Int) -> Unit
) : RecyclerView.Adapter<AdapterLrcEditor.EntryViewHolder>() {

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long = entries[position].id

    override fun getItemCount(): Int = entries.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EntryViewHolder {
        return EntryViewHolder(AdapterLrcEditorItemBinding.inflate(
                LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: EntryViewHolder, position: Int) {
        holder.bind(entries[position], position)
    }

    /**
     * Replaces the entire dataset with [newEntries] and notifies the adapter.
     * Used after structural operations such as paste or load that affect multiple rows.
     */
    fun submitList(newEntries: List<LrcEntryModel>) {
        entries.clear()
        entries.addAll(newEntries)
        notifyDataSetChanged()
    }

    inner class EntryViewHolder(
            private val binding: AdapterLrcEditorItemBinding
    ) : VerticalListViewHolder(binding.root) {

        /**
         * Text watcher installed on the timestamp [EditText].
         * Guards against re-entrant calls with [isBinding] so that programmatic text
         * updates during [bind] do not trigger model mutations.
         */
        private val timestampWatcher = object : TextWatcher {
            var isBinding = false
            var onChanged: ((String) -> Unit)? = null

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                if (!isBinding) onChanged?.invoke(s?.toString().orEmpty())
            }
        }

        /**
         * Text watcher installed on the lyric text [EditText].
         * Works identically to [timestampWatcher] but targets [LrcEntryModel.text].
         */
        private val textWatcher = object : TextWatcher {
            var isBinding = false
            var onChanged: ((String) -> Unit)? = null

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                if (!isBinding) onChanged?.invoke(s?.toString().orEmpty())
            }
        }

        init {
            binding.timestampField.addTextChangedListener(timestampWatcher)
            binding.textField.addTextChangedListener(textWatcher)
        }

        /**
         * Binds [entry] at [index] into the ViewHolder.
         * Timestamps are displayed in `MM:SS.mmm` format; changes are parsed back to milliseconds
         * when focus leaves the field.
         */
        fun bind(entry: LrcEntryModel, index: Int) {
            // Update timestamp field without triggering the watcher.
            timestampWatcher.isBinding = true
            binding.timestampField.setText(entry.timestampMs.toTimestampDisplay())
            timestampWatcher.isBinding = false

            // Update text field without triggering the watcher.
            textWatcher.isBinding = true
            binding.textField.setText(entry.text)
            textWatcher.isBinding = false

            // Re-wire watcher callbacks to the current entry instance.
            timestampWatcher.onChanged = { text ->
                entry.timestampMs = text.parseTimestampDisplay()
            }
            textWatcher.onChanged = { text ->
                entry.text = text
            }

            // Parse timestamp when the field loses focus so partial input is resolved.
            binding.timestampField.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) {
                    val parsed = binding.timestampField.text?.toString()?.parseTimestampDisplay() ?: 0L
                    entry.timestampMs = parsed
                    timestampWatcher.isBinding = true
                    binding.timestampField.setText(parsed.toTimestampDisplay())
                    timestampWatcher.isBinding = false
                }
            }

            binding.clockStamp.setOnClickListener {
                onStamp(bindingAdapterPosition)
            }

            binding.deleteButton.setOnClickListener {
                onDelete(bindingAdapterPosition)
            }
        }
    }

    companion object {

        /**
         * Formats [this] millisecond value as `MM:SS.mmm`.
         *
         * @return a string like `01:23.456`.
         */
        fun Long.toTimestampDisplay(): String {
            val ms = (this % 1000).coerceAtLeast(0)
            val totalSeconds = this / 1000
            val seconds = totalSeconds % 60
            val minutes = totalSeconds / 60
            return "%02d:%02d.%03d".format(minutes, seconds, ms)
        }

        /**
         * Parses a timestamp string in `MM:SS.mmm` (or `MM:SS`) format back to milliseconds.
         * Returns 0 if the input does not match the expected format.
         *
         * @return the parsed duration in milliseconds, or 0 on parse failure.
         */
        fun String.parseTimestampDisplay(): Long {
            return try {
                val colonIdx = indexOf(':')
                if (colonIdx < 0) return 0L
                val minutes = substring(0, colonIdx).toLong()
                val rest = substring(colonIdx + 1)
                val dotIdx = rest.indexOf('.')
                val seconds = if (dotIdx >= 0) rest.substring(0, dotIdx).toLong() else rest.toLong()
                val millis = if (dotIdx >= 0) {
                    rest.substring(dotIdx + 1).padEnd(3, '0').take(3).toLong()
                } else {
                    0L
                }
                (minutes * 60_000L + seconds * 1_000L + millis).coerceAtLeast(0L)
            } catch (_: NumberFormatException) {
                0L
            }
        }
    }
}

