package com.example.ui.components

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
import com.example.telephony.UssdFormatter
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

    /** The code the answer being shown came from, and what it was doing. */
    var answeredRequest by remember { mutableStateOf<DialRequest?>(null) }

    /**
     * The plans this phone buys most often, loaded when a recharge succeeds.
     *
     * The credit has just arrived and what usually follows is the same plan as
     * last time: offering it here saves crossing two screens with a customer
     * waiting at the counter.
     */
    var habits by remember { mutableStateOf<List<com.example.data.model.Offer>>(emptyList()) }

    fun toast(key: String) {
        Toast.makeText(context, LanguageManager.getString(key), Toast.LENGTH_SHORT).show()
    }

    /**
     * Dials a code through the dialer of the system, which is the path that
     * works everywhere. Used on its own, and as the fallback when the in-app
     * USSD channel refuses.
     */
    fun dialCode(code: String, sim: SimCard?): Boolean {
        val failed = CallManager.dial(context, code, sim, direct = true) == DialOutcome.FAILED
        if (failed) toast("action_failed")
        return !failed
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
                // The history is written only for a code that actually went
                // out. It used to be written whatever happened, so a recharge
                // the phone had refused was listed as done — and the duplicate
                // guard then refused to send it again for a day.
                var sent = true
                if (useInApp) {
                    UssdRunner.run(context, code, sim) { result ->
                        when (result) {
                            is UssdResult.Success -> {
                                AppSettings.recordInAppUssdSuccess(context)
                                ussdAnswer = result.response
                                answeredRequest = request
                                // After a recharge, what follows is usually a
                                // plan: the two most often bought are fetched
                                // while the user reads the answer.
                                if (request.kind == ScanHistory.KIND_RECHARGE) {
                                    scope.launch { habits = app.mostBoughtOffers(2) }
                                }
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
                    sent = dialCode(code, sim)
                }
                if (sent) {
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
        val request = answeredRequest
        fun close() {
            ussdAnswer = null
            answeredRequest = null
            habits = emptyList()
        }
        AlertDialog(
            onDismissRequest = { close() },
            title = {
                Text(LanguageManager.getString("ussd_response"), fontWeight = FontWeight.Bold)
            },
            text = {
                Column {
                    Text(answer, style = MaterialTheme.typography.bodyMedium)

                    // An operator sometimes answers with nothing but its
                    // goodbye — "merci d'avoir utilisé le service" — although
                    // the code was right: the session it opened was closed
                    // before doing anything. Dialling the same code the
                    // ordinary way goes through, so it is offered here instead
                    // of leaving the user to start over.
                    if (request is DialRequest.Code && looksLikeNothingHappened(answer)) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = LanguageManager.getString("ussd_answer_empty"),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        OutlinedButton(
                            onClick = {
                                val code = request.code
                                val sim = routeOf(request).sim
                                close()
                                dialCode(code, sim)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Dialpad,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(LanguageManager.getString("dial_directly"))
                        }
                    }

                    // The habit of this phone, right where the credit landed.
                    if (habits.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = LanguageManager.getString("buy_again"),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(4.dp))
                        habits.forEach { offer ->
                            val operator = app.operatorNamed(offer.operatorName)
                            Button(
                                onClick = {
                                    close()
                                    start(
                                        DialRequest.Code(
                                            code = UssdFormatter.format(offer.ussdCode, operator),
                                            operator = operator,
                                            kind = ScanHistory.KIND_OFFER,
                                            label = offer.title,
                                            rawCode = offer.ussdCode
                                        )
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp)
                            ) {
                                Text(
                                    text = offer.title + " · " + offer.price,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { close() }) { Text(LanguageManager.getString("ok")) }
            }
        )
    }

    // There was a third dialog here, offering to retry a USSD code that failed.
    // Nothing could open it any more: a failure now dials the code straight
    // away rather than asking the user to press "retry" on a code that was
    // never wrong. Dead state and dead UI are worse than no UI.
}

/**
 * `true` when the answer of the operator says nothing but goodbye.
 *
 * "Merci d'avoir utilisé le service Yas", "Misaotra", "Thank you for using" —
 * the session was opened and closed without doing the thing that was asked.
 * It happens with the in-app channel while the very same code, dialled the
 * ordinary way, goes through. An answer that also carries digits is left
 * alone: a balance or a confirmation is a real answer.
 */
private fun looksLikeNothingHappened(answer: String): Boolean {
    val text = answer.lowercase()
    val goodbye = listOf("merci", "misaotra", "thank")
    if (goodbye.none { text.contains(it) }) return false
    // A confirmation says how much, or until when. A goodbye says nothing.
    return answer.count { it.isDigit() } < 3
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
