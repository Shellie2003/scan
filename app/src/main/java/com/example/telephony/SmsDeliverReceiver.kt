package com.example.telephony

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.example.data.BlockedNumbers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Receives incoming text messages.
 *
 * Only `SMS_DELIVER` is acted upon. It is sent to the default SMS app alone,
 * and that app is responsible for writing the message to the SMS database —
 * without this, an incoming message would simply be lost.
 *
 * `SMS_RECEIVED`, which every app holding `RECEIVE_SMS` gets, is deliberately
 * ignored: when SCAN is not the default SMS app, the app that *is* already
 * stores the message and notifies the user, and notifying here too would show
 * every message twice.
 */
class SmsDeliverReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return

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
                SmsRepository.storeIncoming(context, address, body, timestamp, subscriptionId)
                if (BlockedNumbers.isBlocked(context, address)) {
                    // Kept, because the app is the SMS app of the phone and
                    // losing a message is not its decision — but read, so a
                    // blocked number leaves neither a sound nor a badge.
                    SmsRepository.markAddressRead(context, address)
                } else {
                    MessageNotifier.notifyIncoming(context, address, body, timestamp, subscriptionId)
                }
            } finally {
                pending.finish()
            }
        }
    }
}

/**
 * Multimedia messages.
 *
 * Only the default SMS app receives this, and only it can fetch what the
 * operator is holding. SCAN cannot assemble a multimedia message yet — that is
 * a download over the operator MMS network and a multipart PDU to parse — but
 * staying silent would be the worst of both: the message reaches nobody and
 * nobody knows it exists.
 *
 * So the notification is read for who sent it, and the user is told. The
 * message itself waits at the operator for several days, which is long enough
 * to hand the SMS role back to another application and receive it there.
 */
class MmsDeliverReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pdu = try {
            intent.getByteArrayExtra("data")
        } catch (e: Exception) {
            null
        }
        val notification = MmsPdu.readNotification(pdu)

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val from = notification.from.orEmpty()
                if (from.isNotBlank() && BlockedNumbers.isBlocked(context, from)) return@launch
                val name = from
                    .takeIf { it.isNotBlank() }
                    ?.let { ContactsRepository.nameFor(context, it) }
                MessageNotifier.notifyMmsWaiting(
                    context = context,
                    address = from,
                    name = name,
                    subject = notification.subject
                )
            } finally {
                pending.finish()
            }
        }
    }
}
