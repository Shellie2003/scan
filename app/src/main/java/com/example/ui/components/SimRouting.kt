package com.example.ui.components

import android.widget.Toast
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import com.example.data.AppSettings
import com.example.data.model.Operator
import com.example.data.model.ScanHistory
import com.example.telephony.CallManager
import com.example.telephony.DefaultDialerManager
import com.example.telephony.OngoingCallStore
import com.example.telephony.DialOutcome
import com.example.telephony.RoutingStatus
import com.example.telephony.SimCard
import com.example.telephony.SimRouting
import com.example.telephony.UssdResult
import com.example.telephony.UssdRunner
import com.example.ui.util.LanguageManager
import com.example.viewmodel.AppViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** An operation waiting to be sent to a SIM card. */
sealed class DialRequest {
    /** A plain call to a contact or a typed number. */
    data class Call(val number: String) : DialRequest()

    /** A recharge, a plan purchase or a transfer, described by its final code. */
    data class Code(
        val code: String,
        val operator: Operator?,
        val kind: String = ScanHistory.KIND_RECHARGE,
        val label: String = "",
        val rawCode: String = "",
        val requireConfirm: Boolean = false,
        val warning: String? = null
    ) : DialRequest()
}

/** Handed to the screens so they can start an operation without knowing about SIM routing. */
class SimLauncher internal constructor(private val onRequest: (DialRequest) -> Unit) {

    fun call(number: String) {
        if (number.isNotBlank()) onRequest(DialRequest.Call(number))
    }

    fun runCode(
        code: String,
        operator: Operator?,
        kind: String = ScanHistory.KIND_RECHARGE,
        label: String = "",
        rawCode: String = "",
        requireConfirm: Boolean = false,
        warning: String? = null
    ) {
        if (code.isNotBlank()) {
            onRequest(
                DialRequest.Code(code, operator, kind, label, rawCode, requireConfirm, warning)
            )
        }
    }
}

/**
 * Wraps a screen with the whole SIM story: choosing the card when several match,
 * confirming the exact code, running it — inside the app when possible — and
 * writing the operation to the history.
 */
