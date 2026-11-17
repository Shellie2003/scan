package com.example.ui.util

import android.content.Context
import android.text.format.DateFormat
import android.text.format.DateUtils
import java.util.Calendar
import java.util.Date

/**
 * The time, written the way a list can be read at a glance.
 *
 * What was there before was `getRelativeTimeSpanString`, and it has two faults
 * that show on every row of every list in this application.
 *
 * The first is the language. It writes in the language of the telephone, not
 * the one the user chose here, so a Malagasy interface was printing
 * "Nov 1, 2025" down the whole screen. The names of the months and the days of
 * Madagascar are not in Android, so nothing is named at all: a date is written
 * in figures, which belongs to no language and is read by everyone.
 *
 * The second is repetition. Under a heading that already says "Nov 1", every
 * row said "Nov 1" again — the one thing the row could not tell you was at what
 * time the call came. [clock] is for those lists; [relative] is for the lists
 * that have no heading above them.
 */
object TimeLabels {

    /** The hour, in the form the telephone is set to — 14:32, or 2:32 PM. */
    fun clock(context: Context, millis: Long): String =
        try {
            DateFormat.getTimeFormat(context).format(Date(millis))
        } catch (e: Exception) {
            ""
        }

    /**
     * The hour today, the word for yesterday, a date in figures before that.
     *
     * For a list of conversations, where each row stands alone.
     */
    fun relative(context: Context, millis: Long): String = when {
        DateUtils.isToday(millis) -> clock(context, millis)
        DateUtils.isToday(millis + DateUtils.DAY_IN_MILLIS) ->
            LanguageManager.getString("yesterday")

        else -> shortDate(millis)
    }

    /**
     * `01/11`, and `01/11/24` when it is not this year.
     *
     * Day first: that is how a date is written here, and the year is only
     * added when it is not the one everybody assumes.
     */
    fun shortDate(millis: Long, now: Long = System.currentTimeMillis()): String {
        val day = Calendar.getInstance().apply { timeInMillis = millis }
        val today = Calendar.getInstance().apply { timeInMillis = now }
        val d = two(day.get(Calendar.DAY_OF_MONTH))
        val m = two(day.get(Calendar.MONTH) + 1)
        val year = day.get(Calendar.YEAR)
        return if (year == today.get(Calendar.YEAR)) {
            "$d/$m"
        } else {
            "$d/$m/" + two(year % 100)
        }
    }

    private fun two(value: Int): String = if (value < 10) "0$value" else value.toString()
}
