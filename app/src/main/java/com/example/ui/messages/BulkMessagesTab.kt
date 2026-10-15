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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.AppSettings
import com.example.data.model.MessageTemplate
import com.example.telephony.Contact
import com.example.telephony.ContactIndex
import com.example.telephony.NumberUtils
import com.example.telephony.SmsRecipient
import com.example.telephony.SmsSender
import com.example.ui.components.EmptyState
import com.example.ui.components.InitialsAvatar
import com.example.ui.components.SimSelector
import com.example.ui.util.LanguageManager
import com.example.viewmodel.AppViewModel
import com.example.viewmodel.MessagesViewModel
import com.example.viewmodel.PhoneViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState

/**
 * Sending one message to many contacts at once.
 *
 * The contact list owns the screen — choosing who receives the message is the
 * long part of the job — and the message itself is written in a sheet opened
 * from the bar at the bottom. That bar used to be a card laid on top of the
 * list, which hid the very contacts the user was trying to pick.
 *
 * Recipients can be picked from the contacts or typed by hand, the body accepts
 * `{NAME}` so every message stays personal, and — when smart routing is on —
 * each recipient is served by the SIM of their own operator.
 */
@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun BulkMessagesTab(
    app: AppViewModel,
    phone: PhoneViewModel,
    messages: MessagesViewModel
) {
    val context = LocalContext.current
    val currentLang by LanguageManager.currentLang

    val permissions = rememberMultiplePermissionsState(
        listOf(Manifest.permission.SEND_SMS, Manifest.permission.READ_CONTACTS)
    )
    LaunchedEffect(Unit) {
        if (!permissions.allPermissionsGranted) permissions.launchMultiplePermissionRequest()
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
    var showComposer by remember { mutableStateOf(false) }
    var showManualDialog by remember { mutableStateOf(false) }
    var showTemplateDialog by remember { mutableStateOf(false) }

    // Progress and report live in the view model: the campaign keeps running
    // when this tab leaves the screen.
    val progress by messages.bulkProgress.collectAsState()
    val report by messages.bulkReport.collectAsState()

    val routing = remember(sims, forcedSubscription) { app.routeFor(null, forcedSubscription) }
    val index = remember(contacts) { ContactIndex(contacts) }
    val filtered = remember(query, index) { index.search(query) }

    fun toggle(contact: Contact) {
        val existing = selected.firstOrNull { NumberUtils.sameNumber(it.number, contact.number) }
        selected = if (existing != null) {
            selected - existing
        } else {
            selected + SmsRecipient(contact.name, contact.number)
        }
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

        // The send goes to a foreground service, which also writes the history
        // entry: leaving this screen — or the app — no longer stops it.
        messages.sendBulk(
            groups = groups,
            body = body,
            subscriptionId = routing.sim?.subscriptionId ?: AppSettings.NO_SUBSCRIPTION,
            simLabel = routing.sim?.title.orEmpty()
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (!SmsSender.hasPermission(context)) {
            EmptyState(
                icon = Icons.AutoMirrored.Filled.Message,
                title = LanguageManager.getString("permission_sms_desc"),
                actionLabel = LanguageManager.getString("grant_permission"),
                onAction = { permissions.launchMultiplePermissionRequest() }
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
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
            IconButton(onClick = { showManualDialog = true }) {
                Icon(
                    imageVector = Icons.Default.Dialpad,
                    contentDescription = LanguageManager.getString("add_numbers"),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        // The recipients already picked, removed one by one with a tap.
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
                        },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = { selected = filtered.map { SmsRecipient(it.name, it.number) } }
            ) {
                Text(
                    text = LanguageManager.getString("select_all"),
                    style = MaterialTheme.typography.labelSmall
                )
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

        // The list is the screen: nothing is laid on top of it any more.
        Box(modifier = Modifier.weight(1f)) {
            if (filtered.isEmpty()) {
                EmptyState(
                    icon = Icons.Default.Group,
                    title = LanguageManager.getString("no_contacts")
                )
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 8.dp)) {
                    items(filtered, key = { it.id.toString() + it.number }) { contact ->
                        val checked = selected.any {
                            NumberUtils.sameNumber(it.number, contact.number)
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { toggle(contact) }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
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

        // One line at the bottom: the SIM, the message, and the send button.
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp
        ) {
            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                progress?.let { (done, total) ->
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
                            .padding(vertical = 4.dp)
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SimSelector(
                        routing = routing,
                        sims = sims,
                        onPick = { sim ->
                            forcedSubscription = sim.subscriptionId
                            AppSettings.setDefaultSmsSubscription(context, sim.subscriptionId)
                        }
                    )
                    OutlinedButton(
                        onClick = { showComposer = true },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = body.ifBlank { LanguageManager.getString("compose") },
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { if (body.isBlank()) showComposer = true else send() },
                        enabled = selected.isNotEmpty() && progress == null,
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = LanguageManager.getString("send"),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(text = selected.size.toString(), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (showComposer) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showComposer = false },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp)
                    .imePadding()
            ) {
                Text(
                    text = LanguageManager.getString("message_body"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(8.dp))

                if (templates.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(templates) { template ->
                            AssistChip(
                                onClick = { body = template.body },
                                label = {
                                    Text(
                                        text = template.title,
                                        style = MaterialTheme.typography.labelSmall
                                    )
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
                    minLines = 3,
                    maxLines = 6,
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

                Row(
                    modifier = Modifier.fillMaxWidth(),
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

                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { showComposer = false },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(LanguageManager.getString("done"), fontWeight = FontWeight.Bold)
                }
            }
        }
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
                            .filter { number ->
                                selected.none { NumberUtils.sameNumber(it.number, number) }
                            }
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
            onDismissRequest = { messages.clearBulkReport() },
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
                Button(onClick = { messages.clearBulkReport() }) {
                    Text(LanguageManager.getString("ok"))
                }
            }
        )
    }
}
