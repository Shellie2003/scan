package com.example.ui

import android.Manifest
import android.content.Intent
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
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

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.Warning
import com.example.ui.util.SimManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

import com.example.ui.util.LanguageManager

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
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptic = LocalHapticFeedback.current
    
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
    LaunchedEffect(scannedCode, selectedOperator, autoValidation) {
        val sanitizedScannedCode = scannedCode.replace(" ", "")
        if (sanitizedScannedCode.length == 14 && selectedOperator != null) {
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
                dialedCodesHistory = dialedCodesHistory + sanitizedScannedCode
                
                val intent = SimManager.createCallIntent(context, ussdCode, selectedOperator!!.name)
                try {
                    context.startActivity(intent)
                    scannedCode = ""
                } catch (e: Exception) {
                    val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(ussdCode)))
                    context.startActivity(dialIntent)
                    scannedCode = ""
                }
            }
        } else {
            safetyWarning = null
        }
    }
    
    val textRecognizer = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    val toneGenerator = remember { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100) }
    
    val cameraController = remember { LifecycleCameraController(context) }

    DisposableEffect(lifecycleOwner) {
        cameraController.apply {
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            setTapToFocusEnabled(true)
            setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
            setImageAnalysisAnalyzer(ContextCompat.getMainExecutor(context)) { imageProxy ->
                val mediaImage = imageProxy.image
                if (mediaImage != null) {
                    val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                    textRecognizer.process(image)
                        .addOnSuccessListener { visionText ->
                            // Traverse through all text blocks and lines to find the code
                            for (block in visionText.textBlocks) {
                                for (line in block.lines) {
                                    // Remove common separators (spaces, dots, hyphens, slashes) to find contiguous digits
                                    val cleaned = line.text.filter { it.isDigit() }
                                    
                                    // Precise regex matching exactly 14 digits (not part of longer serial numbers)
                                    val regex = Regex("(?<!\\d)\\d{14}(?!\\d)")
                                    val match = regex.find(cleaned)
                                    if (match != null) {
                                        val code = match.value
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
                        .addOnCompleteListener {
                            imageProxy.close()
                        }
                } else {
                    imageProxy.close()
                }
            }
            bindToLifecycle(lifecycleOwner)
        }
        onDispose {
            cameraController.unbind()
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
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Color.Black)
                .border(2.dp, brandColor.copy(alpha = 0.4f), RoundedCornerShape(24.dp))
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

            // Dynamic Scanning Viewfinder Frame
            BoxWithConstraints(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(280.dp, 120.dp)
                    .background(Color.Black.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                    .border(2.dp, brandColor, RoundedCornerShape(16.dp))
            ) {
                val heightPx = maxHeight
                
                // Beautiful sweeping neon laser scanner
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
                color = brandColor.copy(alpha = 0.08f),
                border = BorderStroke(1.dp, brandColor.copy(alpha = 0.25f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp, horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val groupedCode = remember(scannedCode) {
                        if (scannedCode.length == 14) {
                            "${scannedCode.substring(0, 4)} ${scannedCode.substring(4, 8)} ${scannedCode.substring(8, 12)} ${scannedCode.substring(12)}"
                        } else if (scannedCode.isNotEmpty()) {
                            scannedCode
                        } else {
                            "•••• •••• •••• ••"
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
                    
                    dialedCodesHistory = dialedCodesHistory + sanitizedScannedCode
                    val intent = SimManager.createCallIntent(context, ussdCode, operator.name)
                    try {
                        context.startActivity(intent)
                        scannedCode = ""
                    } catch (e: Exception) {
                        val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(ussdCode)))
                        context.startActivity(dialIntent)
                        scannedCode = ""
                    }
                }
            },
            enabled = selectedOperator != null && scannedCode.length == 14,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .shadow(
                    elevation = if (selectedOperator != null && scannedCode.length == 14) 8.dp else 0.dp,
                    shape = RoundedCornerShape(16.dp),
                    ambientColor = brandColor,
                    spotColor = brandColor
                ),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = brandColor,
                contentColor = accentColor,
                disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = if (selectedOperator != null) LanguageManager.getString("recharge_btn_text", "operator" to selectedOperator!!.name.uppercase()) else LanguageManager.getString("recharge_btn_default"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
