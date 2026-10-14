package com.example.data

import android.content.Context
import com.example.telephony.NumberUtils

/**
 * Numbers the user never wants to hear from again.
 *
 * A blocked number is hung up before it rings and its messages arrive without a
 * sound — they are still written to the SMS database, because the application is
 * the default SMS app of the phone and losing a message is not ours to decide.
 *
 * Numbers are kept in their national form, so a contact blocked as `0341234567`
 * stays blocked when they call as `+261341234567`.
 */
object BlockedNumbers {

    private const val PREFS = "app_prefs"
    private const val KEY = "blocked_numbers"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun all(context: Context): Set<String> {
        val stored = prefs(context).getStringSet(KEY, emptySet()) ?: emptySet<String>()
        // Sorted, because this list is read by a human looking for one number.
        return stored.toSortedSet()
    }

    fun isBlocked(context: Context, number: String): Boolean {
        val key = keyOf(number)
        if (key.isBlank()) return false
        return all(context).contains(key)
    }

    fun block(context: Context, number: String) {
        val key = keyOf(number)
        if (key.isBlank()) return
        save(context, all(context) + key)
    }

    fun unblock(context: Context, number: String) {
        save(context, all(context) - keyOf(number))
    }

    fun count(context: Context): Int = all(context).size

    private fun save(context: Context, numbers: Set<String>) {
        // A new set is written on purpose: the one handed out by the preferences
        // must never be edited in place, and Android warns about nothing when it is.
        prefs(context).edit().putStringSet(KEY, HashSet(numbers)).apply()
    }

    /** The form a number is stored and compared under. */
    internal fun keyOf(number: String): String = NumberUtils.toNational(number).ifBlank {
        number.trim()
    }
}
