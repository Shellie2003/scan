package com.example

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
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
 * mode, at the size of a Pixel 8. What comes out is what Compose produces from
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
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class RealScreenshotsTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private fun shoot(name: String, content: @Composable () -> Unit) {
        rule.setContent {
            LanguageManager.init(LocalContext.current)
            LanguageManager.setLanguageForTest("mg")
            ScanTheme(darkTheme = false, dynamicColor = false) { content() }
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

    /** The one that may well refuse: it wants a camera. */
    @Test
    fun scan_section() = shoot("screen-scan") {
        val app: AppViewModel = viewModel()
        WithSimRouting(app) { launcher -> ScanScreen(app = app, launcher = launcher) }
    }
}
