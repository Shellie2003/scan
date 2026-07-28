package com.example.telephony

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import com.example.MainActivity
import com.example.R
import com.example.data.AppSettings
import com.example.ui.util.LanguageManager

/**
 * The notification of a received message.
 *
 * It is built as a conversation, not as a line of text: the sender carries the
 * name of the contact when the phone knows it, a coloured avatar with their
 * initials, and the messages already received stay stacked in the same
 * notification. Tapping it opens that discussion — never the dialer — and the
 * answer can be written from the notification itself.
 */
object MessageNotifier {

    const val CHANNEL_ID = "incoming_sms"
    private const val GROUP_KEY = "com.example.notification.MESSAGES"

    /** Opens the conversation with [EXTRA_ADDRESS] straight away. */
    const val ACTION_OPEN_CONVERSATION = "com.example.action.OPEN_CONVERSATION"
    const val ACTION_REPLY = "com.example.action.REPLY_SMS"
    const val ACTION_MARK_READ = "com.example.action.MARK_SMS_READ"

    const val EXTRA_ADDRESS = "com.example.extra.ADDRESS"
    const val EXTRA_NAME = "com.example.extra.NAME"
    const val EXTRA_SUBSCRIPTION = "com.example.extra.SUBSCRIPTION"
    const val KEY_REPLY = "com.example.key.REPLY"

    /** One line of the conversation kept to rebuild the notification. */
    private data class Entry(val body: String, val date: Long, val fromSelf: Boolean)

    private val conversations = java.util.concurrent.ConcurrentHashMap<String, MutableList<Entry>>()

