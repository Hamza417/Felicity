package app.simple.felicity.models

import java.util.concurrent.atomic.AtomicLong

/**
 * Mutable representation of a single LRC lyric entry used exclusively within the
 * [app.simple.felicity.ui.panels.LrcEditor] while the user is editing.
 *
 * Each instance carries a stable [id] generated from an incrementing counter so
 * that [app.simple.felicity.adapters.ui.lists.AdapterLrcEditor] can distinguish
 * rows after insertions and deletions without relying on position alone.
 *
 * @property timestampMs The start time of this lyric line in milliseconds.
 * @property text        The lyric text for this line.
 * @property id          Immutable unique identifier assigned at construction time.
 *
 * @author Hamza417
 */
data class LrcEntryModel(
        var timestampMs: Long,
        var text: String,
        val id: Long = ID_COUNTER.getAndIncrement()
) {
    companion object {
        private val ID_COUNTER = AtomicLong(System.currentTimeMillis())
    }
}

