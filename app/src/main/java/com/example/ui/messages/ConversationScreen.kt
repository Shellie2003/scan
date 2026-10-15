package com.example.ui.messages

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.AppSettings
import com.example.data.BlockedNumbers
import com.example.data.ConversationLock
import com.example.telephony.NumberUtils
import com.example.telephony.SmsMessageItem
import com.example.telephony.SmsSender
import com.example.telephony.SmsThread
import com.example.ui.components.InitialsAvatar
import com.example.ui.components.SimSelector
import com.example.ui.theme.scanColors
import com.example.ui.util.LanguageManager
import com.example.viewmodel.AppViewModel
import com.example.viewmodel.MessagesViewModel

/**
 * One conversation: the messages exchanged with a number, and the field to answer.
 *
 * A long press turns the list into a selection — several messages can then be
 * copied or deleted at once. The SIM used for the answer follows the rule of the
 * rest of the app and is changed from the card icon next to the field.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun ConversationScreen(
    app: AppViewModel,
    messages: MessagesViewModel,
    thread: SmsThread,
    onBack: () -> Unit,
    onCall: (String) -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val items by messages.messages.collectAsState()
    val sims by app.sims.collectAsState()
    val listState = rememberLazyListState()

    var draft by remember(thread.threadId) { mutableStateOf("") }
    var forcedSubscription by remember { mutableStateOf(AppSettings.NO_SUBSCRIPTION) }
    var confirmDeleteThread by remember { mutableStateOf(false) }
    var selected by remember(thread.threadId) { mutableStateOf<Set<Long>>(emptySet()) }
    var confirmDeleteSelection by remember { mutableStateOf(false) }

    // What this discussion is: hidden behind a code, blocked, or neither.
    var menuOpen by remember(thread.address) { mutableStateOf(false) }
    var locked by remember(thread.address) {
        mutableStateOf(ConversationLock.isLocked(context, thread.address))
    }
    var blocked by remember(thread.address) {
        mutableStateOf(BlockedNumbers.isBlocked(context, thread.address))
    }
    var setupPin by remember { mutableStateOf(false) }

    // A locked discussion is not merely hidden from the inbox: whichever way it
    // was reached — a contact, the call history, an `smsto:` link — the code is
    // asked before a single message is drawn.
    var unlocked by remember(thread.address) {
        mutableStateOf(
            !ConversationLock.isLocked(context, thread.address) ||
                ConversationLock.hasRecentAccess()
        )
    }

    fun toast(key: String) {
        android.widget.Toast.makeText(
            context,
            LanguageManager.getString(key),
            android.widget.Toast.LENGTH_SHORT
        ).show()
    }

    val selecting = selected.isNotEmpty()
    val routing = remember(thread.address, sims, forcedSubscription) {
        app.routeForNumber(thread.address, forcedSubscription)
    }

    fun toggle(id: Long) {
        selected = if (id in selected) selected - id else selected + id
    }

    LaunchedEffect(items.size) {
        if (items.isNotEmpty()) listState.animateScrollToItem(items.lastIndex)
    }

    // The keyboard shortens the list: the last message has to stay in sight.
    val imeVisible = WindowInsets.isImeVisible
    LaunchedEffect(imeVisible) {
        if (imeVisible && items.isNotEmpty()) listState.animateScrollToItem(items.lastIndex)
    }

    // Back leaves the selection first, and the conversation only after.
    BackHandler(enabled = true) {
        if (selecting) selected = emptySet() else onBack()
    }

    if (!unlocked) {
        LockedConversationGate(onBack = onBack, onUnlocked = { unlocked = true })
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding()
    ) {
        if (selecting) {
            SelectionBar(
                count = selected.size,
                onClose = { selected = emptySet() },
                onSelectAll = { selected = items.map { it.id }.toSet() },
                onCopy = {
                    val text = items.filter { it.id in selected }.joinToString("\n") { it.body }
                    clipboard.setText(AnnotatedString(text))
                    selected = emptySet()
                    android.widget.Toast.makeText(
                        context,
                        LanguageManager.getString("message_copied"),
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                },
                onDelete = { confirmDeleteSelection = true }
            )
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = LanguageManager.getString("back")
                    )
                }
                InitialsAvatar(text = thread.initials, size = 38.dp)
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = thread.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Text(
                        text = NumberUtils.formatForDisplay(thread.address),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { onCall(thread.address) }) {
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = LanguageManager.getString("call"),
                        tint = MaterialTheme.scanColors.success
                    )
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = LanguageManager.getString("details")
                        )
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    LanguageManager.getString(
                                        if (locked) "unlock_conversation" else "lock_conversation"
                                    )
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (locked) {
                                        Icons.Default.LockOpen
                                    } else {
                                        Icons.Default.Lock
                                    },
                                    contentDescription = null
                                )
                            },
                            onClick = {
                                menuOpen = false
                                when {
                                    locked -> {
                                        ConversationLock.unlock(context, thread.address)
                                        locked = false
                                    }
                                    // The code comes first: there is nothing to
                                    // lock a discussion with before it exists.
                                    !ConversationLock.hasPin(context) -> setupPin = true

                                    else -> {
                                        ConversationLock.lock(context, thread.address)
                                        locked = true
                                        toast("conversation_locked")
                                    }
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    LanguageManager.getString(
                                        if (blocked) "unblock_number" else "block_number"
                                    )
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (blocked) {
                                        Icons.Default.Person
                                    } else {
                                        Icons.Default.Block
                                    },
                                    contentDescription = null
                                )
                            },
                            onClick = {
                                menuOpen = false
                                if (blocked) {
                                    BlockedNumbers.unblock(context, thread.address)
                                    blocked = false
                                    toast("number_unblocked")
                                } else {
                                    BlockedNumbers.block(context, thread.address)
                                    blocked = true
                                    toast("number_blocked")
                                }
                            }
                        )
                        if (thread.threadId >= 0) {
                            // Deleting lives in the menu rather than on the bar:
                            // a header of five buttons has no room for a name.
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = LanguageManager.getString("delete_conversation"),
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
                                    confirmDeleteThread = true
                                }
                            )
                        }
                    }
                }
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(items, key = { it.id }) { message ->
                MessageBubble(
                    message = message,
                    selected = message.id in selected,
                    onClick = { if (selecting) toggle(message.id) },
                    onLongClick = { toggle(message.id) }
                )
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                SimSelector(
                    routing = routing,
                    sims = sims,
                    onPick = { sim -> forcedSubscription = sim.subscriptionId },
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    placeholder = { Text(LanguageManager.getString("write_message")) },
                    maxLines = 4,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                FilledIconButton(
                    onClick = {
                        val body = draft
                        draft = ""
                        messages.send(
                            address = thread.address,
                            body = body,
                            subscriptionId = routing.sim?.subscriptionId ?: AppSettings.NO_SUBSCRIPTION
                        ) { sent ->
                            if (!sent) draft = body
                        }
                    },
                    enabled = draft.isNotBlank() && SmsSender.hasPermission(context),
                    modifier = Modifier.size(52.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = LanguageManager.getString("send")
                    )
                }
            }
        }
    }

    if (confirmDeleteThread) {
        AlertDialog(
            onDismissRequest = { confirmDeleteThread = false },
            title = {
                Text(LanguageManager.getString("delete_conversation"), fontWeight = FontWeight.Bold)
            },
            text = { Text(thread.title) },
            confirmButton = {
                Button(
                    onClick = {
                        confirmDeleteThread = false
                        messages.deleteThread(thread)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) { Text(LanguageManager.getString("delete")) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteThread = false }) {
                    Text(LanguageManager.getString("cancel"))
                }
            }
        )
    }

    if (setupPin) {
        PinSetupDialog(
            onDismiss = { setupPin = false },
            onDone = {
                setupPin = false
                ConversationLock.lock(context, thread.address)
                locked = true
                toast("conversation_locked")
            }
        )
    }

    if (confirmDeleteSelection) {
        AlertDialog(
            onDismissRequest = { confirmDeleteSelection = false },
            title = { Text(LanguageManager.getString("delete"), fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    LanguageManager.getString(
                        "delete_selected_messages",
                        "count" to selected.size
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirmDeleteSelection = false
                        messages.deleteMessages(selected)
                        selected = emptySet()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) { Text(LanguageManager.getString("delete")) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteSelection = false }) {
                    Text(LanguageManager.getString("cancel"))
                }
            }
        )
    }
}

/** Header shown while messages are selected. */
@Composable
private fun SelectionBar(
    count: Int,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    onCopy: () -> Unit,
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
            IconButton(onClick = onSelectAll) {
                Icon(
                    imageVector = Icons.Default.DoneAll,
                    contentDescription = LanguageManager.getString("select_all"),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            IconButton(onClick = onCopy) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = LanguageManager.getString("copy"),
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: SmsMessageItem,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val incoming = message.isIncoming
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                } else {
                    androidx.compose.ui.graphics.Color.Transparent
                }
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 2.dp),
        contentAlignment = if (incoming) Alignment.CenterStart else Alignment.CenterEnd
    ) {
        val shape = RoundedCornerShape(
            topStart = 16.dp,
            topEnd = 16.dp,
            bottomStart = if (incoming) 4.dp else 16.dp,
            bottomEnd = if (incoming) 16.dp else 4.dp
        )
        Surface(
            shape = shape,
            color = if (incoming) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.primaryContainer
            },
            modifier = Modifier
                .widthIn(max = 300.dp)
                .then(
                    if (selected) {
                        Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape)
                    } else {
                        Modifier
                    }
                )
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(
                    text = message.body,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = android.text.format.DateUtils.getRelativeTimeSpanString(
                        message.date,
                        System.currentTimeMillis(),
                        android.text.format.DateUtils.MINUTE_IN_MILLIS
                    ).toString() + if (message.isFailed) " • !" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (message.isFailed) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}
