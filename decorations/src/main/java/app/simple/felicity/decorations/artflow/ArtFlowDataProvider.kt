package app.simple.felicity.decorations.artflow

import android.graphics.Bitmap

/**
 * Provider interface for ArtFlow to load artwork data.
 * This allows decoupling from URI-based loading and enables
 * custom implementations (e.g., file paths, resources, network, etc.)
 */
interface ArtFlowDataProvider {
    /**
     * Returns the total number of items in the carousel
     */
    fun getItemCount(): Int

    /**
     * Load the bitmap for the item at the given index.
     * This is called on a background thread.
     *
     * @param index The index of the item to load
     * @param maxDimension Maximum dimension (width/height) for the bitmap
     * @return The loaded bitmap, or null if loading fails
     */
    fun loadArtwork(index: Int, maxDimension: Int): Bitmap?

    /**
     * Optional: Get an identifier for the item at this index.
     * This is used in click callbacks to identify which item was clicked.
     *
     * @param index The index of the item
     * @return Any object that identifies this item (e.g., Uri, file path, ID, etc.)
     */
    fun getItemId(index: Int): Any? = null
}

