package com.example.telephony

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.DisconnectCause
import android.telecom.InCallService
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import com.example.data.BlockedNumbers
import com.example.ui.call.InCallActivity
import com.example.ui.util.LanguageManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * The in-call service the system binds to once the app is the default phone app.
 *
 * It owns nothing: every call is pushed to [OngoingCallStore], and what the
 * user sees is either the call screen — on a phone that is asleep or locked —
 * or the band at the top, which carries the buttons to answer and to refuse.
 */
class PhoneCallService : InCallService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        // The notification follows the call instead of being written once: it
        // used to keep reading "incoming call" for the whole conversation, and
        // it showed a raw number even when the contact was known.
        scope.launch {
            OngoingCallStore.calls.collect { calls ->
                // A call that is ringing comes first: during a conversation, a
                // second call must be the one the band lets you answer.
                val call = calls.firstOrNull { it.isRinging }
                    ?: calls.firstOrNull { it.isActive }
                    ?: calls.firstOrNull { !it.isDisconnected }
                if (call == null) cancelNotification() else notifyCall(call)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        if (rejectIfBlocked(call)) return
        OngoingCallStore.attach(this)
        OngoingCallStore.add(call)
        resolveContactName(call)
        // Calling back — or being called again — settles the missed call.
        call.details?.handle?.schemeSpecificPart?.takeIf { it.isNotBlank() }?.let { number ->
            MissedCallNotifier.cancel(this, number)
        }
        if (shouldTakeTheScreen(call)) InCallActivity.start(this)
    }

    /**
     * Whether the call screen should take over the whole display.
     *
     * A phone that is asleep or locked has nothing else to show, so the call
     * screen comes up. A phone being used — reading a message, typing a number —
     * keeps what is on it: the band at the top carries the two buttons, and
     * answering no longer costs a detour through a screen nobody asked for.
     *
     * Unless notifications are refused: there would then be no band at all, and
     * an incoming call that shows nothing is a call that is lost.
     */
    private fun shouldTakeTheScreen(call: Call): Boolean {
        if (stateOf(call) != Call.STATE_RINGING) return true
        val notificationsAllowed = try {
            NotificationManagerCompat.from(this).areNotificationsEnabled()
        } catch (e: Exception) {
            false
        }
        if (!notificationsAllowed) return true
        val asleep = (getSystemService(Context.POWER_SERVICE) as? PowerManager)
            ?.isInteractive == false
        val locked = (getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager)
            ?.isKeyguardLocked == true
        return asleep || locked
    }

    /**
     * A blocked number is hung up before anything is shown.
     *
     * Only a ringing call is looked at: a call placed by the user to a number
     * they blocked is a call they just asked for.
     */
    private fun rejectIfBlocked(call: Call): Boolean {
        if (stateOf(call) != Call.STATE_RINGING) return false
        val number = call.details?.handle?.schemeSpecificPart.orEmpty()
        if (number.isBlank() || !BlockedNumbers.isBlocked(this, number)) return false
        return try {
            call.reject(false, null)
            true
        } catch (e: Exception) {
            // The refusal did not go through: better a call that rings than one
            // that rings with no screen to answer it on.
            false
        }
    }

    @Suppress("DEPRECATION")
    private fun stateOf(call: Call): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            call.details?.state ?: Call.STATE_NEW
        } else {
            call.state
        }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        notifyIfMissed(call)
        OngoingCallStore.remove(call)
        if (!OngoingCallStore.hasCalls()) {
            cancelNotification()
            OngoingCallStore.detach()
        }
    }

    /**
     * A call nobody took leaves a notification behind.
     *
     * Only a call the telecom framework itself calls missed: a call the user
     * refused on purpose is not a call they need to be reminded of, and neither
     * is one from a number they blocked.
     */
    private fun notifyIfMissed(call: Call) {
        val details = call.details ?: return
        if (details.disconnectCause?.code != DisconnectCause.MISSED) return
        val number = details.handle?.schemeSpecificPart.orEmpty()
        if (number.isBlank() || BlockedNumbers.isBlocked(this, number)) return
        scope.launch {
            val name = ContactsRepository.nameFor(this@PhoneCallService, number)
            MissedCallNotifier.notifyMissed(this@PhoneCallService, number, name)
        }
    }

    private fun cancelNotification() {
        try {
            NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID)
        } catch (e: Exception) {
            // Nothing was posted.
        }
    }

    // Replaced by onCallEndpointChanged on Android 14, which does not exist on
    // the phones this application is made for. The old callback is the one that
    // works from Android 7 up, so it stays — declared, not left as a warning.
    @Deprecated("Kept for Android 7 to 13; the endpoint API only exists from 14.")
    @Suppress("DEPRECATION")
    override fun onCallAudioStateChanged(audioState: CallAudioState) {
        @Suppress("DEPRECATION")
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

    /**
     * The band at the top of the screen, with what to do about the call on it.
     *
     * A ringing call carries its two buttons — answer and refuse — and an
     * ongoing one the button to hang up, so a call is handled without leaving
     * whatever the user was doing. The call style of the system is asked for
     * first; a phone that refuses it still gets the same two buttons, plainly.
     */
    @android.annotation.SuppressLint("MissingPermission")
    private fun notifyCall(call: CallSnapshot) {
        createChannel(this)
        LanguageManager.init(this)

        val fullScreenIntent = PendingIntent.getActivity(
            this,
            0,
            InCallActivity.intent(this),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val answer = CallActionReceiver.pendingIntent(
            this,
            CallActionReceiver.ACTION_ANSWER,
            call.id
        )
        val decline = CallActionReceiver.pendingIntent(
            this,
            CallActionReceiver.ACTION_DECLINE,
            call.id
        )
        val hangUp = CallActionReceiver.pendingIntent(
            this,
            CallActionReceiver.ACTION_HANG_UP,
            call.id
        )

        val caller = call.label.ifBlank { LanguageManager.getString("unknown_number") }
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            // The system glyph on purpose: in the status bar, a call must read
            // as a call. The SCAN colour below is what says which app it is.
            .setSmallIcon(android.R.drawable.stat_sys_phone_call)
            .setColor(androidx.core.content.ContextCompat.getColor(this, com.example.R.color.brand_indigo))
            .setColorized(true)
            .setContentTitle(
                if (call.isRinging) {
                    getString(com.example.R.string.incoming_call)
                } else {
                    getString(com.example.R.string.ongoing_call)
                }
            )
            // The name of the contact when the phone knows it, the number otherwise.
            .setContentText(caller)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(fullScreenIntent)

        val person = Person.Builder().setName(caller).setKey(call.id).setImportant(true).build()
        val styled = runCatching {
            builder.setStyle(
                if (call.isRinging) {
                    NotificationCompat.CallStyle.forIncomingCall(person, decline, answer)
                } else {
                    NotificationCompat.CallStyle.forOngoingCall(person, hangUp)
                }
            )
        }.isSuccess

        if (!styled) {
            // No call style on this phone: the buttons are added as plain actions,
            // which is the one thing that must not be lost.
            if (call.isRinging) {
                builder.addAction(
                    com.example.R.drawable.ic_action_call,
                    LanguageManager.getString("answer"),
                    answer
                )
                builder.addAction(
                    com.example.R.drawable.ic_action_call_end,
                    LanguageManager.getString("decline"),
                    decline
                )
            } else {
                builder.addAction(
                    com.example.R.drawable.ic_action_call_end,
                    LanguageManager.getString("hang_up"),
                    hangUp
                )
            }
        }

        if (call.isRinging) builder.setFullScreenIntent(fullScreenIntent, true)
        try {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, builder.build())
        } catch (e: SecurityException) {
            // Notification permission refused: the call screen is still started directly.
        } catch (e: Exception) {
            // A notification is never worth taking the call process down.
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
