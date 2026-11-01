package com.example

import com.example.telephony.MmsPdu
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reading the notification an operator pushes when a multimedia message waits.
 *
 * This is binary, written by somebody else's equipment, and it arrives in a
 * broadcast receiver: a parser that throws there takes the process with it. So
 * what is checked here is both what it reads and what it refuses to do when the
 * bytes make no sense.
 */
class MmsPduTest {

    /** Builds a notification PDU the way an operator does. */
    private fun notificationPdu(
        from: String = "+261341234567/TYPE=PLMN",
        subject: String? = "Bonjour",
        subjectCharset: Boolean = false,
        sizeBytes: Int = 8000,
        location: String? = "http://mms.operator.mg/x"
    ): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        // X-Mms-Message-Type = m-notification-ind
        out.write(0x8C); out.write(0x82)
        // X-Mms-Transaction-Id, a plain text string
        out.write(0x98); out.write("Tx1".toByteArray()); out.write(0)
        // X-Mms-MMS-Version 1.0
        out.write(0x8D); out.write(0x90)
        // From: length, address-present token, the address, its zero
        out.write(0x89)
        out.write(1 + from.length + 1)
        out.write(0x80)
        out.write(from.toByteArray()); out.write(0)
        if (subject != null) {
            out.write(0x96)
            if (subjectCharset) {
                // length, charset as a short integer, the text, its zero
                out.write(1 + subject.length + 1)
                out.write(0xEA)
            }
            out.write(subject.toByteArray()); out.write(0)
        }
        // X-Mms-Message-Size, a long integer on two bytes
        out.write(0x8E); out.write(0x02)
        out.write((sizeBytes shr 8) and 0xFF); out.write(sizeBytes and 0xFF)
        if (location != null) {
            out.write(0x83); out.write(location.toByteArray()); out.write(0)
        }
        return out.toByteArray()
    }

    @Test
    fun `the sender is read without the type suffix`() {
        val read = MmsPdu.readNotification(notificationPdu())
        assertEquals("+261341234567", read.from)
    }

    @Test
    fun `a plain subject is read`() {
        assertEquals("Bonjour", MmsPdu.readNotification(notificationPdu()).subject)
    }

    @Test
    fun `a subject declaring its charset is read too`() {
        val pdu = notificationPdu(subject = "Salama", subjectCharset = true)
        assertEquals("Salama", MmsPdu.readNotification(pdu).subject)
    }

    @Test
    fun `the size and the presence of an address are read`() {
        val read = MmsPdu.readNotification(notificationPdu(sizeBytes = 8000))
        assertEquals(8000L, read.sizeBytes)
        assertTrue(read.hasLocation)
    }

    @Test
    fun `a notification without a subject is still attributed`() {
        val read = MmsPdu.readNotification(notificationPdu(subject = null))
        assertEquals("+261341234567", read.from)
        assertNull(read.subject)
    }

    @Test
    fun `nothing at all is answered with nothing`() {
        assertNull(MmsPdu.readNotification(null).from)
        assertNull(MmsPdu.readNotification(ByteArray(0)).from)
    }

    @Test
    fun `bytes that are not a notification are refused quietly`() {
        // A real risk: whatever the equipment of an operator sends, the receiver
        // must come back with empty hands rather than an exception.
        val garbage = byteArrayOf(0x01, 0x02, 0x03, 0x7F, -1, -128, 0x00, 0x42)
        val read = MmsPdu.readNotification(garbage)
        assertNull(read.from)
        assertFalse(read.hasLocation)
    }

    @Test
    fun `a truncated notification gives what it can`() {
        val full = notificationPdu()
        // Cut in the middle of the address: the field says it is longer than
        // what is left of the buffer.
        val cut = full.copyOfRange(0, 20)
        val read = MmsPdu.readNotification(cut)
        assertFalse(read.hasLocation)
    }
}
