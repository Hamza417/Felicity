package app.simple.felicity.viewmodels.panels

import android.app.Application
import app.simple.felicity.extensions.viewmodels.WrappedViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ViewModel for the PreferenceSearch panel.
 *
 * Holds and persists the user's active search query across configuration changes
 * for the lifetime of the fragment. The query is used to filter the merged list
 * of all preference items in real time.
 *
 * @author Hamza417
 */
class PreferenceSearchViewModel(application: Application) : WrappedViewModel(application) {

    private val _searchQuery = MutableStateFlow("")

    /**
     * The current search query entered by the user. Collected by the fragment to
     * drive the preference filter without any debouncing since the preference list
     * is small and lives entirely in memory.
     */
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    /**
     * Updates the active search query.
     *
     * @param query the text typed by the user in the search field.
     */
    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }
}


