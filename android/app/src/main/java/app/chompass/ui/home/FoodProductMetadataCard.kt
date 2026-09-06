package app.chompass.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.chompass.R
import app.chompass.models.FoodProductMetadata
import app.chompass.ui.theme.AppColors

/**
 * Product information card for barcode entries (Open Food Facts enrichment):
 * package size, scores, allergens, labels and the ingredient list, plus the
 * CC BY-SA attribution line that opens the OFF product page. Rows render only
 * when the field is present; the barcode row always shows.
 */
@Composable
internal fun FoodProductMetadataCard(metadata: FoodProductMetadata) {
    val labelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f)
    val uriHandler = LocalUriHandler.current

    SheetPillCard {
        MetadataRow(stringResource(R.string.product_barcode), metadata.barcode, labelColor)
        metadata.packageQuantity?.let {
            SheetHairline()
            MetadataRow(stringResource(R.string.product_package), it, labelColor)
        }
        metadata.nutriScore?.let {
            SheetHairline()
            MetadataRow(stringResource(R.string.product_nutri_score), it, labelColor)
        }
        metadata.novaGroup?.let {
            SheetHairline()
            MetadataRow(stringResource(R.string.product_nova_group), it.toString(), labelColor)
        }
        metadata.ecoScore?.let {
            SheetHairline()
            MetadataRow(stringResource(R.string.product_eco_score), it, labelColor)
        }
        if (metadata.allergens.isNotEmpty()) {
            SheetHairline()
            MetadataRow(stringResource(R.string.product_allergens), metadata.allergens.joinToString(", "), labelColor)
        }
        if (metadata.traces.isNotEmpty()) {
            SheetHairline()
            MetadataRow(stringResource(R.string.product_may_contain), metadata.traces.joinToString(", "), labelColor)
        }
        if (metadata.labels.isNotEmpty()) {
            SheetHairline()
            MetadataRow(stringResource(R.string.product_labels), metadata.labels.joinToString(", "), labelColor)
        }
        if (metadata.categories.isNotEmpty()) {
            SheetHairline()
            MetadataRow(stringResource(R.string.product_categories), metadata.categories.joinToString(", "), labelColor)
        }
        metadata.ingredientsText?.let { ingredients ->
            SheetHairline()
            Column(Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
                Text(
                    stringResource(R.string.product_ingredient_label),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    ingredients,
                    fontSize = 14.sp,
                    color = labelColor,
                )
            }
        }
        SheetHairline()
        Text(
            stringResource(R.string.off_attribution),
            style = MaterialTheme.typography.labelSmall,
            color = AppColors.Calorie,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .clickable {
                    uriHandler.openUri("https://world.openfoodfacts.org/product/${metadata.barcode}")
                }
                .padding(horizontal = 18.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun MetadataRow(label: String, value: String, labelColor: androidx.compose.ui.graphics.Color) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            label,
            fontSize = 15.sp,
            color = labelColor,
            modifier = Modifier.weight(1f),
        )
        Text(
            value,
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1.6f),
        )
    }
}
