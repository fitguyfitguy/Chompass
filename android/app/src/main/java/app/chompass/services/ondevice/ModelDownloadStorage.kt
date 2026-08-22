package app.chompass.services.ondevice

import java.io.File

/**
 * On-disk footprint of on-device models: the `filesDir/models/` tree
 * (final files, `.part`, sidecars) plus LiteRT compile caches. Used by
 * Settings "Clear from this device" so a crashed download can be wiped
 * without adb.
 */
internal object ModelDownloadStorage {
    fun cacheDirs(cacheDir: File): List<File> = listOf(
        File(cacheDir, "litert"),
        File(cacheDir, "litert-mtp"),
    )

    fun treeBytes(root: File): Long {
        if (!root.exists()) return 0L
        if (root.isFile) return root.length()
        return root.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    fun occupiedBytes(modelsDir: File, cacheDir: File): Long =
        treeBytes(modelsDir) + cacheDirs(cacheDir).sumOf { treeBytes(it) }

    fun deleteAll(modelsDir: File, cacheDir: File) {
        modelsDir.listFiles()?.forEach { file ->
            if (file.isDirectory) file.deleteRecursively() else file.delete()
        }
        cacheDirs(cacheDir).forEach { it.deleteRecursively() }
    }
}
