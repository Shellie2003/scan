package com.example.data

import android.content.Context
import com.example.telephony.NumberUtils
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
 * The code is never stored. What is kept is a salted SHA-256 of it, which is
 * enough to check a code and useless to whoever reads the preferences. There is
 * no recovery: forgetting the code means removing it, and every discussion it
 * protected comes back to the inbox — losing nothing, hiding nothing more.
 */
object ConversationLock {

    private const val PREFS = "app_prefs"
    private const val KEY_HASH = "lock_pin_hash"
    private const val KEY_SALT = "lock_pin_salt"
    private const val KEY_LOCKED = "locked_conversations"

    /** Four digits is what a market seller will accept to type ten times a day. */
    const val MIN_LENGTH = 4
    const val MAX_LENGTH = 8

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

    /**
     * Removes the code, and with it every lock.
     *
     * Keeping discussions hidden without a code to open them would make them
     * unreachable, which is a loss the user never asked for.
     */
    fun clearPin(context: Context) {
        prefs(context).edit()
            .remove(KEY_HASH)
            .remove(KEY_SALT)
            .remove(KEY_LOCKED)
            .apply()
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
