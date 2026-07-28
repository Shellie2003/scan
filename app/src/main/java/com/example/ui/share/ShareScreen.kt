package com.example.ui.share

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.Operator
import com.example.data.model.ScanHistory
import com.example.telephony.NumberUtils
import com.example.telephony.UssdFormatter
import com.example.ui.components.InitialsAvatar
import com.example.ui.components.SimIndicator
import com.example.ui.components.SimLauncher
import com.example.ui.components.SimPickerDialog
import com.example.ui.theme.ScanTextStyles
import com.example.ui.util.LanguageManager
import com.example.ui.util.OperatorColorUtils
import com.example.viewmodel.AppViewModel
import com.example.viewmodel.PhoneViewModel

/**
 * What the screen is about to do. A withdrawal joined the two sharing modes
 * because it is the same gesture — a number, an amount, a code to confirm.
 */
private enum class ShareMode(val labelKey: String, val numberLabelKey: String) {
    CREDIT("transfer_credit", "recipient_number"),
    PLAN("share_plan", "recipient_number"),
    WITHDRAW("withdraw_cash", "cashpoint_number")
}

private data class QueuedShare(
    val number: String,
    val amount: String,
    val operatorName: String,
    val code: String,
    val done: Boolean = false
)

/**
 * Sharing credit or a plan with someone else.
 *
 * The operator of the destination number is recognised from its prefix, and the
 * SIM card of that same operator is selected automatically when the phone holds
 * one — which is what keeps a transfer on-net. When no such card is available
 * the app says so instead of silently using another one.
 */
