package com.example.telephony

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class CallEntry(
    val id: Long,
    val number: String,
    val name: String,
    /** One of the `CallLog.Calls.*_TYPE` constants. */
    val type: Int,
    val date: Long,
    val durationSeconds: Long,
    /** Phone account of the SIM used, matched back to a subscription id when possible. */
    val phoneAccountId: String
) {
    val isMissed: Boolean
        get() = type == CallLog.Calls.MISSED_TYPE || type == CallLog.Calls.REJECTED_TYPE

    val isIncoming: Boolean
        get() = type == CallLog.Calls.INCOMING_TYPE

    val isOutgoing: Boolean
        get() = type == CallLog.Calls.OUTGOING_TYPE
}

object CallLogRepository {

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALL_LOG
        ) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    suspend fun load(context: Context, limit: Int = 300): List<CallEntry> =
        withContext(Dispatchers.IO) {
            if (!hasPermission(context)) return@withContext emptyList()
            val projection = arrayOf(
                CallLog.Calls._ID,
                CallLog.Calls.NUMBER,
                CallLog.Calls.CACHED_NAME,
                CallLog.Calls.TYPE,
                CallLog.Calls.DATE,
                CallLog.Calls.DURATION,
                CallLog.Calls.PHONE_ACCOUNT_ID
            )
            val entries = mutableListOf<CallEntry>()
            try {
                context.contentResolver.query(
                    CallLog.Calls.CONTENT_URI,
                    projection,
                    null,
                    null,
                    CallLog.Calls.DATE + " DESC"
                )?.use { cursor ->
                    val idIndex = cursor.getColumnIndex(CallLog.Calls._ID)
                    val numberIndex = cursor.getColumnIndex(CallLog.Calls.NUMBER)
                    val nameIndex = cursor.getColumnIndex(CallLog.Calls.CACHED_NAME)
                    val typeIndex = cursor.getColumnIndex(CallLog.Calls.TYPE)
                    val dateIndex = cursor.getColumnIndex(CallLog.Calls.DATE)
                    val durationIndex = cursor.getColumnIndex(CallLog.Calls.DURATION)
                    val accountIndex = cursor.getColumnIndex(CallLog.Calls.PHONE_ACCOUNT_ID)
                    while (cursor.moveToNext() && entries.size < limit) {
                        entries += CallEntry(
                            id = if (idIndex >= 0) cursor.getLong(idIndex) else 0L,
                            number = if (numberIndex >= 0) cursor.getString(numberIndex).orEmpty() else "",
                            name = if (nameIndex >= 0) cursor.getString(nameIndex).orEmpty() else "",
                            type = if (typeIndex >= 0) cursor.getInt(typeIndex) else 0,
                            date = if (dateIndex >= 0) cursor.getLong(dateIndex) else 0L,
                            durationSeconds = if (durationIndex >= 0) cursor.getLong(durationIndex) else 0L,
                            phoneAccountId = if (accountIndex >= 0) cursor.getString(accountIndex).orEmpty() else ""
                        )
                    }
                }
            } catch (e: SecurityException) {
                return@withContext emptyList()
            } catch (e: Exception) {
                return@withContext emptyList()
            }
            entries
        }

    /** Groups consecutive calls with the same number, the way a call log reads. */
    fun group(entries: List<CallEntry>): List<List<CallEntry>> {
        val groups = mutableListOf<MutableList<CallEntry>>()
        entries.forEach { entry ->
            val last = groups.lastOrNull()
            if (last != null && NumberUtils.sameNumber(last.first().number, entry.number) &&
                last.first().isMissed == entry.isMissed
            ) {
                last += entry
            } else {
                groups += mutableListOf(entry)
            }
        }
        return groups
    }
}
