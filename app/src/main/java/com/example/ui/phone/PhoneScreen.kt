package com.example.ui.phone

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.CallLog
import android.text.format.DateUtils
import android.widget.Toast
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallMissed
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.BlockedNumbers
import com.example.telephony.CallEntry
import com.example.telephony.CallLogRepository
import com.example.telephony.Contact
import com.example.telephony.ContactIndex
import com.example.telephony.ContactsRepository
import com.example.telephony.DefaultDialerManager
import com.example.telephony.NumberUtils
import com.example.ui.components.EmptyState
import com.example.ui.components.ContactAvatar
import com.example.ui.components.InitialsAvatar
import com.example.ui.components.NotificationPermissionBanner
import com.example.ui.components.SimLauncher
import com.example.ui.components.SimSelector
import com.example.ui.theme.scanColors
import com.example.ui.util.LanguageManager
import com.example.viewmodel.AppViewModel
import com.example.viewmodel.PhoneViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState

private enum class PhoneTab(val labelKey: String) {
    RECENTS("tab_recents"),
    CONTACTS("tab_contacts"),
    FAVORITES("tab_favorites")
}

/**
 * The phone part of the app: the call history first, then contacts and
 * favourites, with the keypad one tap away on the floating button.
 *
 * Opening on the history is what a phone app is used for most of the time —
 * calling back someone who just called. Every call goes through [SimLauncher],
 * so the SIM card used is chosen the same way here as for a recharge.
 */
