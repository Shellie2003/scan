package com.example.ui.phone

import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Message
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import com.example.ui.theme.scanColors
import com.example.data.BlockedNumbers
import com.example.telephony.Contact
import com.example.telephony.ContactsRepository
import com.example.telephony.NumberUtils
import com.example.ui.components.ContactAvatar
import com.example.ui.util.LanguageManager
import kotlinx.coroutines.launch

/**
 * Creating or changing a contact, in a sheet rather than a screen of its own.
 *
 * A contact here is a name and a number — what the app itself uses. Everything
 * else a contact may carry is left to the contacts application of the phone,
 * and is never touched by an edit made from SCAN.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactEditorSheet(
    contact: Contact?,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var name by remember(contact) { mutableStateOf(contact?.name.orEmpty()) }
    var number by remember(contact) { mutableStateOf(contact?.number.orEmpty()) }
    var saving by remember { mutableStateOf(false) }

    // The picture picked in this session, still in memory: it is written with
    // the rest when the contact is saved, never before.
    var picture by remember(contact) { mutableStateOf<ByteArray?>(null) }
    var preview by remember(contact) { mutableStateOf<ImageBitmap?>(null) }

    val pickPicture = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val bytes = ContactsRepository.readPicture(context, uri)
            if (bytes == null) {
                Toast.makeText(
                    context,
                    LanguageManager.getString("contact_save_failed"),
                    Toast.LENGTH_SHORT
                ).show()
                return@launch
            }
            picture = bytes
            preview = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }
    }

    fun toast(key: String) {
        Toast.makeText(context, LanguageManager.getString(key), Toast.LENGTH_SHORT).show()
    }

    fun save() {
        val cleanNumber = NumberUtils.sanitize(number)
        if (cleanNumber.length < 3) return
        saving = true
        scope.launch {
            val done = if (contact == null) {
                ContactsRepository.create(context, name.trim(), cleanNumber, picture)
            } else {
                val updated = ContactsRepository.update(context, contact, name.trim(), cleanNumber)
                val pictureDone = picture?.let {
                    ContactsRepository.setPicture(context, contact, it)
                } ?: true
                updated && pictureDone
            }
            saving = false
            if (done) {
                toast("contact_saved")
                onSaved()
            } else {
                toast("contact_save_failed")
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp)
                .imePadding()
        ) {
            Text(
                text = LanguageManager.getString(
                    if (contact == null) "new_contact" else "edit_contact"
                ),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val shown = preview
                    Box(
                        modifier = Modifier
                            .size(88.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                            .clickable {
                                pickPicture.launch(
                                    PickVisualMediaRequest(
                                        ActivityResultContracts.PickVisualMedia.ImageOnly
                                    )
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        when {
                            shown != null -> Image(
                                bitmap = shown,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(88.dp)
                            )

                            contact != null -> ContactAvatar(contact = contact, size = 88.dp)

                            else -> Icon(
                                imageVector = Icons.Default.AddAPhoto,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    TextButton(
                        onClick = {
                            pickPicture.launch(
                                PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageOnly
                                )
                            )
                        }
                    ) {
                        Text(
                            text = LanguageManager.getString("contact_photo"),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(LanguageManager.getString("contact_name")) },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = number,
                onValueChange = { number = it },
                label = { Text(LanguageManager.getString("contact_number")) },
                leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) {
                    Text(LanguageManager.getString("cancel"))
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { save() },
                    enabled = !saving && NumberUtils.sanitize(number).length >= 3,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(LanguageManager.getString("save"), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/** Last word before a contact leaves the phone for good. */
@Composable
fun DeleteContactDialog(
    contact: Contact,
    onDismiss: () -> Unit,
    onDeleted: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(24.dp)
            )
        },
        title = {
            Text(LanguageManager.getString("delete_contact"), fontWeight = FontWeight.Bold)
        },
        text = {
            Text(
                LanguageManager.getString(
                    "delete_contact_confirm",
                    "name" to contact.name.ifBlank {
                        NumberUtils.formatForDisplay(contact.number)
                    }
                )
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    scope.launch {
                        val done = ContactsRepository.delete(context, contact)
                        Toast.makeText(
                            context,
                            LanguageManager.getString(
                                if (done) "contact_deleted" else "contact_save_failed"
                            ),
                            Toast.LENGTH_SHORT
                        ).show()
                        if (done) onDeleted() else onDismiss()
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) { Text(LanguageManager.getString("delete")) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(LanguageManager.getString("cancel"))
            }
        }
    )
}

