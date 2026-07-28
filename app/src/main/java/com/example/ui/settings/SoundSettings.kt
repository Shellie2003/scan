package com.example.ui.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import com.example.data.AppSettings
import com.example.telephony.MessageNotifier
import com.example.ui.util.LanguageManager

/**
 * Choosing what the phone plays: the ringtone of a call, the sound of a message.
 *
 * The call ringtone is set as the ringtone of the phone, through the system.
 * SCAN deliberately does not take the ringing over: a dialer that plays its own
 * ringtone is a dialer that can ring silently the day its playback fails, and a
 * missed call is not a bug this application is willing to risk.
 *
 * The message sound belongs to SCAN alone, and travels with its notification
 * channel.
 */
@Composable
fun SoundSettingsSection() {
    val context = LocalContext.current

    var messageSound by remember { mutableStateOf(AppSettings.messageSound(context)) }
    var ringtoneUri by remember { mutableStateOf(systemRingtone(context)) }

    val messagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val uri = pickedUri(result)
        AppSettings.setMessageSound(context, uri?.toString().orEmpty())
        messageSound = uri?.toString().orEmpty()
        // The sound of a channel is frozen once it exists: this rebuilds it.
        MessageNotifier.createChannel(context)
    }

    val ringtonePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val uri = pickedUri(result) ?: return@rememberLauncherForActivityResult
        val applied = runCatching {
            RingtoneManager.setActualDefaultRingtoneUri(
                context,
                RingtoneManager.TYPE_RINGTONE,
                uri
            )
        }.isSuccess
        ringtoneUri = systemRingtone(context)
        if (!applied) {
            Toast.makeText(
                context,
                LanguageManager.getString("action_failed"),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    val writeSettings = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        if (canWriteSettings(context)) {
            ringtonePicker.launch(
                ringtonePickerIntent(
                    context = context,
                    type = RingtoneManager.TYPE_RINGTONE,
                    current = systemRingtone(context),
                    title = LanguageManager.getString("call_ringtone")
                )
            )
        }
    }

    SoundRow(
        icon = Icons.Default.MusicNote,
        title = LanguageManager.getString("call_ringtone"),
        value = titleOf(context, ringtoneUri),
        onClick = {
            if (canWriteSettings(context)) {
                ringtonePicker.launch(
                    ringtonePickerIntent(
                        context = context,
                        type = RingtoneManager.TYPE_RINGTONE,
                        current = ringtoneUri,
                        title = LanguageManager.getString("call_ringtone")
                    )
                )
            } else {
                // Changing the ringtone of the phone is a system setting: it is
                // granted on a screen of its own, never in a dialog.
                Toast.makeText(
                    context,
                    LanguageManager.getString("ringtone_permission"),
                    Toast.LENGTH_LONG
                ).show()
                runCatching {
                    writeSettings.launch(
                        Intent(
                            Settings.ACTION_MANAGE_WRITE_SETTINGS,
                            Uri.parse("package:" + context.packageName)
                        )
                    )
                }
            }
        }
    )

    SoundRow(
        icon = Icons.Default.Notifications,
        title = LanguageManager.getString("message_sound"),
        value = messageSound
            .takeIf { it.isNotBlank() }
            ?.let { titleOf(context, Uri.parse(it)) }
            ?: LanguageManager.getString("sound_default"),
        onClick = {
            messagePicker.launch(
                ringtonePickerIntent(
                    context = context,
                    type = RingtoneManager.TYPE_NOTIFICATION,
                    current = messageSound.takeIf { it.isNotBlank() }?.let { Uri.parse(it) },
                    title = LanguageManager.getString("message_sound")
                )
            )
        }
    )
}

@Composable
private fun SoundRow(
    icon: ImageVector,
    title: String,
    value: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun ringtonePickerIntent(
    context: Context,
    type: Int,
    current: Uri?,
    title: String
): Intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
    putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, type)
    putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, title)
    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
    // Silence is not offered: a phone that never rings is a phone that loses
    // calls and messages, and the user has the system volume for that.
    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
    putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, current)
    putExtra(
        RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI,
        RingtoneManager.getDefaultUri(type)
    )
}

private fun pickedUri(result: ActivityResult): Uri? {
    val data = result.data ?: return null
    return IntentCompat.getParcelableExtra(
        data,
        RingtoneManager.EXTRA_RINGTONE_PICKED_URI,
        Uri::class.java
    )
}

private fun systemRingtone(context: Context): Uri? = runCatching {
    RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE)
}.getOrNull()

private fun titleOf(context: Context, uri: Uri?): String {
    if (uri == null) return LanguageManager.getString("sound_default")
    return runCatching {
        RingtoneManager.getRingtone(context, uri)?.getTitle(context)
    }.getOrNull() ?: LanguageManager.getString("sound_default")
}

private fun canWriteSettings(context: Context): Boolean = runCatching {
    Settings.System.canWrite(context)
}.getOrDefault(false)
