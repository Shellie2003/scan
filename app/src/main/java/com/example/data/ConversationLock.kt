package com.example.data

import android.content.Context
import com.example.telephony.NumberUtils
import com.example.telephony.SmsRepository
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * The lock on a discussion.
 *
 * A locked conversation leaves the inbox: it lives behind a code the user
 * chooses, and a message arriving in it notifies without a name and without a
 * word of its content. It is what makes the phone shareable — a phone in
 * Madagascar is often handed to a cousin to make a call.
 *
 * Neither the code nor the phrase is ever stored. What is kept is a salted
 * SHA-256 of each, which is enough to check one and useless to whoever reads
 * the preferences.
 *
 * The lock is deliberately irreversible. A code forgotten, with the phrase
 * forgotten too, cannot be worked around: the discussions it protects are
 * deleted, for good, rather than handed back. A lock that gives way to whoever
 * presses "I forgot" protects nothing — and the person it would protect
 * against is often the one holding the phone.
 *
 * The phrase is the one way back, and it is the user who writes it. A sentence
 * is remembered where four digits are not, and it never has to be typed in
 * front of anybody.
 */
object ConversationLock {

    private const val PREFS = "app_prefs"
    private const val KEY_HASH = "lock_pin_hash"
    private const val KEY_SALT = "lock_pin_salt"
    private const val KEY_PHRASE_HASH = "lock_phrase_hash"
    private const val KEY_PHRASE_SALT = "lock_phrase_salt"
    private const val KEY_LOCKED = "locked_conversations"

    /** Four digits is what a market seller will accept to type ten times a day. */
    const val MIN_LENGTH = 4
    const val MAX_LENGTH = 8

    /**
     * A phrase has to be long enough to be a sentence and not a word.
     *
     * Short enough to be typed on a cheap keyboard, long enough that guessing
     * it is not a matter of trying the obvious.
     */
    const val PHRASE_MIN_LENGTH = 8

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // --------------------------------------------------------------------- code

    fun hasPin(context: Context): Boolean =
        prefs(context).getString(KEY_HASH, "").orEmpty().isNotBlank()

    /** Stores a new code. Returns `false` when it is too short to be one. */
    fun setPin(context: Context, pin: String): Boolean {
        if (!isAcceptable(pin)) return false
        val salt = newSalt()
        prefs(context).edit()
            .putString(KEY_SALT, salt)
            .putString(KEY_HASH, hash(pin, salt))
            .apply()
        return true
    }

    fun verify(context: Context, pin: String): Boolean {
        val stored = prefs(context).getString(KEY_HASH, "").orEmpty()
        val salt = prefs(context).getString(KEY_SALT, "").orEmpty()
        if (stored.isBlank() || salt.isBlank()) return false
        return constantTimeEquals(stored, hash(pin, salt))
    }

    // ------------------------------------------------------------------- phrase

    fun hasPhrase(context: Context): Boolean =
        prefs(context).getString(KEY_PHRASE_HASH, "").orEmpty().isNotBlank()

    /**
     * Stores the phrase that can give the code back.
     *
     * Compared with its spaces squeezed and its case ignored: somebody writing
     * their own sentence a year later will not reproduce the capitals, and the
     * point is to let the owner back in, not to test their typing.
     */
    fun setPhrase(context: Context, phrase: String): Boolean {
        if (!isAcceptablePhrase(phrase)) return false
        val salt = newSalt()
        prefs(context).edit()
            .putString(KEY_PHRASE_SALT, salt)
            .putString(KEY_PHRASE_HASH, hash(normalise(phrase), salt))
            .apply()
        return true
    }

    fun verifyPhrase(context: Context, phrase: String): Boolean {
        val stored = prefs(context).getString(KEY_PHRASE_HASH, "").orEmpty()
        val salt = prefs(context).getString(KEY_PHRASE_SALT, "").orEmpty()
        if (stored.isBlank() || salt.isBlank()) return false
        return constantTimeEquals(stored, hash(normalise(phrase), salt))
    }

    fun isAcceptablePhrase(phrase: String): Boolean =
        normalise(phrase).length >= PHRASE_MIN_LENGTH

    /** One run of spaces, no capitals, nothing hanging off the ends. */
    internal fun normalise(phrase: String): String =
        phrase.trim().replace(Regex("\\s+"), " ").lowercase()

