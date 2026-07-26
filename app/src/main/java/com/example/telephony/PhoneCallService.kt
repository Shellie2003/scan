package com.example.telephony

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.ui.call.InCallActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The in-call service the system binds to once the app is the default phone app.
 *
 * It owns nothing: every call is pushed to [OngoingCallStore], and the call
 * screen is either started directly or offered through a full screen
 * notification when the system forbids starting an activity from the background.
 */
class PhoneCallService : InCallService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        OngoingCallStore.attach(this)
        OngoingCallStore.add(call)
        resolveContactName(call)
        @Suppress("DEPRECATION")
        val ringing = call.state == Call.STATE_RINGING
        notifyCall(call, ringing)
        InCallActivity.start(this)
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        OngoingCallStore.remove(call)
        if (!OngoingCallStore.hasCalls()) {
            NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID)
            OngoingCallStore.detach()
        }
    }

    override fun onCallAudioStateChanged(audioState: CallAudioState) {
        super.onCallAudioStateChanged(audioState)
        OngoingCallStore.onAudioStateChanged(audioState)
    }

    private fun resolveContactName(call: Call) {
        val number = call.details?.handle?.schemeSpecificPart ?: return
        scope.launch {
            val name = ContactsRepository.nameFor(this@PhoneCallService, number)
            if (!name.isNullOrBlank()) OngoingCallStore.cacheName(number, name)
        }
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun notifyCall(call: Call, ringing: Boolean) {
        createChannel(this)
        val number = call.details?.handle?.schemeSpecificPart.orEmpty()
        val fullScreenIntent = PendingIntent.getActivity(
            this,
            0,
            InCallActivity.intent(this),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            // The system glyph on purpose: in the status bar, a call must read
            // as a call. The SCAN colour below is what says which app it is.
            .setSmallIcon(android.R.drawable.stat_sys_phone_call)
            .setColor(androidx.core.content.ContextCompat.getColor(this, com.example.R.color.brand_indigo))
            .setColorized(true)
            .setContentTitle(
                if (ringing) {
                    getString(com.example.R.string.incoming_call)
                } else {
                    getString(com.example.R.string.ongoing_call)
                }
            )
            .setContentText(NumberUtils.formatForDisplay(number))
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setContentIntent(fullScreenIntent)
        if (ringing) builder.setFullScreenIntent(fullScreenIntent, true)
        try {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, builder.build())
        } catch (e: SecurityException) {
            // Notification permission refused: the call screen is still started directly.
        }
    }

    companion object {
        const val CHANNEL_ID = "ongoing_calls"
        const val NOTIFICATION_ID = 4201

        fun createChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(CHANNEL_ID) != null) return
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(com.example.R.string.calls_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(com.example.R.string.calls_channel_desc)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }
    }
}

/** Convenience for other components that need to reach the call screen. */
fun Context.startInCallScreen() {
    startActivity(
        InCallActivity.intent(this).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}