@Composable
fun WithSimRouting(
    app: AppViewModel,
    content: @Composable (SimLauncher) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sims by app.sims.collectAsState()

    var pendingChoice by remember { mutableStateOf<Pair<DialRequest, SimRouting>?>(null) }
    var pendingConfirm by remember { mutableStateOf<Pair<DialRequest, SimRouting>?>(null) }
    var ussdAnswer by remember { mutableStateOf<String?>(null) }
    var ussdFailed by remember { mutableStateOf<Pair<DialRequest, SimRouting>?>(null) }

    fun toast(key: String) {
        Toast.makeText(context, LanguageManager.getString(key), Toast.LENGTH_SHORT).show()
    }

    /**
     * Dials a code through the dialer of the system, which is the path that
     * works everywhere. Used on its own, and as the fallback when the in-app
     * USSD channel refuses.
     */
    fun dialCode(code: String, sim: SimCard?) {
        if (CallManager.dial(context, code, sim, direct = true) == DialOutcome.FAILED) {
            toast("action_failed")
        }
    }

    fun execute(request: DialRequest, routing: SimRouting) {
        val sim = routing.sim
        when (request) {
            is DialRequest.Call -> {
                val outcome = CallManager.dial(context, request.number, sim, direct = true)
                if (outcome == DialOutcome.FAILED) {
                    toast("action_failed")
                    return
                }
                // A call placed through telecom can be swallowed in silence —
                // a refused phone account, a modem that says nothing. When the
                // app is the phone app it can see the call appear; if nothing
                // appears, the call is placed again the ordinary way.
                if (outcome == DialOutcome.CALLED && DefaultDialerManager.isDefaultDialer(context)) {
                    scope.launch {
                        delay(CALL_WATCHDOG_MS)
                        if (!OngoingCallStore.hasCalls()) {
                            toast("call_retry")
                            CallManager.dialWithIntent(context, request.number, sim)
                        }
                    }
                }
            }

            is DialRequest.Code -> {
                val code = request.code
                val useInApp = AppSettings.inAppUssd(context) &&
                    AppSettings.inAppUssdWorks(context) &&
                    UssdRunner.isAvailable(context, code)
                if (useInApp) {
                    UssdRunner.run(context, code, sim) { result ->
                        when (result) {
                            is UssdResult.Success -> {
                                AppSettings.recordInAppUssdSuccess(context)
                                ussdAnswer = result.response
                            }
                            // The operator answer could not be read in the app.
                            // The code itself is good: it is dialled straight
                            // away instead of asking the user to try again, and
                            // a phone that keeps refusing stops being asked.
                            else -> {
                                val giveUp = AppSettings.recordInAppUssdFailure(context)
                                toast(if (giveUp) "ussd_direct_from_now" else "ussd_falling_back")
                                dialCode(code, sim)
                            }
                        }
                    }
                } else {
                    dialCode(code, sim)
                }
                app.record(
                    operator = request.operator,
                    code = request.rawCode.ifBlank { code },
                    dialedCode = code,
                    sim = sim,
                    kind = request.kind,
                    label = request.label
                )
            }
        }
    }

    fun routeOf(request: DialRequest): SimRouting = when (request) {
        is DialRequest.Call -> app.routeForNumber(request.number)
        is DialRequest.Code -> app.routeFor(request.operator)
    }

    fun start(request: DialRequest) {
        val routing = routeOf(request)
        val needsConfirm = request is DialRequest.Code &&
            (request.requireConfirm || AppSettings.confirmBeforeDial(context))
        when {
            routing.status == RoutingStatus.NEEDS_CHOICE -> pendingChoice = request to routing
            needsConfirm -> pendingConfirm = request to routing
            else -> execute(request, routing)
        }
    }

    content(SimLauncher(::start))

    pendingChoice?.let { (request, routing) ->
        SimPickerDialog(
            sims = routing.candidates.ifEmpty { sims },
            operator = routing.operator,
            selected = routing.sim,
            onDismiss = { pendingChoice = null },
            onPick = { sim, save ->
                pendingChoice = null
                if (save) app.setPreferredSim(routing.operator, sim)
                val resolved = routing.copy(sim = sim, status = RoutingStatus.READY)
                val needsConfirm = request is DialRequest.Code &&
                    (request.requireConfirm || AppSettings.confirmBeforeDial(context))
                if (needsConfirm) pendingConfirm = request to resolved else execute(request, resolved)
            }
        )
    }

    pendingConfirm?.let { (request, routing) ->
        if (request is DialRequest.Code) {
            ConfirmCodeDialog(
                title = request.label.ifBlank { LanguageManager.getString("confirm_dial_title") },
                code = request.code,
                warning = request.warning ?: fallbackWarning(routing),
                simLabel = routing.sim?.title,
                onConfirm = {
                    pendingConfirm = null
                    execute(request, routing)
                },
                onDismiss = { pendingConfirm = null }
            )
        }
    }

    ussdAnswer?.let { answer ->
        AlertDialog(
            onDismissRequest = { ussdAnswer = null },
            title = {
                Text(LanguageManager.getString("ussd_response"), fontWeight = FontWeight.Bold)
            },
            text = { Text(answer, style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                Button(onClick = { ussdAnswer = null }) { Text(LanguageManager.getString("ok")) }
            }
        )
    }

    ussdFailed?.let { (request, routing) ->
        AlertDialog(
            onDismissRequest = { ussdFailed = null },
            title = { Text(LanguageManager.getString("warning"), fontWeight = FontWeight.Bold) },
            text = { Text(LanguageManager.getString("ussd_failed")) },
            confirmButton = {
                Button(
                    onClick = {
                        ussdFailed = null
                        if (request is DialRequest.Code) {
                            CallManager.dial(context, request.code, routing.sim, direct = true)
                        }
                    }
                ) { Text(LanguageManager.getString("retry")) }
            },
            dismissButton = {
                TextButton(onClick = { ussdFailed = null }) {
                    Text(LanguageManager.getString("cancel"))
                }
            }
        )
    }
}

private fun fallbackWarning(routing: SimRouting): String? = when {
    routing.status == RoutingStatus.NO_SIM -> LanguageManager.getString("no_sim_detected")
    routing.isFallback && routing.operator != null && routing.sim != null ->
        LanguageManager.getString(
            "sim_fallback_warning",
            "operator" to routing.operator.name,
            "sim" to routing.sim.title
        )
    else -> null
}

/** How long a call is given to appear before it is placed again. */
private const val CALL_WATCHDOG_MS = 3_000L

/** Convenience used by the screens that only need the resolved SIM of an operator. */
fun SimRouting.simOrFirst(sims: List<SimCard>): SimCard? = sim ?: sims.firstOrNull()
