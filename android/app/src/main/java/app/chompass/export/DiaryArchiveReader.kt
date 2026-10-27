package app.chompass.export

import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

/**
 * Reads Chompass diary ZIP exports (format 1.7 with photos): the first
 * root-level `*.json` entry is the diary document, `photos/` entries carry the
 * referenced photo bytes keyed by their archive name. Everything stays
 * in-memory — archive names never touch the filesystem. Returns
 * (json text, photo bytes by archive name), or null when no JSON entry exists
 * (not a Chompass diary ZIP).
 */
object DiaryArchiveReader {
    fun read(bytes: ByteArray): Pair<String, Map<String, ByteArray>>? {
        var json: String? = null
        val photos = mutableMapOf<String, ByteArray>()
        runCatching {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (!entry.isDirectory) {
                        val name = entry.name.trimStart('/')
                        when {
                            json == null && name.endsWith(".json") && '/' !in name ->
                                json = zip.readBytes().toString(Charsets.UTF_8)
                            name.startsWith("photos/") -> photos[name] = zip.readBytes()
                        }
                    }
                    zip.closeEntry()
                }
            }
        }.getOrNull() ?: return null
        return json?.let { it to photos.toMap() }
    }
}
