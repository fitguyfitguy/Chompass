package app.chompass.services.grounding

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import java.io.File

/**
 * Read-only base for the compact offline food SQLite assets (USDA, Swiss).
 * Owns the copy-on-first-run of the asset (refreshed via its sha256 manifest, with
 * a size fallback), the read-only [SQLiteDatabase] handle, meta/version/count
 * helpers, close, availability probing, and the shared three-stage search
 * pipeline (FTS → multi-token LIKE → loose LIKE). Subclasses map rows to their own
 * record type and score / rank hits.
 */
abstract class OfflineFoodIndex(
    context: Context,
    assetPath: String,
    manifestAssetPath: String,
    dbFileName: String,
    private val foodsTable: String = "foods",
) {
    private val appContext = context.applicationContext
    private val dbFile: File = File(appContext.filesDir, dbFileName)

    protected val db: SQLiteDatabase
    protected val datasetVersion: String

    init {
        copyAssetIfNeeded(assetPath, manifestAssetPath)
        db = SQLiteDatabase.openDatabase(
            dbFile.absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS,
        )
        datasetVersion = readMeta("dataset_version")
            ?: "unknown"
    }

    fun version(): String = datasetVersion

    fun foodCount(): Int {
        db.rawQuery("SELECT COUNT(*) FROM $foodsTable", null).use { c ->
            return if (c.moveToFirst()) c.getInt(0) else 0
        }
    }

    fun close() {
        db.close()
    }

    /**
     * Run the shared three-stage pipeline and return up to [maxRows] raw rows
     * read through [rowReader], de-duplicated on [idColumn]. The subclass then
     * maps rows to records, scores, filters, and caps the final [limit].
     *
     * Stage order: FTS multi-token MATCH → multi-token LIKE on name/tokens →
     * loose single-token LIKE. FTS is skipped when the packaged DB omits
     * `foods_fts`.
     */
    protected fun <T> searchRows(
        query: String,
        idColumn: String,
        nameColumn: String,
        tokensColumn: String,
        rowReader: (Cursor) -> T,
        maxRows: Int = 80,
    ): List<T> {
        val tokens = QueryNormalizer.normalizeTokens(query)
        if (tokens.isEmpty()) return emptyList()

        val rows = mutableListOf<T>()
        val seen = mutableSetOf<Long>()

        fun read(c: Cursor) {
            val id = c.getLong(c.getColumnIndexOrThrow(idColumn))
            if (seen.add(id)) rows += rowReader(c)
        }

        runCatching {
            val ftsQ = tokens.joinToString(" ")
            db.rawQuery(
                """
                SELECT foods.* FROM foods_fts
                JOIN foods ON foods.$idColumn = foods_fts.rowid
                WHERE foods_fts MATCH ?
                LIMIT $maxRows
                """.trimIndent(),
                arrayOf(ftsQ),
            ).use { c ->
                while (c.moveToNext()) read(c)
            }
        }

        if (rows.isEmpty()) {
            val clauses = mutableListOf<String>()
            val args = mutableListOf<String>()
            for (tok in tokens.take(4)) {
                val like = "%$tok%"
                clauses += "($tokensColumn LIKE ? OR $nameColumn LIKE ?)"
                args += like
                args += like
            }
            db.rawQuery(
                "SELECT * FROM $foodsTable WHERE ${clauses.joinToString(" OR ")} LIMIT 120",
                args.toTypedArray(),
            ).use { c ->
                while (c.moveToNext()) read(c)
            }
        }

        if (rows.isEmpty()) {
            val loose = "%${QueryNormalizer.normalizeQuery(query)}%"
            db.rawQuery(
                "SELECT * FROM $foodsTable WHERE $nameColumn LIKE ? OR $tokensColumn LIKE ? LIMIT 80",
                arrayOf(loose, loose),
            ).use { c ->
                while (c.moveToNext()) read(c)
            }
        }

        return rows
    }

    protected fun readMeta(key: String): String? {
        return runCatching {
            db.rawQuery("SELECT value FROM meta WHERE key = ? LIMIT 1", arrayOf(key)).use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull()
    }

    private fun copyAssetIfNeeded(assetPath: String, manifestAssetPath: String) {
        val am = appContext.assets
        val packagedSha = readPackagedSha256(manifestAssetPath)
        val needCopy = !dbFile.exists() || dbFile.length() == 0L ||
            (packagedSha != null && readInstalledSha256() != packagedSha)
        if (!needCopy) {
            // Fallback: refresh when the packaged asset size changes.
            runCatching {
                val assetSize = am.openFd(assetPath).use { it.length }
                if (dbFile.length() == assetSize) return
            }
        }
        dbFile.parentFile?.mkdirs()
        am.open(assetPath).use { input ->
            dbFile.outputStream().use { output -> input.copyTo(output) }
        }
        packagedSha?.let { writeInstalledSha256(it) }
    }

    private fun readPackagedSha256(manifestAssetPath: String): String? = runCatching {
        appContext.assets.open(manifestAssetPath).bufferedReader().use { reader ->
            val text = reader.readText()
            Regex(""""sha256"\s*:\s*"([a-fA-F0-9]+)"""").find(text)?.groupValues?.get(1)
        }
    }.getOrNull()

    private fun shaFile(): File = File(appContext.filesDir, "${dbFile.name}.sha256")

    private fun readInstalledSha256(): String? =
        runCatching { shaFile().takeIf { it.exists() }?.readText()?.trim() }.getOrNull()

    private fun writeInstalledSha256(sha: String) {
        runCatching { shaFile().writeText(sha) }
    }

    companion object {
        /** True when the APK includes the offline index. */
        fun assetAvailable(context: Context, assetPath: String): Boolean =
            runCatching {
                context.applicationContext.assets.open(assetPath).use { }
                true
            }.getOrDefault(false)
    }
}
