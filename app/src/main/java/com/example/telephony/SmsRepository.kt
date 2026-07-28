package com.example.telephony

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Telephony
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One message of the SMS database. */
data class SmsMessageItem(
    val id: Long,
    val threadId: Long,
    val address: String,
    val body: String,
    val date: Long,
    val type: Int,
    val read: Boolean,
    val subscriptionId: Int
) {
    val isIncoming: Boolean
        get() = type == Telephony.Sms.MESSAGE_TYPE_INBOX

    val isFailed: Boolean
        get() = type == Telephony.Sms.MESSAGE_TYPE_FAILED
}

/** A conversation, represented by its most recent message. */
data class SmsThread(
    val threadId: Long,
    val address: String,
    val contactName: String,
    val snippet: String,
    val date: Long,
    val unreadCount: Int,
    val messageCount: Int
) {
    val title: String
        get() = contactName.ifBlank { NumberUtils.formatForDisplay(address) }

    val initials: String
        get() = contactName.split(" ")
            .filter { it.isNotBlank() }
            .take(2)
            .map { it.first().uppercaseChar() }
            .joinToString("")
            .ifEmpty { "#" }
}

/**
 * Reads and writes the SMS database of the phone.
 *
 * Reading only needs `READ_SMS`. Writing — storing a received or a sent message —
 * is reserved by Android to the default SMS app, so every write is attempted and
 * silently skipped when the app does not hold that role.
 */
object SmsRepository {

    private val projection = arrayOf(
        Telephony.Sms._ID,
        Telephony.Sms.THREAD_ID,
        Telephony.Sms.ADDRESS,
        Telephony.Sms.BODY,
        Telephony.Sms.DATE,
        Telephony.Sms.TYPE,
        Telephony.Sms.READ,
        Telephony.Sms.SUBSCRIPTION_ID
    )

    fun hasReadPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED

    /** URI observed to refresh the screens as soon as a message arrives or leaves. */
    val contentUri: Uri = Telephony.Sms.CONTENT_URI

    /**
     * The conversations, most recent first.
     *
     * The provider is read once and grouped by thread, which keeps a single query
     * for the whole inbox.
     */
    @SuppressLint("MissingPermission")
    suspend fun loadThreads(
        context: Context,
        contacts: List<Contact> = emptyList(),
        limit: Int = 1000
    ): List<SmsThread> = withContext(Dispatchers.IO) {
        val messages = queryMessages(context, selection = null, args = null, limit = limit)
        if (messages.isEmpty()) return@withContext emptyList()

        messages
            .groupBy { it.threadId }
            .map { (threadId, thread) ->
                val newest = thread.maxByOrNull { it.date } ?: thread.first()
                val address = thread.firstOrNull { it.address.isNotBlank() }?.address.orEmpty()
                SmsThread(
                    threadId = threadId,
                    address = address,
                    contactName = contacts.firstOrNull {
                        NumberUtils.sameNumber(it.number, address)
                    }?.name.orEmpty(),
                    snippet = newest.body,
                    date = newest.date,
                    unreadCount = thread.count { it.isIncoming && !it.read },
                    messageCount = thread.size
                )
            }
            .sortedByDescending { it.date }
    }

    /** Every message of one conversation, oldest first. */
    suspend fun loadMessages(context: Context, threadId: Long): List<SmsMessageItem> =
        withContext(Dispatchers.IO) {
            queryMessages(
                context,
                selection = Telephony.Sms.THREAD_ID + " = ?",
                args = arrayOf(threadId.toString()),
                limit = 500
            ).sortedBy { it.date }
        }

    /** Messages exchanged with a number, for a conversation opened from a contact. */
    suspend fun loadMessagesForAddress(context: Context, address: String): List<SmsMessageItem> =
        withContext(Dispatchers.IO) {
            val national = NumberUtils.toNational(address)
            queryMessages(context, null, null, limit = 2000)
                .filter { NumberUtils.sameNumber(it.address, national) }
                .sortedBy { it.date }
        }

