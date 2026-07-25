package com.example.ui.messages

import android.Manifest
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.AppSettings
import com.example.data.model.MessageTemplate
import com.example.data.model.ScanHistory
import com.example.telephony.Contact
import com.example.telephony.ContactsRepository
import com.example.telephony.NumberUtils
import com.example.telephony.SmsRecipient
import com.example.telephony.SmsSendReport
import com.example.telephony.SmsSender
import com.example.ui.components.EmptyState
import com.example.ui.components.InitialsAvatar
import com.example.ui.components.SimIndicator
import com.example.ui.components.SimPickerDialog
import com.example.ui.util.LanguageManager
import com.example.viewmodel.AppViewModel
import com.example.viewmodel.PhoneViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import kotlinx.coroutines.launch

/**
 * Sending one message to many contacts at once.
 *
 * Recipients can be picked from the contacts or typed by hand, the body accepts
 * `{NAME}` so every message stays personal, and — when smart routing is on —
 * each recipient is served by the SIM of their own operator, which keeps the
 * messages on-net whenever a card of that operator is in the phone.
 */
@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MessagesScreen(
    app: AppViewModel,
    phone: PhoneViewModel,
    prefilledNumber: String = ""
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentLang by LanguageManager.currentLang

    val permissions = rememberMultiplePermissionsState(
        listOf(Manifest.permission.SEND_SMS, Manifest.permission.READ_CONTACTS)
    )
    LaunchedEffect(Unit) {
        if (!permissions.allPermissionsGranted) permissions.launchMultiplePermissionRequest()
        phone.refresh()
    }

    val contacts by phone.contacts.collectAsState()
    val sims by app.sims.collectAsState()
    val templates by app.messageTemplates.collectAsState()

    var query by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<List<SmsRecipient>>(emptyList()) }
    var smartRouting by remember { mutableStateOf(AppSettings.smartSimRouting(context)) }
    var forcedSubscription by remember {
        mutableStateOf(AppSettings.defaultSmsSubscription(context))
    }
    var showSimPicker by remember { mutableStateOf(false) }
    var showManualDialog by remember { mutableStateOf(false) }
    var showTemplateDialog by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var report by remember { mutableStateOf<SmsSendReport?>(null) }

    LaunchedEffect(prefilledNumber) {
        if (prefilledNumber.isNotBlank() && selected.none { it.number == prefilledNumber }) {
            val name = contacts.firstOrNull { NumberUtils.sameNumber(it.number, prefilledNumber) }?.name
            selected = selected + SmsRecipient(name.orEmpty(), prefilledNumber)
        }
    }

    val routing = remember(sims, forcedSubscription) { app.routeFor(null, forcedSubscription) }
    val filtered = remember(query, contacts) { ContactsRepository.search(contacts, query) }

    fun toggle(contact: Contact) {
        val existing = selected.firstOrNull { NumberUtils.sameNumber(it.number, contact.number) }
        selected = if (existing != null) selected - existing
        else selected + SmsRecipient(contact.name, contact.number)
    }

    fun send() {
        if (selected.isEmpty() || body.isBlank()) return
        val fallbackSub = routing.sim?.subscriptionId ?: AppSettings.NO_SUBSCRIPTION
        val groups: Map<Int, List<SmsRecipient>> = if (smartRouting && sims.size > 1) {
            selected.groupBy { recipient ->
                app.routeForNumber(recipient.number).sim?.subscriptionId ?: fallbackSub
            }
        } else {
            mapOf(fallbackSub to selected)
        }

        scope.launch {
            var sent = 0
            val total = selected.size
            progress = 0 to total
            var merged = SmsSendReport()
            groups.forEach { (subscriptionId, recipients) ->
                val partial = SmsSender.sendBulk(
                    context = context,
                    recipients = recipients,
                    body = body,
                    subscriptionId = subscriptionId
                ) { done, _, _ ->
                    progress = (sent + done) to total
                }
                sent += recipients.size
                merged = SmsSendReport(
                    sent = merged.sent + partial.sent,
                    failed = merged.failed + partial.failed
                )
            }
            progress = null
            report = merged
            app.record(
                operator = null,
                code = "",
                dialedCode = "",
                sim = routing.sim,
                kind = ScanHistory.KIND_SMS,
                label = LanguageManager.getString("recipients_count", "count" to merged.sent.size)
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = LanguageManager.getString("messages_title"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = LanguageManager.getString("messages_desc"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            LanguageManager.LanguageSelector()
        }

        if (!SmsSender.hasPermission(context)) {
            EmptyState(
                icon = Icons.Default.Message,
                title = LanguageManager.getString("permission_sms_desc"),
                actionLabel = LanguageManager.getString("grant_permission"),
                onAction = { permissions.launchMultiplePermissionRequest() }
            )
        }

        // Selected recipients
        if (selected.isNotEmpty()) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(selected) { recipient ->
                    AssistChip(
                        onClick = { selected = selected - recipient },
                        label = {
                            Text(
                                text = recipient.name.ifBlank {
                                    NumberUtils.formatForDisplay(recipient.number)
                                },
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                placeholder = { Text(LanguageManager.getString("search_contacts")) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = { showManualDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = {
                    selected = filtered.map { SmsRecipient(it.name, it.number) }
                }
            ) {
                Text(LanguageManager.getString("select_all"), style = MaterialTheme.typography.labelSmall)
            }
            TextButton(onClick = { selected = emptyList() }) {
                Text(
                    text = LanguageManager.getString("clear_selection"),
                    style = MaterialTheme.typography.labelSmall
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = LanguageManager.getString("recipients_count", "count" to selected.size),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
        }

        Box(modifier = Modifier.weight(1f)) {
            if (filtered.isEmpty()) {
                EmptyState(icon = Icons.Default.Group, title = LanguageManager.getString("no_contacts"))
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 8.dp)) {
                    items(filtered) { contact ->
                        val checked = selected.any { NumberUtils.sameNumber(it.number, contact.number) }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { toggle(contact) }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = checked, onCheckedChange = { toggle(contact) })
                            Spacer(Modifier.width(4.dp))
                            InitialsAvatar(text = contact.initials, size = 36.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = contact.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = NumberUtils.formatForDisplay(contact.number),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // Composer
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                if (templates.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(templates) { template ->
                            AssistChip(
                                onClick = { body = template.body },
                                label = {
                                    Text(template.title, style = MaterialTheme.typography.labelSmall)
                                }
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    placeholder = { Text(LanguageManager.getString("message_hint")) },
                    minLines = 2,
                    maxLines = 5,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = LanguageManager.getString(
                            "sms_parts",
                            "count" to SmsSender.partCount(
                                context,
                                body,
                                routing.sim?.subscriptionId ?: AppSettings.NO_SUBSCRIPTION
                            )
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.weight(1f))
                    TextButton(
                        onClick = { showTemplateDialog = true },
                        enabled = body.isNotBlank()
                    ) {
                        Text(
                            text = LanguageManager.getString("new_template"),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }

                SimIndicator(
                    routing = routing,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { showSimPicker = true }
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Switch(
                        checked = smartRouting,
                        onCheckedChange = {
                            smartRouting = it
                            AppSettings.setSmartSimRouting(context, it)
                        }
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = LanguageManager.getString("smart_routing"),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.weight(1f)
                    )
                }

                progress?.let { (done, total) ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = LanguageManager.getString(
                            "sending",
                            "done" to done,
                            "total" to total
                        ),
                        style = MaterialTheme.typography.labelSmall
                    )
                    LinearProgressIndicator(
                        progress = { if (total == 0) 0f else done.toFloat() / total },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                    )
                }

                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { send() },
                    enabled = selected.isNotEmpty() && body.isNotBlank() && progress == null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = LanguageManager.getString("send") + " (" + selected.size + ")",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    if (showSimPicker) {
        SimPickerDialog(
            sims = sims,
            operator = null,
            selected = routing.sim,
            onDismiss = { showSimPicker = false },
            onPick = { sim, save ->
                showSimPicker = false
                forcedSubscription = sim.subscriptionId
                if (save) AppSettings.setDefaultSmsSubscription(context, sim.subscriptionId)
            }
        )
    }

    if (showManualDialog) {
        var manual by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showManualDialog = false },
            title = { Text(LanguageManager.getString("add_numbers"), fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        text = LanguageManager.getString("add_numbers_hint"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = manual,
                        onValueChange = { manual = it },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val numbers = NumberUtils.splitNumbers(manual)
                        val added = numbers
                            .filter { number -> selected.none { NumberUtils.sameNumber(it.number, number) } }
                            .map { number ->
                                SmsRecipient(
                                    name = contacts.firstOrNull {
                                        NumberUtils.sameNumber(it.number, number)
                                    }?.name.orEmpty(),
                                    number = number
                                )
                            }
                        selected = selected + added
                        showManualDialog = false
                    }
                ) { Text(LanguageManager.getString("add")) }
            },
            dismissButton = {
                TextButton(onClick = { showManualDialog = false }) {
                    Text(LanguageManager.getString("cancel"))
                }
            }
        )
    }

    if (showTemplateDialog) {
        var title by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showTemplateDialog = false },
            title = { Text(LanguageManager.getString("new_template"), fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(LanguageManager.getString("template_title")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (title.isNotBlank()) {
                            app.saveTemplate(MessageTemplate(title = title.trim(), body = body))
                        }
                        showTemplateDialog = false
                    }
                ) { Text(LanguageManager.getString("save")) }
            },
            dismissButton = {
                TextButton(onClick = { showTemplateDialog = false }) {
                    Text(LanguageManager.getString("cancel"))
                }
            }
        )
    }

    report?.let { result ->
        AlertDialog(
            onDismissRequest = { report = null },
            title = { Text(LanguageManager.getString("messages_title"), fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        text = LanguageManager.getString(
                            "send_report",
                            "sent" to result.sent.size,
                            "failed" to result.failed.size
                        ),
                        fontWeight = FontWeight.Bold
                    )
                    if (result.failed.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        result.failed.take(10).forEach { (recipient, reason) ->
                            Text(
                                text = "• " + recipient.number + " (" + reason + ")",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { report = null }) { Text(LanguageManager.getString("ok")) }
            }
        )
    }
}
