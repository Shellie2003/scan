package com.example

import com.example.ui.util.TimeLabels
import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The date, in figures.
 *
 * The lists used to print "Nov 1, 2025" — in the language of the telephone,
 * not the one chosen in the application, so a Malagasy interface was in
 * English. Figures belong to no language, and the year is only written when it
 * is not the one everybody assumes.
 */
class TimeLabelsTest {

    private fun at(year: Int, month: Int, day: Int): Long =
        Calendar.getInstance().apply {
            set(year, month - 1, day, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    @Test
    fun `a day of this year is written day then month`() {
        val now = at(2025, 11, 17)
        assertEquals("01/11", TimeLabels.shortDate(at(2025, 11, 1), now))
        assertEquals("29/10", TimeLabels.shortDate(at(2025, 10, 29), now))
    }

    @Test
    fun `another year carries its year`() {
        val now = at(2025, 11, 17)
        assertEquals("31/12/24", TimeLabels.shortDate(at(2024, 12, 31), now))
        assertEquals("05/03/26", TimeLabels.shortDate(at(2026, 3, 5), now))
    }

    @Test
    fun `the day and the month always hold two figures`() {
        val now = at(2025, 11, 17)
        assertEquals("05/01", TimeLabels.shortDate(at(2025, 1, 5), now))
    }
}
