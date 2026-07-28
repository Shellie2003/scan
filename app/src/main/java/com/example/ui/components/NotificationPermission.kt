package com.example.ui.components

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.example.data.AppSettings
import com.example.ui.util.LanguageManager

/**
 * The permission that makes an incoming message — and an incoming call — visible.
 *
 * Android 13 and later hand notifications out on request only: without it the
 * app receives everything and shows nothing, which is the worst possible
 * failure for a phone. The system dialog is asked once, and what is left after
 * that is [NotificationPermissionBanner].
 */
@Composable
fun NotificationPermissionGate() {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* The banner reads the result from the system on the next resume. */ }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return@LaunchedEffect
        if (notificationsEnabled(context)) return@LaunchedEffect
        if (AppSettings.notificationPromptShown(context)) return@LaunchedEffect
        AppSettings.setNotificationPromptShown(context, true)
        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

/**
 * Shown as long as notifications are off, on the screens that depend on them.
 *
 * The first tap asks the system; once Android refuses to ask again, it opens the
 * settings page of the app — which is the only way back from a refusal.
 */
@Composable
fun NotificationPermissionBanner(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(notificationsEnabled(context)) }
    var dismissed by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        enabled = notificationsEnabled(context)
        // Android answers without a dialog once the user has refused for good.
        if (!enabled) openNotificationSettings(context)
    }

    // Coming back from the settings must clear the banner without a restart.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        enabled = notificationsEnabled(context)
    }

    if (enabled || dismissed) return

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f)
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.NotificationsOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = LanguageManager.getString("notifications_off_title"),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = LanguageManager.getString("notifications_off_desc"),
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
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            AppSettings.setNotificationPromptShown(context, true)
                            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            openNotificationSettings(context)
                        }
                    }
                ) {
                    Text(
                        text = LanguageManager.getString("enable"),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

private fun notificationsEnabled(context: Context): Boolean = try {
    NotificationManagerCompat.from(context).areNotificationsEnabled()
} catch (e: Exception) {
    true
}

private fun openNotificationSettings(context: Context) {
    val notificationSettings = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    } else {
        null
    }
    val appSettings = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null)
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    val opened = notificationSettings != null &&
        runCatching { context.startActivity(notificationSettings) }.isSuccess
    if (!opened) runCatching { context.startActivity(appSettings) }
}
