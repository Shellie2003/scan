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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
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
