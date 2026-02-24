package app.simple.felicity.viewmodels.panels

import android.app.Application
import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.viewModelScope
import app.simple.felicity.extensions.viewmodels.WrappedViewModel
import app.simple.felicity.preferences.FoldersPreferences
import app.simple.felicity.repository.models.Folder
import app.simple.felicity.repository.repositories.AudioRepository
import app.simple.felicity.repository.sort.FolderSort.sorted
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FoldersViewModel @Inject constructor(
        application: Application,
        private val audioRepository: AudioRepository
) : WrappedViewModel(application) {

    private val _folders = MutableStateFlow<List<Folder>>(emptyList())
    val folders: StateFlow<List<Folder>> = _folders.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            audioRepository.getAllFoldersWithAggregation()
                .map { folderList -> folderList.sorted() }
                .distinctUntilChanged()
                .catch { exception ->
                    Log.e(TAG, "Error loading folders", exception)
                    emit(emptyList())
                }
                .flowOn(Dispatchers.IO)
                .collect { sortedFolders ->
                    _folders.value = sortedFolders
                    Log.d(TAG, "loadData: ${sortedFolders.size} folders loaded")
                }
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        super.onSharedPreferenceChanged(sharedPreferences, key)
        when (key) {
            FoldersPreferences.FOLDER_SORT_STYLE,
            FoldersPreferences.SORT_ORDER -> {
                Log.d(TAG, "onSharedPreferenceChanged: Sorting order changed, updating folders list")
                loadData()
            }
        }
    }

    companion object {
        private const val TAG = "FoldersViewModel"
    }
}

