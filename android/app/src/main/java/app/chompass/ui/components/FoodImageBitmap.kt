package app.chompass.ui.components

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import app.chompass.services.FoodImageStore
import app.chompass.services.decodeSampledBitmap

/**
 * Decodes in-memory image bytes off the composition thread. Keyed on the array
 * identity, so a re-analysis of the same photo does not re-decode. Bounds-samples
 * to the stored-image cap (see [decodeSampledBitmap]), so a 48 MP share-in photo
 * never materializes as a ~192 MB bitmap for a 96–220 dp preview.
 */
@Composable
fun rememberDecodedBitmap(bytes: ByteArray?): Bitmap? {
    val state = produceState<Bitmap?>(initialValue = null, key1 = bytes) {
        value = if (bytes == null) {
            null
        } else {
            withContext(Dispatchers.Default) {
                decodeSampledBitmap(bytes)
            }
        }
    }
    return state.value
}

/** Loads a full-size stored food image off the composition thread. */
@Composable
fun rememberFoodImage(
    imageFilename: String?,
    imageStore: FoodImageStore?,
): Bitmap? {
    val state = produceState<Bitmap?>(initialValue = null, imageFilename, imageStore) {
        val filename = imageFilename
        val store = imageStore
        value = if (filename == null || store == null) {
            null
        } else {
            withContext(Dispatchers.IO) { store.load(filename) }
        }
    }
    return state.value
}

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