    /** Avatar colours, picked from the number so a contact keeps the same one. */
    private val avatarColors = intArrayOf(
        0xFF4A3AD0.toInt(), // brand indigo
        0xFF00696E.toInt(),
        0xFFB3261E.toInt(),
        0xFF16610E.toInt(),
        0xFF7B4E00.toInt(),
        0xFF6750A4.toInt(),
        0xFF00629E.toInt(),
        0xFF9C4146.toInt()
    )

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.sms_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.sms_channel_desc)
                enableVibration(true)
                setShowBadge(true)
            }
        )
    }

    /** A message just arrived: notify, with the name of the contact when known. */
    suspend fun notifyIncoming(
        context: Context,
        address: String,
        body: String,
        timestamp: Long,
        subscriptionId: Int
    ) {
        val name = ContactsRepository.nameFor(context, address)?.takeIf { it.isNotBlank() }
        record(address, Entry(body, timestamp, fromSelf = false))
        post(context, address, name, subscriptionId, alert = true, failed = false)
    }

    /** The user answered from the notification: keep the thread visible, silently. */
    fun notifyOutgoing(
        context: Context,
        address: String,
        name: String?,
        subscriptionId: Int,
        body: String,
        failed: Boolean = false
    ) {
        record(address, Entry(body, System.currentTimeMillis(), fromSelf = true))
        post(context, address, name?.takeIf { it.isNotBlank() }, subscriptionId, alert = false, failed = failed)
    }

    /** Removes the notification of a conversation — used when the user opens it. */
    fun cancel(context: Context, address: String) {
        conversations.remove(key(address))
        try {
            NotificationManagerCompat.from(context).cancel(notificationId(address))
        } catch (e: Exception) {
            // Nothing was posted for this conversation.
        }
    }

    fun notificationId(address: String): Int = ("sms:" + key(address)).hashCode()

    // ------------------------------------------------------------------ internals

    private fun key(address: String): String =
        NumberUtils.toNational(address).ifBlank { address.trim() }

    private fun record(address: String, entry: Entry) {
        val list = conversations.getOrPut(key(address)) { mutableListOf() }
        synchronized(list) {
            list += entry
            while (list.size > 8) list.removeAt(0)
        }
    }

    private fun history(address: String): List<Entry> {
        val list = conversations[key(address)] ?: return emptyList()
        return synchronized(list) { list.toList() }
    }

    private fun post(
        context: Context,
        address: String,
        name: String?,
        subscriptionId: Int,
        alert: Boolean,
        failed: Boolean
    ) {
        createChannel(context)
        LanguageManager.init(context)

        val display = name ?: NumberUtils.formatForDisplay(address)
        val sender = Person.Builder()
            .setName(display)
            .setKey(key(address))
            .setIcon(avatar(display))
            .setImportant(true)
            .build()
        val self = Person.Builder().setName(LanguageManager.getString("you")).build()

        val style = NotificationCompat.MessagingStyle(self)
        val entries = history(address)
        entries.forEach { entry ->
            style.addMessage(
                NotificationCompat.MessagingStyle.Message(
                    entry.body,
                    entry.date,
                    if (entry.fromSelf) null else sender
                )
            )
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(context, R.color.brand_indigo))
            .setStyle(style)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .setOnlyAlertOnce(!alert)
            .setWhen(entries.lastOrNull()?.date ?: System.currentTimeMillis())
            .setShowWhen(true)
            .setGroup(GROUP_KEY)
            .setContentIntent(openConversationIntent(context, address, name))
            .addAction(replyAction(context, address, name, subscriptionId))
            .addAction(markReadAction(context, address))
            .addAction(callAction(context, address))

        if (failed) builder.setSubText(LanguageManager.getString("reply_failed"))

        try {
            NotificationManagerCompat.from(context).notify(notificationId(address), builder.build())
        } catch (e: SecurityException) {
            // Notifications refused by the user.
        }
    }

    private fun openConversationIntent(
        context: Context,
        address: String,
        name: String?
    ): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_CONVERSATION
            // The data makes the intent unique per conversation, so two senders
            // never share the same pending intent.
            data = Uri.fromParts("smsto", address, null)
            putExtra(EXTRA_ADDRESS, address)
            putExtra(EXTRA_NAME, name.orEmpty())
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        return PendingIntent.getActivity(
            context,
            requestCode(address, 0),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun replyAction(
        context: Context,
        address: String,
        name: String?,
        subscriptionId: Int
    ): NotificationCompat.Action {
        val label = LanguageManager.getString("reply")
        val remoteInput = RemoteInput.Builder(KEY_REPLY)
            .setLabel(label)
            .build()
        val intent = Intent(context, SmsReplyReceiver::class.java).apply {
            action = ACTION_REPLY
            data = Uri.fromParts("smsto", address, null)
            putExtra(EXTRA_ADDRESS, address)
            putExtra(EXTRA_NAME, name.orEmpty())
            putExtra(EXTRA_SUBSCRIPTION, subscriptionId)
        }
        val pending = PendingIntent.getBroadcast(
            context,
            requestCode(address, 1),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or mutabilityFlag()
        )
        return NotificationCompat.Action.Builder(R.drawable.ic_action_reply, label, pending)
            .addRemoteInput(remoteInput)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
            .setShowsUserInterface(false)
            .setAllowGeneratedReplies(true)
            .build()
    }

    private fun markReadAction(context: Context, address: String): NotificationCompat.Action {
        val intent = Intent(context, SmsReplyReceiver::class.java).apply {
            action = ACTION_MARK_READ
            data = Uri.fromParts("smsto", address, null)
            putExtra(EXTRA_ADDRESS, address)
        }
        val pending = PendingIntent.getBroadcast(
            context,
            requestCode(address, 2),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Action.Builder(
            R.drawable.ic_action_mark_read,
            LanguageManager.getString("mark_read"),
            pending
        )
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ)
            .setShowsUserInterface(false)
            .build()
    }

    private fun callAction(context: Context, address: String): NotificationCompat.Action {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.fromParts("tel", address, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pending = PendingIntent.getActivity(
            context,
            requestCode(address, 3),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Action.Builder(
            R.drawable.ic_action_call,
            LanguageManager.getString("call"),
            pending
        ).build()
    }

    /** A reply carries text back to the app, which requires a mutable intent. */
    private fun mutabilityFlag(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0

    private fun requestCode(address: String, action: Int): Int = notificationId(address) * 8 + action

    /** Round avatar with the initials of the sender, in their own colour. */
    private fun avatar(display: String): IconCompat {
        val size = 128
        val initials = display.split(" ", "-")
            .filter { it.isNotBlank() && it.first().isLetter() }
            .take(2)
            .map { it.first().uppercaseChar() }
            .joinToString("")
            .ifEmpty { "#" }
        val background = avatarColors[(display.hashCode() and 0x7FFFFFFF) % avatarColors.size]

        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = background
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        paint.color = Color.WHITE
        paint.textSize = if (initials.length > 1) 52f else 64f
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(
            initials,
            size / 2f,
            size / 2f - (paint.descent() + paint.ascent()) / 2f,
            paint
        )
        return IconCompat.createWithBitmap(bitmap)
    }

    /** Subscription used to answer a conversation from the notification. */
    fun replySubscription(context: Context, subscriptionId: Int): Int =
        if (subscriptionId != AppSettings.NO_SUBSCRIPTION) {
            subscriptionId
        } else {
            AppSettings.defaultSmsSubscription(context)
        }
}