    @SuppressLint("MissingPermission")
    private fun queryMessages(
        context: Context,
        selection: String?,
        args: Array<String>?,
        limit: Int
    ): List<SmsMessageItem> {
        if (!hasReadPermission(context)) return emptyList()
        val result = mutableListOf<SmsMessageItem>()
        try {
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                projection,
                selection,
                args,
                Telephony.Sms.DATE + " DESC"
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndex(Telephony.Sms._ID)
                val threadIndex = cursor.getColumnIndex(Telephony.Sms.THREAD_ID)
                val addressIndex = cursor.getColumnIndex(Telephony.Sms.ADDRESS)
                val bodyIndex = cursor.getColumnIndex(Telephony.Sms.BODY)
                val dateIndex = cursor.getColumnIndex(Telephony.Sms.DATE)
                val typeIndex = cursor.getColumnIndex(Telephony.Sms.TYPE)
                val readIndex = cursor.getColumnIndex(Telephony.Sms.READ)
                val subIndex = cursor.getColumnIndex(Telephony.Sms.SUBSCRIPTION_ID)
                while (cursor.moveToNext() && result.size < limit) {
                    result += SmsMessageItem(
                        id = if (idIndex >= 0) cursor.getLong(idIndex) else 0L,
                        threadId = if (threadIndex >= 0) cursor.getLong(threadIndex) else 0L,
                        address = if (addressIndex >= 0) cursor.getString(addressIndex).orEmpty() else "",
                        body = if (bodyIndex >= 0) cursor.getString(bodyIndex).orEmpty() else "",
                        date = if (dateIndex >= 0) cursor.getLong(dateIndex) else 0L,
                        type = if (typeIndex >= 0) cursor.getInt(typeIndex) else 0,
                        read = readIndex < 0 || cursor.getInt(readIndex) == 1,
                        subscriptionId = if (subIndex >= 0) cursor.getInt(subIndex) else -1
                    )
                }
            }
        } catch (e: SecurityException) {
            return emptyList()
        } catch (e: Exception) {
            return emptyList()
        }
        return result
    }

    /** Marks a conversation as read. Only effective for the default SMS app. */
    suspend fun markThreadRead(context: Context, threadId: Long) = withContext(Dispatchers.IO) {
        try {
            val values = ContentValues().apply {
                put(Telephony.Sms.READ, 1)
                put(Telephony.Sms.SEEN, 1)
            }
            context.contentResolver.update(
                Telephony.Sms.CONTENT_URI,
                values,
                Telephony.Sms.THREAD_ID + " = ? AND " + Telephony.Sms.READ + " = 0",
                arrayOf(threadId.toString())
            )
        } catch (e: Exception) {
            // Not the default SMS app: the provider stays untouched.
        }
        Unit
    }

    /**
     * Marks every message received from a number as read, used when the user
     * answers or dismisses the notification of that conversation.
     */
    suspend fun markAddressRead(context: Context, address: String) = withContext(Dispatchers.IO) {
        val ids = loadMessagesForAddress(context, address)
            .filter { it.isIncoming && !it.read }
            .map { it.id }
        if (ids.isEmpty()) return@withContext
        try {
            val values = ContentValues().apply {
                put(Telephony.Sms.READ, 1)
                put(Telephony.Sms.SEEN, 1)
            }
            context.contentResolver.update(
                Telephony.Sms.CONTENT_URI,
                values,
                Telephony.Sms._ID + " IN (" + ids.joinToString(",") + ")",
                null
            )
        } catch (e: Exception) {
            // Not the default SMS app: the provider stays untouched.
        }
        Unit
    }

    /**
     * Stores an incoming message. Android only delivers `SMS_DELIVER` to the
     * default SMS app, and expects that app to persist the message itself.
     */
    fun storeIncoming(
        context: Context,
        address: String,
        body: String,
        timestamp: Long,
        subscriptionId: Int
    ): Boolean = write(context, Telephony.Sms.Inbox.CONTENT_URI) {
        put(Telephony.Sms.ADDRESS, address)
        put(Telephony.Sms.BODY, body)
        put(Telephony.Sms.DATE, System.currentTimeMillis())
        put(Telephony.Sms.DATE_SENT, timestamp)
        put(Telephony.Sms.READ, 0)
        put(Telephony.Sms.SEEN, 0)
        put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
        if (subscriptionId >= 0) put(Telephony.Sms.SUBSCRIPTION_ID, subscriptionId)
    }

    /** Keeps a copy of what we send, the way any messaging app does. */
    fun storeSent(
        context: Context,
        address: String,
        body: String,
        subscriptionId: Int
    ): Boolean = write(context, Telephony.Sms.Sent.CONTENT_URI) {
        put(Telephony.Sms.ADDRESS, address)
        put(Telephony.Sms.BODY, body)
        put(Telephony.Sms.DATE, System.currentTimeMillis())
        put(Telephony.Sms.READ, 1)
        put(Telephony.Sms.SEEN, 1)
        put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT)
        if (subscriptionId >= 0) put(Telephony.Sms.SUBSCRIPTION_ID, subscriptionId)
    }

    suspend fun delete(context: Context, messageId: Long): Boolean = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.delete(
                Telephony.Sms.CONTENT_URI,
                Telephony.Sms._ID + " = ?",
                arrayOf(messageId.toString())
            ) > 0
        } catch (e: Exception) {
            false
        }
    }

    /** Deletes the messages the user picked in a conversation. */
    suspend fun deleteMessages(context: Context, ids: Collection<Long>): Int =
        withContext(Dispatchers.IO) {
            if (ids.isEmpty()) return@withContext 0
            try {
                context.contentResolver.delete(
                    Telephony.Sms.CONTENT_URI,
                    Telephony.Sms._ID + " IN (" + ids.joinToString(",") + ")",
                    null
                )
            } catch (e: Exception) {
                0
            }
        }

    suspend fun deleteThread(context: Context, threadId: Long): Boolean = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.delete(
                Telephony.Sms.CONTENT_URI,
                Telephony.Sms.THREAD_ID + " = ?",
                arrayOf(threadId.toString())
            ) > 0
        } catch (e: Exception) {
            false
        }
    }

    private fun write(context: Context, uri: Uri, block: ContentValues.() -> Unit): Boolean = try {
        context.contentResolver.insert(uri, ContentValues().apply(block)) != null
    } catch (e: Exception) {
        false
    }
}
