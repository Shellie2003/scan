package com.example.ui.messages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.launch

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
    onAccepted: () -> Unit,
    onForgotten: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var pin by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Rounded.Lock,
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
                if (onForgotten != null) {
                    // Offered from the first try rather than after three wrong
                    // codes: somebody who knows the code does not read it, and
                    // somebody who does not know it is already stuck.
                    TextButton(
                        onClick = onForgotten,
                        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = LanguageManager.getString("lock_forgot"),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
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
 * Choosing the code, twice, and the phrase that can give it back.
 *
 * Twice because a code mistyped once and then forgotten costs the discussions
 * it protects. The phrase is asked for in the same breath, and it is not
 * optional the first time: it is the only thing standing between a forgotten
 * code and a permanent loss, and nobody comes back to add it later.
 */
@Composable
fun PinSetupDialog(onDismiss: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    val changing = remember { ConversationLock.hasPin(context) }
    // A lock created before the phrase existed has none: it is asked for here,
    // once, rather than left as a lock with no way back.
    val phraseKnown = remember { ConversationLock.hasPhrase(context) }
    var current by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    var phrase by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Rounded.Lock,
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
            // Scrollable: with the phrase added, this is three fields and two
            // paragraphs, which no longer fits a small screen with the keyboard
            // open — and a field that cannot be reached cannot be filled.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
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
                Spacer(Modifier.height(12.dp))
                Text(
                    text = LanguageManager.getString("lock_phrase_desc"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                PhraseField(
                    value = phrase,
                    label = LanguageManager.getString(
                        if (phraseKnown) "lock_phrase_new" else "lock_phrase"
                    ),
                    onValueChange = {
                        phrase = it
                        error = ""
                    }
                )
                if (phraseKnown) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        // Changing the code is not a reason to have to rewrite
                        // the phrase: left empty, the one already kept stands.
                        text = LanguageManager.getString("lock_phrase_keep"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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

                        // Empty is allowed only when a phrase is already kept.
                        (phrase.isNotBlank() || !phraseKnown) &&
                            !ConversationLock.isAcceptablePhrase(phrase) ->
                            error = LanguageManager.getString("lock_phrase_too_short")

                        else -> {
                            ConversationLock.setPin(context, pin)
                            if (phrase.isNotBlank()) {
                                ConversationLock.setPhrase(context, phrase)
                            }
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
 * The way back in when the code is gone: the phrase, then a new code.
 *
 * The phrase is checked first and the code fields only appear once it is right,
 * so a wrong phrase never leads to typing a code for nothing. When no phrase
 * was ever kept there is nothing to check, and the dialog says so and offers
 * the only remaining exit.
 */
@Composable
fun ForgottenCodeDialog(
    onDismiss: () -> Unit,
    onRecovered: () -> Unit,
    onGiveUp: () -> Unit
) {
    val context = LocalContext.current
    val phraseKnown = remember { ConversationLock.hasPhrase(context) }
    var phrase by remember { mutableStateOf("") }
    var verified by remember { mutableStateOf(false) }
    var pin by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Rounded.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = { Text(LanguageManager.getString("lock_recover"), fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = LanguageManager.getString(
                        when {
                            !phraseKnown -> "lock_give_up_desc"
                            verified -> "lock_recover_done"
                            else -> "lock_recover_desc"
                        }
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (phraseKnown) {
                    Spacer(Modifier.height(12.dp))
                    if (verified) {
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
                    } else {
                        PhraseField(
                            value = phrase,
                            label = LanguageManager.getString("lock_phrase"),
                            onValueChange = {
                                phrase = it
                                error = ""
                            }
                        )
                    }
                }
                if (error.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = error,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Spacer(Modifier.height(4.dp))
                TextButton(
                    onClick = onGiveUp,
                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = LanguageManager.getString("lock_give_up"),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            if (phraseKnown) {
                Button(
                    onClick = {
                        when {
                            !verified -> {
                                if (ConversationLock.verifyPhrase(context, phrase)) {
                                    verified = true
                                    error = ""
                                } else {
                                    error = LanguageManager.getString("lock_phrase_wrong")
                                }
                            }

                            !ConversationLock.isAcceptable(pin) ->
                                error = LanguageManager.getString("lock_code_too_short")

                            pin != again ->
                                error = LanguageManager.getString("lock_code_mismatch")

                            else -> {
                                ConversationLock.setPin(context, pin)
                                ConversationLock.grantAccess()
                                onRecovered()
                            }
                        }
                    },
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        text = LanguageManager.getString(if (verified) "save" else "unlock"),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(LanguageManager.getString("cancel")) }
        }
    )
}

/**
 * Giving the lock up, asked twice.
 *
 * Twice because the first press is often curiosity and the second never is:
 * what follows cannot be undone, and the count of what will go is shown before
 * the button that destroys it.
 */
@Composable
fun GiveUpLockDialog(onDismiss: () -> Unit, onDone: (Int) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val count = remember { ConversationLock.count(context) }
    var sure by remember { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!working) onDismiss() },
        icon = {
            Icon(
                imageVector = Icons.Rounded.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = { Text(LanguageManager.getString("lock_give_up"), fontWeight = FontWeight.Bold) },
        text = {
            Text(
                text = if (sure) {
                    LanguageManager.getString("lock_give_up_confirm", "count" to count)
                } else {
                    LanguageManager.getString("lock_give_up_desc")
                },
                style = MaterialTheme.typography.bodySmall
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    if (!sure) {
                        sure = true
                    } else {
                        working = true
                        scope.launch {
                            val deleted = ConversationLock.forget(context)
                            working = false
                            onDone(deleted)
                        }
                    }
                },
                enabled = !working,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    text = LanguageManager.getString(if (sure) "delete" else "continue"),
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !working) {
                Text(LanguageManager.getString("cancel"))
            }
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
    // The three ways this ends: the code, the phrase, or giving the lock up.
    var recovering by remember { mutableStateOf(false) }
    var givingUp by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Rounded.Lock,
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
    when {
        givingUp -> GiveUpLockDialog(
            onDismiss = { givingUp = false },
            // Nothing is left to open: this discussion was one of the deleted.
            onDone = { onBack() }
        )

        recovering -> ForgottenCodeDialog(
            onDismiss = { recovering = false },
            onRecovered = onUnlocked,
            onGiveUp = {
                recovering = false
                givingUp = true
            }
        )

        else -> PinPromptDialog(
            title = LanguageManager.getString("lock_code"),
            message = LanguageManager.getString("secure_prompt_desc"),
            onDismiss = onBack,
            onAccepted = onUnlocked,
            onForgotten = { recovering = true }
        )
    }
}

/**
 * The phrase, typed in the clear.
 *
 * Not hidden, unlike the code: a sentence written blind on a cheap keyboard is
 * mistyped, and this is read once in a lifetime, alone, after the code is
 * already lost — not ten times a day in front of a customer.
 */
@Composable
private fun PhraseField(value: String, label: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { typed -> onValueChange(typed.take(PHRASE_MAX_LENGTH)) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    )
}

/** Long enough for a sentence, short enough to stay one line of storage. */
private const val PHRASE_MAX_LENGTH = 120

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
