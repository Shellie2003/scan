package com.example.telephony

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.AppSettings
import com.example.data.DatabaseProvider
import com.example.data.model.ScanHistory
import com.example.ui.util.LanguageManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The state of a bulk send, readable from anywhere in the process.
 *
 * The screen that started the campaign observes it, and finds it again as it
 * is when the user comes back to the tab.
 */
object BulkSmsQueue {

    data class Progress(val done: Int, val total: Int)

    private val _progress = MutableStateFlow<Progress?>(null)
    val progress: StateFlow<Progress?> = _progress.asStateFlow()

    private val _report = MutableStateFlow<SmsSendReport?>(null)
    val report: StateFlow<SmsSendReport?> = _report.asStateFlow()

    /** What a running campaign needs to know, handed to the service. */
    internal class Campaign(
        val groups: Map<Int, List<SmsRecipient>>,
        val body: String,
        val subscriptionId: Int,
        val simLabel: String
    ) {
        val total: Int get() = groups.values.sumOf { it.size }
    }

    @Volatile
    private var pending: Campaign? = null

    val isRunning: Boolean get() = _progress.value != null

    fun clearReport() {
        _report.value = null
    }

    internal fun submit(campaign: Campaign) {
        pending = campaign
    }

    internal fun take(): Campaign? {
        val campaign = pending
        pending = null
        return campaign
    }

    internal fun publishProgress(done: Int, total: Int) {
        _progress.value = Progress(done, total)
    }

    internal fun finish(report: SmsSendReport) {
        _progress.value = null
        _report.value = report
    }
}

/**
 * Sends a bulk campaign from a foreground service.
 *
 * Two hundred messages spaced out by the pause the operators need take more
 * than a minute. Run from a screen, the send died with it; run from the view
 * model, it still died when the user left the application. Here it keeps going,
 * with a notification that shows where it is and stays honest about the result.
 */
class BulkSmsService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val campaign = BulkSmsQueue.take()
        if (campaign == null || campaign.total == 0) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        LanguageManager.init(this)
        createChannel(this)
        // Must happen right away: the system gives a few seconds only.
        startInForeground(campaign.total)

        scope.launch {
            val report = send(campaign)
            record(campaign, report)
            BulkSmsQueue.finish(report)
            notifyReport(report)
            ServiceCompat.stopForeground(this@BulkSmsService, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private suspend fun send(campaign: BulkSmsQueue.Campaign): SmsSendReport {
        var alreadySent = 0
        var merged = SmsSendReport()
        BulkSmsQueue.publishProgress(0, campaign.total)

        campaign.groups.forEach { (subscriptionId, recipients) ->
            val partial = SmsSender.sendBulk(
                context = this,
                recipients = recipients,
                body = campaign.body,
                subscriptionId = subscriptionId
            ) { done, _, _ ->
                val total = campaign.total
                BulkSmsQueue.publishProgress(alreadySent + done, total)
                updateNotification(alreadySent + done, total)
            }
            alreadySent += recipients.size
            merged = SmsSendReport(
                sent = merged.sent + partial.sent,
                failed = merged.failed + partial.failed
            )
        }
        return merged
    }

    /** The campaign lands in the history like every other operation of the app. */
    private suspend fun record(campaign: BulkSmsQueue.Campaign, report: SmsSendReport) {
        try {
            DatabaseProvider.getDatabase(this).scanHistoryDao().insertHistory(
                ScanHistory(
                    operatorId = 0,
                    operatorName = "",
                    code = "",
                    dialedCode = "",
                    subscriptionId = campaign.subscriptionId,
                    simLabel = campaign.simLabel,
                    kind = ScanHistory.KIND_SMS,
                    label = LanguageManager.getString(
                        "recipients_count",
                        "count" to report.sent.size
                    )
                )
            )
        } catch (e: Exception) {
            // The history is a comfort, never a reason to lose the send.
        }
    }

    private fun startInForeground(total: Int) {
        try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                buildProgressNotification(0, total),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                } else {
                    0
                }
            )
        } catch (e: Exception) {
            // Refused by the system: the send still runs while the service lives.
        }
    }

    private fun updateNotification(done: Int, total: Int) {
        try {
            NotificationManagerCompat.from(this)
                .notify(NOTIFICATION_ID, buildProgressNotification(done, total))
        } catch (e: SecurityException) {
            // Notifications refused by the user.
        }
    }

    private fun buildProgressNotification(done: Int, total: Int) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(this, R.color.brand_indigo))
            .setContentTitle(LanguageManager.getString("messages_title"))
            .setContentText(
                LanguageManager.getString("sending", "done" to done, "total" to total)
            )
            .setProgress(total, done, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openApp())
            .build()

    private fun notifyReport(report: SmsSendReport) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(this, R.color.brand_indigo))
            .setContentTitle(LanguageManager.getString("messages_title"))
            .setContentText(
                LanguageManager.getString(
                    "send_report",
                    "sent" to report.sent.size,
                    "failed" to report.failed.size
                )
            )
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(openApp())
            .build()
        try {
            NotificationManagerCompat.from(this).notify(REPORT_NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // Notifications refused by the user.
        }
    }

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    companion object {
        const val CHANNEL_ID = "bulk_sms"
        private const val NOTIFICATION_ID = 4301
        private const val REPORT_NOTIFICATION_ID = 4302

        /**
         * Starts a campaign. Called from a screen, so the service is allowed to
         * go to the foreground.
         */
        fun start(
            context: Context,
            groups: Map<Int, List<SmsRecipient>>,
            body: String,
            subscriptionId: Int = AppSettings.NO_SUBSCRIPTION,
            simLabel: String = ""
        ) {
            if (body.isBlank() || groups.values.sumOf { it.size } == 0) return
            if (BulkSmsQueue.isRunning) return
            BulkSmsQueue.submit(
                BulkSmsQueue.Campaign(
                    groups = groups,
                    body = body,
                    subscriptionId = subscriptionId,
                    simLabel = simLabel
                )
            )
            val intent = Intent(context, BulkSmsService::class.java)
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                // The campaign never started: nothing is left pending.
                BulkSmsQueue.take()
            }
        }

        fun createChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(CHANNEL_ID) != null) return
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.bulk_channel_name),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = context.getString(R.string.bulk_channel_desc)
                    setShowBadge(false)
                }
            )
        }
    }
}
