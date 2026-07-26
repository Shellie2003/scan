package com.example.telephony

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

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

        if (isDeliver) {
            SmsRepository.storeIncoming(context, address, body, timestamp, subscriptionId)
        }
        notify(context, address, body)
    }

    private fun notify(context: Context, address: String, body: String) {
        createChannel(context)
        val contentIntent = PendingIntent.getActivity(
            context,
            address.hashCode(),
            Intent(Intent.ACTION_VIEW).apply {
                setClassName(context, "com.example.MainActivity")
                data = android.net.Uri.fromParts("smsto", address, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(com.example.R.drawable.ic_notification)
            .setColor(androidx.core.content.ContextCompat.getColor(context, com.example.R.color.brand_indigo))
            .setContentTitle(NumberUtils.formatForDisplay(address))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(address.hashCode(), notification)
        } catch (e: SecurityException) {
            // Notifications refused by the user.
        }
    }

    companion object {
        const val CHANNEL_ID = "incoming_sms"

        fun createChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(CHANNEL_ID) != null) return
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(com.example.R.string.sms_channel_name),
                    NotificationManager.IMPORTANCE_HIGH
                )
            )
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
