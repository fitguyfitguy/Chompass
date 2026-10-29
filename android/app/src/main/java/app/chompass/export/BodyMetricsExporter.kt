package app.chompass.export

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import app.chompass.models.BodyCompositionEntry
import app.chompass.models.BodyFatEntry
import app.chompass.models.BodyMeasurement
import app.chompass.models.WeightEntry
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class BodyMetricsFormat(val label: String, val ext: String, val mime: String) {
    JSON("JSON", "json", "application/json"),
    CSV("CSV", "csv", "text/csv"),
}

/**
 * Builds a shareable weight / body-composition file (JSON / CSV) from the local
 * stores. Pure logic — mirrors [DiaryExporter]; the caller supplies the entries.
 *
 * CSV keeps the long `metric,timestamp,value,unit` format that 1.7 shipped, so
 * old exports and new ones parse through the same [BodyMetricsImporter] path.
 * JSON carries entry ids so a round-trip re-import upserts in place.
 *
 * Version stamps are conditional (same pattern as the diary's 1.6→1.7 and the
 * sync document's photo bump): the scale-composition array only exists when the
 * user has such rows, so a document without them stays byte-identical to the
 * 1.0 export and older importers keep working.
 */
object BodyMetricsExporter {
    const val APP_NAME = "Chompass"
    const val KIND = "body_metrics"
    const val FORMAT_VERSION = "1.0"

    /** Stamped only when the document carries scale composition rows. */
    const val FORMAT_VERSION_COMPOSITION = "1.1"

    /** Every version [BodyMetricsImporter] accepts. */
    val SUPPORTED_FORMAT_VERSIONS = setOf(FORMAT_VERSION, FORMAT_VERSION_COMPOSITION)

