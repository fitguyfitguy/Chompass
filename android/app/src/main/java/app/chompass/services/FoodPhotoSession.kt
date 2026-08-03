package app.chompass.services

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * App-scoped staging for in-app camera / gallery food photos and the pre-Analyze
 * sheet. Survives Home composition dispose and theme remounts — unlike
 * `remember { }` flags that used to drop gallery results back to bare Home.
 *
 * Home opens a lightweight staging sheet so the user can add a label photo or tip
 * before the LLM call; Analyze starts the morphing Log sheet.
 * External share-ins use [app.chompass.AppContainer.sharedImageInbox] only; Home merges those
 * into this session. Do **not** write gallery picks into the share inbox.
 */
class FoodPhotoSession {
    private val _stagedImages = MutableStateFlow<List<ByteArray>>(emptyList())
    val stagedImages: StateFlow<List<ByteArray>> = _stagedImages.asStateFlow()

    private val _reviewOpen = MutableStateFlow(false)
    val reviewOpen: StateFlow<Boolean> = _reviewOpen.asStateFlow()

    /** True when photos came from the library (share or in-app gallery). */
    private val _importFromLibrary = MutableStateFlow(false)
    val importFromLibrary: StateFlow<Boolean> = _importFromLibrary.asStateFlow()

    /** Bumped when a gallery/share URI read yields no usable bytes (Home snackbar). */
    private val _importFailedTick = MutableStateFlow(0)
    val importFailedTick: StateFlow<Int> = _importFailedTick.asStateFlow()

    fun remainingSlots(): Int =
        (MAX_IMAGES - _stagedImages.value.size).coerceAtLeast(0)

    fun prepareFreshCameraCapture() {
        _stagedImages.value = emptyList()
        _reviewOpen.value = false
        _importFromLibrary.value = false
    }

    fun stageFromCamera(bytes: ByteArray) {
        if (bytes.isEmpty()) return
        _stagedImages.value = (_stagedImages.value + bytes).take(MAX_IMAGES)
        _importFromLibrary.value = false
        _reviewOpen.value = true
    }

    fun stageFromImport(images: List<ByteArray>) {
        if (images.isEmpty()) return
        _stagedImages.value = (_stagedImages.value + images).take(MAX_IMAGES)
        _importFromLibrary.value = true
        _reviewOpen.value = true
    }

    /** Share-sheet bytes merged the same way as an in-app gallery import. */
    fun mergeExternalShare(images: List<ByteArray>) = stageFromImport(images)

    fun removeAt(index: Int) {
        _stagedImages.value = _stagedImages.value.filterIndexed { i, _ -> i != index }
        if (_stagedImages.value.isEmpty()) {
            _reviewOpen.value = false
        }
    }

    fun openReviewIfStaged() {
        if (_stagedImages.value.isNotEmpty()) {
            _reviewOpen.value = true
        }
    }

    fun hideReviewKeepStaged() {
        _reviewOpen.value = false
    }

    fun clear() {
        _stagedImages.value = emptyList()
        _reviewOpen.value = false
        _importFromLibrary.value = false
    }

    fun signalImportFailed() {
        _importFailedTick.value = _importFailedTick.value + 1
    }

    companion object {
        const val MAX_IMAGES = 10
    }
}
