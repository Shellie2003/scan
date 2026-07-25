package com.example.telephony

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * "Reply with a message" while a call is ringing.
 *
 * Android requires the default SMS app to expose this service. The quick answer
 * is sent through the same SIM logic as the rest of the app.
 */
class RespondViaMessageService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_RESPOND_VIA_MESSAGE) {
            val recipient = intent.data?.schemeSpecificPart.orEmpty()
            val body = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
            if (recipient.isNotBlank() && body.isNotBlank()) {
                send(recipient, body)
            }
        }
        stopSelf(startId)
        return START_NOT_STICKY
    }

    private fun send(recipient: String, body: String) {
        if (!SmsSender.hasPermission(this)) return
        val subscriptionId = com.example.data.AppSettings.defaultSmsSubscription(this)
        try {
            SmsSender.sendSingle(this, recipient, body, subscriptionId)
        } catch (e: Exception) {
            // Nothing else can be done from a background service.
        }
    }

    companion object {
        const val ACTION_RESPOND_VIA_MESSAGE = "android.intent.action.RESPOND_VIA_MESSAGE"
    }
}
