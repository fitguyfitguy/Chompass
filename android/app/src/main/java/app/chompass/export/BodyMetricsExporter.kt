package app.chompass.export

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
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
 */
object BodyMetricsExporter {
    const val APP_NAME = "Chompass"
    const val KIND = "body_metrics"
    const val FORMAT_VERSION = "1.0"

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

    /** Returns (filename, content) or null when there is nothing to export. */
    fun build(
        weights: List<WeightEntry>,
        bodyFats: List<BodyFatEntry>,
        measurements: List<BodyMeasurement>,
        format: BodyMetricsFormat,
    ): Pair<String, String>? {
        if (weights.isEmpty() && bodyFats.isEmpty() && measurements.none { it.hasAnyValue }) return null
        val content = when (format) {
            BodyMetricsFormat.JSON -> json(weights, bodyFats, measurements)
            BodyMetricsFormat.CSV -> csv(weights, bodyFats, measurements)
        }
        return "Chompass-Body-Metrics.${format.ext}" to content
    }

    // --- CSV (long format, backwards compatible with the 1.7 export) ---

    private fun csv(
        weights: List<WeightEntry>,
        bodyFats: List<BodyFatEntry>,
        measurements: List<BodyMeasurement>,
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
    @Serializable private data class Doc(
        val export: MetaDto,
        val weights: List<WeightDto>,
        val body_fat: List<BodyFatDto>,
        val measurements: List<MeasurementDto>,
    )

    private val jsonPretty = Json { prettyPrint = true }

    private fun json(
        weights: List<WeightEntry>,
        bodyFats: List<BodyFatEntry>,
        measurements: List<BodyMeasurement>,
    ): String {
        val doc = Doc(
            export = MetaDto(APP_NAME, KIND, FORMAT_VERSION),
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
        )
        return jsonPretty.encodeToString(Doc.serializer(), doc)
    }
}
