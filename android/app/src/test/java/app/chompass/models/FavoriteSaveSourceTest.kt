package app.chompass.models

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * #126: a fresh barcode or photo review can be saved without a diary row.
 * Other sources stay out so a text log, a database pick, or a saved-meal
 * relog does not grow a second save path by accident.
 */
class FavoriteSaveSourceTest {
    @Test
    fun barcodeAndPhotoReviewsCanSaveWithoutLogging() {
        assertTrue(FoodSource.BARCODE.offersFavoriteSave())
        assertTrue(FoodSource.SNAP_FOOD.offersFavoriteSave())
    }

    @Test
    fun otherSourcesDoNotOfferFavoriteSave() {
        assertFalse(FoodSource.NUTRITION_LABEL.offersFavoriteSave())
        assertFalse(FoodSource.TEXT_INPUT.offersFavoriteSave())
        assertFalse(FoodSource.MANUAL.offersFavoriteSave())
        assertFalse(FoodSource.SEARCH.offersFavoriteSave())
        assertFalse(FoodSource.GROUNDED.offersFavoriteSave())
        assertTrue(
            FoodSource.entries.count { it.offersFavoriteSave() } == 2,
        )
    }
}
