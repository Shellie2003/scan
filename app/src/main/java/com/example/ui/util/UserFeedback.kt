package com.example.ui.util

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * What the application says when something did not work.
 *
 * An operation that fails in silence is worse than one that fails loudly: the
 * user presses the same button again, decides the application is broken, and is
 * right. Everything that can fail far from the screen — a line of history the
 * database refused to delete, a star the contacts provider would not write, a
 * code that never reached the operator — reports here, and the root of the
 * application turns it into one line on the screen, whichever page is open.
 *
 * Deliberately a channel and not a state: a message is shown once. It is
 * dropped rather than queued when nobody is listening, because an error from a
 * minute ago, shown on a screen the user has since left, explains nothing.
 */
object UserFeedback {

    private val _messages = MutableSharedFlow<String>(
        replay = 0,
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    val messages = _messages.asSharedFlow()

    /** Reports a failure, by translation key. */
    fun failed(key: String) {
        say(LanguageManager.getString(key))
    }

    fun say(text: String) {
        if (text.isBlank()) return
        _messages.tryEmit(text)
    }

    /**
     * Runs something that touches the database or a provider, and says so when
     * it does not work.
     *
     * Returns whether it went through, for the callers that have something
     * better to do with the answer than show it.
     */
    inline fun <T> attempt(key: String, block: () -> T): T? =
        try {
            block()
        } catch (e: Exception) {
            failed(key)
            null
        }
}

/**
 * Shows what [UserFeedback] reports, wherever the user happens to be.
 *
 * Placed once at the root: a Toast needs no room on the screen and survives the
 * page changing under it, which a snackbar tied to one Scaffold does not.
 */
@Composable
fun UserFeedbackHost() {
    val context: Context = LocalContext.current
    LaunchedEffect(Unit) {
        UserFeedback.messages.collect { text ->
            try {
                Toast.makeText(context, text, Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                // No window: the message is lost, which is better than a crash.
            }
        }
    }
}
