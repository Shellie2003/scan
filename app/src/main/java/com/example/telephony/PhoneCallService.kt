package com.example.telephony

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.PowerManager
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.DisconnectCause
import android.telecom.InCallService
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.ServiceCompat
import com.example.data.BlockedNumbers
import com.example.data.DatabaseProvider
import com.example.ui.call.InCallActivity
import com.example.ui.util.LanguageManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The in-call service the system binds to once the app is the default phone app.
 *
 * It owns nothing: every call is pushed to [OngoingCallStore], and what the
 * user sees is either the call screen — on a phone that is asleep or locked —
 * or the band at the top, which carries the buttons to answer and to refuse.
 */
class PhoneCallService : InCallService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** Whether the band is currently carried by the service itself. */
    private var foreground = false

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
        // Before the scope dies: a band left behind would stay on the screen
        // with no call behind it and no way to dismiss it.
        cancelNotification()
        super.onDestroy()
        scope.cancel()
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        if (rejectIfBlocked(call)) return
        OngoingCallStore.attach(this)
        OngoingCallStore.add(call)
        resolveContactName(call)
        resolveSim(call)
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
        if (foreground) {
            foreground = false
            try {
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            } catch (e: Exception) {
                // Already down.
            }
        }
        try {
            NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID)
        } catch (e: Exception) {
            // Nothing was posted.
        }
    }

    /**
     * Posts the band so that its buttons survive.
     *
     * The call style of the system is kept only for a notification that belongs
     * to a foreground service or carries a full-screen intent. An incoming call
     * has the second; a conversation in progress has neither, and the button to
     * hang up was being dropped on the way to the status bar — which is exactly
     * the button the user was asking for.
     *
     * The promotion is tried and never relied upon: if the system refuses it,
     * the same notification goes out the ordinary way rather than not at all.
     */
    private fun post(notification: Notification) {
        if (promote(notification)) return
        try {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // Notification permission refused: the call screen is started directly.
        } catch (e: Exception) {
            // A notification is never worth taking the call process down.
        }
    }

    private fun promote(notification: Notification): Boolean = try {
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
            } else {
                0
            }
        )
        foreground = true
        true
    } catch (e: Exception) {
        // A phone that refuses the promotion — or an Android that wants a
        // permission this one was not given — must not lose the call for it.
        false
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

    /**
     * The card the call is on, named the way the user named it.
     *
     * Three cards in one phone is the normal case here, and the line that rings
     * says who is calling before the number does. The nickname given in the
     * settings comes first; the label of the operator otherwise.
     */
    private fun resolveSim(call: Call) {
        val handle = call.details?.accountHandle ?: return
        scope.launch {
            val label = withContext(Dispatchers.IO) {
                runCatching {
                    val profiles = runCatching {
                        DatabaseProvider.getDatabase(this@PhoneCallService)
                            .simProfileDao()
                            .getAllOnce()
                    }.getOrDefault(emptyList())
                    SimManager.getSims(this@PhoneCallService, profiles)
                        .firstOrNull {
                            SimManager.phoneAccountHandle(this@PhoneCallService, it) == handle
                        }
                        ?.let { sim ->
                            listOf(sim.slotLabel, sim.label.ifBlank { sim.carrierName })
                                .filter { it.isNotBlank() }
                                .joinToString(" • ")
                        }
                        .orEmpty()
                }.getOrDefault("")
            }
            OngoingCallStore.cacheSimLabel(call, label)
        }
    }

    private fun resolveContactName(call: Call) {
        val number = call.details?.handle?.schemeSpecificPart ?: return
        scope.launch {
            // The name and the face in one lookup: a ringing phone has no time
            // for two round trips to the contacts provider.
            val (name, photo) = ContactsRepository.contactFor(this@PhoneCallService, number)
            OngoingCallStore.cacheContact(number, name, photo)
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
            // Not colorized, deliberately. A colourised notification hands its
            // whole background to the brand colour, and the system then picks
            // the colour of the buttons itself to keep them readable against
            // it — which is exactly the choice we want to make here. Red and
            // green on the two buttons are worth more than indigo behind them.
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

        // The line that is ringing, on the band itself.
        if (call.simLabel.isNotBlank()) builder.setSubText(call.simLabel)

        val person = Person.Builder().setName(caller).setKey(call.id).setImportant(true).build()
        val red = androidx.core.content.ContextCompat.getColor(
            this,
            com.example.R.color.call_decline
        )
        val green = androidx.core.content.ContextCompat.getColor(
            this,
            com.example.R.color.call_answer
        )
        val styled = runCatching {
            // Two real buttons, with their icons and their colours — refuse in
            // red on the left, answer in green on the right. The order is the
            // order of the arguments, and the colours are asked for by name:
            // the framework draws the buttons itself, and these are the only
            // handles it gives on them.
            val style = if (call.isRinging) {
                NotificationCompat.CallStyle.forIncomingCall(person, decline, answer)
                    .setDeclineButtonColorHint(red)
                    .setAnswerButtonColorHint(green)
            } else {
                NotificationCompat.CallStyle.forOngoingCall(person, hangUp)
                    .setDeclineButtonColorHint(red)
            }
            builder.setStyle(style.setIsVideo(false))
        }.isSuccess

        if (!styled) {
            // No call style on this phone: the buttons become plain actions,
            // which is the one thing that must not be lost. Refuse first so
            // that red is still on the left — the colour of a plain action is
            // the system's to choose, the side is not.
            if (call.isRinging) {
                builder.addAction(
                    com.example.R.drawable.ic_action_call_end,
                    LanguageManager.getString("decline"),
                    decline
                )
                builder.addAction(
                    com.example.R.drawable.ic_action_call,
                    LanguageManager.getString("answer"),
                    answer
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
        post(builder.build())
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
