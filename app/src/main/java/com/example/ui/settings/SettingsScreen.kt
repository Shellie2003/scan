package com.example.ui.settings

import android.Manifest
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.AppSettings
import com.example.data.model.MessageTemplate
import com.example.data.model.Operator
import com.example.data.model.SimProfile
import com.example.telephony.DefaultDialerManager
import com.example.telephony.SimCard
import com.example.telephony.SimManager
import com.example.ui.AboutScreen
import com.example.ui.components.SectionHeader
import com.example.ui.theme.ThemeController
import com.example.ui.theme.ThemeMode
import com.example.ui.util.LanguageManager
import com.example.ui.util.OperatorColorUtils
import com.example.viewmodel.AppViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

/**
 * Everything the user can change: operators and their codes, SIM cards, plans,
 * message templates and the behaviour of the automatic choices.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun SettingsScreen(app: AppViewModel) {
    val context = LocalContext.current
    val currentLang by LanguageManager.currentLang
    val operators by app.operators.collectAsState()
    val sims by app.sims.collectAsState()
    val templates by app.messageTemplates.collectAsState()
    val history by app.history.collectAsState()
    val profiles by app.simProfiles.collectAsState()

    val phoneStatePermission = rememberPermissionState(Manifest.permission.READ_PHONE_STATE)
    LaunchedEffect(Unit) {
        if (!phoneStatePermission.status.isGranted) phoneStatePermission.launchPermissionRequest()
        app.refreshSims()
    }

    var editingOperator by remember { mutableStateOf<Operator?>(null) }
    var deletingOperator by remember { mutableStateOf<Operator?>(null) }
    var editingSim by remember { mutableStateOf<SimCard?>(null) }
    var editingTemplate by remember { mutableStateOf<MessageTemplate?>(null) }
    var confirmRestoreOffers by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }

    var smartRouting by remember { mutableStateOf(AppSettings.smartSimRouting(context)) }
    var alwaysAsk by remember { mutableStateOf(AppSettings.alwaysAskSim(context)) }
    var confirmDial by remember { mutableStateOf(AppSettings.confirmBeforeDial(context)) }
    var inAppUssd by remember { mutableStateOf(AppSettings.inAppUssd(context)) }

    if (showAbout) {
        AboutScreen(onBack = { showAbout = false })
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Text(
            text = LanguageManager.getString("settings_title"),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
        )

        // ------------------------------------------------------------- language
        SectionHeader(LanguageManager.getString("language"))
        Text(
            text = LanguageManager.getString("language_desc"),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        LanguageManager.LanguageSelector()

        // --------------------------------------------------------------- sounds
        SectionHeader(LanguageManager.getString("section_sounds"))
        SoundSettingsSection()

        // ------------------------------------------------------------ operators
        SectionHeader(LanguageManager.getString("section_operators"))
        operators.forEach { operator ->
            OperatorRow(
                operator = operator,
                onEdit = { editingOperator = operator },
                onDelete = { deletingOperator = operator }
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(
                onClick = {
                    editingOperator = Operator(name = "", template = "", codeLength = 14)
                }
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(LanguageManager.getString("new_operator"))
            }
            TextButton(onClick = { app.restoreDefaultOperators() }) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(LanguageManager.getString("restore_defaults"))
            }
        }

        // ----------------------------------------------------------------- SIMs
        SectionHeader(LanguageManager.getString("section_sims"))
        Text(
            text = LanguageManager.getString("sim_multi_hint"),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        if (sims.isEmpty()) {
            Text(
                text = if (SimManager.hasPhoneStatePermission(context)) {
                    LanguageManager.getString("no_sim_detected")
                } else {
                    LanguageManager.getString("no_sim_permission")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
            TextButton(onClick = { phoneStatePermission.launchPermissionRequest() }) {
                Text(LanguageManager.getString("grant_permission"))
            }
        }
        sims.forEach { sim ->
            val operator = SimManager.operatorOf(sim, operators)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable { editingSim = sim },
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.SimCard,
                        contentDescription = null,
                        tint = OperatorColorUtils.colorOf(operator)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = sim.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = sim.subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (operator != null) {
                            Text(
                                text = operator.name,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = OperatorColorUtils.colorOf(operator)
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = LanguageManager.getString("edit_sim"),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
        TextButton(onClick = { app.refreshSims() }) {
            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(LanguageManager.getString("retry"))
        }

        // ------------------------------------------------------------- behaviour
        SectionHeader(LanguageManager.getString("section_general"))
        SettingSwitch(
            title = LanguageManager.getString("smart_routing"),
            description = LanguageManager.getString("smart_routing_desc"),
            checked = smartRouting,
            onCheckedChange = {
                smartRouting = it
                AppSettings.setSmartSimRouting(context, it)
            }
        )
        SettingSwitch(
            title = LanguageManager.getString("always_ask_sim"),
            description = LanguageManager.getString("always_ask_sim_desc"),
            checked = alwaysAsk,
            onCheckedChange = {
                alwaysAsk = it
                AppSettings.setAlwaysAskSim(context, it)
            }
        )
        SettingSwitch(
            title = LanguageManager.getString("confirm_before_dial"),
            description = LanguageManager.getString("confirm_before_dial_desc"),
            checked = confirmDial,
            onCheckedChange = {
                confirmDial = it
                AppSettings.setConfirmBeforeDial(context, it)
            }
        )
        SettingSwitch(
            title = LanguageManager.getString("ussd_response"),
            description = LanguageManager.getString("in_app_ussd_desc"),
            checked = inAppUssd,
            onCheckedChange = {
                inAppUssd = it
                AppSettings.setInAppUssd(context, it)
                // Switching it back on is the user saying "try again", after the
                // app gave up on a phone that kept refusing the channel.
                if (it) AppSettings.resetInAppUssd(context)
            }
        )

        val isDefaultDialer = remember { DefaultDialerManager.isDefaultDialer(context) }
        Text(
            text = if (isDefaultDialer) {
                LanguageManager.getString("default_dialer_active")
            } else {
                LanguageManager.getString("default_dialer_title")
            },
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = if (isDefaultDialer) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(vertical = 8.dp)
        )

        // ------------------------------------------------------------ appearance
        AppearanceSection()

        // ----------------------------------------------------------------- plans
        SectionHeader(LanguageManager.getString("section_offers"))
        TextButton(onClick = { confirmRestoreOffers = true }) {
            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(LanguageManager.getString("restore_defaults"))
        }

        // -------------------------------------------------------------- messages
        SectionHeader(LanguageManager.getString("section_messages"))
        templates.forEach { template ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable { editingTemplate = template },
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
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
                            text = template.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = template.body,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2
                        )
                    }
                    IconButton(onClick = { app.deleteTemplate(template) }) {
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
        TextButton(onClick = { editingTemplate = MessageTemplate(title = "", body = "") }) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(LanguageManager.getString("new_template"))
        }

        // --------------------------------------------------------------- history
        SectionHeader(LanguageManager.getString("section_history"))
        if (history.isEmpty()) {
            Text(
                text = LanguageManager.getString("empty_history"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            history.take(15).forEach { entry ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = entry.label.ifBlank { entry.dialedCode },
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = listOf(
                                historyKindLabel(entry.kind),
                                entry.operatorName,
                                entry.simLabel
                            ).filter { it.isNotBlank() }.joinToString(" • "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = android.text.format.DateUtils.getRelativeTimeSpanString(
                            entry.timestamp,
                            System.currentTimeMillis(),
                            android.text.format.DateUtils.MINUTE_IN_MILLIS
                        ).toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterVertically)
                    )
                    IconButton(
                        onClick = { app.deleteHistory(entry) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = LanguageManager.getString("delete"),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
            TextButton(onClick = { app.clearHistory() }) {
                Text(
                    text = LanguageManager.getString("clear_history"),
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        SectionHeader(LanguageManager.getString("about_title"))
        TextButton(onClick = { showAbout = true }) {
            Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(LanguageManager.getString("about_title"))
        }

        Spacer(Modifier.height(96.dp))
    }

    editingOperator?.let { operator ->
        OperatorEditorDialog(
            operator = operator,
            onDismiss = { editingOperator = null },
            onSave = {
                if (operator.id != 0 && !operator.name.equals(it.name, ignoreCase = true)) {
                    app.renameOperator(operator, it.name)
                    app.saveOperator(it)
                } else {
                    app.saveOperator(it)
                }
                editingOperator = null
            }
        )
    }

    deletingOperator?.let { operator ->
        AlertDialog(
            onDismissRequest = { deletingOperator = null },
            title = { Text(LanguageManager.getString("confirm_delete"), fontWeight = FontWeight.Bold) },
            text = {
                Text(LanguageManager.getString("confirm_delete_desc", "operator" to operator.name))
            },
            confirmButton = {
                Button(
                    onClick = {
                        app.deleteOperator(operator)
                        deletingOperator = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) { Text(LanguageManager.getString("delete")) }
            },
            dismissButton = {
                TextButton(onClick = { deletingOperator = null }) {
                    Text(LanguageManager.getString("cancel"))
                }
            }
        )
    }

    editingSim?.let { sim ->
        val profile = profiles.firstOrNull { it.subscriptionId == sim.subscriptionId }
            ?: SimProfile(subscriptionId = sim.subscriptionId)
        SimProfileDialog(
            sim = sim,
            profile = profile,
            operators = operators,
            onDismiss = { editingSim = null },
            onSave = {
                app.saveSimProfile(it)
                editingSim = null
            }
        )
    }

    editingTemplate?.let { template ->
        TemplateDialog(
            template = template,
            onDismiss = { editingTemplate = null },
            onSave = {
                app.saveTemplate(it)
                editingTemplate = null
            }
        )
    }

    if (confirmRestoreOffers) {
        AlertDialog(
            onDismissRequest = { confirmRestoreOffers = false },
            title = { Text(LanguageManager.getString("restore_defaults"), fontWeight = FontWeight.Bold) },
            text = { Text(LanguageManager.getString("restore_offers_confirm")) },
            confirmButton = {
                Button(
                    onClick = {
                        app.restoreDefaultOffers()
                        confirmRestoreOffers = false
                    }
                ) { Text(LanguageManager.getString("confirm")) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRestoreOffers = false }) {
                    Text(LanguageManager.getString("cancel"))
                }
            }
        )
    }
}

private fun historyKindLabel(kind: String): String = when (kind) {
    com.example.data.model.ScanHistory.KIND_OFFER -> LanguageManager.getString("history_offer")
    com.example.data.model.ScanHistory.KIND_TRANSFER -> LanguageManager.getString("history_transfer")
    com.example.data.model.ScanHistory.KIND_SMS -> LanguageManager.getString("history_sms")
    else -> LanguageManager.getString("history_recharge")
}

/**
 * Light or dark, and whether SCAN keeps its own colours.
 *
 * The choice is applied at once through `ThemeController`, which every screen —
 * the call screen included — reads.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppearanceSection() {
    val context = LocalContext.current
    val mode by ThemeController.mode
    val dynamic by ThemeController.dynamicColors

    SectionHeader(LanguageManager.getString("section_appearance"))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val options = listOf(
            ThemeMode.SYSTEM to LanguageManager.getString("theme_system"),
            ThemeMode.LIGHT to LanguageManager.getString("theme_light"),
            ThemeMode.DARK to LanguageManager.getString("theme_dark")
        )
        options.forEach { (value, label) ->
            val selected = mode == value
            Surface(
                onClick = { ThemeController.setMode(context, value) },
                modifier = Modifier.weight(1f),
                shape = MaterialTheme.shapes.small,
                color = if (selected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                },
                border = if (selected) {
                    BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                } else {
                    null
                }
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                )
            }
        }
    }

    // The wallpaper palette only exists from Android 12 on.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        SettingSwitch(
            title = LanguageManager.getString("dynamic_colors"),
            description = LanguageManager.getString("dynamic_colors_desc"),
            checked = dynamic,
            onCheckedChange = { ThemeController.setDynamicColors(context, it) }
        )
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                text = description,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun OperatorRow(
    operator: Operator,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onEdit() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .height(64.dp)
                    .background(OperatorColorUtils.colorOf(operator))
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(12.dp)
            ) {
                Text(
                    text = operator.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = OperatorColorUtils.colorOf(operator)
                )
                Text(
                    text = operator.template,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = LanguageManager.getString("code_length") + ": " + operator.codeLength,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onEdit) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = LanguageManager.getString("edit"),
                    modifier = Modifier.size(18.dp)
                )
            }
            IconButton(onClick = onDelete) {
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

/** Full editor: every dialable code of an operator can be changed here. */
@Composable
private fun OperatorEditorDialog(
    operator: Operator,
    onDismiss: () -> Unit,
    onSave: (Operator) -> Unit
) {
    var name by remember { mutableStateOf(operator.name) }
    var template by remember { mutableStateOf(operator.template) }
    var codeLength by remember { mutableStateOf(operator.codeLength.toString()) }
    var balance by remember { mutableStateOf(operator.balanceTemplate) }
    var transfer by remember { mutableStateOf(operator.transferTemplate) }
    var share by remember { mutableStateOf(operator.shareTemplate) }
    var withdraw by remember { mutableStateOf(operator.withdrawTemplate) }
    var service by remember { mutableStateOf(operator.serviceNumber) }
    var prefixes by remember { mutableStateOf(operator.prefixes) }
    var keywords by remember { mutableStateOf(operator.keywords) }
    var color by remember { mutableStateOf(operator.colorHex) }
    var strip by remember { mutableStateOf(operator.stripUssdSymbols) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(
                text = if (operator.id == 0) {
                    LanguageManager.getString("new_operator")
                } else {
                    LanguageManager.getString("edit_operator")
                },
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = LanguageManager.getString("operator_dialog_desc"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Field(name, LanguageManager.getString("operator_name")) { name = it }
                Field(template, LanguageManager.getString("recharge_code")) { template = it }
                Field(codeLength, LanguageManager.getString("code_length")) {
                    codeLength = it.filter { c -> c.isDigit() }.take(2)
                }
                Field(balance, LanguageManager.getString("balance_code")) { balance = it }
                Field(transfer, LanguageManager.getString("transfer_code")) { transfer = it }
                Field(share, LanguageManager.getString("share_code")) { share = it }
                Field(withdraw, LanguageManager.getString("withdraw_code")) { withdraw = it }
                Text(
                    text = LanguageManager.getString("placeholders_hint"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Field(service, LanguageManager.getString("service_number")) { service = it }
                Field(prefixes, LanguageManager.getString("prefixes")) { prefixes = it }
                Field(keywords, LanguageManager.getString("keywords")) { keywords = it }
                Field(color, LanguageManager.getString("color")) { color = it }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = strip, onCheckedChange = { strip = it })
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            text = LanguageManager.getString("strip_symbols"),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = LanguageManager.getString("strip_symbols_desc"),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (template.isNotBlank()) {
                    val length = codeLength.toIntOrNull() ?: 14
                    Text(
                        text = LanguageManager.getString("preview_generated") + " " +
                            template.replace("{CODE}", "1".repeat(length)),
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        operator.copy(
                            name = name.trim(),
                            template = template.trim(),
                            codeLength = codeLength.toIntOrNull()?.coerceIn(4, 20) ?: 14,
                            balanceTemplate = balance.trim(),
                            transferTemplate = transfer.trim(),
                            shareTemplate = share.trim(),
                            withdrawTemplate = withdraw.trim(),
                            serviceNumber = service.trim(),
                            prefixes = prefixes.trim(),
                            keywords = keywords.trim(),
                            colorHex = color.trim(),
                            stripUssdSymbols = strip,
                            templatesVerified = true
                        )
                    )
                },
                enabled = name.isNotBlank() && template.isNotBlank()
            ) { Text(LanguageManager.getString("save")) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(LanguageManager.getString("cancel")) }
        }
    )
}

@Composable
private fun SimProfileDialog(
    sim: SimCard,
    profile: SimProfile,
    operators: List<Operator>,
    onDismiss: () -> Unit,
    onSave: (SimProfile) -> Unit
) {
    var label by remember { mutableStateOf(profile.label.ifBlank { sim.displayName }) }
    var number by remember { mutableStateOf(profile.phoneNumber.ifBlank { sim.number }) }
    var operatorName by remember { mutableStateOf(profile.operatorName) }
    var enabled by remember { mutableStateOf(profile.enabled) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        title = { Text(LanguageManager.getString("edit_sim"), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = sim.subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Field(label, LanguageManager.getString("sim_label")) { label = it }
                Field(number, LanguageManager.getString("sim_number")) { number = it }
                Text(
                    text = LanguageManager.getString("sim_operator_override"),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { operatorName = "" }) {
                        Text(
                            text = LanguageManager.getString("none"),
                            fontWeight = if (operatorName.isBlank()) FontWeight.ExtraBold else FontWeight.Normal
                        )
                    }
                    operators.forEach { operator ->
                        TextButton(onClick = { operatorName = operator.name }) {
                            Text(
                                text = operator.name,
                                fontWeight = if (operatorName == operator.name) {
                                    FontWeight.ExtraBold
                                } else {
                                    FontWeight.Normal
                                },
                                color = OperatorColorUtils.colorOf(operator)
                            )
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                    Spacer(Modifier.width(8.dp))
                    Text(LanguageManager.getString("sim_enabled"))
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        profile.copy(
                            label = label.trim(),
                            phoneNumber = number.trim(),
                            operatorName = operatorName.trim(),
                            enabled = enabled
                        )
                    )
                }
            ) { Text(LanguageManager.getString("save")) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(LanguageManager.getString("cancel")) }
        }
    )
}

@Composable
private fun TemplateDialog(
    template: MessageTemplate,
    onDismiss: () -> Unit,
    onSave: (MessageTemplate) -> Unit
) {
    var title by remember { mutableStateOf(template.title) }
    var body by remember { mutableStateOf(template.body) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        title = { Text(LanguageManager.getString("templates"), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(title, LanguageManager.getString("template_title")) { title = it }
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    label = { Text(LanguageManager.getString("message_body")) },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = LanguageManager.getString("message_hint"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(template.copy(title = title.trim(), body = body)) },
                enabled = title.isNotBlank() && body.isNotBlank()
            ) { Text(LanguageManager.getString("save")) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(LanguageManager.getString("cancel")) }
        }
    )
}

@Composable
private fun Field(value: String, label: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    )
}
