package com.example.telephony

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import androidx.annotation.RequiresApi
import com.example.data.model.Operator

enum class DialOutcome {
    /** The call was placed by the app. */
    CALLED,

    /** The system dialer was opened with the code ready to be sent. */
    DIALER_OPENED,

    /** Nothing could be started. */
    FAILED
}

/**
 * Turns a stored code into the exact string to dial.
 *
 * The guiding rule is "what you store is what you dial": a code already written
 * as proper USSD (`#123#`, `*114*500*1#`) is never rewritten, so the user stays
 * in control of every code they edit.
 */
object UssdFormatter {

    fun format(rawCode: String, operator: Operator?): String {
        val clean = rawCode.trim().replace(" ", "")
        if (clean.isEmpty()) return clean

        // Already a complete USSD string.
        if ((clean.startsWith("*") || clean.startsWith("#")) && clean.endsWith("#")) return clean

        // Operators such as Orange Madagascar dial their codes as plain strings.
        if (operator?.stripUssdSymbols == true) return clean.trim('*', '#')

        // A bare number is a number: never wrap it into a USSD string.
        if (clean.all { it.isDigit() }) return clean

        val prefixed = if (clean.startsWith("*") || clean.startsWith("#")) clean else "*$clean"
        return if (prefixed.endsWith("#")) prefixed else "$prefixed#"
    }

    /** Fills a template with the values of an operation. */
    fun fill(
        template: String,
        code: String = "",
        number: String = "",
        amount: String = "",
        pin: String = ""
    ): String = template
        .replace("{CODE}", code)
        .replace("{NUMBER}", number)
        .replace("{AMOUNT}", amount)
        .replace("{PIN}", pin)

    /** `true` when the string can be sent through the in-app USSD channel. */
    fun isUssd(code: String): Boolean =
        (code.startsWith("*") || code.startsWith("#")) && code.endsWith("#")
}

object CallManager {

    /**
     * Places a call, or dials a USSD string, on a precise SIM card.
     *
     * @param direct place the call ourselves instead of handing it to the dialer.
     */
    @SuppressLint("MissingPermission")
    fun dial(
        context: Context,
        code: String,
        sim: SimCard?,
        direct: Boolean = true
    ): DialOutcome {
        if (code.isBlank()) return DialOutcome.FAILED
        val uri = Uri.fromParts("tel", code, null)
        val canCall = direct && SimManager.hasCallPermission(context)

        if (canCall) {
            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
            val handle = sim?.let { SimManager.phoneAccountHandle(context, it) }
            if (telecomManager != null) {
                try {
                    val extras = Bundle().apply {
                        if (handle != null) {
                            putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, handle)
                        }
                    }
                    telecomManager.placeCall(uri, extras)
                    return DialOutcome.CALLED
                } catch (e: SecurityException) {
                    // fall through to the intent based path
                } catch (e: Exception) {
                    // fall through to the intent based path
                }
            }
            val callIntent = Intent(Intent.ACTION_CALL, uri).applySimExtras(context, sim)
            if (start(context, callIntent)) return DialOutcome.CALLED
        }

        val dialIntent = Intent(Intent.ACTION_DIAL, uri).applySimExtras(context, sim)
        return if (start(context, dialIntent)) DialOutcome.DIALER_OPENED else DialOutcome.FAILED
    }

    /** Opens the system dialer without placing the call. */
    fun openDialer(context: Context, code: String): Boolean =
        start(context, Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", code, null)))

    private fun Intent.applySimExtras(context: Context, sim: SimCard?): Intent {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (sim == null) return this
        SimManager.phoneAccountHandle(context, sim)?.let {
            putExtra(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, it)
        }
        // Extras understood by the dialers of several manufacturers.
        putExtra("com.android.phone.extra.slot", sim.slotIndex)
        putExtra("com.android.phone.force.slot", true)
        putExtra("simSlot", sim.slotIndex)
        putExtra("slot", sim.slotIndex)
        putExtra("subscription", sim.subscriptionId)
        putExtra("Subscription", sim.subscriptionId)
        putExtra("simId", sim.subscriptionId)
        putExtra("subscription_id", sim.subscriptionId)
        return this
    }

    private fun start(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent)
        true
    } catch (e: Exception) {
        false
    }
}

/** Result of a USSD session run inside the app. */
sealed class UssdResult {
    data class Success(val response: String) : UssdResult()
    data class Failure(val code: Int) : UssdResult()

    /** The in-app channel is unavailable; the caller should dial instead. */
    object Unsupported : UssdResult()
}

/**
 * Runs a USSD code without leaving the app, on Android 8 and above.
 *
 * The response of the operator is returned as text, which is what lets the user
 * confirm a recharge or read a balance without switching to the phone app.
 */
object UssdRunner {

    fun isAvailable(context: Context, code: String): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            SimManager.hasCallPermission(context) &&
            UssdFormatter.isUssd(code)

    fun run(
        context: Context,
        code: String,
        sim: SimCard?,
        onResult: (UssdResult) -> Unit
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || !isAvailable(context, code)) {
            onResult(UssdResult.Unsupported)
            return
        }
        sendRequest(context, code, sim, onResult)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    @SuppressLint("MissingPermission")
    private fun sendRequest(
        context: Context,
        code: String,
        sim: SimCard?,
        onResult: (UssdResult) -> Unit
    ) {
        val base = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        if (base == null) {
            onResult(UssdResult.Unsupported)
            return
        }
        val telephonyManager = try {
            if (sim != null) base.createForSubscriptionId(sim.subscriptionId) else base
        } catch (e: Exception) {
            base
        }
        val handler = Handler(Looper.getMainLooper())
        val callback = object : TelephonyManager.UssdResponseCallback() {
            override fun onReceiveUssdResponse(
                telephonyManager: TelephonyManager,
                request: String,
                response: CharSequence
            ) {
                onResult(UssdResult.Success(response.toString()))
            }

            override fun onReceiveUssdResponseFailed(
                telephonyManager: TelephonyManager,
                request: String,
                failureCode: Int
            ) {
                onResult(UssdResult.Failure(failureCode))
            }
        }
        try {
            telephonyManager.sendUssdRequest(code, callback, handler)
        } catch (e: SecurityException) {
            onResult(UssdResult.Unsupported)
        } catch (e: Exception) {
            onResult(UssdResult.Unsupported)
        }
    }
}
