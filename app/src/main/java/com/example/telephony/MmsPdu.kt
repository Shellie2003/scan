package com.example.telephony

/**
 * What arrives when someone sends a multimedia message.
 *
 * The operator does not push the message itself: it pushes a notification —
 * `M-Notification.ind` — holding who sent it, a subject, a size and the address
 * where the message waits. Fetching and assembling the message that follows is
 * a stack of its own (a download over the operator MMS network, then a
 * multipart PDU to parse); SCAN does not have it yet.
 *
 * What is read here is only enough to say to the user *who* wrote and *that*
 * something arrived, instead of losing the message in silence. Every field is
 * optional and every failure is a null: a notification whose encoding is not
 * understood must still be announced.
 *
 * Encoding, per OMA-MMS and WAP-230: a sequence of fields, each opening with
 * `0x80 | assigned-number`.
 */
object MmsPdu {

    private const val FIELD_FROM = 0x89
    private const val FIELD_SUBJECT = 0x96
    private const val FIELD_MESSAGE_SIZE = 0x8E
    private const val FIELD_CONTENT_LOCATION = 0x83

    /** Marks an address that is actually present, rather than "insert mine". */
    private const val TOKEN_ADDRESS_PRESENT = 0x80

    data class Notification(
        val from: String? = null,
        val subject: String? = null,
        val sizeBytes: Long = 0L,
        val hasLocation: Boolean = false
    )

    /** Reads what it can of a notification PDU. Never throws. */
    fun readNotification(pdu: ByteArray?): Notification {
        if (pdu == null || pdu.isEmpty()) return Notification()
        return try {
            parse(pdu)
        } catch (e: Exception) {
            Notification()
        }
    }

    private fun parse(pdu: ByteArray): Notification {
        var from: String? = null
        var subject: String? = null
        var size = 0L
        var hasLocation = false

        var index = 0
        while (index < pdu.size) {
            val field = pdu[index].toInt() and 0xFF
            index++
            if (field < 0x80) {
                // Not a field header: the stream is not what was expected, and
                // guessing further would only produce nonsense.
                break
            }
            when (field) {
                FIELD_FROM -> {
                    val (value, next) = readLengthPrefixed(pdu, index)
                    index = next
                    from = addressOf(value)
                }

                FIELD_SUBJECT -> {
                    val (value, next) = readEncodedString(pdu, index)
                    index = next
                    subject = value?.takeIf { it.isNotBlank() }
                }

                FIELD_MESSAGE_SIZE -> {
                    val (value, next) = readLongInteger(pdu, index)
                    index = next
                    size = value
                }

                FIELD_CONTENT_LOCATION -> {
                    val (value, next) = readTextString(pdu, index)
                    index = next
                    hasLocation = !value.isNullOrBlank()
                }

                else -> index = skipValue(pdu, index)
            }
            if (from != null && subject != null && size > 0 && hasLocation) break
        }
        return Notification(from, subject, size, hasLocation)
    }

    /** `+261341234567/TYPE=PLMN` is an address; the suffix is not part of it. */
    private fun addressOf(raw: String?): String? {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return null
        return value.substringBefore("/TYPE=").takeIf { it.isNotBlank() }
    }

    /**
     * A value introduced by its length: the From field, whose body is a token
     * followed by the address.
     */
    private fun readLengthPrefixed(pdu: ByteArray, start: Int): Pair<String?, Int> {
        val (length, afterLength) = readValueLength(pdu, start)
        val end = (afterLength + length).coerceAtMost(pdu.size)
        if (afterLength >= end) return null to end
        var cursor = afterLength
        val token = pdu[cursor].toInt() and 0xFF
        if (token == TOKEN_ADDRESS_PRESENT) cursor++
        val (text, _) = readEncodedString(pdu, cursor, end)
        return text to end
    }

    /** `encoded-string-value`: a plain string, or a charset followed by one. */
    private fun readEncodedString(
        pdu: ByteArray,
        start: Int,
        limit: Int = pdu.size
    ): Pair<String?, Int> {
        if (start >= limit) return null to start
        val first = pdu[start].toInt() and 0xFF
        // A short length means a charset is declared before the text itself.
        if (first in 1..30) {
            val end = (start + 1 + first).coerceAtMost(limit)
            var cursor = start + 1
            // The charset is a short integer or a long one; either way it is
            // skipped: what SCAN shows is the text.
            if (cursor < end && (pdu[cursor].toInt() and 0xFF) >= 0x80) cursor++
            return readTextString(pdu, cursor, end).first to end
        }
        return readTextString(pdu, start, limit)
    }

    /** `text-string`: bytes up to a zero, with an optional leading quote. */
    private fun readTextString(
        pdu: ByteArray,
        start: Int,
        limit: Int = pdu.size
    ): Pair<String?, Int> {
        var cursor = start
        if (cursor < limit && (pdu[cursor].toInt() and 0xFF) == 0x7F) cursor++
        val builder = StringBuilder()
        while (cursor < limit) {
            val byte = pdu[cursor].toInt() and 0xFF
            cursor++
            if (byte == 0) break
            builder.append(byte.toChar())
        }
        return builder.toString().takeIf { it.isNotEmpty() } to cursor
    }

    private fun readLongInteger(pdu: ByteArray, start: Int): Pair<Long, Int> {
        if (start >= pdu.size) return 0L to start
        val first = pdu[start].toInt() and 0xFF
        if (first >= 0x80) return (first and 0x7F).toLong() to (start + 1)
        val end = (start + 1 + first).coerceAtMost(pdu.size)
        var value = 0L
        for (cursor in (start + 1) until end) {
            value = (value shl 8) or (pdu[cursor].toLong() and 0xFF)
        }
        return value to end
    }

    /** The length of a value, short or written as a variable-length integer. */
    private fun readValueLength(pdu: ByteArray, start: Int): Pair<Int, Int> {
        if (start >= pdu.size) return 0 to start
        val first = pdu[start].toInt() and 0xFF
        if (first <= 30) return first to (start + 1)
        if (first == 31) {
            var value = 0
            var cursor = start + 1
            while (cursor < pdu.size) {
                val byte = pdu[cursor].toInt() and 0xFF
                cursor++
                value = (value shl 7) or (byte and 0x7F)
                if (byte and 0x80 == 0) break
            }
            return value to cursor
        }
        // A short integer standing on its own: one byte, no length.
        return 0 to start
    }

    /** Steps over a value whose field is of no interest here. */
    private fun skipValue(pdu: ByteArray, start: Int): Int {
        if (start >= pdu.size) return start
        val first = pdu[start].toInt() and 0xFF
        return when {
            // A short integer is the value itself.
            first >= 0x80 -> start + 1
            first <= 30 -> (start + 1 + first).coerceAtMost(pdu.size)
            first == 31 -> {
                val (length, afterLength) = readValueLength(pdu, start)
                (afterLength + length).coerceAtMost(pdu.size)
            }
            // A text string: up to its zero.
            else -> readTextString(pdu, start).second
        }
    }
}
