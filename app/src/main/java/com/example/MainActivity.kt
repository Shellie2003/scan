package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
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
import com.example.telephony.MessageNotifier
import com.example.telephony.MissedCallNotifier
import com.example.telephony.NumberUtils
import com.example.telephony.PhoneCallService
import com.example.ui.ScanScreen
import com.example.ui.TolotraScreen
import com.example.ui.components.NotificationPermissionGate
import com.example.ui.components.WithSimRouting
import com.example.ui.messages.ConversationScreen
import com.example.ui.messages.MessagesScreen
import com.example.ui.phone.PhoneScreen
import com.example.ui.settings.SettingsScreen
import com.example.ui.share.ShareScreen
import com.example.ui.theme.ScanTheme
import com.example.ui.theme.ThemeController
import com.example.ui.util.LanguageManager
import com.example.viewmodel.AppViewModel
import com.example.viewmodel.MessagesViewModel
import com.example.viewmodel.PhoneViewModel

/** Every destination of the app, named in one place. */
object Routes {
    const val PHONE = "phone"
    const val MESSAGES = "messages"
    const val CONVERSATION = "messages/conversation"
    const val SCAN = "scan"
    const val OFFERS = "offers"
    const val SHARE = "offers/share"
    const val SETTINGS = "settings"
}

/**
 * What an incoming intent asks the app to open.
 *
 * A message notification asks for the discussion itself — not for the dialer,
 * which is what the app shows when it is simply launched.
 */
sealed interface AppTarget {
    data class Dial(val number: String) : AppTarget
    data class Conversation(val address: String, val name: String = "") : AppTarget
    data object Messages : AppTarget
}

class MainActivity : ComponentActivity() {

    /** Destination asked by the intent that started — or resumed — the activity. */
    private var target by mutableStateOf<AppTarget?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        LanguageManager.init(this)
        ThemeController.init(this)
        PhoneCallService.createChannel(this)
        MessageNotifier.createChannel(this)
        MissedCallNotifier.createChannel(this)
        target = targetFromIntent(intent)