    /**
     * Removes the lock for somebody who has just given the code.
     *
     * Nothing is lost here: the discussions go back to the inbox. Proving the
     * code is what separates this from [forget] — the owner is taking their own
     * lock off, not being let past it.
     */
    fun removeWithCode(context: Context) {
        prefs(context).edit()
            .remove(KEY_HASH)
            .remove(KEY_SALT)
            .remove(KEY_PHRASE_HASH)
            .remove(KEY_PHRASE_SALT)
            .remove(KEY_LOCKED)
            .apply()
        revokeAccess()
    }

    /**
     * Gives the lock up: the discussions behind it are deleted, for good.
     *
     * This is the only way out when the code and the phrase are both gone, and
     * it is deliberately a loss. A lock that opened for whoever pressed "I
     * forgot" would protect nothing, and the person it protects against is
     * usually the one holding the phone.
     *
     * Returns the number of messages deleted. The preferences are cleared even
     * when nothing could be deleted — a read permission refused must not leave
     * a lock nobody can open behind.
     */
    suspend fun forget(context: Context): Int {
        var deleted = 0
        lockedKeys(context).forEach { key ->
            deleted += try {
                SmsRepository.deleteConversation(context, key)
            } catch (e: Exception) {
                0
            }
        }
        removeWithCode(context)
        return deleted
    }

    fun isAcceptable(pin: String): Boolean =
        pin.length in MIN_LENGTH..MAX_LENGTH && pin.all { it.isDigit() }

    // ------------------------------------------------------------------- access

    /**
     * How long a code typed once keeps opening the locked discussions.
     *
     * Hiding a discussion from the inbox is not enough: it can still be reached
     * from a contact, from the call history or from an `smsto:` link, and it
     * would open with no code asked. Every way in now goes through the same
     * question — but a code typed in the locked list must not be asked again
     * for each discussion of that list, so it stands for two minutes.
     *
     * In memory only: closing the application asks for the code again.
     */
    private const val GRANT_MS = 2 * 60 * 1000L

    @Volatile
    private var grantedAt = 0L

    fun grantAccess() {
        grantedAt = System.currentTimeMillis()
    }

    fun hasRecentAccess(): Boolean =
        grantedAt > 0L && System.currentTimeMillis() - grantedAt < GRANT_MS

    /** Closes the locked discussions again, without waiting for the delay. */
    fun revokeAccess() {
        grantedAt = 0L
    }

    // ------------------------------------------------------------ conversations

    fun lockedKeys(context: Context): Set<String> {
        val stored = prefs(context).getStringSet(KEY_LOCKED, emptySet()) ?: emptySet<String>()
        // A copy: the set handed out by the preferences must never be held on to.
        return HashSet(stored)
    }

    fun isLocked(context: Context, address: String): Boolean {
        val key = keyOf(address)
        if (key.isBlank()) return false
        return lockedKeys(context).contains(key)
    }

    fun lock(context: Context, address: String) {
        val key = keyOf(address)
        if (key.isBlank() || !hasPin(context)) return
        save(context, lockedKeys(context) + key)
    }

    fun unlock(context: Context, address: String) {
        save(context, lockedKeys(context) - keyOf(address))
    }

    fun count(context: Context): Int = lockedKeys(context).size

    private fun save(context: Context, keys: Set<String>) {
        prefs(context).edit().putStringSet(KEY_LOCKED, HashSet(keys)).apply()
    }

    /** The form a conversation is recognised by, whatever way the number is written. */
    internal fun keyOf(address: String): String =
        NumberUtils.toNational(address).ifBlank { address.trim() }

    // ------------------------------------------------------------------ hashing

    /**
     * Salted SHA-256, written as hexadecimal.
     *
     * Hexadecimal by hand rather than `android.util.Base64`: this has to be
     * exercisable by a plain unit test, and the Android encoder is not there.
     */
    internal fun hash(pin: String, salt: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest((salt + "|" + pin).toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { byte ->
            val value = byte.toInt() and 0xFF
            val hex = Integer.toHexString(value)
            if (hex.length == 1) "0$hex" else hex
        }
    }

    private fun newSalt(): String {
        val bytes = ByteArray(12)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { byte ->
            val value = byte.toInt() and 0xFF
            val hex = Integer.toHexString(value)
            if (hex.length == 1) "0$hex" else hex
        }
    }

    /** Compares without telling, by how long it took, where two codes differ. */
    internal fun constantTimeEquals(left: String, right: String): Boolean {
        if (left.length != right.length) return false
        var difference = 0
        for (index in left.indices) {
            difference = difference or (left[index].code xor right[index].code)
        }
        return difference == 0
    }
}
