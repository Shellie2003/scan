package com.example.ui.messages

import android.Manifest
import android.content.Intent
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ConversationLock
import com.example.telephony.Contact
import com.example.telephony.ContactIndex
import com.example.telephony.DefaultSmsManager
import com.example.telephony.NumberUtils
import com.example.telephony.SmsRepository
import com.example.telephony.SmsThread
import com.example.ui.components.EmptyState
import com.example.ui.components.ContactAvatar
import com.example.ui.components.InitialsAvatar
import com.example.ui.components.NotificationPermissionBanner
import com.example.ui.util.LanguageManager
import com.example.viewmodel.AppViewModel
import com.example.viewmodel.MessagesViewModel
import com.example.viewmodel.PhoneViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState

private enum class MessagesTab(val labelKey: String) {
    INBOX("tab_inbox"),
    BULK("tab_bulk"),
    CONTACTS("tab_contacts")
}

/**
 * The messaging part of the app, in one place: the inbox and its conversations,
 * the bulk sending, and the contacts to start from.
 *
 * A conversation is a destination of its own — opening one is asked of the
 * navigation, which is what lets a notification land straight inside it.
 */
@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MessagesScreen(
    app: AppViewModel,
    phone: PhoneViewModel,
    messages: MessagesViewModel,
    onOpenConversation: (address: String, name: String) -> Unit,
    onOpenThread: (SmsThread) -> Unit
) {
    val context = LocalContext.current
    val currentLang by LanguageManager.currentLang
    val permissions = rememberMultiplePermissionsState(
        listOf(
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_CONTACTS
        )
    )

    var tab by remember { mutableStateOf(MessagesTab.INBOX) }
    var selectedThreads by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var showNewMessage by remember { mutableStateOf(false) }
    val contacts by phone.contacts.collectAsState()
    val threads by messages.threads.collectAsState()

    // The lock on discussions: which ones are hidden, whether a code exists, and
    // whether the hidden ones are being shown right now.
    var lockedKeys by remember { mutableStateOf(ConversationLock.lockedKeys(context)) }
    var hasPin by remember { mutableStateOf(ConversationLock.hasPin(context)) }
    var secureOpen by remember { mutableStateOf(false) }
    var askPin by remember { mutableStateOf(false) }
    var setupPin by remember { mutableStateOf(false) }
    var pendingLock by remember { mutableStateOf<Set<Long>>(emptySet()) }

    val openThreads = remember(threads, lockedKeys) {
        threads.filter { !lockedKeys.contains(ConversationLock.keyOf(it.address)) }
    }
    val secretThreads = remember(threads, lockedKeys) {
        threads.filter { lockedKeys.contains(ConversationLock.keyOf(it.address)) }
    }

    fun lock(ids: Set<Long>) {
        threads.filter { it.threadId in ids }
            .forEach { ConversationLock.lock(context, it.address) }
        lockedKeys = ConversationLock.lockedKeys(context)
        selectedThreads = emptySet()
    }

    LaunchedEffect(Unit) {
        if (!permissions.allPermissionsGranted) permissions.launchMultiplePermissionRequest()
        phone.refresh()
    }

    LaunchedEffect(contacts, permissions.allPermissionsGranted) {
        messages.refresh(contacts)
    }

    // Leaving a selection — or the locked discussions — must not leave the screen.
    BackHandler(enabled = selectedThreads.isNotEmpty() || secureOpen) {
        if (selectedThreads.isNotEmpty()) selectedThreads = emptySet() else secureOpen = false
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (selectedThreads.isNotEmpty()) {
                ThreadSelectionBar(
                    count = selectedThreads.size,
                    locked = secureOpen,
                    onClose = { selectedThreads = emptySet() },
                    onLock = {
                        if (secureOpen) {
                            threads.filter { it.threadId in selectedThreads }
                                .forEach { ConversationLock.unlock(context, it.address) }
                            lockedKeys = ConversationLock.lockedKeys(context)
                            selectedThreads = emptySet()
                        } else if (hasPin) {
                            lock(selectedThreads)
                        } else {
                            // No code yet: it is created first, and the chosen
                            // discussions are locked as soon as it exists.
                            pendingLock = selectedThreads
                            setupPin = true
                        }
                    },
                    onDelete = {
                        messages.deleteThreads(threads.filter { it.threadId in selectedThreads })
                        selectedThreads = emptySet()
                    }
                )
            } else {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Text(
                        text = LanguageManager.getString("nav_messages"),
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
            }

            NotificationPermissionBanner()

            DefaultSmsBanner()

            TabRow(selectedTabIndex = tab.ordinal) {
                MessagesTab.entries.forEach { entry ->
                    Tab(
                        selected = tab == entry,
                        onClick = {
                            tab = entry
                            selectedThreads = emptySet()
                            // Leaving the inbox closes the locked discussions: a
                            // code is asked again on the way back in.
                            secureOpen = false
                        },
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
                                    MessagesTab.INBOX -> Icons.Default.Inbox
                                    MessagesTab.BULK -> Icons.Default.Group
                                    MessagesTab.CONTACTS -> Icons.Default.Contacts
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
                    MessagesTab.INBOX -> InboxTab(
                        threads = if (secureOpen) secretThreads else openThreads,
                        selected = selectedThreads,
                        hasPermission = SmsRepository.hasReadPermission(context),
                        onRequestPermission = { permissions.launchMultiplePermissionRequest() },
                        onOpen = { thread ->
                            if (selectedThreads.isEmpty()) {
                                onOpenThread(thread)
                            } else {
                                selectedThreads = selectedThreads.toggle(thread.threadId)
                            }
                        },
                        onToggleSelection = { thread ->
                            selectedThreads = selectedThreads.toggle(thread.threadId)
                        },
                        // The way in and out of the locked discussions, at the top
                        // of the list where it is seen without being in the way.
                        secureCount = secretThreads.size,
                        secureOpen = secureOpen,
                        onOpenSecure = { if (hasPin) askPin = true else setupPin = true },
                        onCloseSecure = { secureOpen = false }
                    )

                    MessagesTab.BULK -> BulkMessagesTab(
                        app = app,
                        phone = phone,
                        messages = messages
                    )

                    MessagesTab.CONTACTS -> MessageContactsTab(
                        contacts = contacts,
                        onOpen = { contact -> onOpenConversation(contact.number, contact.name) }
                    )
                }
            }
        }

        // Starting a new message is the first thing this screen is asked for.
        if (tab != MessagesTab.BULK && selectedThreads.isEmpty()) {
            ExtendedFloatingActionButton(
                onClick = { showNewMessage = true },
                icon = { Icon(Icons.Default.Message, contentDescription = null) },
                text = { Text(LanguageManager.getString("new_message")) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            )
        }
    }

    if (showNewMessage) {
        NewMessageSheet(
            contacts = contacts,
            onDismiss = { showNewMessage = false },
            onPick = { address, name ->
                showNewMessage = false
                onOpenConversation(address, name)
            }
        )
    }

    if (askPin) {
        PinPromptDialog(
            title = LanguageManager.getString("secure_conversations"),
            message = LanguageManager.getString("secure_prompt_desc"),
            onDismiss = { askPin = false },
            onAccepted = {
                askPin = false
                secureOpen = true
            }
        )
    }

    if (setupPin) {
        PinSetupDialog(
            onDismiss = {
                setupPin = false
                pendingLock = emptySet()
            },
            onDone = {
                setupPin = false
                hasPin = true
                if (pendingLock.isNotEmpty()) {
                    lock(pendingLock)
                    pendingLock = emptySet()
                }
            }
        )
    }
}

private fun Set<Long>.toggle(id: Long): Set<Long> = if (id in this) this - id else this + id

/** Header shown while conversations are selected. */
@Composable
private fun ThreadSelectionBar(
    count: Int,
    /** `true` when the selection is made among discussions already locked. */
    locked: Boolean,
    onClose: () -> Unit,
    onLock: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer, tonalElevation = 3.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = LanguageManager.getString("close"),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Text(
                text = LanguageManager.getString("selected_count", "count" to count),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onLock) {
                Icon(
                    imageVector = if (locked) Icons.Default.LockOpen else Icons.Default.Lock,
                    contentDescription = LanguageManager.getString(
                        if (locked) "unlock_conversation" else "lock_conversation"
                    ),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = LanguageManager.getString("delete"),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

/** Picking who a new message goes to: a contact, or a number typed or pasted. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewMessageSheet(
    contacts: List<Contact>,
    onDismiss: () -> Unit,
    onPick: (address: String, name: String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    val index = remember(contacts) { ContactIndex(contacts) }
    val filtered = remember(query, index) { index.search(query) }
    val typedNumber = remember(query) { NumberUtils.sanitize(query) }
    val canUseTyped = typedNumber.length >= 5 &&
        filtered.none { NumberUtils.sameNumber(it.number, typedNumber) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
        ) {
            Text(
                text = LanguageManager.getString("new_message"),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            Text(
                text = LanguageManager.getString("new_conversation_hint"),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )
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

            if (canUseTyped) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(typedNumber, "") }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    InitialsAvatar(text = "#")
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = NumberUtils.formatForDisplay(typedNumber),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = LanguageManager.getString("write_message"),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.Message,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (filtered.isEmpty() && !canUseTyped) {
                EmptyState(
                    icon = Icons.Default.Contacts,
                    title = LanguageManager.getString("no_contacts")
                )
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 420.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(filtered, key = { it.id.toString() + it.number }) { contact ->
                        ContactLine(
                            contact = contact,
                            onClick = { onPick(contact.number, contact.name) }
                        )
                    }
                }
            }
        }
    }
}

/** Invitation to become the default SMS app, which is what completes the inbox. */
@Composable
private fun DefaultSmsBanner() {
    val context = LocalContext.current
    var isDefault by remember { mutableStateOf(DefaultSmsManager.isDefaultSmsApp(context)) }
    var dismissed by remember { mutableStateOf(false) }
    val roleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        isDefault = DefaultSmsManager.isDefaultSmsApp(context)
    }

    if (isDefault || dismissed) return

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = LanguageManager.getString("default_sms_title"),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = LanguageManager.getString("default_sms_desc"),
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
                        val intent: Intent? = DefaultSmsManager.requestIntent(context)
                        if (intent != null) roleLauncher.launch(intent)
                    }
                ) {
                    Text(
                        text = LanguageManager.getString("default_sms_cta"),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun InboxTab(
    threads: List<SmsThread>,
    selected: Set<Long>,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onOpen: (SmsThread) -> Unit,
    onToggleSelection: (SmsThread) -> Unit,
    secureCount: Int = 0,
    secureOpen: Boolean = false,
    onOpenSecure: () -> Unit = {},
    onCloseSecure: () -> Unit = {}
) {
    if (!hasPermission) {
        EmptyState(
            icon = Icons.Default.Inbox,
            title = LanguageManager.getString("permission_sms_read"),
            description = LanguageManager.getString("permission_sms_read_desc"),
            actionLabel = LanguageManager.getString("grant_permission"),
            onAction = onRequestPermission
        )
        return
    }
    Column(modifier = Modifier.fillMaxSize()) {
        if (secureOpen) {
            SecureHeader(count = threads.size, onBack = onCloseSecure)
        }

        if (threads.isEmpty()) {
            if (!secureOpen && secureCount > 0) {
                SecureEntryRow(count = secureCount, onClick = onOpenSecure)
            }
            EmptyState(
                icon = if (secureOpen) Icons.Default.Lock else Icons.Default.Message,
                title = LanguageManager.getString(
                    if (secureOpen) "no_locked_conversations" else "no_conversations"
                ),
                description = LanguageManager.getString("long_press_hint")
            )
            return@Column
        }

        LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
            if (!secureOpen && secureCount > 0) {
                item {
                    SecureEntryRow(count = secureCount, onClick = onOpenSecure)
                }
            }
            items(threads, key = { it.threadId }) { thread ->
                ThreadRow(
                    thread = thread,
                    isSelected = thread.threadId in selected,
                    onClick = { onOpen(thread) },
                    onLongClick = { onToggleSelection(thread) }
                )
            }
        }
    }
}

/** Title of the locked discussions, with the way back to the inbox. */
@Composable
private fun SecureHeader(count: Int, onBack: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = LanguageManager.getString("back")
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = LanguageManager.getString("secure_conversations"),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = LanguageManager.getString("secure_count", "count" to count),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** The locked discussions, behind their code, from the top of the inbox. */
@Composable
private fun SecureEntryRow(count: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = LanguageManager.getString("secure_conversations"),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = LanguageManager.getString("secure_count", "count" to count),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** One conversation of the inbox. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ThreadRow(
    thread: SmsThread,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isSelected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                } else {
                    MaterialTheme.colorScheme.background
                }
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        InitialsAvatar(text = thread.initials)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = thread.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (thread.unreadCount > 0) {
                    FontWeight.ExtraBold
                } else {
                    FontWeight.Bold
                },
                maxLines = 1
            )
            Text(
                text = thread.snippet,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = android.text.format.DateUtils.getRelativeTimeSpanString(
                    thread.date,
                    System.currentTimeMillis(),
                    android.text.format.DateUtils.MINUTE_IN_MILLIS
                ).toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (thread.unreadCount > 0) {
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = thread.unreadCount.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageContactsTab(
    contacts: List<Contact>,
    onOpen: (Contact) -> Unit
) {
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
        if (filtered.isEmpty()) {
            EmptyState(icon = Icons.Default.Contacts, title = LanguageManager.getString("no_contacts"))
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                items(filtered) { contact ->
                    ContactLine(contact = contact, onClick = { onOpen(contact) })
                }
            }
        }
    }
}

/** One contact, ready to be written to. */
@Composable
private fun ContactLine(contact: Contact, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ContactAvatar(contact = contact)
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
        Icon(
            imageVector = Icons.Default.Message,
            contentDescription = LanguageManager.getString("send_message"),
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
    }
}
