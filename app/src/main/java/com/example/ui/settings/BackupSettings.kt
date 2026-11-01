package com.example.ui.settings

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.data.SettingsBackup
import com.example.ui.components.SectionHeader
import com.example.ui.util.LanguageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Writing the settings out to a file, and reading them back.
 *
 * The file goes wherever the user says through the picker of the system, so no
 * storage permission is involved and the backup can live on a memory card, in a
 * message to oneself, or on the computer of the cybercafé.
 */
@Composable
fun BackupSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }

    fun toast(text: String) {
        Toast.makeText(context, text, Toast.LENGTH_LONG).show()
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            val done = withContext(Dispatchers.IO) {
                try {
                    val text = SettingsBackup.export(context)
                    context.contentResolver.openOutputStream(uri)?.use { stream ->
                        stream.write(text.toByteArray(Charsets.UTF_8))
                    } != null
                } catch (e: Exception) {
                    false
                }
            }
            busy = false
            toast(
                LanguageManager.getString(if (done) "backup_saved" else "backup_failed")
            )
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        // A settings file is a few kilobytes; anything far
                        // bigger is not one, and is not read into memory.
                        val bytes = stream.readBytes()
                        if (bytes.size > MAX_FILE_BYTES) null else bytes.decodeToString()
                    }
                } catch (e: Exception) {
                    null
                }
            }
            val report = text?.let { SettingsBackup.import(context, it) }
            busy = false
            when {
                report == null -> toast(LanguageManager.getString("backup_failed"))
                report.failed && report.total == 0 ->
                    toast(LanguageManager.getString("backup_failed"))

                else -> toast(
                    LanguageManager.getString("backup_restored", "count" to report.total)
                )
            }
        }
    }

    SectionHeader(LanguageManager.getString("section_backup"))
    Text(
        text = LanguageManager.getString("backup_desc"),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(4.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(
            enabled = !busy,
            onClick = { exportLauncher.launch(SettingsBackup.suggestedFileName()) }
        ) {
            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(LanguageManager.getString("backup_export"))
        }
        TextButton(
            enabled = !busy,
            onClick = {
                importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
            }
        ) {
            Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(LanguageManager.getString("backup_import"))
        }
    }
}

/** A settings file is small; two megabytes is already far past generous. */
private const val MAX_FILE_BYTES = 2 * 1024 * 1024
