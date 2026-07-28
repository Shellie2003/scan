package com.example.ui.call

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.telephony.CallSnapshot
import com.example.telephony.NumberUtils
import com.example.telephony.OngoingCallStore
import com.example.ui.components.InitialsAvatar
import com.example.ui.theme.ScanTheme
import com.example.ui.theme.ThemeController
import com.example.ui.theme.scanColors
import com.example.ui.util.LanguageManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Full screen call UI. It is what makes the app usable as the phone app of the
 * device: the system hands the calls to [com.example.telephony.PhoneCallService],
 * which brings this screen up.
 */
class InCallActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()
        LanguageManager.init(this)
        ThemeController.init(this)
        setContent {
            ScanTheme {
                InCallScreen(onFinished = { finish() })
            }
        }
    }

    private fun showOverLockScreen() {
        // Whatever the version: the screen must not go dark in the middle of a
        // call, or hanging up means waking the phone up first.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
    }

    companion object {
        fun intent(context: Context): Intent =
            Intent(context, InCallActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

        fun start(context: Context) {
            try {
                context.startActivity(intent(context))
            } catch (e: Exception) {
                // Background activity start refused: the notification takes over.
            }
        }
    }
}

@Composable
fun InCallScreen(onFinished: () -> Unit) {
    val calls by OngoingCallStore.calls.collectAsState()
    val muted by OngoingCallStore.muted.collectAsState()
    val speakerOn by OngoingCallStore.speakerOn.collectAsState()
    var showKeypad by remember { mutableStateOf(false) }

    LaunchedEffect(calls) {
        if (calls.isEmpty()) {
            delay(600)
            onFinished()
        }
    }

    val call = calls.firstOrNull { !it.isDisconnected } ?: calls.firstOrNull()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(24.dp)
    ) {
        if (call == null) {
            Text(
                text = LanguageManager.getString("call_ended"),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.align(Alignment.Center)
            )
            return@Box
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(top = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            InitialsAvatar(
                text = call.label.take(1).uppercase().ifBlank { "#" },
                size = 96.dp
            )
            Spacer(Modifier.height(20.dp))
            Text(
                text = call.label.ifBlank { LanguageManager.getString("unknown_number") },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
            CallStatusLine(call)
            if (call.displayName.isNotBlank() && call.number.isNotBlank()) {
                Text(
                    text = NumberUtils.formatForDisplay(call.number),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (showKeypad && !call.isRinging) {
                DtmfPad(onDigit = { OngoingCallStore.playDtmf(call.id, it) })
                Spacer(Modifier.height(16.dp))
            }

            if (!call.isRinging) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    CallToggle(
                        icon = if (muted) Icons.Default.MicOff else Icons.Default.Mic,
                        label = LanguageManager.getString("mute"),
                        active = muted,
                        onClick = { OngoingCallStore.setMuted(!muted) }
                    )
                    CallToggle(
                        icon = Icons.Default.VolumeUp,
                        label = LanguageManager.getString("speaker"),
                        active = speakerOn,
                        onClick = { OngoingCallStore.setSpeaker(!speakerOn) }
                    )
                    CallToggle(
                        icon = if (call.isHeld) Icons.Default.PlayArrow else Icons.Default.Pause,
                        label = LanguageManager.getString("hold"),
                        active = call.isHeld,
                        onClick = { OngoingCallStore.toggleHold(call.id) }
                    )
                    CallToggle(
                        icon = Icons.Default.Dialpad,
                        label = LanguageManager.getString("keypad"),
                        active = showKeypad,
                        onClick = { showKeypad = !showKeypad }
                    )
                }
                Spacer(Modifier.height(28.dp))
            }

            if (call.isRinging) {
                SwipeToAnswer(
                    onAnswer = { OngoingCallStore.answer(call.id) },
                    onDecline = { OngoingCallStore.reject(call.id) },
                    modifier = Modifier.padding(bottom = 40.dp)
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 40.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    BigCallButton(
                        icon = Icons.Default.CallEnd,
                        color = MaterialTheme.scanColors.hangUp,
                        description = LanguageManager.getString("hang_up"),
                        onClick = { OngoingCallStore.hangUp(call.id) }
                    )
                }
            }
        }
    }
}

/**
 * Answering by sliding, the way a phone does it.
 *
 * The button sits in the middle and follows the finger: to the right to take
 * the call, to the left to refuse it. A press alone never answers — a phone
 * ringing in a pocket must not be picked up by a knee. The two ends stay
 * touchable for whoever cannot drag.
 */
@Composable
private fun SwipeToAnswer(
    onAnswer: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val travel = with(density) { 110.dp.toPx() }
    val offsetX = remember { Animatable(0f) }
    val progress = (offsetX.value / travel).coerceIn(-1f, 1f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(96.dp),
        contentAlignment = Alignment.Center
    ) {
        SwipeEnd(
            icon = Icons.Default.CallEnd,
            color = MaterialTheme.scanColors.hangUp,
            description = LanguageManager.getString("decline"),
            alpha = 0.4f + 0.6f * (-progress).coerceAtLeast(0f),
            onClick = onDecline,
            modifier = Modifier.align(Alignment.CenterStart)
        )
        SwipeEnd(
            icon = Icons.Default.Call,
            color = MaterialTheme.scanColors.success,
            description = LanguageManager.getString("answer"),
            alpha = 0.4f + 0.6f * progress.coerceAtLeast(0f),
            onClick = onAnswer,
            modifier = Modifier.align(Alignment.CenterEnd)
        )

        Text(
            text = LanguageManager.getString("swipe_to_answer"),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .graphicsLayer { alpha = 1f - kotlin.math.abs(progress) }
        )

        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .size(72.dp)
                .clip(CircleShape)
                .background(
                    when {
                        progress > 0.1f -> MaterialTheme.scanColors.success
                        progress < -0.1f -> MaterialTheme.scanColors.hangUp
                        else -> MaterialTheme.colorScheme.primary
                    }
                )
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        scope.launch {
                            offsetX.snapTo(
                                (offsetX.value + delta).coerceIn(-travel * 1.2f, travel * 1.2f)
                            )
                        }
                    },
                    onDragStopped = {
                        when {
                            offsetX.value >= travel -> onAnswer()
                            offsetX.value <= -travel -> onDecline()
                            else -> offsetX.animateTo(0f)
                        }
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Call,
                contentDescription = LanguageManager.getString("answer"),
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

/** One end of the slider: a target to aim at, and a button for who taps. */
@Composable
private fun SwipeEnd(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    description: String,
    alpha: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.20f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = color.copy(alpha = alpha.coerceIn(0f, 1f)),
            modifier = Modifier.size(26.dp)
        )
    }
}

@Composable
private fun CallStatusLine(call: CallSnapshot) {
    var elapsed by remember { mutableStateOf(0L) }

    LaunchedEffect(call.id, call.connectTimeMillis, call.state) {
        while (call.isActive && call.connectTimeMillis > 0) {
            elapsed = (System.currentTimeMillis() - call.connectTimeMillis) / 1000
            delay(1000)
        }
    }

    val text = when {
        call.isRinging -> LanguageManager.getString("incoming_call_label")
        call.isDialing -> LanguageManager.getString("calling")
        call.isHeld -> LanguageManager.getString("call_on_hold")
        call.isDisconnected -> LanguageManager.getString("call_ended")
        call.isActive && call.connectTimeMillis > 0 -> formatElapsed(elapsed)
        else -> ""
    }
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun CallToggle(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledIconButton(
            onClick = onClick,
            modifier = Modifier.size(56.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = if (active) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                contentColor = if (active) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        ) {
            Icon(imageVector = icon, contentDescription = label)
        }
        Spacer(Modifier.height(4.dp))
        Text(text = label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun BigCallButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    description: String,
    contentColor: Color = Color.White,
    onClick: () -> Unit
) {
    FilledIconButton(
        onClick = onClick,
        modifier = Modifier.size(72.dp),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = color,
            contentColor = contentColor
        )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            modifier = Modifier.size(32.dp)
        )
    }
}

@Composable
private fun DtmfPad(onDigit: (Char) -> Unit) {
    val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "*", "0", "#")
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            keys.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { key ->
                        FilledIconButton(
                            onClick = { onDigit(key.first()) },
                            modifier = Modifier
                                .size(56.dp)
                                .padding(4.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            )
                        ) {
                            Text(text = key, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

private fun formatElapsed(seconds: Long): String {
    val minutes = seconds / 60
    val rest = seconds % 60
    return "%02d:%02d".format(minutes, rest)
}