@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PhoneScreen(
    app: AppViewModel,
    phone: PhoneViewModel,
    launcher: SimLauncher,
    dialNumber: String = "",
    dialTicket: Int = 0,
    onMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val currentLang by LanguageManager.currentLang
    val permissions = rememberMultiplePermissionsState(
        buildList {
            add(Manifest.permission.READ_CONTACTS)
            // Asked together with the read: one dialog for the contacts, and
            // creating or editing one never stops on a refusal later.
            add(Manifest.permission.WRITE_CONTACTS)
            add(Manifest.permission.READ_CALL_LOG)
            // Asked with the read, and for the same reason as the contacts:
            // without it a line of the history cannot be removed, which is
            // exactly what the testers could not do.
            add(Manifest.permission.WRITE_CALL_LOG)
            add(Manifest.permission.READ_PHONE_STATE)
            add(Manifest.permission.CALL_PHONE)
            // Without it the SIM cards never report their own number, and naming
            // two cards of the same operator becomes guesswork. The permission
            // only exists from Android 8 on.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                add(Manifest.permission.READ_PHONE_NUMBERS)
            }
        }
    )

    var tab by remember { mutableStateOf(PhoneTab.RECENTS) }
    var input by remember { mutableStateOf("") }
    var showKeypad by remember { mutableStateOf(false) }

    // Editing a contact: null means nothing open, a Contact means it is being
    // changed, and the "new contact" case is carried by [creatingContact].
    var detailsContact by remember { mutableStateOf<Contact?>(null) }
    var editingContact by remember { mutableStateOf<Contact?>(null) }
    var creatingContact by remember { mutableStateOf(false) }
    var deletingContact by remember { mutableStateOf<Contact?>(null) }
    var confirmClearLog by remember { mutableStateOf(false) }

    // Number a new contact starts from, when it comes from a call nobody named.
    var newContactNumber by remember { mutableStateOf("") }

    // A `tel:` intent, or the phone button of another app, opens the keypad
    // ready to dial — even on the same number twice in a row.
    LaunchedEffect(dialTicket) {
        if (dialTicket > 0) {
            input = dialNumber
            showKeypad = true
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

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Without notifications, an incoming call the system refuses to show
            // full screen is simply lost.
            NotificationPermissionBanner()

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
                    PhoneTab.RECENTS -> RecentsTab(
                        entries = callLog,
                        hasPermission = CallLogRepository.hasPermission(context),
                        onRequestPermission = { permissions.launchMultiplePermissionRequest() },
                        onCall = { launcher.call(it) },
                        onMessage = onMessage,
                        onDelete = { group ->
                            if (CallLogRepository.hasWritePermission(context)) {
                                phone.deleteCalls(group.map { it.id })
                            } else {
                                permissions.launchMultiplePermissionRequest()
                            }
                        },
                        onClear = {
                            if (CallLogRepository.hasWritePermission(context)) {
                                confirmClearLog = true
                            } else {
                                permissions.launchMultiplePermissionRequest()
                            }
                        },
                        onBlock = { number ->
                            BlockedNumbers.block(context, number)
                            Toast.makeText(
                                context,
                                LanguageManager.getString("number_blocked"),
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        onAddContact = { number ->
                            if (ContactsRepository.hasWritePermission(context)) {
                                newContactNumber = number
                                creatingContact = true
                            } else {
                                permissions.launchMultiplePermissionRequest()
                            }
                        }
                    )

                    PhoneTab.CONTACTS -> ContactsTab(
                        contacts = contacts,
                        hasPermission = ContactsRepository.hasPermission(context),
                        onRequestPermission = { permissions.launchMultiplePermissionRequest() },
                        onCall = { launcher.call(it) },
                        onMessage = onMessage,
                        onOpen = { contact -> detailsContact = contact },
                        onCreate = {
                            if (ContactsRepository.hasWritePermission(context)) {
                                creatingContact = true
                            } else {
                                permissions.launchMultiplePermissionRequest()
                            }
                        },
                        onEdit = { contact ->
                            if (ContactsRepository.hasWritePermission(context)) {
                                editingContact = contact
                            } else {
                                permissions.launchMultiplePermissionRequest()
                            }
                        },
                        onDelete = { contact -> deletingContact = contact }
                    )

                    PhoneTab.FAVORITES -> FavoritesTab(
                        contacts = contacts.filter { it.isFavorite },
                        onCall = { launcher.call(it) }
                    )
                }
            }
        }

        FloatingActionButton(
            onClick = { showKeypad = true },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Dialpad,
                contentDescription = LanguageManager.getString("open_keypad")
            )
        }
    }

    detailsContact?.let { contact ->
        ContactDetailsDialog(
            contact = contact,
            onDismiss = { detailsContact = null },
            onCall = {
                detailsContact = null
                launcher.call(contact.number)
            },
            onMessage = {
                detailsContact = null
                onMessage(contact.number)
            },
            onEdit = {
                detailsContact = null
                if (ContactsRepository.hasWritePermission(context)) {
                    editingContact = contact
                } else {
                    permissions.launchMultiplePermissionRequest()
                }
            },
            onDelete = {
                detailsContact = null
                deletingContact = contact
            },
            onToggleFavorite = { favorite ->
                if (ContactsRepository.hasWritePermission(context)) {
                    phone.setFavorite(contact, favorite)
                } else {
                    permissions.launchMultiplePermissionRequest()
                }
            }
        )
    }

    if (confirmClearLog) {
        AlertDialog(
            onDismissRequest = { confirmClearLog = false },
            title = {
                Text(LanguageManager.getString("clear_history"), fontWeight = FontWeight.Bold)
            },
            text = { Text(LanguageManager.getString("clear_history_confirm")) },
            confirmButton = {
                Button(
                    onClick = {
                        confirmClearLog = false
                        phone.clearCallLog()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) { Text(LanguageManager.getString("delete")) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearLog = false }) {
                    Text(LanguageManager.getString("cancel"))
                }
            }
        )
    }

    val editorContact = editingContact
    if (creatingContact || editorContact != null) {
        ContactEditorSheet(
            contact = editorContact,
            initialNumber = newContactNumber,
            onDismiss = {
                creatingContact = false
                editingContact = null
                newContactNumber = ""
            },
            onSaved = {
                creatingContact = false
                editingContact = null
                newContactNumber = ""
                phone.refresh()
            }
        )
    }

    deletingContact?.let { contact ->
        DeleteContactDialog(
            contact = contact,
            onDismiss = { deletingContact = null },
            onDeleted = {
                deletingContact = null
                phone.refresh()
            }
        )
    }

    if (showKeypad) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showKeypad = false },
            sheetState = sheetState
        ) {
            KeypadSheet(
                app = app,
                input = input,
                contacts = contacts,
                onInputChange = { input = it },
                onCall = {
                    showKeypad = false
                    launcher.call(it)
                },
                onMessage = {
                    showKeypad = false
                    onMessage(it)
                },
                onRequestSimPermission = { permissions.launchMultiplePermissionRequest() }
            )
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

/**
 * The keypad, opened from the floating button.
 *
 * A number can be pasted into it — the one a friend sent by message, or copied
 * from anywhere else — and the number being dialled can be copied back out with
 * a long press.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun KeypadSheet(
    app: AppViewModel,
    input: String,
    contacts: List<Contact>,
    onInputChange: (String) -> Unit,
    onCall: (String) -> Unit,
    onMessage: (String) -> Unit,
    onRequestSimPermission: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val sims by app.sims.collectAsState()
    val routing = remember(input, sims) { app.routeForNumber(input) }
    val index = remember(contacts) { ContactIndex(contacts) }
    val suggestions = remember(input, index) {
        if (input.length < 2) emptyList() else index.search(input).take(12)
    }

    fun toast(key: String) {
        Toast.makeText(context, LanguageManager.getString(key), Toast.LENGTH_SHORT).show()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
    ) {
        if (suggestions.isNotEmpty()) {
            LazyColumn(modifier = Modifier.heightIn(max = 220.dp)) {
                items(suggestions, key = { it.id.toString() + it.number }) { contact ->
                    ContactRow(
                        contact = contact,
                        onCall = { onCall(contact.number) },
                        onMessage = { onMessage(contact.number) }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        Text(
            text = if (input.isEmpty()) "" else NumberUtils.formatForDisplay(input),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    enabled = input.isNotEmpty(),
                    onClick = {},
                    onLongClick = {
                        clipboard.setText(AnnotatedString(input))
                        toast("number_copied")
                    }
                )
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

        // Copy and paste, so a number can travel between apps without retyping.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = {
                    val pasted = NumberUtils.sanitize(clipboard.getText()?.text.orEmpty())
                    if (pasted.isBlank()) toast("nothing_to_paste") else onInputChange(pasted)
                }
            ) {
                Icon(
                    imageVector = Icons.Default.ContentPaste,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(LanguageManager.getString("paste"), style = MaterialTheme.typography.labelMedium)
            }
            if (input.isNotEmpty()) {
                TextButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(input))
                        toast("number_copied")
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = LanguageManager.getString("copy"),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                TextButton(onClick = { onMessage(input) }) {
                    Icon(
                        imageVector = Icons.Default.Message,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = LanguageManager.getString("send_message"),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }

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
                            onLongClick = { if (digit == "0") onInputChange(input + "+") }
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
            SimSelector(
                routing = routing,
                sims = sims,
                onPick = { sim -> app.setPreferredSim(routing.operator, sim) },
                onRequestPermission = onRequestSimPermission,
                modifier = Modifier.size(56.dp)
            )
            FilledIconButton(
                onClick = { if (input.isNotBlank()) onCall(input) },
                enabled = input.isNotBlank(),
                modifier = Modifier.size(64.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.scanColors.success,
                    contentColor = MaterialTheme.scanColors.onSuccess
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
    onMessage: (String) -> Unit,
    /** Removes one line — every call of the group shown on that line. */
    onDelete: (List<CallEntry>) -> Unit,
    onClear: () -> Unit,
    onBlock: (String) -> Unit,
    onAddContact: (String) -> Unit
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
        // Emptying the history belongs where the history is, not in the settings.
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onClear) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = LanguageManager.getString("clear_history"),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
        items(groups) { group ->
            val first = group.first()
            CallRow(
                entry = first,
                count = group.size,
                onCall = { onCall(first.number) },
                onMessage = { onMessage(first.number) },
                onDelete = { onDelete(group) },
                onBlock = { onBlock(first.number) },
                onAddContact = { onAddContact(first.number) }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CallRow(
    entry: CallEntry,
    count: Int,
    onCall: () -> Unit,
    onMessage: () -> Unit,
    onDelete: () -> Unit,
    onBlock: () -> Unit,
    onAddContact: () -> Unit
) {
    val title = entry.name.ifBlank {
        NumberUtils.formatForDisplay(entry.number).ifBlank {
            LanguageManager.getString("unknown_number")
        }
    }
    val copy = rememberNumberCopier(entry.number)
    // A long press on a line is where what can be done with it lives: copying
    // the number, blocking it, and removing the line from the history.
    var menuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onCall, onLongClick = { menuOpen = true })
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        InitialsAvatar(
            text = if (entry.name.isNotBlank()) entry.name.take(1).uppercase() else "#",
            color = if (entry.isMissed) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.primary
            }
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (count > 1) "$title ($count)" else title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = if (entry.isMissed) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
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
                tint = MaterialTheme.scanColors.success
            )
        }

        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(LanguageManager.getString("copy")) },
                leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                onClick = {
                    menuOpen = false
                    copy()
                }
            )
            DropdownMenuItem(
                text = { Text(LanguageManager.getString("send_message")) },
                leadingIcon = { Icon(Icons.Default.Message, contentDescription = null) },
                onClick = {
                    menuOpen = false
                    onMessage()
                }
            )
            // A number with no name is almost always someone to keep.
            if (entry.name.isBlank()) {
                DropdownMenuItem(
                    text = { Text(LanguageManager.getString("new_contact")) },
                    leadingIcon = { Icon(Icons.Default.PersonAdd, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        onAddContact()
                    }
                )
            }
            DropdownMenuItem(
                text = { Text(LanguageManager.getString("block_number")) },
                leadingIcon = { Icon(Icons.Default.Block, contentDescription = null) },
                onClick = {
                    menuOpen = false
                    onBlock()
                }
            )
            DropdownMenuItem(
                text = {
                    Text(
                        text = LanguageManager.getString("delete_call"),
                        color = MaterialTheme.colorScheme.error
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                },
                onClick = {
                    menuOpen = false
                    onDelete()
                }
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
    onMessage: (String) -> Unit,
    onOpen: (Contact) -> Unit,
    onCreate: () -> Unit,
    onEdit: (Contact) -> Unit,
    onDelete: (Contact) -> Unit
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
    val index = remember(contacts) { ContactIndex(contacts) }
    val filtered = remember(query, index) { index.search(query) }

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
        LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
            // At the top of the list, where a contact is looked for and not found.
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onCreate)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PersonAdd,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = LanguageManager.getString("new_contact"),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (filtered.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Default.Person,
                        title = LanguageManager.getString("no_contacts")
                    )
                }
            }

            items(filtered, key = { it.id.toString() + it.number }) { contact ->
                ContactRow(
                    contact = contact,
                    onCall = { onCall(contact.number) },
                    onMessage = { onMessage(contact.number) },
                    onOpen = { onOpen(contact) },
                    onEdit = { onEdit(contact) },
                    onDelete = { onDelete(contact) }
                )
            }
        }
    }
}

