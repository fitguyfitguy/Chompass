package app.chompass.export

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import app.chompass.models.BodyFatEntry
import app.chompass.models.BodyMeasurement
import app.chompass.models.WeightEntry
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

enum class BodyMetricsSourceFormat { NOFUD_JSON, NOFUD_CSV, OPENSCALE_CSV, GENERIC_CSV }

sealed class BodyMetricsImportResult {
    data class Success(
        val weights: List<WeightEntry>,
        val bodyFats: List<BodyFatEntry>,
        val measurements: List<BodyMeasurement>,
        val sourceFormat: BodyMetricsSourceFormat,
    ) : BodyMetricsImportResult()
    object EmptyPayload : BodyMetricsImportResult()
    object UnsupportedFormat : BodyMetricsImportResult()
    data class Malformed(val reason: String) : BodyMetricsImportResult()
}

/**
 * Parses weight / body-composition files into local entries. Mirrors [DiaryImporter].
 *
 * Accepted formats, sniffed in order:
 *  1. Chompass body-metrics JSON (the [BodyMetricsExporter] JSON output)
 *  2. Chompass long CSV `metric,timestamp,value,unit` (incl. the legacy 1.7 export)
 *  3. openScale CSV (header has `dateTime` + `weight` columns)
 *  4. Generic weight CSV (a date column + a weight column, MyFitnessPal-style)
 *
 * Entries without an id in the file get a deterministic id derived from the
 * metric + timestamp (never the value), so re-importing the same file — or a
 * corrected version of it — upserts in place instead of duplicating.
 */
object BodyMetricsImporter {

    private const val MIN_WEIGHT_KG = 20.0
    private const val MAX_WEIGHT_KG = 500.0
    private const val MIN_BODY_FAT_PERCENT = 1.0
    private const val MAX_BODY_FAT_PERCENT = 70.0
    private const val LB_TO_KG = 0.45359237

    private val parser = Json { ignoreUnknownKeys = true; isLenient = true }
    private val openScaleFmt = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")

    fun parse(
        text: String,
        zone: ZoneId = ZoneId.systemDefault(),
        weightUnitHint: String = "kg",
    ): BodyMetricsImportResult {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return BodyMetricsImportResult.EmptyPayload
        if (trimmed.startsWith("{")) return parseNoFudJson(trimmed)

        val lines = trimmed.lines().map { it.trimEnd('\r') }.filter { it.isNotBlank() }
        if (lines.isEmpty()) return BodyMetricsImportResult.EmptyPayload
        val header = csvParseLine(lines.first()).map { it.trim().lowercase() }
        return when {
            header == listOf("metric", "timestamp", "value", "unit") ->
                parseNoFudCsv(lines.drop(1), zone)
            header.contains("datetime") && header.contains("weight") ->
                parseOpenScaleCsv(header, lines.drop(1), zone)
            header.any { it.startsWith("date") } && header.any { it.contains("weight") } ->
                parseGenericCsv(header, lines.drop(1), zone, weightUnitHint)
            else -> BodyMetricsImportResult.UnsupportedFormat
        }
    }

    // --- deterministic ids (value never in the seed, matching WeightRepository.externalId) ---

    private fun fileId(kind: String, time: Instant): UUID =
        UUID.nameUUIDFromBytes("file-$kind:${time.toEpochMilli()}".toByteArray())

    private fun weightOk(kg: Double): Boolean = kg.isFinite() && kg in MIN_WEIGHT_KG..MAX_WEIGHT_KG
    private fun bodyFatOk(percent: Double): Boolean =
        percent.isFinite() && percent in MIN_BODY_FAT_PERCENT..MAX_BODY_FAT_PERCENT
    private fun circumferenceOk(cm: Double): Boolean = cm.isFinite() && cm > 0 && cm < 500

    private fun success(
        weights: List<WeightEntry>,
        bodyFats: List<BodyFatEntry>,
        measurements: List<BodyMeasurement>,
        format: BodyMetricsSourceFormat,
    ): BodyMetricsImportResult =
        if (weights.isEmpty() && bodyFats.isEmpty() && measurements.isEmpty()) {
            BodyMetricsImportResult.EmptyPayload
        } else {
            BodyMetricsImportResult.Success(
                weights.sortedBy { it.date },
                bodyFats.sortedBy { it.date },
                measurements.sortedBy { it.date },
                format,
            )
        }

