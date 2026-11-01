package com.example.telephony

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.telephony.SmsManager
import com.example.ui.util.LanguageManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * What the operator answered about a message we sent.
 *
 * Handing a message to the telephony stack is not sending it. Without credit,
 * without network, or to a number that does not exist, the message is refused
 * seconds later — and until now nothing in the application was listening. A
 * campaign of two hundred messages could report two hundred sent while forty
 * had been thrown away, which for someone selling credit is money and trust.
 *
 * The row of the message is carried in the data of the intent, so each message
 * answers for itself.
 */
class SmsSentReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SMS_SENT) return
        // The result code is set by the telephony stack, not by the sender.
        if (resultCode == Activity.RESULT_OK) return

        val row = intent.data ?: return
        val address = intent.getStringExtra(EXTRA_ADDRESS).orEmpty()
        val notify = intent.getBooleanExtra(EXTRA_NOTIFY, false)
        val reason = reasonOf(resultCode)

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                SmsRepository.markFailed(context, row)
                BulkSmsQueue.recordLateFailure()
                if (notify && address.isNotBlank()) {
                    LanguageManager.init(context)
                    val name = ContactsRepository.nameFor(context, address)
                    MessageNotifier.notifySendFailed(context, address, name, reason)
                }
            } finally {
                pending.finish()
            }
        }
    }

    /** The word for what went wrong, in the language in use. */
    private fun reasonOf(code: Int): String = LanguageManager.getString(
        when (code) {
            SmsManager.RESULT_ERROR_NO_SERVICE -> "send_failed_no_service"
            SmsManager.RESULT_ERROR_RADIO_OFF -> "send_failed_radio_off"
            SmsManager.RESULT_ERROR_NULL_PDU -> "send_failed_generic"
            else -> "send_failed_generic"
        }
    )

    companion object {
        const val ACTION_SMS_SENT = "com.example.action.SMS_SENT"
        const val EXTRA_ADDRESS = "com.example.extra.SENT_ADDRESS"
        const val EXTRA_NOTIFY = "com.example.extra.SENT_NOTIFY"

        /**
         * The answer slip handed to the telephony stack along with a message.
         *
         * [row] is both where the verdict is written and what makes this
         * pending intent different from every other one: two messages must
         * never share it, or one verdict would settle both.
         */
        fun pendingIntent(
            context: Context,
            row: Uri?,
            address: String,
            notify: Boolean
        ): PendingIntent? {
            if (row == null) return null
            val intent = Intent(context, SmsSentReceiver::class.java).apply {
                action = ACTION_SMS_SENT
                data = row
                putExtra(EXTRA_ADDRESS, address)
                putExtra(EXTRA_NOTIFY, notify)
            }
            return try {
                PendingIntent.getBroadcast(
                    context,
                    0,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}
