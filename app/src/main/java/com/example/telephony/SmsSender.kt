package com.example.telephony

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.example.data.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** One recipient of a bulk send. */
data class SmsRecipient(val name: String, val number: String)

data class SmsSendReport(
    val sent: List<SmsRecipient> = emptyList(),
    val failed: List<Pair<SmsRecipient, String>> = emptyList()
) {
    val total: Int get() = sent.size + failed.size
}

object SmsSender {

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED

    /**
     * Sends the same message to several recipients through a precise SIM card.
     *
     * Messages are spaced out by [AppSettings.bulkSmsDelayMs] because operators
     * silently drop bursts sent too quickly.
     *
     * `{NAME}` and `{NUMBER}` in [body] are replaced for each recipient.
     */
    suspend fun sendBulk(
        context: Context,
        recipients: List<SmsRecipient>,
        body: String,
        subscriptionId: Int,
        onProgress: (done: Int, total: Int, current: SmsRecipient) -> Unit = { _, _, _ -> }
    ): SmsSendReport = withContext(Dispatchers.IO) {
        if (!hasPermission(context)) {
            return@withContext SmsSendReport(
                failed = recipients.map { it to "permission" }
            )
        }
        val manager = smsManager(context, subscriptionId)
            ?: return@withContext SmsSendReport(failed = recipients.map { it to "sms_unavailable" })

        val pause = AppSettings.bulkSmsDelayMs(context)
        val sent = mutableListOf<SmsRecipient>()
        val failed = mutableListOf<Pair<SmsRecipient, String>>()

        recipients.forEachIndexed { index, recipient ->
            val text = personalize(body, recipient)
            try {
                val parts = manager.divideMessage(text)
                if (parts.size <= 1) {
                    manager.sendTextMessage(recipient.number, null, text, null, null)
                } else {
                    manager.sendMultipartTextMessage(recipient.number, null, parts, null, null)
                }
                sent += recipient
            } catch (e: Exception) {
                failed += recipient to (e.message ?: e.javaClass.simpleName)
            }
            onProgress(index + 1, recipients.size, recipient)
            if (index < recipients.lastIndex && pause > 0) delay(pause)
        }
        SmsSendReport(sent, failed)
    }

    fun personalize(body: String, recipient: SmsRecipient): String = body
        .replace("{NAME}", recipient.name.ifBlank { recipient.number })
        .replace("{NUMBER}", recipient.number)

    /** Number of SMS a body will be split into, shown live while typing. */
    fun partCount(context: Context, body: String, subscriptionId: Int): Int {
        if (body.isEmpty()) return 0
        val manager = smsManager(context, subscriptionId) ?: return 1
        return try {
            manager.divideMessage(body).size
        } catch (e: Exception) {
            1
        }
    }

    @SuppressLint("MissingPermission")
    private fun smsManager(context: Context, subscriptionId: Int): SmsManager? = try {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> modernSmsManager(context, subscriptionId)
            subscriptionId != AppSettings.NO_SUBSCRIPTION ->
                legacySmsManager(subscriptionId)
            else -> legacyDefaultSmsManager()
        }
    } catch (e: Exception) {
        null
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun modernSmsManager(context: Context, subscriptionId: Int): SmsManager? {
        val manager = context.getSystemService(SmsManager::class.java) ?: return null
        return if (subscriptionId != AppSettings.NO_SUBSCRIPTION) {
            manager.createForSubscriptionId(subscriptionId)
        } else {
            manager
        }
    }

    @Suppress("DEPRECATION")
    private fun legacySmsManager(subscriptionId: Int): SmsManager =
        SmsManager.getSmsManagerForSubscriptionId(subscriptionId)

    @Suppress("DEPRECATION")
    private fun legacyDefaultSmsManager(): SmsManager = SmsManager.getDefault()
}