    // --- 1. Chompass JSON ---

    private fun parseNoFudJson(text: String): BodyMetricsImportResult {
        val root = try {
            parser.parseToJsonElement(text) as? JsonObject
                ?: return BodyMetricsImportResult.UnsupportedFormat
        } catch (t: Throwable) {
            return BodyMetricsImportResult.Malformed(t.localizedMessage ?: "Invalid JSON")
        }
        val export = root["export"] as? JsonObject ?: return BodyMetricsImportResult.UnsupportedFormat
        val app = export["app"].asString().orEmpty().trim().lowercase()
        if (app != "chompass" && app != "nofud" && app != "fud ai") return BodyMetricsImportResult.UnsupportedFormat
        if (export["kind"].asString() != BodyMetricsExporter.KIND) return BodyMetricsImportResult.UnsupportedFormat
        if (export["format_version"].asString() != BodyMetricsExporter.FORMAT_VERSION) {
            return BodyMetricsImportResult.UnsupportedFormat
        }

        val weights = mutableListOf<WeightEntry>()
        for (el in root["weights"].asArray()) {
            val obj = el as? JsonObject ?: return BodyMetricsImportResult.Malformed("Invalid weight entry")
            val date = obj["date"].asInstant() ?: return BodyMetricsImportResult.Malformed("Invalid weight date")
            val kg = obj["weight_kg"].asDouble() ?: return BodyMetricsImportResult.Malformed("Missing weight_kg")
            if (!weightOk(kg)) continue
            weights += WeightEntry(id = obj["id"].asUuid() ?: fileId("weight", date), date = date, weightKg = kg)
        }
        val bodyFats = mutableListOf<BodyFatEntry>()
        for (el in root["body_fat"].asArray()) {
            val obj = el as? JsonObject ?: return BodyMetricsImportResult.Malformed("Invalid body-fat entry")
            val date = obj["date"].asInstant() ?: return BodyMetricsImportResult.Malformed("Invalid body-fat date")
            val percent = obj["body_fat_percent"].asDouble()
                ?: return BodyMetricsImportResult.Malformed("Missing body_fat_percent")
            if (!bodyFatOk(percent)) continue
            bodyFats += BodyFatEntry(
                id = obj["id"].asUuid() ?: fileId("bodyfat", date),
                date = date,
                bodyFatFraction = percent / 100.0,
            )
        }
        val measurements = mutableListOf<BodyMeasurement>()
        for (el in root["measurements"].asArray()) {
            val obj = el as? JsonObject ?: return BodyMetricsImportResult.Malformed("Invalid measurement entry")
            val date = obj["date"].asInstant() ?: return BodyMetricsImportResult.Malformed("Invalid measurement date")
            fun cm(key: String): Double? = obj[key].asDouble()?.takeIf { circumferenceOk(it) }
            val m = BodyMeasurement(
                id = obj["id"].asUuid() ?: fileId("measure", date),
                date = date,
                neckCm = cm("neck_cm"), waistCm = cm("waist_cm"),
                hipsCm = cm("hips_cm"), chestCm = cm("chest_cm"),
                upperArmCm = cm("upper_arm_cm"), thighCm = cm("thigh_cm"),
                calfCm = cm("calf_cm"), wristCm = cm("wrist_cm"),
            )
            if (m.hasAnyValue) measurements += m
        }
        return success(weights, bodyFats, measurements, BodyMetricsSourceFormat.NOFUD_JSON)
    }

    // --- 2. Chompass long CSV (metric,timestamp,value,unit) ---

    private val csvSiteByMetric: Map<String, BodyMeasurement.Site> =
        BodyMetricsExporter.siteMetricNames.entries.associate { (site, name) -> name to site }
            // openScale/1.7 tolerance: singular/alias spellings
            .plus(mapOf("hip" to BodyMeasurement.Site.HIPS, "biceps" to BodyMeasurement.Site.UPPER_ARM))

