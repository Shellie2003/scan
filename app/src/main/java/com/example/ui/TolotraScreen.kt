package com.example.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.Offer
import com.example.ui.util.OperatorColorUtils
import com.example.ui.util.LanguageManager
import com.example.ui.util.SimManager
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun TolotraScreen() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE) }
    val autoValidation = remember { prefs.getBoolean("auto_validation", false) }
    val callPermissionState = rememberPermissionState(Manifest.permission.CALL_PHONE)
    val phoneStatePermissionState = rememberPermissionState(Manifest.permission.READ_PHONE_STATE)
    val currentLang by LanguageManager.currentLang
    
    LaunchedEffect(Unit) {
        if (!phoneStatePermissionState.status.isGranted) {
            phoneStatePermissionState.launchPermissionRequest()
        }
    }
    
    val operators = listOf("Yas", "Orange", "Airtel")
    var selectedOperator by remember { mutableStateOf(operators[0]) }
    
    val categories = listOf("Internet", "Appel", "SMS")
    var selectedCategory by remember(selectedOperator) { mutableStateOf(categories[0]) }

    val operatorTheme = OperatorColorUtils.getOperatorTheme(selectedOperator)

    // Real-world plan codes for Madagascar operators
    val yasOffers = listOf(
        // Internet (formerly Ye'low)
        Offer("Net Mora 500", "500 Ar TTC", "130 Mo d'internet", "24 h", "#322*65#", "Yas", "Internet"),
        Offer("Net One 1000", "1 000 Ar TTC", "1 Go d'internet", "24 h", "#322*64#", "Yas", "Internet"),
        Offer("Net Mora 2000", "2 000 Ar TTC", "450 Mo d'internet", "48 h", "#322*68#", "Yas", "Internet"),
        Offer("Net Mora 2500", "2 500 Ar TTC", "2.4 Go d'internet", "24 h", "#322*69#", "Yas", "Internet"),
        Offer("Net Mora 5000", "5 000 Ar TTC", "3 Go d'internet", "7 j", "#322*70#", "Yas", "Internet"),
        
        // Appel (Mora Mora)
        Offer("Mora 500", "500 Ar TTC", "Appels nationaux Mora", "24 h", "#322*20#", "Yas", "Appel"),
        Offer("Mora 1000", "1 000 Ar TTC", "Appels nationaux + 40 Mo + 40 SMS", "1 j", "#322*21#", "Yas", "Appel"),
        Offer("Mora +2000", "2 000 Ar TTC", "Appels nationaux + 100 Mo + 50 SMS", "2 j", "#322*32#", "Yas", "Appel"),
        Offer("Mora +5000", "5 000 Ar TTC", "Appels nationaux + 200 Mo + 100 SMS", "7 j", "#322*35#", "Yas", "Appel"),
        Offer("International 5000", "5 000 Ar TTC", "15 min d'appels internationaux", "24 h", "#322*52#", "Yas", "Appel"),
        
        // SMS
        Offer("Yellow SMS 200", "200 Ar TTC", "125 SMS", "24 h", "#322*62#", "Yas", "SMS"),
        Offer("Yellow SMS 500", "500 Ar TTC", "400 SMS", "3 j", "#322*63#", "Yas", "SMS")
    )
    
    val orangeOffers = listOf(
        // Internet (BeNet)
        Offer("BeNet 500", "500 Ar TTC", "150 Mo d'internet BeNet", "24 h", "#202*1*1#", "Orange", "Internet"),
        Offer("BeNet 1000", "1 000 Ar TTC", "400 Mo d'internet BeNet", "24 h", "#202*1*2#", "Orange", "Internet"),
        Offer("BeNet 2000", "2 000 Ar TTC", "1 Go d'internet BeNet", "2 j", "#202*1*3#", "Orange", "Internet"),
        Offer("BeNet 5000", "5 000 Ar TTC", "3 Go d'internet BeNet", "7 j", "#202*1*4#", "Orange", "Internet"),
        Offer("BeNet Max", "10 000 Ar TTC", "7 Go d'internet BeNet", "30 j", "#202*1*5#", "Orange", "Internet"),
        
        // Appel (BeTek)
        Offer("BeTek 500", "500 Ar TTC", "15 min d'appels nationaux", "24 h", "#202*2*1#", "Orange", "Appel"),
        Offer("BeTek 1000", "1 000 Ar TTC", "35 min d'appels nationaux", "24 h", "#202*2*2#", "Orange", "Appel"),
        Offer("BeTek 2000", "2 000 Ar TTC", "80 min d'appels nationaux", "48 h", "#202*2*3#", "Orange", "Appel"),
        Offer("BeTek 5000", "5 000 Ar TTC", "250 min d'appels nationaux", "7 j", "#202*2*4#", "Orange", "Appel"),
        
        // SMS (SMS Free)
        Offer("SMS Free 200", "200 Ar TTC", "100 SMS tout opérateur", "24 h", "#202*3*1#", "Orange", "SMS"),
        Offer("SMS Free 500", "500 Ar TTC", "300 SMS tout opérateur", "3 j", "#202*3*2#", "Orange", "SMS")
    )
    
    val airtelOffers = listOf(
        // Internet (Airtel Net)
        Offer("Net 500", "500 Ar TTC", "100 Mo d'internet Airtel", "24 h", "*100*1*1#", "Airtel", "Internet"),
        Offer("Net 1000", "1 000 Ar TTC", "300 Mo d'internet Airtel", "24 h", "*100*1*2#", "Airtel", "Internet"),
        Offer("Net 2000", "2 000 Ar TTC", "800 Mo d'internet Airtel", "2 j", "*100*1*3#", "Airtel", "Internet"),
        Offer("Net 5000", "5 000 Ar TTC", "2.5 Go d'internet Airtel", "7 j", "*100*1*4#", "Airtel", "Internet"),
        
        // Appel (Airtel Malaza)
        Offer("Malaza 500", "500 Ar TTC", "10 min d'appels nationaux", "24 h", "*100*2*1#", "Airtel", "Appel"),
        Offer("Malaza 1000", "1 000 Ar TTC", "25 min d'appels nationaux", "24 h", "*100*2*2#", "Airtel", "Appel"),
        Offer("Malaza 2000", "2 000 Ar TTC", "60 min d'appels nationaux", "48 h", "*100*2*3#", "Airtel", "Appel"),
        Offer("Malaza 5000", "5 000 Ar TTC", "180 min d'appels nationaux", "7 j", "*100*2*4#", "Airtel", "Appel"),
        
        // SMS
        Offer("Airtel SMS 200", "200 Ar TTC", "80 SMS nationaux", "24 h", "*100*3*1#", "Airtel", "SMS"),
        Offer("Airtel SMS 500", "500 Ar TTC", "250 SMS nationaux", "3 j", "*100*3*2#", "Airtel", "SMS")
    )

    val offersMap = mapOf("Yas" to yasOffers, "Orange" to orangeOffers, "Airtel" to airtelOffers)

    val categoryDisplayNames = mapOf(
        "Internet" to LanguageManager.getString("plan_internet"),
        "Appel" to LanguageManager.getString("plan_appel"),
        "SMS" to LanguageManager.getString("plan_sms")
    )

    Column(modifier = Modifier.fillMaxSize()) {
        // Top row with Screen title and Language Selector
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = LanguageManager.getString("nav_offers"),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = operatorTheme.mainColor
            )
            LanguageManager.LanguageSelector()
        }

        TabRow(
            selectedTabIndex = operators.indexOf(selectedOperator),
            containerColor = operatorTheme.mainColor,
            contentColor = operatorTheme.accentColor
        ) {
            operators.forEach { operator ->
                Tab(
                    selected = selectedOperator == operator,
                    onClick = { selectedOperator = operator },
                    text = { Text(operator, fontWeight = FontWeight.Bold) },
                    selectedContentColor = operatorTheme.accentColor,
                    unselectedContentColor = operatorTheme.unselectedColor
                )
            }
        }

        TabRow(
            selectedTabIndex = categories.indexOf(selectedCategory),
            containerColor = operatorTheme.containerColor,
            contentColor = operatorTheme.accentColor
        ) {
            categories.forEach { category ->
                Tab(
                    selected = selectedCategory == category,
                    onClick = { selectedCategory = category },
                    text = { Text(categoryDisplayNames[category] ?: category, fontWeight = FontWeight.Medium) },
                    selectedContentColor = operatorTheme.accentColor,
                    unselectedContentColor = operatorTheme.unselectedColor
                )
            }
        }
        
        // Check if SIM is present for the selected operator
        val isSimPresent = remember(selectedOperator, phoneStatePermissionState.status.isGranted) {
            SimManager.isSimPresentForOperator(context, selectedOperator)
        }

        if (!isSimPresent) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
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
                        imageVector = Icons.Default.Info,
                        contentDescription = "Warning",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = LanguageManager.getString("no_sim", "operator" to selectedOperator),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
        
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp)
        ) {
            items(offersMap[selectedOperator]?.filter { it.category == selectedCategory } ?: emptyList()) { offer ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .border(
                            width = 1.dp,
                            color = operatorTheme.mainColor.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable {
                            if (autoValidation && !callPermissionState.status.isGranted) {
                                callPermissionState.launchPermissionRequest()
                            } else {
                                val useDirectCall = autoValidation && callPermissionState.status.isGranted
                                val intent = if (useDirectCall) {
                                    SimManager.createCallIntent(context, offer.ussdCode, selectedOperator)
                                } else {
                                    Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(offer.ussdCode)))
                                }
                                try {
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(offer.ussdCode)))
                                    context.startActivity(dialIntent)
                                }
                            }
                        },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = offer.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = operatorTheme.mainColor
                            )
                            
                            Box(
                                modifier = Modifier
                                    .background(
                                        color = operatorTheme.mainColor.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = categoryDisplayNames[offer.category] ?: offer.category,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = operatorTheme.mainColor
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = offer.price,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            
                            if (offer.validity.isNotEmpty()) {
                                Text(
                                    text = offer.validity,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = offer.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = LanguageManager.getString("details"),
                                    tint = operatorTheme.mainColor,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = LanguageManager.getString("details"),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = operatorTheme.mainColor,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            
                            Button(
                                onClick = {
                                    if (autoValidation && !callPermissionState.status.isGranted) {
                                        callPermissionState.launchPermissionRequest()
                                    } else {
                                        val useDirectCall = autoValidation && callPermissionState.status.isGranted
                                        val intent = if (useDirectCall) {
                                            SimManager.createCallIntent(context, offer.ussdCode, selectedOperator)
                                        } else {
                                            Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(offer.ussdCode)))
                                        }
                                        try {
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(offer.ussdCode)))
                                            context.startActivity(dialIntent)
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = operatorTheme.mainColor,
                                    contentColor = operatorTheme.accentColor
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Call,
                                    contentDescription = "Call",
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = offer.ussdCode,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
