package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.telephony.NumberUtils
import com.example.telephony.PhoneCallService
import com.example.telephony.SmsDeliverReceiver
import com.example.ui.ScanScreen
import com.example.ui.TolotraScreen
import com.example.ui.components.WithSimRouting
import com.example.ui.messages.MessagesScreen
import com.example.ui.phone.PhoneScreen
import com.example.ui.settings.SettingsScreen
import com.example.ui.share.ShareScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.util.LanguageManager
import com.example.viewmodel.AppViewModel
import com.example.viewmodel.MessagesViewModel
import com.example.viewmodel.PhoneViewModel

class MainActivity : ComponentActivity() {

    /** Number received through a `tel:` intent, so the app can act as the phone app. */
    private var incomingNumber by mutableStateOf("")

    /** Number received through an `sms:` intent, so the app can act as the SMS app. */
    private var incomingMessageNumber by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        LanguageManager.init(this)
        PhoneCallService.createChannel(this)
        SmsDeliverReceiver.createChannel(this)
        incomingNumber = numberFromIntent(intent)
        incomingMessageNumber = messageNumberFromIntent(intent)

        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainScreen(
                    incomingNumber = incomingNumber,
                    incomingMessageNumber = incomingMessageNumber,
                    onNumberConsumed = { incomingNumber = "" },
                    onMessageNumberConsumed = { incomingMessageNumber = "" }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incomingNumber = numberFromIntent(intent)
        incomingMessageNumber = messageNumberFromIntent(intent)
    }

    private fun numberFromIntent(intent: Intent?): String {
        val data = intent?.data ?: return ""
        if (data.scheme != "tel") return ""
        return NumberUtils.sanitize(data.schemeSpecificPart.orEmpty())
    }

    private fun messageNumberFromIntent(intent: Intent?): String {
        val data = intent?.data ?: return ""
        if (data.scheme !in listOf("sms", "smsto", "mms", "mmsto")) return ""
        return NumberUtils.sanitize(data.schemeSpecificPart.orEmpty())
    }
}

private data class NavigationItem(
    val route: String,
    val labelKey: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

private val navigationItems = listOf(
    NavigationItem("Phone", "nav_phone", Icons.Default.Phone),
    NavigationItem("Messages", "nav_messages", Icons.Default.Message),
    NavigationItem("Scan", "nav_scan", Icons.Default.QrCodeScanner),
    NavigationItem("Tolotra", "nav_offers", Icons.Default.LocalOffer),
    NavigationItem("Settings", "nav_settings", Icons.Default.Settings)
)

@Composable
fun MainScreen(
    incomingNumber: String = "",
    incomingMessageNumber: String = "",
    onNumberConsumed: () -> Unit = {},
    onMessageNumberConsumed: () -> Unit = {}
) {
    val navController = rememberNavController()
    val currentLang by LanguageManager.currentLang
    val app: AppViewModel = viewModel()
    val phone: PhoneViewModel = viewModel()
    val messages: MessagesViewModel = viewModel()

    var messageRecipient by remember { mutableStateOf("") }

    Scaffold(
        bottomBar = {
            NavigationBar(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 12.dp)
                    .navigationBarsPadding()
                    .shadow(
                        elevation = 12.dp,
                        shape = RoundedCornerShape(24.dp),
                        clip = false,
                        ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                        spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                    )
                    .clip(RoundedCornerShape(24.dp)),
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
                tonalElevation = 8.dp
            ) {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination
                navigationItems.forEach { item ->
                    val isSelected = currentDestination?.hierarchy?.any { it.route == item.route } == true
                    val label = LanguageManager.getString(item.labelKey)
                    NavigationBarItem(
                        icon = {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = label,
                                modifier = Modifier.size(22.dp)
                            )
                        },
                        label = {
                            Text(
                                text = label,
                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                fontSize = 10.sp,
                                letterSpacing = 0.2.sp,
                                maxLines = 1
                            )
                        },
                        selected = isSelected,
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        ),
                        onClick = {
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        WithSimRouting(app) { launcher ->
            NavHost(
                navController = navController,
                startDestination = "Phone",
                modifier = Modifier.padding(innerPadding)
            ) {
                composable("Phone") {
                    PhoneScreen(
                        app = app,
                        phone = phone,
                        launcher = launcher,
                        prefilledNumber = incomingNumber,
                        onMessage = { number ->
                            messageRecipient = number
                            navController.navigate("Messages") { launchSingleTop = true }
                        }
                    )
                }
                composable("Messages") {
                    MessagesScreen(
                        app = app,
                        phone = phone,
                        messages = messages,
                        prefilledNumber = messageRecipient,
                        onCall = { number -> launcher.call(number) }
                    )
                }
                composable("Scan") { ScanScreen(app = app, launcher = launcher) }
                composable("Tolotra") {
                    TolotraScreen(
                        app = app,
                        launcher = launcher,
                        onOpenShare = {
                            navController.navigate("Share") { launchSingleTop = true }
                        }
                    )
                }
                composable("Share") { ShareScreen(app = app, phone = phone, launcher = launcher) }
                composable("Settings") { SettingsScreen(app = app) }
            }
        }
    }

    LaunchedEffect(incomingNumber) {
        if (incomingNumber.isNotBlank()) {
            navController.navigate("Phone") { launchSingleTop = true }
            onNumberConsumed()
        }
    }

    LaunchedEffect(incomingMessageNumber) {
        if (incomingMessageNumber.isNotBlank()) {
            messageRecipient = incomingMessageNumber
            navController.navigate("Messages") { launchSingleTop = true }
            onMessageNumberConsumed()
        }
    }
}
