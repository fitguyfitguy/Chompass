package app.chompass.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.chompass.R
import app.chompass.ui.components.FudGlassSurface
import app.chompass.ui.navigation.BottomNavScrollPadding
import app.chompass.ui.theme.AppColors
import app.chompass.ui.theme.warning
import app.chompass.ui.theme.AppRadii
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width

@Composable
fun CalculationMethodsScreen(
    onBack: () -> Unit
) {
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                top = 14.dp,
                bottom = BottomNavScrollPadding
            ),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onBack() }
                            .padding(horizontal = 2.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            tint = AppColors.Calorie,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.nav_settings), color = AppColors.Calorie, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            item {
                Text(
                    stringResource(R.string.settings_calc_methods),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.settings_calc_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.62f)
                )
            }

            item {
                CalcMethodSection(stringResource(R.string.settings_calc_sec_bmr)) {
                    CalcFormulaCard(
                        name = stringResource(R.string.settings_calc_mifflin_name),
                        usedWhen = stringResource(R.string.settings_calc_mifflin_used),
                        formula = stringResource(R.string.settings_calc_mifflin_formula),
                        citation = "Mifflin MD, St Jeor ST, et al. (1990). \"A new predictive equation for resting energy expenditure in healthy individuals.\" Am J Clin Nutr 51(2):241–247.",
                        url = "https://pubmed.ncbi.nlm.nih.gov/2305711/"
                    )
                    CalcFormulaCard(
                        name = stringResource(R.string.settings_calc_katch_name),
                        usedWhen = stringResource(R.string.settings_calc_katch_used),
                        formula = stringResource(R.string.settings_calc_katch_formula),
                        citation = "McArdle WD, Katch FI, Katch VL. Exercise Physiology: Nutrition, Energy, and Human Performance, 7th ed. Lippincott Williams & Wilkins, 2010.",
                        url = null
                    )
                }
            }

            item {
                CalcMethodSection(stringResource(R.string.settings_calc_sec_tdee)) {
                    CalcFormulaCard(
                        name = stringResource(R.string.settings_calc_tdee_name),
                        usedWhen = stringResource(R.string.settings_calc_tdee_used),
                        formula = stringResource(R.string.settings_calc_tdee_formula),
                        citation = "Standard PAL (Physical Activity Level) coefficients from FAO/WHO/UNU joint expert consultation on human energy requirements (2001). Also widely used by ACSM and USDA Dietary Guidelines.",
                        url = "https://www.fao.org/3/y5686e/y5686e00.htm"
                    )
                }
            }

            item {
                CalcMethodSection(stringResource(R.string.settings_calc_calorie_target)) {
                    CalcFormulaCard(
                        name = stringResource(R.string.settings_calc_target_name),
                        usedWhen = stringResource(R.string.settings_calc_target_used),
                        formula = stringResource(R.string.settings_calc_target_formula),
                        citation = "Hall KD, et al. (2011). \"Quantification of the effect of energy imbalance on bodyweight.\" Lancet 378(9793):826–837. The classic 3,500-kcal-per-pound rule originates from Wishnofsky M (1958), Am J Clin Nutr 6:542–546.",
                        url = "https://www.thelancet.com/journals/lancet/article/PIIS0140-6736(11)60812-X/fulltext"
                    )
                }
            }

            item {
                CalcMethodSection(stringResource(R.string.settings_calc_macro_split)) {
                    CalcFormulaCard(
                        name = stringResource(R.string.settings_calc_split_name),
                        usedWhen = stringResource(R.string.settings_calc_split_used),
                        formula = stringResource(R.string.settings_calc_split_formula),
                        citation = "Morton RW, et al. (2018). \"A systematic review, meta-analysis and meta-regression of the effect of protein supplementation on resistance training-induced gains in muscle mass and strength.\" Br J Sports Med 52(6):376–384.",
                        url = "https://bjsm.bmj.com/content/52/6/376"
                    )
                }
            }

            item {
                CalcMethodSection(stringResource(R.string.settings_calc_micro_values)) {
                    CalcFormulaCard(
                        name = stringResource(R.string.settings_calc_micro_name),
                        usedWhen = stringResource(R.string.settings_calc_micro_used),
                        formula = null,
                        citation = "Estimates rely on the underlying AI model's training data (USDA FoodData Central, manufacturer panels, scientific literature). Accuracy varies by food, portion-size visibility, and provider model. Always cross-check labels for foods you log frequently.",
                        url = "https://fdc.nal.usda.gov/"
                    )
                }
            }

            item {
                CalcMethodSection(stringResource(R.string.settings_calc_sec_home_gauge)) {
                    CalcFormulaCard(
                        name = stringResource(R.string.settings_calc_home_gauge_name),
                        usedWhen = stringResource(R.string.settings_calc_home_gauge_used),
                        formula = stringResource(R.string.settings_calc_home_gauge_formula),
                        citation = "PAL multipliers follow FAO/WHO physical activity levels; moderate 1.465 is an app-specific gradation.",
                        url = null
                    )
                }
            }

            item {
                CalcMethodSection(stringResource(R.string.settings_calc_sec_forecast)) {
                    CalcFormulaCard(
                        name = stringResource(R.string.settings_calc_forecast_name),
                        usedWhen = stringResource(R.string.settings_calc_forecast_used),
                        formula = stringResource(R.string.settings_calc_forecast_formula),
                        citation = "Hall KD, et al. (2011). \"Quantification of the effect of energy imbalance on bodyweight.\" Lancet 378(9793):826–837.",
                        url = "https://www.thelancet.com/journals/lancet/article/PIIS0140-6736(11)60812-X/fulltext"
                    )
                    CalcFormulaCard(
                        name = stringResource(R.string.settings_calc_adaptive_name),
                        usedWhen = stringResource(R.string.settings_calc_adaptive_used),
                        formula = stringResource(R.string.settings_calc_adaptive_formula),
                        citation = "Adaptive nudge limits are app policy guardrails, not a published clinical protocol. Energy conversion uses the same 7,700 kcal/kg constant as goal pacing.",
                        url = null
                    )
                }
            }

            item {
                CalcMethodSection(stringResource(R.string.settings_calc_sec_body)) {
                    CalcFormulaCard(
                        name = stringResource(R.string.settings_calc_body_name),
                        usedWhen = stringResource(R.string.settings_calc_body_used),
                        formula = stringResource(R.string.settings_calc_body_formula),
                        citation = "Hodgdon JA, Beckett MB. (1984). \"Prediction of percent body fat for U.S. Navy men and women from body circumferences and height.\" Report No. 84–29, Naval Health Research Center.",
                        url = null
                    )
                }
            }

            item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(AppRadii.Field))
                        .background(MaterialTheme.colorScheme.warning.copy(alpha = 0.09f))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        stringResource(R.string.settings_not_medical_title),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        stringResource(R.string.settings_not_medical_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

@Composable
internal fun CalcMethodSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 4.dp)
        )
        content()
    }
}

@Composable
internal fun CalcFormulaCard(
    name: String,
    usedWhen: String,
    formula: String?,
    citation: String,
    url: String?
) {
    val uriHandler = LocalUriHandler.current
    FudGlassSurface(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = AppRadii.Container,
        padding = 14.dp,
        allowBlur = false
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                usedWhen,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f)
            )
            if (formula != null) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                        .padding(10.dp)
                ) {
                    Text(
                        formula,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "SOURCE",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                )
                Text(
                    citation,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                if (url != null) {
                    Text(
                        "Open source ↗",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = AppColors.Calorie,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { uriHandler.openUri(url) }
                            .padding(vertical = 2.dp)
                    )
                }
            }
        }
    }
}