    private fun parseNoFudCsv(rows: List<String>, zone: ZoneId): BodyMetricsImportResult {
        val weights = mutableListOf<WeightEntry>()
        val bodyFats = mutableListOf<BodyFatEntry>()
        // Site rows sharing a timestamp collapse into one snapshot, like the export wrote them.
        val measurementsByTime = LinkedHashMap<Instant, BodyMeasurement>()

        for ((index, line) in rows.withIndex()) {
            val cols = csvParseLine(line)
            if (cols.size < 4) return BodyMetricsImportResult.Malformed("Row ${index + 2}: expected 4 columns")
            val metric = cols[0].trim().lowercase()
            val time = parseTimestamp(cols[1].trim(), zone)
                ?: return BodyMetricsImportResult.Malformed("Row ${index + 2}: invalid timestamp")
            val value = cols[2].trim().toDoubleOrNull()
                ?: return BodyMetricsImportResult.Malformed("Row ${index + 2}: invalid value")
            val unit = cols[3].trim().lowercase()
            when (metric) {
                "weight" -> {
                    val kg = if (unit == "lb" || unit == "lbs") value * LB_TO_KG else value
                    if (weightOk(kg)) weights += WeightEntry(id = fileId("weight", time), date = time, weightKg = kg)
                }
                "body_fat" -> {
                    if (bodyFatOk(value)) {
                        bodyFats += BodyFatEntry(id = fileId("bodyfat", time), date = time, bodyFatFraction = value / 100.0)
                    }
                }
                else -> {
                    val site = csvSiteByMetric[metric] ?: continue
                    if (!circumferenceOk(value)) continue
                    val existing = measurementsByTime[time]
                        ?: BodyMeasurement(id = fileId("measure", time), date = time)
                    measurementsByTime[time] = existing.setting(site, value)
                }
            }
        }
        return success(
            weights, bodyFats,
            measurementsByTime.values.filter { it.hasAnyValue },
            BodyMetricsSourceFormat.NOFUD_CSV,
        )
    }

    // --- 3. openScale CSV ---

    private val openScaleSites: Map<String, BodyMeasurement.Site> = mapOf(
        "neck" to BodyMeasurement.Site.NECK,
        "waist" to BodyMeasurement.Site.WAIST,
        "hip" to BodyMeasurement.Site.HIPS,
        "chest" to BodyMeasurement.Site.CHEST,
        "biceps" to BodyMeasurement.Site.UPPER_ARM,
        "thigh" to BodyMeasurement.Site.THIGH,
        "calf" to BodyMeasurement.Site.CALF,
    )

    private fun parseOpenScaleCsv(header: List<String>, rows: List<String>, zone: ZoneId): BodyMetricsImportResult {
        val dateIdx = header.indexOf("datetime")
        val weightIdx = header.indexOf("weight")
        val fatIdx = header.indexOf("fat")
        val siteIdx = openScaleSites.mapValues { (name, _) -> header.indexOf(name) }
            .entries.associate { (name, idx) -> openScaleSites.getValue(name) to idx }

        val weights = mutableListOf<WeightEntry>()
        val bodyFats = mutableListOf<BodyFatEntry>()
        val measurements = mutableListOf<BodyMeasurement>()

        for ((index, line) in rows.withIndex()) {
            val cols = csvParseLine(line)
            fun col(i: Int): String? = cols.getOrNull(i)?.trim()?.takeIf { it.isNotEmpty() }
            val time = col(dateIdx)?.let { parseOpenScaleDate(it, zone) }
                ?: return BodyMetricsImportResult.Malformed("Row ${index + 2}: invalid dateTime")

            col(weightIdx)?.toDoubleOrNull()?.takeIf { weightOk(it) }?.let {
                weights += WeightEntry(id = fileId("weight", time), date = time, weightKg = it)
            }
            if (fatIdx >= 0) {
                col(fatIdx)?.toDoubleOrNull()?.takeIf { bodyFatOk(it) }?.let {
                    bodyFats += BodyFatEntry(id = fileId("bodyfat", time), date = time, bodyFatFraction = it / 100.0)
                }
            }
            var m = BodyMeasurement(id = fileId("measure", time), date = time)
            for ((site, idx) in siteIdx) {
                if (idx < 0) continue
                col(idx)?.toDoubleOrNull()?.takeIf { circumferenceOk(it) }?.let { m = m.setting(site, it) }
            }
            if (m.hasAnyValue) measurements += m
        }
        return success(weights, bodyFats, measurements, BodyMetricsSourceFormat.OPENSCALE_CSV)
    }

