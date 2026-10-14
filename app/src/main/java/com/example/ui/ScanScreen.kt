package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.camera.core.CameraSelector
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.AppSettings
import com.example.data.model.Operator
import com.example.data.model.ScanHistory
import com.example.telephony.UssdFormatter
import com.example.ui.components.SimIndicator
import com.example.ui.components.SimLauncher
import com.example.ui.components.SimPickerDialog
import com.example.ui.theme.scanColors
import com.example.ui.theme.ScanTextStyles
import com.example.ui.util.LanguageManager
import com.example.ui.util.OperatorColorUtils
import com.example.viewmodel.AppViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ScanScreen(app: AppViewModel, launcher: SimLauncher) {
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    val currentLang by LanguageManager.currentLang
    val operators by app.operators.collectAsState()

    LaunchedEffect(Unit) {
        cameraPermissionState.launchPermissionRequest()
    }

    if (cameraPermissionState.status.isGranted) {
        CameraContent(app = app, launcher = launcher, operators = operators)
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = LanguageManager.getString("camera_required"),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = LanguageManager.getString("camera_required_desc"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = { cameraPermissionState.launchPermissionRequest() }) {
                    Text(LanguageManager.getString("allow_access"))
                }
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CameraContent(
    app: AppViewModel,
    launcher: SimLauncher,
    operators: List<Operator>
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptic = LocalHapticFeedback.current
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    val sims by app.sims.collectAsState()

    var selectedOperator by remember { mutableStateOf<Operator?>(null) }
    var scannedCode by remember { mutableStateOf("") }
    var isEditing by remember { mutableStateOf(false) }
    var torchEnabled by remember { mutableStateOf(false) }
    var showSimPicker by remember { mutableStateOf(false) }

    var autoValidation by remember { mutableStateOf(AppSettings.autoValidation(context)) }
    val callPermissionState = rememberPermissionState(Manifest.permission.CALL_PHONE)
    val phoneStatePermissionState = rememberPermissionState(Manifest.permission.READ_PHONE_STATE)

    var dialedCodesHistory by remember { mutableStateOf(setOf<String>()) }
    var safetyWarning by remember { mutableStateOf<String?>(null) }

    var isCooldownActive by remember { mutableStateOf(false) }
    var cooldownTimeLeft by remember { mutableStateOf(0) }

    var autoDetectedOperator by remember { mutableStateOf<Operator?>(null) }
    var showAutoDetectedMessageTimer by remember { mutableStateOf(0) }
    var showScanSuccessState by remember { mutableStateOf(false) }

    // Number of digits printed on the ticket, taken from the operator settings.
    val expectedLength = selectedOperator?.codeLength ?: 14
    val routing = remember(selectedOperator, sims) { app.routeFor(selectedOperator) }

    LaunchedEffect(scannedCode, expectedLength) {
        val sanitized = scannedCode.replace(" ", "")
        if (sanitized.length == expectedLength) {
            val uniqueDigits = sanitized.toSet()
            if (uniqueDigits.size > 3 && !dialedCodesHistory.contains(sanitized)) {
                showScanSuccessState = true
                delay(1500)
                showScanSuccessState = false
            }
        }
    }

    LaunchedEffect(showAutoDetectedMessageTimer) {
        if (showAutoDetectedMessageTimer > 0) {
            delay(1000)
            showAutoDetectedMessageTimer--
            if (showAutoDetectedMessageTimer == 0) {
                autoDetectedOperator = null
            }
        }
    }

    fun executeRecharge(code: String) {
        val operator = selectedOperator ?: return
        if (isCooldownActive) return

        isCooldownActive = true
        cooldownTimeLeft = 3
        dialedCodesHistory = dialedCodesHistory + code

        launcher.runCode(
            code = UssdFormatter.format(
                UssdFormatter.fill(operator.template, code = code),
                operator
            ),
            operator = operator,
            kind = ScanHistory.KIND_RECHARGE,
            label = LanguageManager.getString("recharge") + " " + operator.name,
            rawCode = code
        )

        coroutineScope.launch {
            while (cooldownTimeLeft > 0) {
                delay(1000)
                cooldownTimeLeft--
            }
            isCooldownActive = false
            scannedCode = ""
        }
    }

    LaunchedEffect(Unit) {
        if (!phoneStatePermissionState.status.isGranted) {
            phoneStatePermissionState.launchPermissionRequest()
        }
    }

    LaunchedEffect(operators) {
        if (selectedOperator == null && operators.isNotEmpty()) {
            selectedOperator = operators.first()
        } else {
            selectedOperator = operators.firstOrNull { it.id == selectedOperator?.id }
                ?: operators.firstOrNull()
        }
    }

    LaunchedEffect(scannedCode, selectedOperator, autoValidation, isCooldownActive) {
        if (isCooldownActive) return@LaunchedEffect

        val sanitized = scannedCode.replace(" ", "")
        if (sanitized.length == expectedLength && selectedOperator != null) {
            if (sanitized.toSet().size <= 3) {
                safetyWarning = LanguageManager.getString("suspicious_code")
                return@LaunchedEffect
            }
            if (dialedCodesHistory.contains(sanitized) || app.wasUsedRecently(sanitized)) {
                safetyWarning = LanguageManager.getString("duplicate_code")
                return@LaunchedEffect
            }
            safetyWarning = null
            if (autoValidation) executeRecharge(sanitized)
        } else {
            safetyWarning = null
        }
    }

    val textRecognizer = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    // The audio service refuses to hand out a tone generator when it is busy;
    // a beep is not worth crashing the scanner for.
    val toneGenerator = remember {
        runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100) }.getOrNull()
    }
    val cameraController = remember { LifecycleCameraController(context) }
    // Every frame used to be analysed on the thread that draws the screen.
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

    // The keywords of an operator are stored as one string and split on every
    // read: doing that per operator and per frame was pure garbage collection.
    val keywordIndex by rememberUpdatedState(
        remember(operators) { operators.map { it to it.keywordList } }
    )

    // Registered before the camera effect on purpose: effects are disposed in
    // reverse order, so the camera is unbound before the executor stops.
    DisposableEffect(Unit) {
        onDispose {
            // Native resources: a text recogniser and a tone generator that are
            // never released are leaked for the lifetime of the process.
            runCatching { textRecognizer.close() }
            runCatching { toneGenerator?.release() }
            analysisExecutor.shutdown()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            cameraController.apply {
                cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                setTapToFocusEnabled(true)
                setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
                setImageAnalysisAnalyzer(analysisExecutor) { imageProxy ->
                    if (isCooldownActive) {
                        imageProxy.close()
                        return@setImageAnalysisAnalyzer
                    }

                    val mediaImage = imageProxy.image
                    if (mediaImage != null) {
                        val image = InputImage.fromMediaImage(
                            mediaImage,
                            imageProxy.imageInfo.rotationDegrees
                        )
                        textRecognizer.process(image)
                            .addOnSuccessListener { visionText ->
                                // 1. Recognise the operator from the words printed on the ticket,
                                //    using the keywords stored with each operator.
                                val fullText = visionText.text.lowercase()
                                val detectedOp = keywordIndex.firstOrNull { (_, keywords) ->
                                    keywords.any { it.isNotBlank() && fullText.contains(it) }
                                }?.first
                                if (detectedOp != null && detectedOp.id != selectedOperator?.id) {
                                    selectedOperator = detectedOp
                                    autoDetectedOperator = detectedOp
                                    showAutoDetectedMessageTimer = 3
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                }

                                // 2. Look for a group of exactly the expected number of digits.
                                val length = selectedOperator?.codeLength ?: 14
                                val regex = Regex("(?<!\\d)(?:\\d[\\s.-]*){$length}(?!\\d)")
                                for (block in visionText.textBlocks) {
                                    for (line in block.lines) {
                                        val match = regex.find(line.text)
                                        if (match != null) {
                                            val code = match.value.filter { it.isDigit() }
                                            if (code.length == length && scannedCode != code) {
                                                scannedCode = code
                                                toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP)
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                return@addOnSuccessListener
                                            }
                                        }
                                    }
                                }
                            }
                            .addOnCompleteListener { imageProxy.close() }
                    } else {
                        imageProxy.close()
                    }
                }
                bindToLifecycle(lifecycleOwner)
            }
        }
        onDispose {
            if (hasPermission) cameraController.unbind()
        }
    }

    val theme = OperatorColorUtils.themeOf(selectedOperator)
    val brandColor = theme.mainColor
    val accentColor = theme.accentColor

    val infiniteTransition = rememberInfiniteTransition(label = "laser")
    val laserYFraction by infiniteTransition.animateFloat(
        initialValue = 0.05f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laserPosition"
    )

    // The whole page scrolls. On a small screen — the phones this app is made
    // for — the operator row, the switch, the preview and the recharge button
    // do not fit together: the preview used to be squeezed down to nothing and
    // the reading frame was simply not on the screen any more.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = LanguageManager.getString("step_1_choose_operator"),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        ) {
            items(operators) { operator ->
                val operatorColor = OperatorColorUtils.colorOf(operator)
                val isSelected = selectedOperator?.id == operator.id
                Surface(
                    onClick = {
                        selectedOperator = operator
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) operatorColor else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    contentColor = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    border = BorderStroke(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) operatorColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier.height(44.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = operator.name,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Which SIM card the recharge will be sent from — tap to change, which is
        // what makes several cards of the same operator usable safely.
        SimIndicator(
            routing = routing,
            color = brandColor,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            onClick = { showSimPicker = true }
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (autoValidation) {
                    brandColor.copy(alpha = 0.06f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                }
            ),
            border = BorderStroke(
                width = 1.dp,
                color = if (autoValidation) {
                    brandColor.copy(alpha = 0.25f)
                } else {
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
                }
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (autoValidation) Icons.Default.FlashOn else Icons.Default.FlashOff,
                        contentDescription = null,
                        tint = if (autoValidation) brandColor else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = LanguageManager.getString("auto_validation"),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (autoValidation) brandColor else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = LanguageManager.getString("auto_validation_short_desc"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                }
                Switch(
                    checked = autoValidation,
                    onCheckedChange = { isChecked ->
                        if (isChecked && !callPermissionState.status.isGranted) {
                            callPermissionState.launchPermissionRequest()
                        }
                        autoValidation = isChecked
                        AppSettings.setAutoValidation(context, isChecked)
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = accentColor,
                        checkedTrackColor = brandColor,
                        uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = LanguageManager.getString(
                    "step_2_frame_digits_n",
                    "count" to expectedLength
                ),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            IconButton(
                onClick = {
                    torchEnabled = !torchEnabled
                    cameraController.enableTorch(torchEnabled)
                },
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = if (torchEnabled) brandColor else MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Icon(
                    imageVector = if (torchEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                    contentDescription = LanguageManager.getString("flash"),
                    tint = if (torchEnabled) accentColor else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        val cameraSuccess = MaterialTheme.scanColors.cameraSuccess
        val previewBorderColor = if (showScanSuccessState) cameraSuccess else brandColor.copy(alpha = 0.4f)
        val viewfinderBorderColor = if (showScanSuccessState) cameraSuccess else brandColor

        Box(
            modifier = Modifier
                // A height of its own, not what is left over: inside a scrolling
                // page there is no leftover, and a preview must have a size the
                // ticket can be held up to.
                .height(320.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Color.Black)
                .border(2.dp, previewBorderColor, RoundedCornerShape(24.dp))
        ) {
            AndroidView(
                factory = { ctx -> PreviewView(ctx).apply { controller = cameraController } },
                modifier = Modifier.fillMaxSize()
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f))
            )

            val detected = autoDetectedOperator
            if (detected != null && !showScanSuccessState) {
                val detectedOperatorName = detected.name
                Surface(
                    color = brandColor,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp)
                        .padding(horizontal = 16.dp)
                ) {
                    Text(
                        text = LanguageManager.getString(
                            "operator_auto_detected",
                            "operator" to detectedOperatorName
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = accentColor,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            BoxWithConstraints(
                modifier = Modifier
                    .align(Alignment.Center)
                    // Measured against the preview, not in fixed millimetres: on
                    // a 320 dp screen a 280 dp frame touched both borders.
                    .fillMaxWidth(0.86f)
                    .height(120.dp)
                    .background(Color.Black.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                    .border(2.dp, viewfinderBorderColor, RoundedCornerShape(16.dp))
            ) {
                val heightPx = maxHeight

                if (!isCooldownActive && !showScanSuccessState) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .offset(y = heightPx * laserYFraction)
                            .background(brandColor)
                            .shadow(
                                elevation = 8.dp,
                                shape = RoundedCornerShape(1.dp),
                                ambientColor = brandColor,
                                spotColor = brandColor,
                                clip = false
                            )
                    )
                }

                Text(
                    text = LanguageManager.getString("reading_frame"),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 8.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = showScanSuccessState,
                enter = fadeIn() + scaleIn(initialScale = 0.8f),
                exit = fadeOut() + scaleOut(targetScale = 1.2f),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.75f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        val pulseTransition = rememberInfiniteTransition(label = "successPulse")
                        val pulseScale by pulseTransition.animateFloat(
                            initialValue = 0.9f,
                            targetValue = 1.15f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(500, easing = FastOutSlowInEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "pulseScale"
                        )

                        Box(
                            modifier = Modifier
                                .size(96.dp)
                                .graphicsLayer {
                                    scaleX = pulseScale
                                    scaleY = pulseScale
                                }
                                .background(cameraSuccess.copy(alpha = 0.15f), RoundedCornerShape(48.dp))
                                .border(3.dp, cameraSuccess, RoundedCornerShape(48.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = cameraSuccess,
                                modifier = Modifier.size(64.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = LanguageManager.getString("scan_success_msg"),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = LanguageManager.getString(
                                "scan_success_sub_n",
                                "count" to expectedLength
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.scanColors.cameraSuccessSoft,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = LanguageManager.getString("detected_recharge_code"),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = brandColor
            )

            if (scannedCode.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = {
                            clipboardManager.setText(
                                androidx.compose.ui.text.AnnotatedString(scannedCode)
                            )
                            android.widget.Toast.makeText(
                                context,
                                LanguageManager.getString("copied"),
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = LanguageManager.getString("copy"),
                            tint = brandColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(onClick = { isEditing = !isEditing }, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = LanguageManager.getString("edit"),
                            tint = brandColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = {
                            scannedCode = ""
                            isEditing = false
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        val warning = safetyWarning
        if (warning != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = warning,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        if (isEditing) {
            OutlinedTextField(
                value = scannedCode,
                onValueChange = { value ->
                    if (value.all { it.isDigit() } && value.length <= expectedLength + 2) {
                        scannedCode = value
                    }
                },
                textStyle = ScanTextStyles.code,
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                placeholder = { Text(LanguageManager.getString("type_code_manually")) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = brandColor,
                    cursorColor = brandColor
                )
            )
        } else {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                shape = RoundedCornerShape(16.dp),
                color = if (showScanSuccessState) {
                    MaterialTheme.scanColors.successContainer.copy(alpha = 0.45f)
                } else {
                    brandColor.copy(alpha = 0.08f)
                },
                border = BorderStroke(
                    width = if (showScanSuccessState) 2.dp else 1.dp,
                    color = if (showScanSuccessState) {
                        MaterialTheme.scanColors.success
                    } else {
                        brandColor.copy(alpha = 0.25f)
                    }
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp, horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val groupedCode = remember(scannedCode, expectedLength) {
                        when {
                            scannedCode.length == expectedLength -> scannedCode.chunked(4).joinToString(" ")
                            scannedCode.isNotEmpty() -> scannedCode
                            else -> "•".repeat(expectedLength).chunked(4).joinToString(" ")
                        }
                    }
                    Text(
                        text = groupedCode,
                        style = ScanTextStyles.codeLarge,
                        color = if (scannedCode.isNotEmpty()) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        },
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        val canRecharge = selectedOperator != null &&
            scannedCode.replace(" ", "").length == expectedLength &&
            !isCooldownActive

        Button(
            onClick = { executeRecharge(scannedCode.replace(" ", "")) },
            enabled = canRecharge,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .shadow(
                    elevation = if (canRecharge) 8.dp else 0.dp,
                    shape = RoundedCornerShape(16.dp),
                    ambientColor = brandColor,
                    spotColor = brandColor
                ),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = brandColor,
                contentColor = accentColor,
                disabledContainerColor = if (isCooldownActive) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                },
                disabledContentColor = if (isCooldownActive) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                }
            )
        ) {
            val operatorName = selectedOperator?.name
            Text(
                text = when {
                    isCooldownActive -> LanguageManager.getString(
                        "cooldown_active",
                        "seconds" to cooldownTimeLeft
                    )
                    operatorName != null -> LanguageManager.getString(
                        "recharge_btn_text",
                        "operator" to operatorName.uppercase()
                    )
                    else -> LanguageManager.getString("recharge_btn_default")
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }

    if (showSimPicker) {
        SimPickerDialog(
            sims = routing.candidates.ifEmpty { sims },
            operator = selectedOperator,
            selected = routing.sim,
            onDismiss = { showSimPicker = false },
            onPick = { sim, save ->
                showSimPicker = false
                if (save) app.setPreferredSim(selectedOperator, sim)
            }
        )
    }
}
