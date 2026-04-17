package app.simple.felicity.viewmodels.panels

import android.app.Application
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import app.simple.felicity.extensions.viewmodels.WrappedViewModel
import app.simple.felicity.shared.storage.RemovableStorageDetector
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/**
 * ViewModel powering [app.simple.felicity.ui.subpanels.PathPickerFragment].
 *
 * It starts by listing all storage roots on the device (internal + any SD cards),
 * then lets the user drill down into folders. Only folders and audio files that
 * Felicity can actually play are shown — no random JPEGs or PDFs cluttering the list.
 *
 * @author Hamza417
 */
@HiltViewModel
class PathPickerViewModel @Inject constructor(
        application: Application
) : WrappedViewModel(application) {

    /**
     * Every audio format Felicity can open. Files with any other extension are hidden
     * so the list stays clean and useful, not a dumping ground for random files.
     */
    private val supportedAudioExtensions = setOf(
            "mp3", "flac", "ogg", "opus", "wav", "aac", "m4a", "wma",
            "ape", "alac", "aiff", "aif", "dsf", "dsd", "mka", "webm",
            "3gp", "amr", "mid", "midi", "xm", "mod", "s3m", "it"
    )

    /** The currently displayed list of items (folders + supported audio files). */
    val items: MutableLiveData<List<PathItem>> = MutableLiveData(emptyList())

    /** The path the user is currently browsing. Null means we're at the root storage list. */
    val currentPath: MutableLiveData<String?> = MutableLiveData(null)

    /**
     * Keeps track of where the user has been so the back button can take them up a level.
     * Think of it like a browser's back stack.
     */
    private val pathStack: ArrayDeque<String?> = ArrayDeque()

    init {
        loadStorageRoots()
    }

    /**
     * Load the top-level storage devices (internal storage + any SD cards).
     * This is the first thing the user sees when the picker opens.
     */
    private fun loadStorageRoots() {
        viewModelScope.launch(Dispatchers.IO) {
            val roots = mutableListOf<PathItem>()
            val removableVolumes = RemovableStorageDetector.getAllStorageVolumes(context)
            removableVolumes.forEach { storageInfo ->
                val path = storageInfo.path() ?: return@forEach
                if (!path.exists() || !path.canRead()) return@forEach

                roots.add(
                        PathItem(
                                file = path,
                                isDirectory = true,
                                displayName = storageInfo.description() ?: path.name
                        )
                )
            }

            currentPath.postValue(null)
            items.postValue(roots)
        }
    }

    /**
     * Navigate into the given folder and load its contents.
     * The current path is pushed onto the back stack before navigating.
     */
    fun navigateTo(path: String) {
        pathStack.addLast(currentPath.value)
        loadDirectory(path)
    }

    /**
     * Go back to the previous directory, or back to the storage root list if we're
     * already at a top-level path. Returns true if we went back, false if there's nowhere left to go.
     */
    fun navigateBack(): Boolean {
        if (pathStack.isEmpty()) return false
        val previous = pathStack.removeLast()
        if (previous == null) {
            loadStorageRoots()
        } else {
            loadDirectory(previous)
        }
        return true
    }

    /**
     * Check if we're at the very top (the storage root list) — at this level
     * the back button should close the fragment rather than go up a directory.
     */
    fun isAtRoot(): Boolean {
        return pathStack.isEmpty()
    }

    /**
     * Load the files and folders inside the given directory path.
     * Only shows folders and audio files the app can actually play.
     */
    private fun loadDirectory(path: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val dir = File(path)
            if (!dir.exists() || !dir.canRead()) {
                items.postValue(emptyList())
                currentPath.postValue(path)
                return@launch
            }

            val contents = dir.listFiles() ?: emptyArray()
            val result = contents
                .filter { file ->
                    when {
                        // Always show directories so the user can keep drilling down.
                        file.isDirectory -> !file.name.startsWith(".")
                        // Only show audio files we actually support.
                        file.isFile -> file.extension.lowercase() in supportedAudioExtensions
                        else -> false
                    }
                }
                .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                .map { file ->
                    PathItem(
                            file = file,
                            isDirectory = file.isDirectory,
                            displayName = file.name
                    )
                }

            currentPath.postValue(path)
            items.postValue(result)
        }
    }

    /**
     * Represents a single row in the path picker list — could be a folder or an audio file.
     */
    data class PathItem(
            val file: File,
            val isDirectory: Boolean,
            val displayName: String
    )
}

