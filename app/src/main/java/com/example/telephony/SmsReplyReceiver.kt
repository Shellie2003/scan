package com.example.telephony

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import com.example.data.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Answers a message straight from its notification.
 *
 * The reply is sent through the SIM the message came in on, so a conversation
 * started on one card stays on that card.
 */
class SmsReplyReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val address = intent.getStringExtra(MessageNotifier.EXTRA_ADDRESS).orEmpty()
        if (address.isBlank()) return
        val name = intent.getStringExtra(MessageNotifier.EXTRA_NAME).orEmpty()
        val subscriptionId = MessageNotifier.replySubscription(
            context,
            intent.getIntExtra(MessageNotifier.EXTRA_SUBSCRIPTION, AppSettings.NO_SUBSCRIPTION)
        )

        when (intent.action) {
            MessageNotifier.ACTION_MARK_READ -> {
                val pending = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        SmsRepository.markAddressRead(context, address)
                        MessageNotifier.cancel(context, address)
                    } finally {
                        pending.finish()
                    }
                }
            }

            MessageNotifier.ACTION_REPLY -> {
                val body = RemoteInput.getResultsFromIntent(intent)
                    ?.getCharSequence(MessageNotifier.KEY_REPLY)
                    ?.toString()
                    .orEmpty()
                    .trim()
                if (body.isBlank()) return
                val pending = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val sent = SmsSender.sendSingle(context, address, body, subscriptionId)
                        if (sent) SmsRepository.markAddressRead(context, address)
                        MessageNotifier.notifyOutgoing(
                            context = context,
                            address = address,
                            name = name,
                            subscriptionId = subscriptionId,
                            body = body,
                            failed = !sent
                        )
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }
}
