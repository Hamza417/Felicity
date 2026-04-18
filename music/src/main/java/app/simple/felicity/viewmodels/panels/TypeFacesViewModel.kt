package app.simple.felicity.viewmodels.panels

import android.app.Application
import android.graphics.Typeface
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import app.simple.felicity.decorations.typeface.TypeFace
import app.simple.felicity.decorations.typeface.TypefaceStyle
import app.simple.felicity.extensions.viewmodels.WrappedViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * ViewModel for the TypeFaces preference panel.
 *
 * Loading fonts from assets is a heavy operation — doing it on the main thread
 * causes visible stutters. This ViewModel does all that heavy lifting in a
 * background thread so that by the time the list shows up on screen, every
 * font is already sitting warm and cozy in memory.
 *
 * When the panel is closed (and this ViewModel is cleared), we politely ask
 * those fonts to leave memory. No hoarding allowed.
 *
 * @author Hamza417
 */
class TypeFacesViewModel(application: Application) : WrappedViewModel(application) {

    /**
     * This map holds every pre-built typeface, keyed by "FontName-Weight"
     * (e.g. "Jost-700"). The adapter can pull typefaces from here instantly,
     * without touching the assets again.
     */
    private val _typefaceCache = MutableLiveData<Map<String, Typeface>>()

    /**
     * The publicly observable snapshot of the preloaded typeface map.
     * Observe this in the fragment to know when fonts are ready to roll.
     */
    val typefaceCache: LiveData<Map<String, Typeface>> = _typefaceCache

    init {
        preloadTypefaces()
    }

    /**
     * Kicks off the background loading of every font weight for every font
     * family listed in the typeface panel. This runs on the I/O dispatcher
     * so the UI thread stays buttery smooth.
     */
    private fun preloadTypefaces() {
        viewModelScope.launch(Dispatchers.IO) {
            // Build a fresh local map so we can publish it all at once when done.
            val result = mutableMapOf<String, Typeface>()

            TypeFace.list.forEach { model ->
                TypefaceStyle.entries.forEach { style ->
                    val typeface = TypeFace.getTypeFace(model.name, style.style, context)
                    val weight = style.name
                    result["${model.name}-$weight"] = typeface
                }
            }

            // Let observers know the feast is ready — all fonts loaded and waiting.
            _typefaceCache.postValue(result)
        }
    }

    /**
     * Called automatically when the panel is destroyed and this ViewModel is
     * no longer needed. We clean up the preloaded fonts from the global cache
     * here, because keeping them around would waste memory for no good reason.
     */
    override fun onCleared() {
        super.onCleared()
        TypeFace.clearAllPreloadedCache()
    }
}

