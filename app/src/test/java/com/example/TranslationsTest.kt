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