    private val csvTimeFmt: DateTimeFormatter =
        DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneId.systemDefault())

    /** metric-column name per circumference site, shared with the importer. */
    internal val siteMetricNames: Map<BodyMeasurement.Site, String> = mapOf(
        BodyMeasurement.Site.NECK to "neck",
        BodyMeasurement.Site.WAIST to "waist",
        BodyMeasurement.Site.HIPS to "hips",
        BodyMeasurement.Site.CHEST to "chest",
        BodyMeasurement.Site.UPPER_ARM to "upper_arm",
        BodyMeasurement.Site.THIGH to "thigh",
        BodyMeasurement.Site.CALF to "calf",
        BodyMeasurement.Site.WRIST to "wrist",
    )

    /** metric-column name per scale composition metric, shared with the importer. */
    internal val compositionMetricNames: Map<BodyCompositionEntry.CompositionMetric, String> = mapOf(
        BodyCompositionEntry.CompositionMetric.BONE_MASS to "bone_mass",
        BodyCompositionEntry.CompositionMetric.TOTAL_BODY_WATER to "total_body_water",
        BodyCompositionEntry.CompositionMetric.MUSCLE_MASS to "muscle_mass",
        BodyCompositionEntry.CompositionMetric.LEAN_MASS to "lean_mass",
        BodyCompositionEntry.CompositionMetric.VISCERAL_FAT to "visceral_fat",
    )

    /** CSV unit column for a composition metric — masses are kg, the rating is unitless. */
    internal fun compositionUnit(metric: BodyCompositionEntry.CompositionMetric): String =
        if (metric.isMass) "kg" else "index"

    /** Returns (filename, content) or null when there is nothing to export. */
    fun build(
        weights: List<WeightEntry>,
        bodyFats: List<BodyFatEntry>,
        measurements: List<BodyMeasurement>,
        compositions: List<BodyCompositionEntry> = emptyList(),
        format: BodyMetricsFormat,
    ): Pair<String, String>? {
        val scaleRows = compositions.filter { it.hasAnyValue }
        if (weights.isEmpty() && bodyFats.isEmpty() && measurements.none { it.hasAnyValue } &&
            scaleRows.isEmpty()
        ) {
            return null
        }
        val content = when (format) {
            BodyMetricsFormat.JSON -> json(weights, bodyFats, measurements, scaleRows)
            BodyMetricsFormat.CSV -> csv(weights, bodyFats, measurements, scaleRows)
        }
        return "Chompass-Body-Metrics.${format.ext}" to content
    }

    // --- CSV (long format, backwards compatible with the 1.7 export) ---

    private fun csv(
        weights: List<WeightEntry>,
        bodyFats: List<BodyFatEntry>,
        measurements: List<BodyMeasurement>,
        compositions: List<BodyCompositionEntry>,
    ): String {
        val rows = StringBuilder()
        rows.append("metric,timestamp,value,unit\n")
        fun row(metric: String, timestamp: String, value: Double, unit: String) {
            rows.append(metric).append(',')
                .append(csvEscape(timestamp)).append(',')
                .append(String.format(Locale.US, "%.2f", value)).append(',')
                .append(unit).append('\n')
        }
        weights.sortedBy { it.date }.forEach {
            row("weight", csvTimeFmt.format(it.date), it.weightKg, "kg")
        }
        bodyFats.sortedBy { it.date }.forEach {
            row("body_fat", csvTimeFmt.format(it.date), it.bodyFatPercent, "percent")
        }
        measurements.sortedBy { it.date }.forEach { m ->
            val ts = csvTimeFmt.format(m.date)
            BodyMeasurement.Site.values().forEach { site ->
                m.value(site)?.let { row(siteMetricNames.getValue(site), ts, it, "cm") }
            }
        }
        compositions.sortedBy { it.date }.forEach { c ->
            val ts = csvTimeFmt.format(c.date)
            BodyCompositionEntry.CompositionMetric.entries.forEach { metric ->
                c.value(metric)?.let {
                    row(compositionMetricNames.getValue(metric), ts, it, compositionUnit(metric))
                }
            }
        }
        return rows.toString()
    }

    private fun csvEscape(field: String): String =
        if (field.contains(',') || field.contains('"') || field.contains('\n')) {
            "\"" + field.replace("\"", "\"\"") + "\""
        } else field

    // --- JSON ---

    @Serializable private data class MetaDto(val app: String, val kind: String, val format_version: String)
    @Serializable private data class WeightDto(val id: String, val date: String, val weight_kg: Double)
    @Serializable private data class BodyFatDto(val id: String, val date: String, val body_fat_percent: Double)
    @Serializable private data class MeasurementDto(
        val id: String, val date: String,
        val neck_cm: Double? = null, val waist_cm: Double? = null,
        val hips_cm: Double? = null, val chest_cm: Double? = null,
        val upper_arm_cm: Double? = null, val thigh_cm: Double? = null,
        val calf_cm: Double? = null, val wrist_cm: Double? = null,
    )
    @Serializable private data class CompositionDto(
        val id: String, val date: String,
        val bone_mass_kg: Double? = null,
        val total_body_water_kg: Double? = null,
        val muscle_mass_kg: Double? = null,
        val lean_mass_kg: Double? = null,
        val visceral_fat_rating: Double? = null,
    )
    @Serializable private data class Doc(
        val export: MetaDto,
        val weights: List<WeightDto>,
        val body_fat: List<BodyFatDto>,
        val measurements: List<MeasurementDto>,
        /** Omitted entirely unless the export carries scale composition rows. */
        val body_composition: List<CompositionDto>? = null,
    )

    private val jsonPretty = Json { prettyPrint = true }

    private fun json(
        weights: List<WeightEntry>,
        bodyFats: List<BodyFatEntry>,
        measurements: List<BodyMeasurement>,
        compositions: List<BodyCompositionEntry>,
    ): String {
        val doc = Doc(
            export = MetaDto(
                APP_NAME,
                KIND,
                if (compositions.isEmpty()) FORMAT_VERSION else FORMAT_VERSION_COMPOSITION,
            ),
            weights = weights.sortedBy { it.date }.map {
                WeightDto(it.id.toString(), it.date.toString(), it.weightKg)
            },
            body_fat = bodyFats.sortedBy { it.date }.map {
                BodyFatDto(it.id.toString(), it.date.toString(), it.bodyFatPercent)
            },
            measurements = measurements.filter { it.hasAnyValue }.sortedBy { it.date }.map {
                MeasurementDto(
                    id = it.id.toString(), date = it.date.toString(),
                    neck_cm = it.neckCm, waist_cm = it.waistCm,
                    hips_cm = it.hipsCm, chest_cm = it.chestCm,
                    upper_arm_cm = it.upperArmCm, thigh_cm = it.thighCm,
                    calf_cm = it.calfCm, wrist_cm = it.wristCm,
                )
            },
            body_composition = compositions.takeIf { it.isNotEmpty() }?.sortedBy { it.date }?.map {
                CompositionDto(
                    id = it.id.toString(), date = it.date.toString(),
                    bone_mass_kg = it.boneMassKg,
                    total_body_water_kg = it.totalBodyWaterKg,
                    muscle_mass_kg = it.muscleMassKg,
                    lean_mass_kg = it.leanMassKg,
                    visceral_fat_rating = it.visceralFatRating,
                )
            },
        )
        return jsonPretty.encodeToString(Doc.serializer(), doc)
    }
}
