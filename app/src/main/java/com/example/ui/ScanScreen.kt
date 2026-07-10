package com.example.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import androidx.camera.core.CameraSelector
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.DatabaseProvider
import com.example.data.model.Operator
import com.example.ui.util.OperatorColorUtils
import com.example.viewmodel.OperatorsViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.CheckCircle
import com.example.ui.util.SimManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

import com.example.ui.util.LanguageManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val viewModel: OperatorsViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return OperatorsViewModel(DatabaseProvider.getDatabase(context).operatorDao()) as T
            }
        }
    )
    val operators by viewModel.operators.collectAsState()
    
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    val currentLang by LanguageManager.currentLang

    var isLifecycleResumed by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isLifecycleResumed = true
            } else if (event == Lifecycle.Event.ON_PAUSE) {
                isLifecycleResumed = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        isLifecycleResumed = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        cameraPermissionState.launchPermissionRequest()
    }

    if (cameraPermissionState.status.isGranted) {
        CameraContent(operators)
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
fun CameraContent(operators: List<Operator>) {
    val currentOperators by rememberUpdatedState(operators)
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptic = LocalHapticFeedback.current
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    
    var selectedOperator by remember { mutableStateOf<Operator?>(null) }
    var scannedCode by remember { mutableStateOf("") }
    var isEditing by remember { mutableStateOf(false) }
    var torchEnabled by remember { mutableStateOf(false) }
    
    val prefs = remember { context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE) }
    var autoValidation by remember { mutableStateOf(prefs.getBoolean("auto_validation", false)) }
    val callPermissionState = rememberPermissionState(android.Manifest.permission.CALL_PHONE)
    val phoneStatePermissionState = rememberPermissionState(android.Manifest.permission.READ_PHONE_STATE)
    
    var dialedCodesHistory by remember { mutableStateOf(setOf<String>()) }
    var safetyWarning by remember { mutableStateOf<String?>(null) }
    
    // Cooldown/delay state (3 seconds pause after USSD execution)
    var isCooldownActive by remember { mutableStateOf(false) }
    var cooldownTimeLeft by remember { mutableStateOf(0) }
    val coroutineScope = rememberCoroutineScope()
    
    // Visual feedback for automatic operator detection
    var autoDetectedOperator by remember { mutableStateOf<Operator?>(null) }
    var showAutoDetectedMessageTimer by remember { mutableStateOf(0) }
    
    // Visual success state when a valid code is detected
    var showScanSuccessState by remember { mutableStateOf(false) }
    
    LaunchedEffect(scannedCode, selectedOperator) {
        val sanitized = scannedCode.replace(" ", "")
        val expectedLength = if (selectedOperator?.name?.equals("Airtel", ignoreCase = true) == true) 15 else 14
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
    
    // Common USSD execute function with 3s pause
    fun executeUssd(ussdCode: String, operatorName: String, codeToHistory: String) {
        if (isCooldownActive) return
        
        isCooldownActive = true
        cooldownTimeLeft = 3
        dialedCodesHistory = dialedCodesHistory + codeToHistory
        
        val intent = SimManager.createCallIntent(context, ussdCode, operatorName)
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(ussdCode)))
            context.startActivity(dialIntent)
        }
        
        coroutineScope.launch {
            while (cooldownTimeLeft > 0) {
                delay(1000)
                cooldownTimeLeft--
            }
            isCooldownActive = false
            scannedCode = "" // Clear the code and resume scanning only after the 3-second pause
        }
    }
    
    // Request permission to read active SIM card status
    LaunchedEffect(Unit) {
        if (!phoneStatePermissionState.status.isGranted) {
            phoneStatePermissionState.launchPermissionRequest()
        }
    }
    
    // Automatically select the first operator if available
    LaunchedEffect(operators) {
        if (selectedOperator == null && operators.isNotEmpty()) {
            selectedOperator = operators.first()
        }
    }
    
    // Automatically trigger dial/call with safety checks and SIM card routing
    LaunchedEffect(scannedCode, selectedOperator, autoValidation, isCooldownActive) {
        if (isCooldownActive) return@LaunchedEffect
        
        val sanitizedScannedCode = scannedCode.replace(" ", "")
        val expectedLength = if (selectedOperator?.name?.equals("Airtel", ignoreCase = true) == true) 15 else 14
        if (sanitizedScannedCode.length == expectedLength && selectedOperator != null) {
            val uniqueDigits = sanitizedScannedCode.toSet()
            if (uniqueDigits.size <= 3) {
                safetyWarning = LanguageManager.getString("suspicious_code")
                return@LaunchedEffect
            }
            
            if (dialedCodesHistory.contains(sanitizedScannedCode)) {
                safetyWarning = LanguageManager.getString("duplicate_code")
                return@LaunchedEffect
            }
            
            safetyWarning = null
            
            if (autoValidation) {
                val ussdCode = selectedOperator!!.template.replace("{CODE}", sanitizedScannedCode)
                executeUssd(ussdCode, selectedOperator!!.name, sanitizedScannedCode)
            }
        } else {
            safetyWarning = null
        }
    }
    
    val textRecognizer = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    val toneGenerator = remember { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100) }
    
    val cameraController = remember { LifecycleCameraController(context) }
    
    DisposableEffect(lifecycleOwner) {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            cameraController.apply {
                cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                setTapToFocusEnabled(true)
                setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
                setImageAnalysisAnalyzer(ContextCompat.getMainExecutor(context)) { imageProxy ->
                    if (isCooldownActive) {
                        imageProxy.close()
                        return@setImageAnalysisAnalyzer
                    }
                    
                    val mediaImage = imageProxy.image
                    if (mediaImage != null) {
                        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                        textRecognizer.process(image)
                            .addOnSuccessListener { visionText ->
                                // 1. Auto-detect operator from the frame text to minimize clicks!
                                val fullText = visionText.text.lowercase()
                                val detectedOp = currentOperators.find { op ->
                                    val opName = op.name.lowercase()
                                    fullText.contains(opName) ||
                                    (opName == "yas" && (fullText.contains("telma") || fullText.contains("mora") || fullText.contains("ye'low") || fullText.contains("yellow"))) ||
                                    (opName == "orange" && fullText.contains("benet")) ||
                                    (opName == "airtel" && fullText.contains("malaza"))
                                }
                                if (detectedOp != null && detectedOp != selectedOperator) {
                                    selectedOperator = detectedOp
                                    autoDetectedOperator = detectedOp
                                    showAutoDetectedMessageTimer = 3
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                                
                                // 2. Traverse through all text blocks and lines to find the code
                                val expectedLength = if (selectedOperator?.name?.equals("Airtel", ignoreCase = true) == true) 15 else 14
                                for (block in visionText.textBlocks) {
                                    for (line in block.lines) {
                                        // Precise regex matching exactly expectedLength digits with optional spacing, dots, or dashes
                                        val regexPattern = Regex("(?<!\\d)(?:\\d[\\s.-]*){$expectedLength}(?!\\d)")
                                        val match = regexPattern.find(line.text)
                                        if (match != null) {
                                            val code = match.value.filter { it.isDigit() }
                                            if (code.length == expectedLength) {
                                                if (scannedCode != code) {
                                                    scannedCode = code
                                                    toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP)
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    return@addOnSuccessListener
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            .addOnCompleteListener {
                                imageProxy.close()
                            }
                    } else {
                        imageProxy.close()
                    }
                }
                bindToLifecycle(lifecycleOwner)
            }
        }
        onDispose {
            if (hasPermission) {
                cameraController.unbind()
            }
        }
    }

    val operatorTheme = selectedOperator?.let { OperatorColorUtils.getOperatorTheme(it.name) }
    val brandColor = operatorTheme?.mainColor ?: MaterialTheme.colorScheme.primary
    val containerColor = operatorTheme?.containerColor ?: MaterialTheme.colorScheme.primaryContainer
    val accentColor = operatorTheme?.accentColor ?: MaterialTheme.colorScheme.onPrimary
    val unselectedColor = operatorTheme?.unselectedColor ?: MaterialTheme.colorScheme.outline

    // Laser scanning bar animation
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        // Step 1 Header with Language Selector
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = LanguageManager.getString("step_1_choose_operator"),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f)
            )
            LanguageManager.LanguageSelector()
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            items(operators) { operator ->
                val currentOpColor = OperatorColorUtils.getOperatorColor(operator.name)
                val isSelected = selectedOperator == operator
                
                Surface(
                    onClick = { 
                        selectedOperator = operator 
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) currentOpColor else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    contentColor = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    border = BorderStroke(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) currentOpColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
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

        // Check if SIM is present for the selected operator
        val isSimPresent = remember(selectedOperator, phoneStatePermissionState.status.isGranted) {
            selectedOperator?.let { op ->
                SimManager.isSimPresentForOperator(context, op.name)
            } ?: true
        }

        if (!isSimPresent && selectedOperator != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Warning",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = LanguageManager.getString("no_sim", "operator" to selectedOperator!!.name),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        // Auto-validation Toggle Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (autoValidation) brandColor.copy(alpha = 0.06f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            ),
            border = BorderStroke(
                width = 1.dp,
                color = if (autoValidation) brandColor.copy(alpha = 0.25f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
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
                        if (isChecked) {
                            if (!callPermissionState.status.isGranted) {
                                callPermissionState.launchPermissionRequest()
                            }
                        }
                        autoValidation = isChecked
                        prefs.edit().putBoolean("auto_validation", isChecked).apply()
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

        // Step 2 Header & Flash Button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = LanguageManager.getString("step_2_frame_digits"),
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

        // Camera Preview & Viewfinder Box
        val previewBorderColor = if (showScanSuccessState) Color(0xFF4CAF50) else brandColor.copy(alpha = 0.4f)
        val viewfinderBorderColor = if (showScanSuccessState) Color(0xFF4CAF50) else brandColor

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Color.Black)
                .border(2.dp, previewBorderColor, RoundedCornerShape(24.dp))
        ) {
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        controller = cameraController
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
            
            // Outer semi-transparent layer around viewfinder to focus eyes
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f))
            )
            
            // Auto-detected operator notification overlay
            if (autoDetectedOperator != null && !showScanSuccessState) {
                Surface(
                    color = brandColor,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp)
                        .padding(horizontal = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = LanguageManager.getString("operator_auto_detected", "operator" to autoDetectedOperator!!.name),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = accentColor
                        )
                    }
                }
            }

            // Dynamic Scanning Viewfinder Frame
            BoxWithConstraints(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(280.dp, 120.dp)
                    .background(Color.Black.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                    .border(2.dp, viewfinderBorderColor, RoundedCornerShape(16.dp))
            ) {
                val heightPx = maxHeight
                
                // Beautiful sweeping neon laser scanner - hide when scanning is on pause/cooldown or successful
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
                
                // Tech Viewfinder corner accents for scanner feel
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

            // Success Feedback Overlay
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
                        // Pulsing Success Checkmark
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
                                .background(Color(0xFF4CAF50).copy(alpha = 0.15f), RoundedCornerShape(48.dp))
                                .border(3.dp, Color(0xFF4CAF50), RoundedCornerShape(48.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Success",
                                tint = Color(0xFF4CAF50),
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
                            text = LanguageManager.getString("scan_success_sub"),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF81C784),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Code detected state display
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
                            clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(scannedCode))
                            android.widget.Toast.makeText(context, LanguageManager.getString("copy") + "!", android.widget.Toast.LENGTH_SHORT).show()
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
                    IconButton(
                        onClick = { isEditing = !isEditing },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Code",
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
                            contentDescription = "Clear Code",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
        
        if (safetyWarning != null) {
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
                        contentDescription = "Warning",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = safetyWarning!!,
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
                onValueChange = { 
                    if (it.all { char -> char.isDigit() } && it.length <= 16) {
                        scannedCode = it
                    }
                },
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                ),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
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
                    .padding(bottom = 16.dp),
                shape = RoundedCornerShape(16.dp),
                color = if (showScanSuccessState) Color(0xFFE8F5E9) else brandColor.copy(alpha = 0.08f),
                border = BorderStroke(
                    width = if (showScanSuccessState) 2.dp else 1.dp,
                    color = if (showScanSuccessState) Color(0xFF4CAF50) else brandColor.copy(alpha = 0.25f)
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp, horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val groupedCode = remember(scannedCode, selectedOperator) {
                        val expectedLength = if (selectedOperator?.name?.equals("Airtel", ignoreCase = true) == true) 15 else 14
                        if (scannedCode.length == expectedLength) {
                            "${scannedCode.substring(0, 4)} ${scannedCode.substring(4, 8)} ${scannedCode.substring(8, 12)} ${scannedCode.substring(12)}"
                        } else if (scannedCode.isNotEmpty()) {
                            scannedCode
                        } else {
                            if (expectedLength == 15) "•••• •••• •••• •••" else "•••• •••• •••• ••"
                        }
                    }
                    
                    Text(
                        text = groupedCode,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified
                        ),
                        color = if (scannedCode.isNotEmpty()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
        
        // Recharge dynamic Action Button
        Button(
            onClick = {
                selectedOperator?.let { operator ->
                    val sanitizedScannedCode = scannedCode.replace(" ", "")
                    val ussdCode = operator.template.replace("{CODE}", sanitizedScannedCode)
                    executeUssd(ussdCode, operator.name, sanitizedScannedCode)
                }
            },
            enabled = selectedOperator != null && scannedCode.replace(" ", "").length == (if (selectedOperator?.name?.equals("Airtel", ignoreCase = true) == true) 15 else 14) && !isCooldownActive,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .shadow(
                    elevation = if (selectedOperator != null && scannedCode.replace(" ", "").length == (if (selectedOperator?.name?.equals("Airtel", ignoreCase = true) == true) 15 else 14) && !isCooldownActive) 8.dp else 0.dp,
                    shape = RoundedCornerShape(16.dp),
                    ambientColor = brandColor,
                    spotColor = brandColor
                ),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = brandColor,
                contentColor = accentColor,
                disabledContainerColor = if (isCooldownActive) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                disabledContentColor = if (isCooldownActive) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = if (isCooldownActive) {
                        LanguageManager.getString("cooldown_active", "seconds" to cooldownTimeLeft)
                    } else if (selectedOperator != null) {
                        LanguageManager.getString("recharge_btn_text", "operator" to selectedOperator!!.name.uppercase())
                    } else {
                        LanguageManager.getString("recharge_btn_default")
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
