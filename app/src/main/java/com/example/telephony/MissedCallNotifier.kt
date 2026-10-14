package com.example.telephony

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.ui.util.LanguageManager

/**
 * The notification of a call that was not taken.
 *
 * Being the phone application of the device means owning this: nothing else
 * will say that someone called. Without it a missed call left no trace but a
 * line in a history nobody opens — and in a day of work, that is a customer
 * lost.
 *
 * One notification per number, carrying the name of the contact when the phone
 * knows it, and the two things one does about a missed call: call back, or write.
 */
object MissedCallNotifier {

    const val CHANNEL_ID = "missed_calls"

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.missed_calls_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.missed_calls_channel_desc)
                setShowBadge(true)
            }
        )
    }

    fun notifyMissed(context: Context, number: String, name: String?) {
        if (number.isBlank()) return
        createChannel(context)
        LanguageManager.init(context)

        val display = name?.takeIf { it.isNotBlank() } ?: NumberUtils.formatForDisplay(number)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_action_call_end)
            .setColor(ContextCompat.getColor(context, R.color.brand_indigo))
            .setContentTitle(LanguageManager.getString("missed_call"))
            .setContentText(display)
            .setCategory(NotificationCompat.CATEGORY_MISSED_CALL)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setShowWhen(true)
            .setWhen(System.currentTimeMillis())
            .setContentIntent(activity(context, "tel", number, 0))
            .addAction(
                R.drawable.ic_action_call,
                LanguageManager.getString("call"),
                activity(context, "tel", number, 1)
            )
            .addAction(
                R.drawable.ic_action_reply,
                LanguageManager.getString("send_message"),
                activity(context, "smsto", number, 2)
            )

        try {
            NotificationManagerCompat.from(context).notify(idOf(number), builder.build())
        } catch (e: SecurityException) {
            // Notifications refused: the call stays in the history.
        } catch (e: Exception) {
            // A notification is never worth taking the call process down.
        }
    }

    /** Removes the notification, which is what calling the number back means. */
    fun cancel(context: Context, number: String) {
        try {
            NotificationManagerCompat.from(context).cancel(idOf(number))
        } catch (e: Exception) {
            // Nothing was posted for this number.
        }
    }

    private fun activity(
        context: Context,
        scheme: String,
        number: String,
        action: Int
    ): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            this.action = Intent.ACTION_VIEW
            data = Uri.fromParts(scheme, number, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        return PendingIntent.getActivity(
            context,
            idOf(number) * 4 + action,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun idOf(number: String): Int = ("missed:" + NumberUtils.toNational(number)).hashCode()
}
