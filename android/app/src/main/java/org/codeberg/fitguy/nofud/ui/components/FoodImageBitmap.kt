package org.codeberg.fitguy.nofud.ui.components

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.codeberg.fitguy.nofud.services.FoodImageStore

/** Loads a food-entry thumbnail off the composition thread. */
@Composable
fun rememberFoodThumbnail(
    imageFilename: String?,
    imageStore: FoodImageStore?,
): Bitmap? {
    val state = produceState<Bitmap?>(initialValue = null, imageFilename, imageStore) {
        val filename = imageFilename
        val store = imageStore
        value = if (filename == null || store == null) {
            null
        } else {
            withContext(Dispatchers.IO) { store.loadThumbnail(filename) }
        }
    }
    return state.value
}
