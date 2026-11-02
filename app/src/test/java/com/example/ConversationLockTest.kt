package com.example

import com.example.data.BlockedNumbers
import com.example.data.ConversationLock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The logic behind the lock of a discussion and the blocked numbers.
 *
 * Everything here is pure: no phone, no preferences. What is checked is what
 * the rest cannot be allowed to get wrong — that a code is never stored as
 * itself, and that a number is recognised however it is written.
 */
class ConversationLockTest {

    @Test
    fun `the same code and salt always give the same fingerprint`() {
        assertEquals(
            ConversationLock.hash("1234", "abcdef"),
            ConversationLock.hash("1234", "abcdef")
        )
    }

    @Test
    fun `two codes never share a fingerprint`() {
        assertFalse(
            ConversationLock.hash("1234", "abcdef") == ConversationLock.hash("1235", "abcdef")
        )
    }

    @Test
    fun `the same code under two salts gives two fingerprints`() {
        assertFalse(
            ConversationLock.hash("1234", "abcdef") == ConversationLock.hash("1234", "fedcba")
        )
    }

    @Test
    fun `a fingerprint never carries the code`() {
        val fingerprint = ConversationLock.hash("9271", "a1b2c3")
        assertFalse(fingerprint.contains("9271"))
        // Sixty-four hexadecimal characters: a whole SHA-256 and nothing else.
        assertEquals(64, fingerprint.length)
        assertTrue(fingerprint.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun `a code is four to eight digits and nothing else`() {
        assertTrue(ConversationLock.isAcceptable("1234"))
        assertTrue(ConversationLock.isAcceptable("12345678"))
        assertFalse(ConversationLock.isAcceptable("123"))
        assertFalse(ConversationLock.isAcceptable("123456789"))
        assertFalse(ConversationLock.isAcceptable("12a4"))
        assertFalse(ConversationLock.isAcceptable(""))
    }

    // ------------------------------------------------------------------ phrase

    @Test
    fun `a phrase is read past the capitals and the extra spaces`() {
        // A year later, nobody reproduces their own capitals. What is compared
        // is the sentence, not the typing.
        val reference = ConversationLock.normalise("Ny anaran'ny zaza voalohany")
        assertEquals(reference, ConversationLock.normalise("ny anaran'ny zaza voalohany"))
        assertEquals(reference, ConversationLock.normalise("  NY ANARAN'NY   ZAZA  VOALOHANY  "))
        assertEquals(reference, ConversationLock.normalise("Ny Anaran'ny\tZaza\nVoalohany"))
    }

    @Test
    fun `two different phrases stay different`() {
        assertFalse(
            ConversationLock.normalise("le nom de mon village") ==
                ConversationLock.normalise("le nom de mon frere")
        )
    }

    @Test
    fun `a phrase has to be a sentence and not a word`() {
        assertTrue(ConversationLock.isAcceptablePhrase("mon village natal"))
        assertTrue(ConversationLock.isAcceptablePhrase("12345678"))
        assertFalse(ConversationLock.isAcceptablePhrase("court"))
        assertFalse(ConversationLock.isAcceptablePhrase(""))
        // Spaces alone do not make a length: squeezed first, measured after.
        assertFalse(ConversationLock.isAcceptablePhrase("   a    b   "))
    }

    @Test
    fun `a phrase fingerprint never carries the phrase`() {
        val phrase = "ny tanana nahaterahako"
        val fingerprint = ConversationLock.hash(ConversationLock.normalise(phrase), "a1b2c3")
        assertFalse(fingerprint.contains("tanana"))
        assertEquals(64, fingerprint.length)
    }

    @Test
    fun `comparing two fingerprints answers like an equality`() {
        assertTrue(ConversationLock.constantTimeEquals("abc", "abc"))
        assertFalse(ConversationLock.constantTimeEquals("abc", "abd"))
        assertFalse(ConversationLock.constantTimeEquals("abc", "abcd"))
    }

    @Test
    fun `a locked discussion is the same whatever way the number is written`() {
        val national = ConversationLock.keyOf("0341234567")
        assertEquals(national, ConversationLock.keyOf("+261 34 12 345 67"))
        assertEquals(national, ConversationLock.keyOf("261341234567"))
        assertFalse(national == ConversationLock.keyOf("0341234568"))
    }

    @Test
    fun `a blocked number is the same whatever way it is written`() {
        val national = BlockedNumbers.keyOf("0331234567")
        assertEquals(national, BlockedNumbers.keyOf("+261331234567"))
        assertEquals(national, BlockedNumbers.keyOf("00261331234567"))
    }
}
