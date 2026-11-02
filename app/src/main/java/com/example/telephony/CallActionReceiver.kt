package com.example.telephony

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.example.ui.call.InCallActivity
import com.example.ui.util.LanguageManager

/**
 * Answering and refusing a call from the band at the top of the screen.
 *
 * Until now the notification only opened the call screen: taking a call while
 * reading a message meant a tap, a wait for the screen to come up, and then the
 * slide. The two buttons of the notification act on the call directly, which is
 * what every phone does and what a call needs — it does not wait.
 */
class CallActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(EXTRA_CALL_ID).orEmpty()
        val done = when (intent.action) {
            ACTION_ANSWER -> {
                val answered = OngoingCallStore.answer(id)
                // The conversation now belongs on the screen; a refused start is
                // swallowed by the activity itself, and the notification remains.
                if (answered) InCallActivity.start(context)
                answered
            }

            ACTION_DECLINE -> OngoingCallStore.reject(id)
            ACTION_HANG_UP -> OngoingCallStore.hangUp(id)
            // Not ours: nothing to report.
            else -> true
        }
        if (!done) {
            // A button that does nothing is the worst answer of all: the user
            // presses it again, and again, while the phone keeps ringing. One
            // line is enough to send them to the call screen instead.
            LanguageManager.init(context)
            try {
                Toast.makeText(
                    context,
                    LanguageManager.getString("call_action_failed"),
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                // No window to show it in; the call screen below still opens.
            }
            InCallActivity.start(context)
        }
    }

    companion object {
        const val ACTION_ANSWER = "com.example.action.ANSWER_CALL"
        const val ACTION_DECLINE = "com.example.action.DECLINE_CALL"
        const val ACTION_HANG_UP = "com.example.action.HANG_UP_CALL"

        const val EXTRA_CALL_ID = "com.example.extra.CALL_ID"

        /** A button of the call notification, aimed at one precise call. */
        fun pendingIntent(context: Context, action: String, callId: String): PendingIntent {
            val intent = Intent(context, CallActionReceiver::class.java).apply {
                this.action = action
                // The data keeps one pending intent per call and per button, so
                // answering never ends up refusing an earlier call.
                data = Uri.fromParts("call", callId + ":" + action, null)
                putExtra(EXTRA_CALL_ID, callId)
            }
            return PendingIntent.getBroadcast(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