    private fun parseOpenScaleDate(raw: String, zone: ZoneId): Instant? =
        runCatching { LocalDateTime.parse(raw, openScaleFmt).atZone(zone).toInstant() }.getOrNull()
            ?: parseTimestamp(raw, zone)

    // --- 4. Generic weight CSV (date + weight columns; MyFitnessPal / SparkyFitness style) ---

    private fun parseGenericCsv(
        header: List<String>,
        rows: List<String>,
        zone: ZoneId,
        weightUnitHint: String,
    ): BodyMetricsImportResult {
        val dateIdx = header.indexOfFirst { it.startsWith("date") }
        val weightIdx = header.indexOfFirst { it.contains("weight") }
        val fatIdx = header.indexOfFirst { it.contains("body fat") || it.contains("body_fat") || it.contains("bodyfat") }
        val weightHeader = header[weightIdx]
        val poundsColumn = weightHeader.contains("(lb") || weightHeader.contains("lbs")
        val poundsHint = weightUnitHint.trim().lowercase().startsWith("lb")

        val weights = mutableListOf<WeightEntry>()
        val bodyFats = mutableListOf<BodyFatEntry>()

        for ((index, line) in rows.withIndex()) {
            val cols = csvParseLine(line)
            fun col(i: Int): String? = cols.getOrNull(i)?.trim()?.takeIf { it.isNotEmpty() }
            val time = col(dateIdx)?.let { parseTimestamp(it, zone) }
                ?: return BodyMetricsImportResult.Malformed("Row ${index + 2}: invalid date")
            val rawWeight = col(weightIdx)?.toDoubleOrNull() ?: continue
            val kg = when {
                poundsColumn -> rawWeight * LB_TO_KG
                weightHeader.contains("(kg") -> rawWeight
                poundsHint -> rawWeight * LB_TO_KG
                else -> rawWeight
            }
            if (weightOk(kg)) weights += WeightEntry(id = fileId("weight", time), date = time, weightKg = kg)
            if (fatIdx >= 0) {
                col(fatIdx)?.removeSuffix("%")?.trim()?.toDoubleOrNull()?.takeIf { bodyFatOk(it) }?.let {
                    bodyFats += BodyFatEntry(id = fileId("bodyfat", time), date = time, bodyFatFraction = it / 100.0)
                }
            }
        }
        return success(weights, bodyFats, emptyList(), BodyMetricsSourceFormat.GENERIC_CSV)
    }

    // --- shared parsing helpers ---

    /** ISO offset date-time, ISO instant, ISO local date-time, or a bare date (mapped to noon). */
    private fun parseTimestamp(raw: String, zone: ZoneId): Instant? {
        runCatching { return OffsetDateTime.parse(raw).toInstant() }
        runCatching { return Instant.parse(raw) }
        runCatching { return LocalDateTime.parse(raw).atZone(zone).toInstant() }
        runCatching { return LocalDate.parse(raw).atTime(12, 0).atZone(zone).toInstant() }
        // US-style date (MyFitnessPal exports), e.g. 07/09/2026
        runCatching {
            return LocalDate.parse(raw, DateTimeFormatter.ofPattern("MM/dd/yyyy"))
                .atTime(12, 0).atZone(zone).toInstant()
        }
        return null
    }

    /** Minimal quote-aware CSV field splitter (inverse of the exporter's csvEscape). */
    internal fun csvParseLine(line: String): List<String> {
        val out = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                inQuotes && ch == '"' && i + 1 < line.length && line[i + 1] == '"' -> { field.append('"'); i++ }
                ch == '"' -> inQuotes = !inQuotes
                ch == ',' && !inQuotes -> { out += field.toString(); field.clear() }
                else -> field.append(ch)
            }
            i++
        }
        out += field.toString()
        return out
    }

    private fun JsonElement?.asString(): String? = (this as? JsonPrimitive)?.contentOrNull
    private fun JsonElement?.asDouble(): Double? = (this as? JsonPrimitive)?.doubleOrNull
    private fun JsonElement?.asArray(): JsonArray = (this as? JsonArray) ?: JsonArray(emptyList())
    private fun JsonElement?.asUuid(): UUID? =
        asString()?.let { runCatching { UUID.fromString(it) }.getOrNull() }
    private fun JsonElement?.asInstant(): Instant? =
        asString()?.let { runCatching { Instant.parse(it) }.getOrNull() }
}
