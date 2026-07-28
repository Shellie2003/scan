package com.example.telephony

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.example.data.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * "Reply with a message" while a call is ringing.
 *
 * Android requires the default SMS app to expose this service. The quick answer
 * is sent through the same SIM logic as the rest of the app — off the main
 * thread, because sending reaches the telephony binder and writes the copy to
 * the SMS provider, and the phone is ringing while it happens.
 */
class RespondViaMessageService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val recipient = intent?.data?.schemeSpecificPart.orEmpty()
        val body = intent?.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        if (intent?.action != ACTION_RESPOND_VIA_MESSAGE ||
            recipient.isBlank() ||
            body.isBlank()
        ) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        scope.launch {
            send(recipient, body)
            stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private fun send(recipient: String, body: String) {
        if (!SmsSender.hasPermission(this)) return
        val subscriptionId = AppSettings.defaultSmsSubscription(this)
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
