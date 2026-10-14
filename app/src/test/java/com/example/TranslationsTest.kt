package com.example

import com.example.ui.util.LanguageManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The three languages must carry the same keys.
 *
 * A key added to one map only does not crash: the app falls back to Malagasy,
 * and a French user reads a Malagasy sentence. That happened twice, so it is
 * checked here instead of being noticed on a phone.
 */
class TranslationsTest {

    @Test
    fun `the three languages are declared`() {
        assertEquals(setOf("mg", "fr", "en"), LanguageManager.languages)
    }

    @Test
    fun `every language carries exactly the same keys`() {
        val reference = LanguageManager.keysOf("mg")
        assertTrue("Malagasy carries no key", reference.isNotEmpty())
        LanguageManager.languages.forEach { lang ->
            val keys = LanguageManager.keysOf(lang)
            assertEquals(
                "missing in $lang: " + (reference - keys).sorted(),
                emptyList<String>(),
                (reference - keys).sorted()
            )
            assertEquals(
                "only in $lang: " + (keys - reference).sorted(),
                emptyList<String>(),
                (keys - reference).sorted()
            )
        }
    }

    /**
     * A key declared twice in the same language.
     *
     * Kotlin accepts it — the last value quietly wins — so nothing ever says
     * that a translation was overwritten, and the duplicate survives until
     * someone changes one of the two. The source is read here because the map
     * itself cannot tell: a set has already swallowed the repetition.
     */
    @Test
    fun `no key is declared twice in the same language`() {
        val source = languageManagerSource()
        listOf("mg", "fr", "en").forEach { lang ->
            val start = source.indexOf("\"$lang\" to mapOf(")
            assertTrue("$lang map not found", start > 0)
            val end = source.indexOf("\n        )", start)
            val body = source.substring(start, end)
            val keys = Regex("^\\s+\"([a-z0-9_]+)\" to \"", RegexOption.MULTILINE)
                .findAll(body)
                .map { it.groupValues[1] }
                .toList()
            val duplicated = keys.groupBy { it }.filterValues { it.size > 1 }.keys.sorted()
            assertEquals("declared twice in $lang: $duplicated", emptyList<String>(), duplicated)
        }
    }

    /** The translation file itself, whichever directory the tests run from. */
    private fun languageManagerSource(): String {
        val relative = "src/main/java/com/example/ui/util/LanguageManager.kt"
        val candidates = listOf(java.io.File(relative), java.io.File("app/$relative"))
        val file = candidates.firstOrNull { it.exists() }
        assertTrue(
            "LanguageManager.kt not found from " + java.io.File(".").absolutePath,
            file != null
        )
        return file!!.readText()
    }

    @Test
    fun `no translation is left empty`() {
        LanguageManager.languages.forEach { lang ->
            LanguageManager.keysOf(lang).forEach { key ->
                LanguageManager.setLanguageForTest(lang)
                assertTrue("$lang/$key is empty", LanguageManager.getString(key).isNotBlank())
            }
        }
    }
}