/** Copies a number to the clipboard, so it can be pasted anywhere else. */
@Composable
private fun rememberNumberCopier(number: String): () -> Unit {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    return {
        clipboard.setText(AnnotatedString(NumberUtils.toNational(number)))
        Toast.makeText(
            context,
            LanguageManager.getString("number_copied"),
            Toast.LENGTH_SHORT
        ).show()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContactRow(
    contact: Contact,
    onCall: () -> Unit,
    onMessage: () -> Unit,
    /** Opens the card of the contact. Without it, a tap places the call. */
    onOpen: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null
) {
    val copy = rememberNumberCopier(contact.number)
    // A long press opens what can be done with a contact. Where the row is only
    // a suggestion — under the keypad — it stays a shortcut to copy the number.
    val hasMenu = onEdit != null || onDelete != null
    var menuOpen by remember { mutableStateOf(false) }

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onOpen ?: onCall,
                    onLongClick = { if (hasMenu) menuOpen = true else copy() }
                )
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ContactAvatar(contact = contact)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = contact.name.ifBlank {
                        NumberUtils.formatForDisplay(contact.number)
                    },
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
                    tint = MaterialTheme.scanColors.success
                )
            }
            if (hasMenu) {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = LanguageManager.getString("details"),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(LanguageManager.getString("copy")) },
                leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                onClick = {
                    menuOpen = false
                    copy()
                }
            )
            onEdit?.let { edit ->
                DropdownMenuItem(
                    text = { Text(LanguageManager.getString("edit_contact")) },
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        edit()
                    }
                )
            }
            onDelete?.let { delete ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = LanguageManager.getString("delete_contact"),
                            color = MaterialTheme.colorScheme.error
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                    },
                    onClick = {
                        menuOpen = false
                        delete()
                    }
                )
            }
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
                    ContactAvatar(contact = contact, size = 52.dp)
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
