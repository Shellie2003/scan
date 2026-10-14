package com.example.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.BlockedNumbers
import com.example.data.ConversationLock
import com.example.telephony.NumberUtils
import com.example.ui.components.SectionHeader
import com.example.ui.messages.PinSetupDialog
import com.example.ui.util.LanguageManager

/**
 * The lock of the discussions and the numbers that are refused.
 *
 * Both belong here rather than buried in a screen: a code is set once, and a
 * blocked number has to be findable again on the day its owner is forgiven.
 */
@Composable
fun SecuritySection() {
    val context = LocalContext.current

    var hasPin by remember { mutableStateOf(ConversationLock.hasPin(context)) }
    var lockedCount by remember { mutableStateOf(ConversationLock.count(context)) }
    var blocked by remember { mutableStateOf(BlockedNumbers.all(context).toList()) }
    var setupPin by remember { mutableStateOf(false) }
    var confirmRemovePin by remember { mutableStateOf(false) }

    SectionHeader(LanguageManager.getString("section_security"))
    Text(
        text = LanguageManager.getString("secure_section_desc"),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(8.dp))

    Card(
        modifier = Modifier.fillMaxWidth(),
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
                imageVector = if (hasPin) Icons.Default.Lock else Icons.Default.LockOpen,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = LanguageManager.getString("lock_code"),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (hasPin) {
                        LanguageManager.getString("secure_count", "count" to lockedCount)
                    } else {
                        LanguageManager.getString("lock_none")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = { setupPin = true }) {
            Text(
                LanguageManager.getString(if (hasPin) "lock_change" else "lock_create")
            )
        }
        if (hasPin) {
            TextButton(onClick = { confirmRemovePin = true }) {
                Text(
                    text = LanguageManager.getString("lock_remove"),
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }

    // ------------------------------------------------------------ blocked numbers
    SectionHeader(LanguageManager.getString("section_blocked"))
    if (blocked.isEmpty()) {
        Text(
            text = LanguageManager.getString("no_blocked_numbers"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    blocked.forEach { number ->
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Block,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = NumberUtils.formatForDisplay(number),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = {
                        BlockedNumbers.unblock(context, number)
                        blocked = BlockedNumbers.all(context).toList()
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = LanguageManager.getString("unblock_number"),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }

    if (setupPin) {
        PinSetupDialog(
            onDismiss = { setupPin = false },
            onDone = {
                setupPin = false
                hasPin = true
                lockedCount = ConversationLock.count(context)
            }
        )
    }

    if (confirmRemovePin) {
        AlertDialog(
            onDismissRequest = { confirmRemovePin = false },
            title = {
                Text(LanguageManager.getString("lock_remove"), fontWeight = FontWeight.Bold)
            },
            text = { Text(LanguageManager.getString("lock_remove_confirm")) },
            confirmButton = {
                Button(
                    onClick = {
                        confirmRemovePin = false
                        // Removing the code frees the discussions it hid: a
                        // hidden discussion with no way in would simply be lost.
                        ConversationLock.clearPin(context)
                        hasPin = false
                        lockedCount = 0
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) { Text(LanguageManager.getString("delete")) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemovePin = false }) {
                    Text(LanguageManager.getString("cancel"))
                }
            }
        )
    }
}