@Composable
fun ShareScreen(
    app: AppViewModel,
    phone: PhoneViewModel,
    launcher: SimLauncher
) {
    val currentLang by LanguageManager.currentLang
    val operators by app.operators.collectAsState()
    val sims by app.sims.collectAsState()
    val contacts by phone.contacts.collectAsState()

    var mode by remember { mutableStateOf(ShareMode.CREDIT) }
    var number by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var forcedSubscription by remember { mutableStateOf(com.example.data.AppSettings.NO_SUBSCRIPTION) }
    var showSimPicker by remember { mutableStateOf(false) }
    var showContacts by remember { mutableStateOf(false) }
    var queue by remember { mutableStateOf<List<QueuedShare>>(emptyList()) }

    LaunchedEffect(Unit) { phone.refresh() }

    val destinationOperator = remember(number, operators) {
        NumberUtils.operatorForNumber(number, operators)
    }
    val routing = remember(number, sims, forcedSubscription) {
        app.routeForNumber(number, forcedSubscription)
    }
    // The operator whose template is used is the one of the SIM that sends.
    val sendingOperator: Operator? = remember(routing, operators, destinationOperator) {
        routing.sim?.let { sim ->
            operators.firstOrNull { com.example.telephony.SimManager.matches(sim, it) }
        } ?: destinationOperator
    }
    val theme = OperatorColorUtils.themeOf(sendingOperator)

    val template = when (mode) {
        ShareMode.CREDIT -> sendingOperator?.transferTemplate.orEmpty()
        ShareMode.PLAN -> sendingOperator?.shareTemplate.orEmpty()
        ShareMode.WITHDRAW -> sendingOperator?.withdrawTemplate.orEmpty()
    }
    val code = remember(template, number, amount, pin) {
        UssdFormatter.fill(
            template = template,
            number = NumberUtils.toNational(number),
            amount = amount.trim(),
            pin = pin.trim()
        )
    }
    val formattedCode = remember(code, sendingOperator) {
        UssdFormatter.format(code, sendingOperator)
    }
    val ready = template.isNotBlank() && number.isNotBlank() && amount.isNotBlank()

    fun send(target: String, value: String, dialCode: String, operator: Operator?) {
        launcher.runCode(
            code = dialCode,
            operator = operator,
            kind = ScanHistory.KIND_TRANSFER,
            label = LanguageManager.getString(mode.labelKey) + " " + value + " → " +
                NumberUtils.formatForDisplay(target),
            rawCode = value,
            requireConfirm = true,
            warning = if (operator?.templatesVerified == false) {
                LanguageManager.getString("verify_code_warning")
            } else {
                null
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = LanguageManager.getString("share_title"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = theme.mainColor
                )
                Text(
                    text = LanguageManager.getString("share_desc"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        TabRow(
            selectedTabIndex = mode.ordinal,
            containerColor = theme.mainColor,
            contentColor = theme.accentColor
        ) {
            ShareMode.entries.forEach { entry ->
                Tab(
                    selected = mode == entry,
                    onClick = { mode = entry },
                    text = {
                        Text(
                            text = LanguageManager.getString(entry.labelKey),
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    },
                    selectedContentColor = theme.accentColor,
                    unselectedContentColor = theme.unselectedColor
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // A withdrawal moves real money at a counter: say plainly what the app
        // does with the secret code, which is nothing at all.
        if (mode == ShareMode.WITHDRAW) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = theme.mainColor.copy(alpha = 0.10f)
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = LanguageManager.getString("withdraw_desc"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = LanguageManager.getString("withdraw_pin_notice"),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = theme.mainColor
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = number,
                onValueChange = { number = it },
                label = { Text(LanguageManager.getString(mode.numberLabelKey)) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = { showContacts = true }) {
                Icon(
                    imageVector = Icons.Default.Contacts,
                    contentDescription = LanguageManager.getString("tab_contacts"),
                    tint = theme.mainColor
                )
            }
        }

        if (number.isNotBlank()) {
            Text(
                text = destinationOperator?.let {
                    LanguageManager.getString("detected_operator", "operator" to it.name)
                } ?: LanguageManager.getString("unknown_operator"),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (destinationOperator != null) {
                    OperatorColorUtils.colorOf(destinationOperator)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = amount,
            onValueChange = { amount = it },
            label = { Text(LanguageManager.getString("amount")) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        )

        // A withdrawal never carries a secret code: it is typed in the operator
        // prompt, where it belongs, and never in a string this screen builds.
        if (template.contains("{PIN}") && mode != ShareMode.WITHDRAW) {
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = pin,
                onValueChange = { pin = it },
                label = { Text(LanguageManager.getString("pin_code")) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(Modifier.height(12.dp))

        SimIndicator(
            routing = routing,
            color = theme.mainColor,
            modifier = Modifier.fillMaxWidth(),
            onClick = { showSimPicker = true }
        )

        Spacer(Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            )
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = LanguageManager.getString("code_preview"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (template.isBlank()) {
                        LanguageManager.getString(
                            "template_missing",
                            "operator" to (sendingOperator?.name ?: "")
                        )
                    } else {
                        formattedCode
                    },
                    style = ScanTextStyles.code,
                    color = if (template.isBlank()) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                if (template.isNotBlank() && sendingOperator?.templatesVerified == false) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = LanguageManager.getString("verify_code_warning"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    TextButton(
                        onClick = {
                            sendingOperator?.let { app.saveOperator(it.copy(templatesVerified = true)) }
                        }
                    ) { Text(LanguageManager.getString("mark_verified")) }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    queue = queue + QueuedShare(
                        number = number,
                        amount = amount,
                        operatorName = sendingOperator?.name.orEmpty(),
                        code = formattedCode
                    )
                    number = ""
                    amount = ""
                },
                enabled = ready,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = theme.mainColor.copy(alpha = 0.15f),
                    contentColor = theme.mainColor
                )
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(LanguageManager.getString("queue_share"), style = MaterialTheme.typography.labelMedium)
            }
            Button(
                onClick = { send(number, amount, formattedCode, sendingOperator) },
                enabled = ready,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = theme.mainColor,
                    contentColor = theme.accentColor
                )
            ) {
                Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(LanguageManager.getString("execute"), style = MaterialTheme.typography.labelMedium)
            }
        }

        if (queue.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            Text(
                text = LanguageManager.getString("share_queue"),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(Modifier.height(8.dp))
            queue.forEachIndexed { index, item ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (item.done) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        }
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = NumberUtils.formatForDisplay(item.number) + "  •  " + item.amount,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = item.operatorName + "  " + item.code,
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (item.done) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            IconButton(
                                onClick = {
                                    val operator = app.operatorNamed(item.operatorName)
                                    send(item.number, item.amount, item.code, operator)
                                    queue = queue.toMutableList().also { list ->
                                        list[index] = item.copy(done = true)
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Send,
                                    contentDescription = LanguageManager.getString("execute"),
                                    tint = theme.mainColor
                                )
                            }
                        }
                        IconButton(onClick = { queue = queue - item }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = LanguageManager.getString("delete"),
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(96.dp))
    }

    if (showSimPicker) {
        SimPickerDialog(
            sims = sims,
            operator = destinationOperator,
            selected = routing.sim,
            onDismiss = { showSimPicker = false },
            onPick = { sim, _ ->
                showSimPicker = false
                forcedSubscription = sim.subscriptionId
            }
        )
    }

    if (showContacts) {
        AlertDialog(
            onDismissRequest = { showContacts = false },
            title = { Text(LanguageManager.getString("tab_contacts"), fontWeight = FontWeight.Bold) },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                    items(contacts) { contact ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    number = contact.number
                                    showContacts = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            InitialsAvatar(text = contact.initials, size = 36.dp)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(contact.name, fontWeight = FontWeight.Bold)
                                Text(
                                    text = NumberUtils.formatForDisplay(contact.number),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showContacts = false }) {
                    Text(LanguageManager.getString("close"))
                }
            }
        )
    }
}
