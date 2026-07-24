package app.chompass.parity

import org.json.JSONObject
import java.io.File

/**
 * Loads committed cross-app fixtures from `testdata/parity/` at the repo root.
 * Path is injected by Gradle (`chompass.parity.dir`) so unit tests do not depend on CWD.
 */
object ParityFixtures {
    fun dir(): File {
        val fromProp = System.getProperty("chompass.parity.dir")
        if (!fromProp.isNullOrBlank()) {
            val f = File(fromProp)
            require(f.isDirectory) { "chompass.parity.dir is not a directory: $fromProp" }
            return f
        }
        // Fallback when running from an IDE without the Gradle system property.
        var dir = File(System.getProperty("user.dir") ?: ".")
        repeat(6) {
            val candidate = File(dir, "testdata/parity")
            if (candidate.isDirectory) return candidate
            dir = dir.parentFile ?: return@repeat
        }
        error("Could not locate testdata/parity (set chompass.parity.dir)")
    }

    fun file(name: String): File {
        val f = File(dir(), name)
        require(f.isFile) { "Missing parity fixture: ${f.absolutePath}" }
        return f
    }

    fun readText(name: String): String = file(name).readText()

    fun readJson(name: String): JSONObject = JSONObject(readText(name))
}
