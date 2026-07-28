package com.example.telephony

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Receives incoming text messages.
 *
 * Two cases, and the difference matters:
 *  - `SMS_DELIVER` is sent only to the default SMS app, and that app is
 *    responsible for writing the message to the SMS database. Without this, an
 *    incoming message would simply be lost.
 *  - `SMS_RECEIVED` is sent to every app holding `RECEIVE_SMS`; the system has
 *    already stored the message, so we only refresh and notify.
 */
class SmsDeliverReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val isDeliver = action == Telephony.Sms.Intents.SMS_DELIVER_ACTION
        val isReceived = action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION
        if (!isDeliver && !isReceived) return
        // The default SMS app receives both broadcasts for the same message;
        // only the delivery one is acted upon so nothing is done twice.
        if (isReceived && DefaultSmsManager.isDefaultSmsApp(context)) return

        val messages = try {
            Telephony.Sms.Intents.getMessagesFromIntent(intent)
        } catch (e: Exception) {
            null
        } ?: return
        if (messages.isEmpty()) return

        // A long text arrives split in parts that all belong to the same sender.
        val address = messages.firstOrNull()?.displayOriginatingAddress.orEmpty()
        val body = messages.joinToString("") { it.displayMessageBody.orEmpty() }
        val timestamp = messages.firstOrNull()?.timestampMillis ?: System.currentTimeMillis()
        val subscriptionId = intent.getIntExtra("subscription", -1)

        // Storing the message and reading the contact name both touch content
        // providers: the broadcast is kept alive while that runs off the main thread.
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (isDeliver) {
                    SmsRepository.storeIncoming(context, address, body, timestamp, subscriptionId)
                }
                MessageNotifier.notifyIncoming(context, address, body, timestamp, subscriptionId)
            } finally {
                pending.finish()
            }
        }
    }
}

/**
 * Required to be eligible as the default SMS app. Multimedia messages are not
 * handled by the app, they are left to the system.
 */
class MmsDeliverReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = Unit
}