/**
 * The card of a contact, opened by tapping their line.
 *
 * Tapping used to place the call straight away, which is a hard thing to undo
 * when the wrong line was touched. The card shows who it is — photo included —
 * and lets the call be the deliberate gesture it should be.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ContactDetailsDialog(
    contact: Contact,
    onDismiss: () -> Unit,
    onCall: () -> Unit,
    onMessage: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleFavorite: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    var favorite by remember(contact) { mutableStateOf(contact.isFavorite) }
    var blocked by remember(contact) {
        mutableStateOf(BlockedNumbers.isBlocked(context, contact.number))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = null,
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // The picture is changed from the picture: tapping it opens the
                // editor, where it is picked.
                Box(
                    modifier = Modifier.clickable(onClick = onEdit),
                    contentAlignment = Alignment.BottomEnd
                ) {
                    ContactAvatar(contact = contact, size = 88.dp)
                    if (contact.photoUri.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddAPhoto,
                                contentDescription = LanguageManager.getString("contact_photo"),
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = contact.name.ifBlank {
                        LanguageManager.getString("unknown_number")
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(2.dp))
                // A long press on the number copies it, as everywhere else.
                Text(
                    text = NumberUtils.formatForDisplay(contact.number),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.combinedClickable(
                        onClick = {},
                        onLongClick = {
                            clipboard.setText(
                                AnnotatedString(NumberUtils.toNational(contact.number))
                            )
                            Toast.makeText(
                                context,
                                LanguageManager.getString("number_copied"),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    )
                )

                Spacer(Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    ContactAction(
                        icon = Icons.Default.Call,
                        label = LanguageManager.getString("call"),
                        tint = MaterialTheme.scanColors.success,
                        onClick = onCall
                    )
                    ContactAction(
                        icon = Icons.Default.Message,
                        label = LanguageManager.getString("send_message"),
                        tint = MaterialTheme.colorScheme.primary,
                        onClick = onMessage
                    )
                    ContactAction(
                        icon = Icons.Default.Edit,
                        label = LanguageManager.getString("edit"),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        onClick = onEdit
                    )
                    ContactAction(
                        icon = Icons.Default.Delete,
                        label = LanguageManager.getString("delete"),
                        tint = MaterialTheme.colorScheme.error,
                        onClick = onDelete
                    )
                }

                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // The star of the phone itself, which is what fills the
                    // favourites tab — until now nothing could put anyone in it.
                    ContactAction(
                        icon = if (favorite) Icons.Default.Star else Icons.Default.StarBorder,
                        label = LanguageManager.getString("favorite"),
                        tint = MaterialTheme.scanColors.warning,
                        onClick = {
                            favorite = !favorite
                            onToggleFavorite(favorite)
                        }
                    )
                    ContactAction(
                        icon = if (blocked) Icons.Default.Person else Icons.Default.Block,
                        label = LanguageManager.getString(
                            if (blocked) "unblock_number" else "block_number"
                        ),
                        tint = MaterialTheme.colorScheme.error,
                        onClick = {
                            if (blocked) {
                                BlockedNumbers.unblock(context, contact.number)
                                blocked = false
                                Toast.makeText(
                                    context,
                                    LanguageManager.getString("number_unblocked"),
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                BlockedNumbers.block(context, contact.number)
                                blocked = true
                                Toast.makeText(
                                    context,
                                    LanguageManager.getString("number_blocked"),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    )
                    ContactAction(
                        icon = Icons.Default.Share,
                        label = LanguageManager.getString("share"),
                        tint = MaterialTheme.colorScheme.primary,
                        onClick = { shareContact(context, contact) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(LanguageManager.getString("close"))
            }
        }
    )
}

/**
 * Passes a contact to whatever app the user wants to pass it to.
 *
 * Plain text, not a file: a name and a number sent through a message is how a
 * number actually travels between two people here.
 */
private fun shareContact(context: android.content.Context, contact: Contact) {
    val text = listOf(
        contact.name.trim(),
        NumberUtils.toNational(contact.number)
    ).filter { it.isNotBlank() }.joinToString(" : ")
    try {
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_TEXT, text)
        }
        context.startActivity(
            android.content.Intent.createChooser(
                intent,
                LanguageManager.getString("share")
            )
        )
    } catch (e: Exception) {
        Toast.makeText(
            context,
            LanguageManager.getString("contact_save_failed"),
            Toast.LENGTH_SHORT
        ).show()
    }
}

/** One round action of the contact card, with its name under it. */
@Composable
private fun ContactAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledIconButton(
            onClick = onClick,
            modifier = Modifier.size(48.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = tint.copy(alpha = 0.14f),
                contentColor = tint
            )
        ) {
            Icon(imageVector = icon, contentDescription = label, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}