        enableEdgeToEdge()
        setContent {
            ScanTheme {
                MainScreen(target = target, onTargetConsumed = { target = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        target = targetFromIntent(intent)
    }

    private fun targetFromIntent(intent: Intent?): AppTarget? {
        if (intent == null) return null

        if (intent.action == MessageNotifier.ACTION_OPEN_CONVERSATION) {
            val address = intent.getStringExtra(MessageNotifier.EXTRA_ADDRESS).orEmpty()
            return if (address.isBlank()) {
                AppTarget.Messages
            } else {
                AppTarget.Conversation(
                    address = address,
                    name = intent.getStringExtra(MessageNotifier.EXTRA_NAME).orEmpty()
                )
            }
        }

        val data = intent.data
        if (data == null) {
            // DIAL and CALL_BUTTON without a number: the user wants the keypad.
            val wantsKeypad = intent.action == Intent.ACTION_DIAL ||
                intent.action == Intent.ACTION_CALL_BUTTON
            return if (wantsKeypad) AppTarget.Dial("") else null
        }

        val part = data.schemeSpecificPart.orEmpty().substringBefore('?')
        return when (data.scheme) {
            "tel" -> AppTarget.Dial(NumberUtils.sanitize(part))
            "sms", "smsto", "mms", "mmsto" -> {
                val address = NumberUtils.sanitize(part)
                if (address.isBlank()) AppTarget.Messages else AppTarget.Conversation(address)
            }
            else -> null
        }
    }
}

private data class NavigationItem(
    val route: String,
    val labelKey: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

private val navigationItems = listOf(
    NavigationItem(Routes.PHONE, "nav_phone", Icons.Default.Phone),
    NavigationItem(Routes.MESSAGES, "nav_messages", Icons.Default.Message),
    NavigationItem(Routes.SCAN, "nav_scan", Icons.Default.QrCodeScanner),
    NavigationItem(Routes.OFFERS, "nav_offers", Icons.Default.LocalOffer),
    NavigationItem(Routes.SETTINGS, "nav_settings", Icons.Default.Settings)
)

@Composable
fun MainScreen(
    target: AppTarget? = null,
    onTargetConsumed: () -> Unit = {}
) {
    val navController = rememberNavController()
    val currentLang by LanguageManager.currentLang
    val app: AppViewModel = viewModel()
    val phone: PhoneViewModel = viewModel()
    val messages: MessagesViewModel = viewModel()

    // The number the dialer opens on, and a ticket that reopens the keypad even
    // when the same number is dialled twice in a row.
    var dialNumber by remember { mutableStateOf("") }
    var dialTicket by remember { mutableIntStateOf(0) }

    // Nothing the app receives is worth anything if it cannot be shown.
    NotificationPermissionGate()

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    // The bar belongs to the five main sections; a conversation or the sharing
    // screen is a step inside one of them and takes the whole height.
    val showBottomBar = currentDestination?.hierarchy?.any { destination ->
        navigationItems.any { it.route == destination.route }
    } == true

    fun openSection(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun openConversation(address: String, name: String = "") {
        if (address.isBlank()) return
        messages.openWithAddress(address, name)
        navController.navigate(Routes.CONVERSATION) { launchSingleTop = true }
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                ScanBottomBar(
                    isSelected = { route ->
                        currentDestination?.hierarchy?.any { it.route == route } == true
                    },
                    onSelect = { route -> openSection(route) }
                )
            }
        }
    ) { innerPadding ->
        WithSimRouting(app) { launcher ->
            NavHost(
                navController = navController,
                startDestination = Routes.PHONE,
                modifier = Modifier
                    .padding(innerPadding)
                    // What the scaffold already paid for is consumed here, so the
                    // keyboard padding of the message composer stays exact.
                    .consumeWindowInsets(innerPadding)
            ) {
                composable(Routes.PHONE) {
                    PhoneScreen(
                        app = app,
                        phone = phone,
                        launcher = launcher,
                        dialNumber = dialNumber,
                        dialTicket = dialTicket,
                        onMessage = { number -> openConversation(number) }
                    )
                }
                composable(Routes.MESSAGES) {
                    MessagesScreen(
                        app = app,
                        phone = phone,
                        messages = messages,
                        onOpenConversation = { address, name -> openConversation(address, name) },
                        onOpenThread = { thread ->
                            messages.open(thread)
                            navController.navigate(Routes.CONVERSATION) { launchSingleTop = true }
                        }
                    )
                }
                composable(Routes.CONVERSATION) {
                    val thread by messages.openThread.collectAsState()
                    val current = thread
                    // Closing the conversation — from the back arrow, a deletion or
                    // the system back — is what leaves the screen.
                    LaunchedEffect(current) {
                        if (current == null) {
                            navController.popBackStack(Routes.CONVERSATION, inclusive = true)
                        }
                    }
                    if (current != null) {
                        ConversationScreen(
                            app = app,
                            messages = messages,
                            thread = current,
                            onBack = { messages.close() },
                            onCall = { number -> launcher.call(number) }
                        )
                    }
                }
                composable(Routes.SCAN) { ScanScreen(app = app, launcher = launcher) }
                composable(Routes.OFFERS) {
                    TolotraScreen(
                        app = app,
                        launcher = launcher,
                        onOpenShare = {
                            navController.navigate(Routes.SHARE) { launchSingleTop = true }
                        }
                    )
                }
                composable(Routes.SHARE) { ShareScreen(app = app, phone = phone, launcher = launcher) }
                composable(Routes.SETTINGS) { SettingsScreen(app = app) }
            }
        }
    }

    LaunchedEffect(target) {
        when (val asked = target) {
            null -> Unit

            is AppTarget.Dial -> {
                dialNumber = asked.number
                dialTicket += 1
                openSection(Routes.PHONE)
                onTargetConsumed()
            }

            is AppTarget.Conversation -> {
                openSection(Routes.MESSAGES)
                openConversation(asked.address, asked.name)
                onTargetConsumed()
            }

            AppTarget.Messages -> {
                openSection(Routes.MESSAGES)
                onTargetConsumed()
            }
        }
    }
}

/** The floating bar of the five main sections. */
@Composable
private fun ScanBottomBar(
    isSelected: (String) -> Boolean,
    onSelect: (String) -> Unit
) {
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
        containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.97f),
        tonalElevation = 8.dp
    ) {
        navigationItems.forEach { item ->
            val selected = isSelected(item.route)
            val label = LanguageManager.getString(item.labelKey)

            // The scan is what the application is named after and what is
            // reached for in a shop, in a hurry: it is a button among tabs.
            if (item.route == Routes.SCAN) {
                NavigationBarItem(
                    icon = {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .shadow(
                                    elevation = 8.dp,
                                    shape = CircleShape,
                                    ambientColor = MaterialTheme.colorScheme.primary,
                                    spotColor = MaterialTheme.colorScheme.primary
                                )
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = label,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    },
                    label = null,
                    alwaysShowLabel = false,
                    selected = selected,
                    colors = NavigationBarItemDefaults.colors(
                        // The circle already says where it is: no pill behind it.
                        indicatorColor = Color.Transparent
                    ),
                    onClick = { onSelect(item.route) }
                )
                return@forEach
            }

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
                        fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
                        fontSize = 10.sp,
                        letterSpacing = 0.2.sp,
                        maxLines = 1
                    )
                },
                selected = selected,
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                ),
                onClick = { onSelect(item.route) }
            )
        }
    }
}
