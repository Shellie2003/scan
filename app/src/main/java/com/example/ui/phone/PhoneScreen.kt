package com.example.ui.phone

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.provider.CallLog
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallMissed
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.telephony.CallEntry
import com.example.telephony.CallLogRepository
import com.example.telephony.Contact
import com.example.telephony.ContactsRepository
import com.example.telephony.DefaultDialerManager
import com.example.telephony.NumberUtils
import com.example.ui.components.EmptyState
import com.example.ui.components.InitialsAvatar
import com.example.ui.components.SimIndicator
import com.example.ui.components.SimLauncher
import com.example.ui.util.LanguageManager
import com.example.viewmodel.AppViewModel
import com.example.viewmodel.PhoneViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState

private enum class PhoneTab(val labelKey: String) {
    KEYPAD("tab_keypad"),
    RECENTS("tab_recents"),
    CONTACTS("tab_contacts"),
    FAVORITES("tab_favorites")
}

/**
 * The phone part of the app: keypad, call log, contacts and favourites.
 *
 * Every call goes through [SimLauncher], so the SIM card used is chosen the same
 * way here as for a recharge or a plan purchase.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PhoneScreen(
    app: AppViewModel,
    phone: PhoneViewModel,
    launcher: SimLauncher,
    prefilledNumber: String = "",
    onMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val currentLang by LanguageManager.currentLang
    val permissions = rememberMultiplePermissionsState(
        listOf(
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.CALL_PHONE
        )
    )

    var tab by remember { mutableStateOf(PhoneTab.KEYPAD) }
    var input by remember { mutableStateOf(prefilledNumber) }

    LaunchedEffect(prefilledNumber) {
        if (prefilledNumber.isNotBlank()) {
            input = prefilledNumber
            tab = PhoneTab.KEYPAD
        }
    }

    LaunchedEffect(Unit) {
        if (!permissions.allPermissionsGranted) permissions.launchMultiplePermissionRequest()
    }

    LaunchedEffect(permissions.allPermissionsGranted) {
        phone.refresh()
        app.refreshSims()
    }

    val contacts by phone.contacts.collectAsState()
    val callLog by phone.callLog.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        DefaultDialerBanner()

        TabRow(selectedTabIndex = tab.ordinal) {
            PhoneTab.entries.forEach { entry ->
                Tab(
                    selected = tab == entry,
                    onClick = { tab = entry },
                    text = {
                        Text(
                            text = LanguageManager.getString(entry.labelKey),
                            fontWeight = if (tab == entry) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 12.sp
                        )
                    },
                    icon = {
                        Icon(
                            imageVector = when (entry) {
                                PhoneTab.KEYPAD -> Icons.Default.Dialpad
                                PhoneTab.RECENTS -> Icons.Default.History
                                PhoneTab.CONTACTS -> Icons.Default.Contacts
                                PhoneTab.FAVORITES -> Icons.Default.Star
                            },
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            when (tab) {
                PhoneTab.KEYPAD -> KeypadTab(
                    app = app,
                    input = input,
                    contacts = contacts,
                    onInputChange = { input = it },
                    onCall = { launcher.call(it) },
                    onMessage = onMessage
                )

                PhoneTab.RECENTS -> RecentsTab(
                    entries = callLog,
                    hasPermission = CallLogRepository.hasPermission(context),
                    onRequestPermission = { permissions.launchMultiplePermissionRequest() },
                    onCall = { launcher.call(it) },
                    onMessage = onMessage
                )

                PhoneTab.CONTACTS -> ContactsTab(
                    contacts = contacts,
                    hasPermission = ContactsRepository.hasPermission(context),
                    onRequestPermission = { permissions.launchMultiplePermissionRequest() },
                    onCall = { launcher.call(it) },
                    onMessage = onMessage
                )

                PhoneTab.FAVORITES -> FavoritesTab(
                    contacts = contacts.filter { it.isFavorite },
                    onCall = { launcher.call(it) }
                )
            }
        }
    }
}

/** Invitation to replace the phone app of the system, shown until it is accepted. */
@Composable
private fun DefaultDialerBanner() {
    val context = LocalContext.current
    var isDefault by remember { mutableStateOf(DefaultDialerManager.isDefaultDialer(context)) }
    var dismissed by remember { mutableStateOf(false) }
    val roleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        isDefault = DefaultDialerManager.isDefaultDialer(context)
    }

    if (isDefault || dismissed) return

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = LanguageManager.getString("default_dialer_title"),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = LanguageManager.getString("default_dialer_desc"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = { dismissed = true }) {
                    Text(LanguageManager.getString("later"))
                }
                TextButton(
                    onClick = {
                        val intent: Intent? = DefaultDialerManager.requestIntent(context)
                        if (intent != null) roleLauncher.launch(intent)
                    }
                ) {
                    Text(
                        text = LanguageManager.getString("default_dialer_cta"),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun KeypadTab(
    app: AppViewModel,
    input: String,
    contacts: List<Contact>,
    onInputChange: (String) -> Unit,
    onCall: (String) -> Unit,
    onMessage: (String) -> Unit
) {
    val sims by app.sims.collectAsState()
    val routing = remember(input, sims) { app.routeForNumber(input) }
    val suggestions = remember(input, contacts) {
        if (input.length < 2) emptyList() else ContactsRepository.search(contacts, input).take(12)
    }
    var showSimPicker by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        if (suggestions.isNotEmpty()) {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(suggestions) { contact ->
                    ContactRow(
                        contact = contact,
                        onCall = { onCall(contact.number) },
                        onMessage = { onMessage(contact.number) }
                    )
                }
            }
        } else {
            Spacer(Modifier.weight(1f))
        }

        Text(
            text = if (input.isEmpty()) "" else NumberUtils.formatForDisplay(input),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp)
        )

        if (input.isNotEmpty()) {
            val operator = routing.operator
            Text(
                text = operator?.let {
                    LanguageManager.getString("detected_operator", "operator" to it.name)
                } ?: LanguageManager.getString("unknown_operator"),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(Modifier.height(8.dp))

        SimIndicator(
            routing = routing,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(bottom = 8.dp),
            onClick = { showSimPicker = true }
        )

        val keys = listOf(
            "1" to "", "2" to "ABC", "3" to "DEF",
            "4" to "GHI", "5" to "JKL", "6" to "MNO",
            "7" to "PQRS", "8" to "TUV", "9" to "WXYZ",
            "*" to "", "0" to "+", "#" to ""
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            keys.chunked(3).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    row.forEach { (digit, letters) ->
                        DialKey(
                            digit = digit,
                            letters = letters,
                            onClick = { onInputChange(input + digit) },
                            onLongClick = {
                                if (digit == "0") onInputChange(input + "+")
                            }
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(Modifier.width(56.dp))
            FilledIconButton(
                onClick = { if (input.isNotBlank()) onCall(input) },
                enabled = input.isNotBlank(),
                modifier = Modifier.size(64.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = Color(0xFF2E7D32),
                    contentColor = Color.White
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Call,
                    contentDescription = LanguageManager.getString("call"),
                    modifier = Modifier.size(28.dp)
                )
            }
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .combinedClickable(
                        enabled = input.isNotEmpty(),
                        onClick = { onInputChange(input.dropLast(1)) },
                        onLongClick = { onInputChange("") }
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (input.isNotEmpty()) {
                    Icon(
                        imageVector = Icons.Default.Backspace,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (showSimPicker) {
        com.example.ui.components.SimPickerDialog(
            sims = sims,
            operator = routing.operator,
            selected = routing.sim,
            onDismiss = { showSimPicker = false },
            onPick = { sim, _ ->
                showSimPicker = false
                app.setPreferredSim(routing.operator, sim)
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DialKey(
    digit: String,
    letters: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = digit,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Medium
            )
            if (letters.isNotEmpty()) {
                Text(
                    text = letters,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 9.sp
                )
            }
        }
    }
}

@Composable
private fun RecentsTab(
    entries: List<CallEntry>,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onCall: (String) -> Unit,
    onMessage: (String) -> Unit
) {
    if (!hasPermission) {
        EmptyState(
            icon = Icons.Default.History,
            title = LanguageManager.getString("permission_calllog"),
            description = LanguageManager.getString("permission_calllog_desc"),
            actionLabel = LanguageManager.getString("grant_permission"),
            onAction = onRequestPermission
        )
        return
    }
    if (entries.isEmpty()) {
        EmptyState(
            icon = Icons.Default.History,
            title = LanguageManager.getString("no_recent_calls")
        )
        return
    }
    val groups = remember(entries) { CallLogRepository.group(entries) }
    LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
        items(groups) { group ->
            val first = group.first()
            CallRow(
                entry = first,
                count = group.size,
                onCall = { onCall(first.number) },
                onMessage = { onMessage(first.number) }
            )
        }
    }
}

@Composable
private fun CallRow(
    entry: CallEntry,
    count: Int,
    onCall: () -> Unit,
    onMessage: () -> Unit
) {
    val title = entry.name.ifBlank {
        NumberUtils.formatForDisplay(entry.number).ifBlank {
            LanguageManager.getString("unknown_number")
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onCall)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        InitialsAvatar(
            text = if (entry.name.isNotBlank()) {
                entry.name.take(1).uppercase()
            } else {
                "#"
            },
            color = if (entry.isMissed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (count > 1) "$title ($count)" else title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = if (entry.isMissed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = when {
                        entry.isMissed -> Icons.Default.CallMissed
                        entry.isIncoming -> Icons.Default.CallReceived
                        else -> Icons.Default.CallMade
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = DateUtils.getRelativeTimeSpanString(
                        entry.date,
                        System.currentTimeMillis(),
                        DateUtils.MINUTE_IN_MILLIS
                    ).toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (entry.type == CallLog.Calls.OUTGOING_TYPE && entry.durationSeconds > 0) {
                    Text(
                        text = " • " + formatDuration(entry.durationSeconds),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        IconButton(onClick = onMessage) {
            Icon(
                imageVector = Icons.Default.Message,
                contentDescription = LanguageManager.getString("send_message"),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
        IconButton(onClick = onCall) {
            Icon(
                imageVector = Icons.Default.Call,
                contentDescription = LanguageManager.getString("call"),
                tint = Color(0xFF2E7D32)
            )
        }
    }
}

@Composable
private fun ContactsTab(
    contacts: List<Contact>,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onCall: (String) -> Unit,
    onMessage: (String) -> Unit
) {
    if (!hasPermission) {
        EmptyState(
            icon = Icons.Default.Contacts,
            title = LanguageManager.getString("permission_contacts"),
            description = LanguageManager.getString("permission_contacts_desc"),
            actionLabel = LanguageManager.getString("grant_permission"),
            onAction = onRequestPermission
        )
        return
    }
    var query by remember { mutableStateOf("") }
    val filtered = remember(query, contacts) { ContactsRepository.search(contacts, query) }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            placeholder = { Text(LanguageManager.getString("search_contacts")) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )
        if (filtered.isEmpty()) {
            EmptyState(
                icon = Icons.Default.Person,
                title = LanguageManager.getString("no_contacts")
            )
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                items(filtered) { contact ->
                    ContactRow(
                        contact = contact,
                        onCall = { onCall(contact.number) },
                        onMessage = { onMessage(contact.number) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ContactRow(
    contact: Contact,
    onCall: () -> Unit,
    onMessage: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onCall)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        InitialsAvatar(text = contact.initials)
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
        IconButton(onClick = onMessage) {
            Icon(
                imageVector = Icons.Default.Message,
                contentDescription = LanguageManager.getString("send_message"),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
        IconButton(onClick = onCall) {
            Icon(
                imageVector = Icons.Default.Call,
                contentDescription = LanguageManager.getString("call"),
                tint = Color(0xFF2E7D32)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FavoritesTab(contacts: List<Contact>, onCall: (String) -> Unit) {
    if (contacts.isEmpty()) {
        EmptyState(
            icon = Icons.Default.Star,
            title = LanguageManager.getString("no_favorites")
        )
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(contacts.size) { index ->
            val contact = contacts[index]
            Surface(
                onClick = { onCall(contact.number) },
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    InitialsAvatar(text = contact.initials, size = 52.dp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = contact.name,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

private fun formatDuration(seconds: Long): String {
    val minutes = seconds / 60
    val rest = seconds % 60
    return if (minutes > 0) "${minutes}min ${rest}s" else "${rest}s"
}
