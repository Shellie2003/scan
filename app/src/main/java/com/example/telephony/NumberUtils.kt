package com.example.telephony

import com.example.data.model.Operator

/**
 * Phone number helpers, written for Madagascar numbering (+261, national form
 * `03X XX XXX XX`) but tolerant of anything else the user may dial.
 */
object NumberUtils {

    private const val COUNTRY_CODE = "261"

    /** Digits, `+`, `*` and `#` only. */
    fun sanitize(raw: String): String =
        raw.filter { it.isDigit() || it == '+' || it == '*' || it == '#' }

    /**
     * National form used for comparisons: `+261340000000`, `261340000000` and
     * `0340000000` all become `0340000000`.
     */
    fun toNational(raw: String): String {
        var n = sanitize(raw).removePrefix("+")
        if (n.startsWith("00$COUNTRY_CODE")) n = n.removePrefix("00$COUNTRY_CODE")
        else if (n.startsWith(COUNTRY_CODE) && n.length > COUNTRY_CODE.length + 6) {
            n = n.removePrefix(COUNTRY_CODE)
        }
        if (!n.startsWith("0") && n.length == 9) n = "0$n"
        return n
    }

    /** The `03X` prefix of a national number, or an empty string. */
    fun prefixOf(raw: String): String {
        val national = toNational(raw)
        return if (national.length >= 3 && national.startsWith("0")) national.take(3) else ""
    }

    /** Operator owning this number, based on the (editable) prefixes of each operator. */
    fun operatorForNumber(raw: String, operators: List<Operator>): Operator? {
        val prefix = prefixOf(raw)
        if (prefix.isEmpty()) return null
        return operators.firstOrNull { operator -> operator.prefixList.any { it == prefix } }
    }

    /** `true` for USSD strings and short codes, which must never be reformatted as a number. */
    fun isServiceCode(raw: String): Boolean {
        val s = sanitize(raw)
        return s.contains('*') || s.contains('#') || (s.length in 1..6 && !s.startsWith("+"))
    }

    /** Two numbers belong to the same subscriber. */
    fun sameNumber(a: String, b: String): Boolean {
        val na = toNational(a)
        val nb = toNational(b)
        if (na.isEmpty() || nb.isEmpty()) return false
        return na == nb || na.takeLast(9) == nb.takeLast(9)
    }

    /** `034 12 345 67` — display only, never dialled. */
    fun formatForDisplay(raw: String): String {
        val national = toNational(raw)
        if (national.length != 10 || !national.startsWith("0")) return raw
        return buildString {
            append(national.substring(0, 3)).append(' ')
            append(national.substring(3, 5)).append(' ')
            append(national.substring(5, 8)).append(' ')
            append(national.substring(8, 10))
        }
    }

    /** Splits a pasted list of numbers on commas, semicolons, spaces and new lines. */
    fun splitNumbers(raw: String): List<String> =
        raw.split(',', ';', '\n', ' ', '\t')
            .map { sanitize(it) }
            .filter { it.length >= 5 }
            .distinct()
}
