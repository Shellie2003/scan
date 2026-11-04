package com.example

import android.Manifest
import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.ScanScreen
import com.example.ui.TolotraScreen
import com.example.ui.components.WithSimRouting
import com.example.ui.messages.BulkMessagesTab
import com.example.ui.messages.MessagesScreen
import com.example.ui.phone.PhoneScreen
import com.example.ui.theme.ScanTheme
import com.example.ui.util.LanguageManager
import com.example.viewmodel.AppViewModel
import com.example.viewmodel.MessagesViewModel
import com.example.viewmodel.PhoneViewModel
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.example.data.DataSeeder
import com.example.data.DatabaseProvider
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.shadows.ShadowContentResolver
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Pictures of the real screens, drawn by the real code.
 *
 * Not a test that can fail on a difference: nothing is compared to anything.
 * It exists so that the screens can be *seen* outside a telephone — for a
 * tutorial, for a shop listing, for a bug report — without anyone having to
 * hold a device and take a photograph.
 *
 * Robolectric draws the composables on the machine itself, in native graphics
 * mode, at the size of a Pixel 8 on Android 33 — below the version that
 * brought the full-screen-call permission, which Robolectric cannot grant
 * and which would put a card across the top of every picture. What comes out is what Compose produces from
 * this source: the real spacing, the real typeface, the real colours of the
 * theme. The lists are empty because there is no telephone underneath — no
 * contacts provider, no messages, no SIM card — and that emptiness is honest:
 * it is what the application shows on a phone where nothing has happened yet.
 *
 * Each screen is a test of its own so that one that cannot be drawn — the scan
 * screen asks for a camera, and there is none here — does not take the others
 * down with it.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [33])
class RealScreenshotsTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    /**
     * What a telephone brings that a build machine does not.
     *
     * Without this every screen draws a permission card and an empty list, and
     * the pictures show an application that has never been allowed to do
     * anything — which is not what it looks like in a hand.
     */
    @Before
    fun giveItATelephone() {
        val app = androidx.test.core.app.ApplicationProvider
            .getApplicationContext<Application>()

        Shadows.shadowOf(app).grantPermissions(
            Manifest.permission.CAMERA,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.WRITE_CONTACTS,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.WRITE_CALL_LOG,
            Manifest.permission.READ_SMS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_PHONE_NUMBERS,
            Manifest.permission.POST_NOTIFICATIONS
        )

        // The contacts, the messages and the call history.
        FakeAndroidData.AUTHORITIES.forEach { authority ->
            ShadowContentResolver.registerProviderInternal(authority, FakeAndroidData())
        }

        // Being the telephone application. From Android 10 the application
        // asks the RoleManager, not the TelecomManager — setting the default
        // dialer on the second changed nothing, and the card stayed.
        runCatching {
            val roles = app.getSystemService(android.content.Context.ROLE_SERVICE)
                as android.app.role.RoleManager
            Shadows.shadowOf(roles).addHeldRole(android.app.role.RoleManager.ROLE_DIALER)
        }.onFailure { println("SCANSHOT role: " + it) }

        // The operators and the plans the application ships with. Seeded here
        // and waited for: the application seeds on a coroutine of its own, and
        // a picture taken before it finishes shows an empty catalogue.
        runBlocking {
            // Opening it is what starts the application's own seeding, on a
            // coroutine of its own. Let that finish, wipe, then seed once —
            // otherwise every operator and every plan comes out twice, which
            // is what the first populated picture showed.
            val db = DatabaseProvider.getDatabase(app)
            repeat(4) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                kotlinx.coroutines.delay(20)
            }
            db.clearAllTables()
            DataSeeder.seed(app, db)
        }
    }

    private fun shoot(
        name: String,
        /** Pressed once the screen has settled, before the picture. */
        then: String? = null,
        content: @Composable () -> Unit
    ) {
        rule.setContent {
            LanguageManager.init(LocalContext.current)
            LanguageManager.setLanguageForTest("mg")
            ScanTheme(darkTheme = false, dynamicColor = false) { content() }
        }
        // Let it settle. The lists arrive on coroutines of their own, and the
        // first run of this caught a call history that was there and a
        // contact list that was not — the same screen, a few milliseconds
        // apart.
        repeat(6) {
            rule.waitForIdle()
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            rule.mainClock.advanceTimeBy(500)
        }
        rule.waitForIdle()
        if (then != null) {
            runCatching { rule.onNodeWithText(then).performClick() }
                .onFailure { println("SCANSHOT could not press " + then) }
            repeat(4) {
                rule.waitForIdle()
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                rule.mainClock.advanceTimeBy(500)
            }
            rule.waitForIdle()
        }
        rule.onRoot().captureRoboImage(filePath = "src/test/screenshots/$name.png")
    }

    /** The whole application, bar and all, on the section it opens on. */
    @Test
    fun whole_application() = shoot("screen-main") { MainScreen() }

    @Test
    fun phone_section() = shoot("screen-phone") {
        val app: AppViewModel = viewModel()
        val phone: PhoneViewModel = viewModel()
        WithSimRouting(app) { launcher ->
            PhoneScreen(app = app, phone = phone, launcher = launcher, onMessage = {})
        }
    }

    @Test
    fun offers_section() = shoot("screen-offers") {
        val app: AppViewModel = viewModel()
        WithSimRouting(app) { launcher ->
            TolotraScreen(app = app, launcher = launcher, onOpenShare = {})
        }
    }

    @Test
    fun messages_section() = shoot("screen-messages") {
        val app: AppViewModel = viewModel()
        val phone: PhoneViewModel = viewModel()
        val messages: MessagesViewModel = viewModel()
        MessagesScreen(
            app = app,
            phone = phone,
            messages = messages,
            onOpenConversation = { _, _ -> },
            onOpenThread = {}
        )
    }

    @Test
    fun bulk_messages() = shoot("screen-bulk") {
        val app: AppViewModel = viewModel()
        val phone: PhoneViewModel = viewModel()
        val messages: MessagesViewModel = viewModel()
        BulkMessagesTab(app = app, phone = phone, messages = messages)
    }

    @Test
    fun contacts_tab() = shoot("screen-contacts", then = "Kontaka") {
        val app: AppViewModel = viewModel()
        val phone: PhoneViewModel = viewModel()
        WithSimRouting(app) { launcher ->
            PhoneScreen(app = app, phone = phone, launcher = launcher, onMessage = {})
        }
    }

    @Test
    fun bulk_from_messages() = shoot("screen-bulk-real", then = "Betsaka") {
        val app: AppViewModel = viewModel()
        val phone: PhoneViewModel = viewModel()
        val messages: MessagesViewModel = viewModel()
        MessagesScreen(
            app = app,
            phone = phone,
            messages = messages,
            onOpenConversation = { _, _ -> },
            onOpenThread = {}
        )
    }

    /** The one that may well refuse: it wants a camera. */
    @Test
    fun scan_section() = shoot("screen-scan") {
        val app: AppViewModel = viewModel()
        WithSimRouting(app) { launcher -> ScanScreen(app = app, launcher = launcher) }
    }
}
