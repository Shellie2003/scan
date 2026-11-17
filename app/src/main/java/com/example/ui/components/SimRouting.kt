package com.example.ui.components

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.example.telephony.NumberUtils
import com.example.telephony.RoutingStatus
import com.example.telephony.SimCard
import com.example.telephony.SimManager
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
        val warning: String? = null,
        /**
         * The card the screen is showing, when the user picked it there.
         *
         * Without this the choice was thrown away unless the user also ticked
         * "remember": the screen said one line and the code left on another.
         */
        val sim: SimCard? = null
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
        warning: String? = null,
        sim: SimCard? = null
    ) {
        if (code.isNotBlank()) {
            onRequest(
                DialRequest.Code(
                    code, operator, kind, label, rawCode, requireConfirm, warning, sim
                )
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
    var pendingNoLine by remember { mutableStateOf<Pair<DialRequest, SimRouting>?>(null) }
    var ussdAnswer by remember { mutableStateOf<String?>(null) }

    /**
     * A code whose answer the telephone could not read, waiting on the user.
     *
     * The application used to dial it again by itself. It must not: the code
     * had very often reached the operator already — the dialog of the operator
     * was on the screen — and sending it a second time is both a second
     * operation and, repeated, what makes an operator block the line.
     */
    var ussdTrouble by remember { mutableStateOf<Pair<DialRequest, SimRouting>?>(null) }

    /**
     * `true` from the moment a code is handed to the modem until it answers.
     *
     * One operation at a time. Two USSD sessions at once is what put two and
     * three dialogs of the telephone on top of each other before anything had
     * been done.
     */
    var ussdInFlight by remember { mutableStateOf(false) }

    /** When the last operation was started, so that a double tap sends one code. */
    var lastStart by remember { mutableStateOf(0L) }

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

    /** Writes an operation to the history — only ever one that really went out. */
    fun record(request: DialRequest.Code, sim: SimCard?) {
        app.record(
            operator = request.operator,
            code = request.rawCode.ifBlank { request.code },
            dialedCode = request.code,
            sim = sim,
            kind = request.kind,
            label = request.label
        )
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
                //
                // Never for a code, though. A string with a star or a hash is
                // not a call: the telephone hands it to the network and answers
                // in a dialog of its own, and nothing is ever added to the list
                // of calls for it. The watchdog therefore found nothing every
                // single time — and said "the call did not go through, trying
                // again" over the operator's own dialog, then opened a second
                // one by sending the code a second time. That is the popup
                // between the two dialogs, and this is where it came from.
                val isCode = NumberUtils.isMmi(request.number)
                if (!isCode &&
                    outcome == DialOutcome.CALLED &&
                    DefaultDialerManager.isDefaultDialer(context)
                ) {
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
                    ussdInFlight = true
                    UssdRunner.run(context, code, sim) { result ->
                        ussdInFlight = false
                        when (result) {
                            is UssdResult.Success -> {
                                AppSettings.recordInAppUssdSuccess(context)
                                ussdAnswer = result.response
                                answeredRequest = request
                                record(request, sim)
                                // After a recharge, what follows is usually a
                                // plan: the two most often bought are fetched
                                // while the user reads the answer.
                                if (request.kind == ScanHistory.KIND_RECHARGE) {
                                    scope.launch { habits = app.mostBoughtOffers(2) }
                                }
                            }
                            // The channel does not exist on this telephone, so
                            // the code never left: it is dialled, and that is
                            // the end of it.
                            is UssdResult.Unsupported -> {
                                AppSettings.recordInAppUssdFailure(context)
                                if (dialCode(code, sim)) record(request, sim)
                            }
                            // A refusal, or nothing at all. What is certain is
                            // only this: the application cannot tell whether
                            // the operator got the code — very often it did,
                            // and its own dialog is on the screen behind. The
                            // user can see that; the application cannot. So it
                            // dials nothing, writes nothing, and asks.
                            else -> {
                                AppSettings.recordInAppUssdFailure(context)
                                ussdTrouble = request to routing
                            }
                        }
                    }
                } else if (dialCode(code, sim)) {
                    // The history is written only for a code that actually went
                    // out. It used to be written whatever happened, so a
                    // recharge the phone had refused was listed as done — and
                    // the duplicate guard then refused to send it again.
                    record(request, sim)
                }
            }
        }
    }

    fun routeOf(request: DialRequest): SimRouting = when (request) {
        is DialRequest.Call -> app.routeForNumber(request.number)
        is DialRequest.Code -> {
            val base = app.routeFor(request.operator)
            // A card named by the screen is the card the code goes out on.
            // There is nothing left to choose, so no picker is raised either.
            val chosen = request.sim
            if (chosen == null) {
                base
            } else {
                base.copy(
                    sim = chosen,
                    status = RoutingStatus.READY,
                    isFallback = request.operator != null &&
                        !SimManager.matches(chosen, request.operator)
                )
            }
        }
    }

    fun start(request: DialRequest) {
        // A code already on its way is the only thing the modem can hold. A
        // second one started now opens a second session, and the telephone puts
        // up a second dialog over the first.
        if (ussdInFlight) {
            toast("ussd_busy")
            return
        }
        // A code sent during a conversation is refused by the modem, and what
        // reaches the user is a telephony error rather than their recharge.
        if (request is DialRequest.Code && OngoingCallStore.hasRealCall()) {
            toast("call_in_progress")
            return
        }
        // And a button pressed twice is one operation, not two. A recharge sent
        // twice is money sent twice.
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastStart < DOUBLE_TAP_MS) return
        lastStart = now

        val routing = routeOf(request)
        val needsConfirm = request is DialRequest.Code &&
            (request.requireConfirm || AppSettings.confirmBeforeDial(context))
        when {
            // Nothing to run it on. Said before the call screen opens, not
            // after: a call placed with no line reaches the telephone and dies
            // there, and what the user sees is a screen that never moves.
            routing.hasNoLine -> pendingNoLine = request to routing
            routing.status == RoutingStatus.NEEDS_CHOICE -> pendingChoice = request to routing
            needsConfirm -> pendingConfirm = request to routing
            else -> execute(request, routing)
        }
    }

    content(SimLauncher(::start))

    pendingNoLine?.let { (request, routing) ->
        val missingPermission = routing.status == RoutingStatus.NO_PERMISSION
        AlertDialog(
            onDismissRequest = { pendingNoLine = null },
            icon = {
                Icon(
                    imageVector = Icons.Rounded.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = {
                Text(
                    text = LanguageManager.getString("no_line_title"),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = LanguageManager.getString(
                        if (missingPermission) "no_permission_desc" else "no_line_desc"
                    ),
                    style = MaterialTheme.typography.bodySmall
                )
            },
            confirmButton = {
                if (missingPermission) {
                    Button(onClick = {
                        pendingNoLine = null
                        openAppSettings(context)
                    }) {
                        Text(LanguageManager.getString("grant_permission"))
                    }
                } else {
                    // Refused outright would be wrong: a telephone can place a
                    // call through its own dialer on a line this application
                    // cannot see. The attempt belongs to the user, warned.
                    Button(onClick = {
                        val request1 = request
                        pendingNoLine = null
                        execute(request1, routing)
                    }) {
                        Text(LanguageManager.getString("continue_anyway"))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingNoLine = null }) {
                    Text(LanguageManager.getString("cancel"))
                }
            }
        )
    }

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
                // An operator answer can be a dozen lines, and two plan
                // buttons follow it: on a small screen the whole thing has to
                // be reachable, not cut off at the second line.
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
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
                                imageVector = Icons.Rounded.Dialpad,
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

    // The answer of the operator could not be read inside the application. The
    // code may have gone through all the same — that is the usual case, and the
    // words of the operator are on the screen behind this dialog. So it says
    // what is true, and leaves the decision to send it again to the user.
    ussdTrouble?.let { (request, routing) ->
        if (request is DialRequest.Code) {
            AlertDialog(
                onDismissRequest = { ussdTrouble = null },
                icon = {
                    Icon(
                        imageVector = Icons.Rounded.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                },
                title = {
                    Text(
                        text = LanguageManager.getString("ussd_unread_title"),
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        Text(
                            text = LanguageManager.getString("ussd_unread_desc"),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = request.code,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        val code = request.code
                        val sim = routing.sim
                        ussdTrouble = null
                        // Dialled the ordinary way this time, which is the path
                        // that works on these telephones — and written to the
                        // history, because this one did leave.
                        if (dialCode(code, sim)) record(request, sim)
                    }) {
                        Text(LanguageManager.getString("send_again"))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { ussdTrouble = null }) {
                        Text(LanguageManager.getString("cancel"))
                    }
                }
            )
        }
    }
}

/**
 * How long two presses count as one.
 *
 * Long enough to swallow a double tap and the twitch of a thumb on a cheap
 * touchscreen, short enough that a user who meant two recharges gets two.
 */
private const val DOUBLE_TAP_MS = 1_200L

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
    routing.status == RoutingStatus.NO_PERMISSION ->
        LanguageManager.getString("sim_permission_missing")
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

/**
 * The page of the telephone where the permissions of this application live.
 *
 * Asking again from here is not possible: a permission refused twice is never
 * asked again by Android, and the only way back is that page.
 */
private fun openAppSettings(context: android.content.Context) {
    try {
        context.startActivity(
            android.content.Intent(
                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                android.net.Uri.fromParts("package", context.packageName, null)
            ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (e: Exception) {
        Toast.makeText(
            context,
            LanguageManager.getString("action_failed"),
            Toast.LENGTH_SHORT
        ).show()
    }
}

/** Convenience used by the screens that only need the resolved SIM of an operator. */
fun SimRouting.simOrFirst(sims: List<SimCard>): SimCard? = sim ?: sims.firstOrNull()
