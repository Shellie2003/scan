package com.example.ui.messages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.ConversationLock
import com.example.ui.util.LanguageManager

/**
 * The code that opens the locked discussions.
 *
 * Digits only, and hidden while typed: this is entered in front of whoever
 * handed the phone back, which is the whole reason the lock exists.
 */
@Composable
fun PinPromptDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    onAccepted: () -> Unit
) {
    val context = LocalContext.current
    var pin by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                PinField(
                    value = pin,
                    label = LanguageManager.getString("lock_code"),
                    onValueChange = {
                        pin = it
                        wrong = false
                    }
                )
                if (wrong) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = LanguageManager.getString("lock_code_wrong"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (ConversationLock.verify(context, pin)) {
                        // The code just given opens the locked discussions for a
                        // short while, so reading two of them costs one code.
                        ConversationLock.grantAccess()
                        onAccepted()
                    } else {
                        wrong = true
                    }
                },
                enabled = pin.length >= ConversationLock.MIN_LENGTH,
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(LanguageManager.getString("unlock"), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(LanguageManager.getString("cancel")) }
        }
    )
}

/**
 * Choosing the code, twice.
 *
 * Twice because there is nothing to fall back on: a code mistyped once and
 * forgotten would leave the discussions it protects reachable only by removing
 * the lock altogether.
 */
@Composable
fun PinSetupDialog(onDismiss: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    val changing = remember { ConversationLock.hasPin(context) }
    var current by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = LanguageManager.getString(if (changing) "lock_change" else "lock_create"),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = LanguageManager.getString("lock_create_desc"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                if (changing) {
                    PinField(
                        value = current,
                        label = LanguageManager.getString("lock_code_current"),
                        onValueChange = {
                            current = it
                            error = ""
                        }
                    )
                    Spacer(Modifier.height(8.dp))
                }
                PinField(
                    value = pin,
                    label = LanguageManager.getString("lock_code_new"),
                    onValueChange = {
                        pin = it
                        error = ""
                    }
                )
                Spacer(Modifier.height(8.dp))
                PinField(
                    value = again,
                    label = LanguageManager.getString("lock_code_again"),
                    onValueChange = {
                        again = it
                        error = ""
                    }
                )
                if (error.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = error,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    when {
                        changing && !ConversationLock.verify(context, current) ->
                            error = LanguageManager.getString("lock_code_wrong")

                        !ConversationLock.isAcceptable(pin) ->
                            error = LanguageManager.getString("lock_code_too_short")

                        pin != again ->
                            error = LanguageManager.getString("lock_code_mismatch")

                        else -> {
                            ConversationLock.setPin(context, pin)
                            // The user has just chosen it: they are not asked
                            // for it again in the same breath.
                            ConversationLock.grantAccess()
                            onDone()
                        }
                    }
                },
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(LanguageManager.getString("save"), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(LanguageManager.getString("cancel")) }
        }
    )
}

/**
 * What stands in place of a locked discussion until the code is given.
 *
 * Every way into a discussion passes here — a notification, a contact, the call
 * history, an `smsto:` link — so hiding it from the inbox is no longer the only
 * thing protecting it. Refusing the question goes back where the user came from.
 */
@Composable
fun LockedConversationGate(onBack: () -> Unit, onUnlocked: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(56.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = LanguageManager.getString("secure_conversations"),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
    PinPromptDialog(
        title = LanguageManager.getString("lock_code"),
        message = LanguageManager.getString("secure_prompt_desc"),
        onDismiss = onBack,
        onAccepted = onUnlocked
    )
}

@Composable
private fun PinField(value: String, label: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { typed ->
            val digits = typed.filter { it.isDigit() }.take(ConversationLock.MAX_LENGTH)
            onValueChange(digits)
        },
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    )
}
