package app.chompass.parity

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Lightweight JVM checks that contract schemas and parity fixtures stay present
 * and that schema `const` versions match fixture format versions. Full Draft
 * 2020-12 validation runs in `scripts/validate_parity_contracts.py` via
 * `release:check-parity`.
 */
class ContractSchemaPresenceTest {
    @Test
    fun contractSchemasExistAndMatchFixtureVersions() {
        val contracts = repoFile("contracts")
        assertTrue(File(contracts, "diary-1.1.schema.json").isFile)
        assertTrue(File(contracts, "body-metrics-1.0.schema.json").isFile)
        assertTrue(File(contracts, "meal-share-v1.schema.json").isFile)

        val diarySchema = JSONObject(File(contracts, "diary-1.1.schema.json").readText())
        val diaryConst = diarySchema
            .getJSONObject("properties")
            .getJSONObject("export")
            .getJSONObject("properties")
            .getJSONObject("format_version")
            .getString("const")
        val diaryFixture = ParityFixtures.readJson("diary-sample.json")
        assertEquals(diaryConst, diaryFixture.getJSONObject("export").getString("format_version"))

        val bodySchema = JSONObject(File(contracts, "body-metrics-1.0.schema.json").readText())
        val bodyConst = bodySchema
            .getJSONObject("properties")
            .getJSONObject("export")
            .getJSONObject("properties")
            .getJSONObject("format_version")
            .getString("const")
        val bodyFixture = ParityFixtures.readJson("body-metrics-sample.json")
        assertEquals(bodyConst, bodyFixture.getJSONObject("export").getString("format_version"))

        val mealSchema = JSONObject(File(contracts, "meal-share-v1.schema.json").readText())
        val mealConst = mealSchema.getJSONObject("properties").getJSONObject("v").getInt("const")
        val mealFixture = ParityFixtures.readJson("meal-share-sample.json")
        assertEquals(mealConst, mealFixture.getInt("v"))
    }

    private fun repoFile(relative: String): File {
        val parity = ParityFixtures.dir()
        val root = parity.parentFile?.parentFile
            ?: error("expected testdata/parity under repo root")
        val f = File(root, relative)
        require(f.exists()) { "missing $relative at ${f.absolutePath}" }
        return f
    }
}
