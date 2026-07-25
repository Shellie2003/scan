package com.example.ui.messages

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.AppSettings
import com.example.telephony.NumberUtils
import com.example.telephony.SmsMessageItem
import com.example.telephony.SmsSender
import com.example.telephony.SmsThread
import com.example.ui.components.InitialsAvatar
import com.example.ui.components.SimIndicator
import com.example.ui.components.SimPickerDialog
import com.example.ui.util.LanguageManager
import com.example.viewmodel.AppViewModel
import com.example.viewmodel.MessagesViewModel

/**
 * One conversation: the messages exchanged with a number, and the field to answer.
 *
 * The SIM used for the answer follows the same rule as the rest of the app — the
 * card of the operator of the destination number when the phone holds one — and
 * stays visible and changeable above the input field.
 */
@Composable
fun ConversationScreen(
    app: AppViewModel,
    messages: MessagesViewModel,
    thread: SmsThread,
    onBack: () -> Unit,
    onCall: (String) -> Unit
) {
    val context = LocalContext.current
    val items by messages.messages.collectAsState()
    val sims by app.sims.collectAsState()
    val listState = rememberLazyListState()

    var draft by remember(thread.threadId) { mutableStateOf("") }
    var forcedSubscription by remember { mutableStateOf(AppSettings.NO_SUBSCRIPTION) }
    var showSimPicker by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val routing = remember(thread.address, sims, forcedSubscription) {
        app.routeForNumber(thread.address, forcedSubscription)
    }

    LaunchedEffect(items.size) {
        if (items.isNotEmpty()) listState.animateScrollToItem(items.lastIndex)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
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
                    tint = androidx.compose.ui.graphics.Color(0xFF2E7D32)
                )
            }
            if (thread.threadId >= 0) {
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = LanguageManager.getString("delete"),
                        tint = MaterialTheme.colorScheme.error
                    )
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
                MessageBubble(message)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .padding(bottom = 12.dp)
        ) {
            SimIndicator(
                routing = routing,
                modifier = Modifier.fillMaxWidth(),
                onClick = { showSimPicker = true }
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Bottom) {
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
                        imageVector = Icons.Default.Send,
                        contentDescription = LanguageManager.getString("send")
                    )
                }
            }
        }
    }

    if (showSimPicker) {
        SimPickerDialog(
            sims = sims,
            operator = routing.operator,
            selected = routing.sim,
            onDismiss = { showSimPicker = false },
            onPick = { sim, save ->
                showSimPicker = false
                forcedSubscription = sim.subscriptionId
                if (save) AppSettings.setDefaultSmsSubscription(context, sim.subscriptionId)
            }
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = {
                Text(LanguageManager.getString("delete_conversation"), fontWeight = FontWeight.Bold)
            },
            text = { Text(thread.title) },
            confirmButton = {
                Button(
                    onClick = {
                        confirmDelete = false
                        messages.deleteThread(thread)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) { Text(LanguageManager.getString("delete")) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(LanguageManager.getString("cancel"))
                }
            }
        )
    }
}

@Composable
private fun MessageBubble(message: SmsMessageItem) {
    val incoming = message.isIncoming
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (incoming) Alignment.CenterStart else Alignment.CenterEnd
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (incoming) 4.dp else 16.dp,
                bottomEnd = if (incoming) 16.dp else 4.dp
            ),
            color = if (incoming) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.primaryContainer
            },
            modifier = Modifier.widthIn(max = 300.dp)
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
