package com.example.ui.messages

import android.Manifest
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.telephony.ContactsRepository
import com.example.telephony.DefaultSmsManager
import com.example.telephony.NumberUtils
import com.example.telephony.SmsRepository
import com.example.telephony.SmsThread
import com.example.ui.components.EmptyState
import com.example.ui.components.InitialsAvatar
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
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MessagesScreen(
    app: AppViewModel,
    phone: PhoneViewModel,
    messages: MessagesViewModel,
    prefilledNumber: String = "",
    onCall: (String) -> Unit = {}
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
    val contacts by phone.contacts.collectAsState()
    val threads by messages.threads.collectAsState()
    val openThread by messages.openThread.collectAsState()

    LaunchedEffect(Unit) {
        if (!permissions.allPermissionsGranted) permissions.launchMultiplePermissionRequest()
        phone.refresh()
    }

    LaunchedEffect(contacts, permissions.allPermissionsGranted) {
        messages.refresh(contacts)
    }

    LaunchedEffect(prefilledNumber) {
        if (prefilledNumber.isNotBlank()) {
            val name = contacts.firstOrNull { NumberUtils.sameNumber(it.number, prefilledNumber) }?.name
            messages.openWithAddress(prefilledNumber, name.orEmpty())
        }
    }

    val thread = openThread
    if (thread != null) {
        ConversationScreen(
            app = app,
            messages = messages,
            thread = thread,
            onBack = { messages.close() },
            onCall = onCall
        )
        return
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
            LanguageManager.LanguageSelector()
        }

        DefaultSmsBanner()

        TabRow(selectedTabIndex = tab.ordinal) {
            MessagesTab.entries.forEach { entry ->
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
                    threads = threads,
                    hasPermission = SmsRepository.hasReadPermission(context),
                    onRequestPermission = { permissions.launchMultiplePermissionRequest() },
                    onOpen = { messages.open(it) }
                )

                MessagesTab.BULK -> BulkMessagesTab(app = app, phone = phone)

                MessagesTab.CONTACTS -> MessageContactsTab(
                    contacts = contacts,
                    onOpen = { contact ->
                        messages.openWithAddress(contact.number, contact.name)
                    }
                )
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

@Composable
private fun InboxTab(
    threads: List<SmsThread>,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onOpen: (SmsThread) -> Unit
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
    if (threads.isEmpty()) {
        EmptyState(
            icon = Icons.Default.Message,
            title = LanguageManager.getString("no_conversations")
        )
        return
    }
    LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
        items(threads, key = { it.threadId }) { thread ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(thread) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                InitialsAvatar(text = thread.initials)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = thread.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (thread.unreadCount > 0) FontWeight.ExtraBold else FontWeight.Bold,
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
    }
}

@Composable
private fun MessageContactsTab(
    contacts: List<com.example.telephony.Contact>,
    onOpen: (com.example.telephony.Contact) -> Unit
) {
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
            EmptyState(icon = Icons.Default.Contacts, title = LanguageManager.getString("no_contacts"))
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                items(filtered) { contact ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpen(contact) }
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
                        Icon(
                            imageVector = Icons.Default.Message,
                            contentDescription = LanguageManager.getString("send_message"),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}
