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
import androidx.compose.material.icons.filled.Edit
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
    var editingOffer by remember { mutableStateOf<Offer?>(null) }

    fun formatUssdForDialing(code: String, operator: String): String {
        val clean = code.trim()
        if (clean.isEmpty()) return clean
        
        if (clean == "123" || clean == "#123#") {
            return "#123#"
        }
        
        if (operator.equals("Orange", ignoreCase = true)) {
            var result = clean
            while (result.startsWith("*") || result.startsWith("#")) {
                result = result.substring(1)
            }
            while (result.endsWith("#")) {
                result = result.substring(0, result.length - 1)
            }
            return result
        }
        
        if ((clean.startsWith("*") || clean.startsWith("#")) && clean.endsWith("#")) {
            return clean
        }
        
        var formatted = clean
        if (!formatted.startsWith("*") && !formatted.startsWith("#")) {
            formatted = "*$formatted"
        }
        if (!formatted.endsWith("#")) {
            formatted = "$formatted#"
        }
        return formatted
    }
    
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
        // Internet
        Offer("Ye’low 500", "500 Ar", "130 Mo pour surfer sur le réseau N°1 à Madagascar", "24H", "#322*65#", "Yas", "Internet"),
        Offer("Ye’low One", "1 000 Ar", "1 Go pour surfer sur le réseau N°1 à Madagascar", "24H", "#322*64#", "Yas", "Internet"),
        Offer("Ye’low 2000", "2 000 Ar", "450 Mo pour surfer sur le réseau N°1 à Madagascar", "48H", "#322*68#", "Yas", "Internet"),
        Offer("Ye’low 2500", "2 500 Ar", "2,4 Go pour surfer sur le réseau N°1 à Madagascar", "24H", "#322*69#", "Yas", "Internet"),
        Offer("Net Week 3000", "3 000 Ar", "700 Mo pour surfer sur le réseau N°1 à Madagascar", "7 jours", "#322*70#", "Yas", "Internet"),
        Offer("Net Week 5000", "5 000 Ar", "1,2 Go pour surfer sur le réseau N°1 à Madagascar", "7 jours", "#322*71#", "Yas", "Internet"),
        Offer("Net Week 10000", "10 000 Ar", "3 Go pour surfer sur le réseau N°1 à Madagascar", "7 jours", "#322*73#", "Yas", "Internet"),
        Offer("Net Month 15000", "15 000 Ar", "2,5 Go pour surfer sur le réseau N°1 à Madagascar", "30 jours", "#322*80#", "Yas", "Internet"),
        Offer("Net Month 25000", "25 000 Ar", "4,5 Go pour surfer sur le réseau N°1 à Madagascar", "30 jours", "#322*8#", "Yas", "Internet"),
        Offer("Net Month 75000", "75 000 Ar", "15 Go pour surfer sur le réseau N°1 à Madagascar", "30 jours", "#322*13#", "Yas", "Internet"),
        Offer("Net Month 125000", "125 000 Ar", "29 Go pour surfer sur le réseau N°1 à Madagascar", "30 jours", "#322*17#", "Yas", "Internet"),
        Offer("Net Month 175000", "175 000 Ar", "58 Go pour surfer sur le réseau N°1 à Madagascar", "30 jours", "#322*19#", "Yas", "Internet"),
        Offer("Net Month 200000", "200 000 Ar", "100 Go pour surfer sur le réseau N°1 à Madagascar", "30 jours", "#322*15#", "Yas", "Internet"),
        
        // Appel (la famille M'ora et les offres First)
        Offer("M'Ora 500", "500 Ar", "500 Ar de crédit d'appels inclus, 25 Mo, 25 SMS. Avec renouvellement automatique", "1 jour", "#322*20#", "Yas", "Appel"),
        Offer("M'Ora One", "1 000 Ar", "1000 Ar de crédit d'appels inclus, 40 Mo, 40 SMS. Avec renouvellement automatique", "1 jour", "#322*21#", "Yas", "Appel"),
        Offer("M'Ora +2000", "2 000 Ar", "2000 Ar de crédit d'appels inclus, 100 Mo d'Internet, 50 SMS. Avec renouvellement automatique", "2 jours", "#322*32#", "Yas", "Appel"),
        Offer("M'Ora +5000", "5 000 Ar", "5000 Ar de crédit d'appels inclus, 200 Mo d'Internet, 100 SMS. Avec renouvellement automatique", "7 jours", "#322*35#", "Yas", "Appel"),
        Offer("International", "5 000 Ar", "15 minutes vers l'international*", "24H", "#322*52#", "Yas", "Appel"),
        Offer("First Premium", "10 000 Ar", "10 000 Ar de crédits d'appels inclus, 350 Mo, 100 SMS vers mon réseau. Avec renouvellement automatique", "30 jours", "#322*91#", "Yas", "Appel"),
        Offer("First Premium +", "15 000 Ar", "15 000 Ar de crédit d'appels inclus, 5 minutes vers l'international*, 500 Mo d'Internet, 150 SMS vers mon réseau. Avec renouvellement automatique", "30 jours", "#322*92#", "Yas", "Appel"),
        Offer("First Prestige", "30 000 Ar", "30 000 Ar de crédit d'appels inclus, 10 minutes vers l'international*, 15 Go d'Internet, 400 SMS vers mon réseau. Avec renouvellement automatique", "30 jours", "#322*93#", "Yas", "Appel"),
        Offer("First Royal", "50 000 Ar", "50 000 Ar de crédit d'appels inclus, 15 minutes vers l'international*, 2 Go d'Internet, 700 SMS vers mon réseau. Avec renouvellement automatique", "30 jours", "#322*95#", "Yas", "Appel"),

        // SMS
        Offer("Ye’low 100", "100 Ar", "20 Mo et/ou 20 SMS vers mon réseau", "1 jour", "#322*67#", "Yas", "SMS"),
        Offer("Ye’low 200", "200 Ar", "40 Mo et/ou 40 SMS vers mon réseau", "24H", "#322*63#", "Yas", "SMS"),
        Offer("Ye’low SMS", "200 Ar", "100 SMS vers mon réseau, 25 SMS vers tous les réseaux. Limité à 1 souscription/jour", "24H", "#322*62#", "Yas", "SMS"),
        Offer("Ye’low 1000", "1 000 Ar", "100 SMS et/ou 50 Mo jour + 50 Mo nuit", "30 jours", "#322*66#", "Yas", "SMS")
    )
    
    val orangeOffers = listOf(
        // Internet (Akama & Be Connect)
        Offer("Akama Go", "100 Ar", "20 Mo. Disponible aussi sur Max it ou composez le 222*1", "24 heures", "222*1", "Orange", "Internet"),
        Offer("Akama Go+", "250 Ar", "50 Mo. Disponible aussi sur Max it ou composez le 222*11", "24 heures", "222*11", "Orange", "Internet"),
        Offer("Akama Up", "500 Ar", "130 Mo. Disponible aussi sur Max it ou composez le 222*33", "24 heures", "222*33", "Orange", "Internet"),
        Offer("Akama Plus", "1 000 Ar", "500 Mo d'Internet + 500 Mo Nuit (22h à 6h). Disponible aussi sur Max it ou composez le 222*7", "24 heures", "222*7", "Orange", "Internet"),
        Offer("Akama Al 1Go", "1 000 Ar", "1 Go + 1 nuit (22h à 6h). Disponible aussi sur Max it ou composez le 222*01", "1 jour", "222*01", "Orange", "Internet"),
        Offer("Akama Full", "2 000 Ar", "1 Go + 1 Go Nuit (22h à 6h). Disponible aussi sur Max it ou composez le 222*77", "24 heures", "222*77", "Orange", "Internet"),
        Offer("Akama Al 2Go", "2 500 Ar", "2 Go + 3 nuits (22h à 6h). Disponible aussi sur Max it ou composez le 222*03", "3 jours", "222*03", "Orange", "Internet"),
        Offer("Akama Fun", "6 000 Ar", "1.4 Go. Disponible aussi sur Max it ou composez le 222*9", "5 jours", "222*9", "Orange", "Internet"),
        Offer("Be Connect 450Mo", "2 000 Ar", "450 Mo d'Internet + 150 Mo Spotify + 125 Mo Bonus Orange Money. Disponible aussi sur Max it ou composez le #123#", "7 jours", "123", "Orange", "Internet"),
        Offer("Be Connect 800Mo", "3 500 Ar", "800 Mo d'Internet + 150 Mo Spotify + 200 Mo Bonus Orange Money. Disponible aussi sur Max it ou composez le #123#", "7 jours", "123", "Orange", "Internet"),
        Offer("Be Connect 1,2Go", "5 000 Ar", "1.2 Go d'Internet + 150 Mo Spotify + 300 Mo Bonus Orange Money. Disponible aussi sur Max it ou composez le #123#", "7 jours", "123", "Orange", "Internet"),
        
        // Appel (Be)
        Offer("Be 500", "500 Ar", "SMS vers Orange : 25, Internet : 25 Mo, Bonus Orange Money : 100 Ar", "24 heures", "224*1*1", "Orange", "Appel"),
        Offer("Be 1 000", "1 000 Ar", "SMS vers Orange : 40, Internet : 40 Mo, Bonus Orange Money : 200 Ar", "24 heures", "224*1*2", "Orange", "Appel"),
        Offer("Be 2 000", "2 000 Ar", "SMS vers Orange : 50, Internet : 100 Mo, Bonus Orange Money : 400 Ar", "3 jours", "224*7*1", "Orange", "Appel"),
        Offer("Be 5 000", "5 000 Ar", "SMS vers Orange : 100, Internet : 200 Mo, Bonus Orange Money : 1 000 Ar", "7 jours", "224*7*2", "Orange", "Appel"),
        Offer("Be 10 000", "10 000 Ar", "SMS vers Orange : 100, Internet : 300 Mo, Appel International : 5 min, Bonus Orange Money : 2 000 Ar", "30 jours", "224*30*1", "Orange", "Appel"),
        Offer("Be 20 000", "20 000 Ar", "SMS vers Orange : 200, Internet : 500 Mo, Appel International : 10 min, Bonus Orange Money : 4 000 Ar", "30 jours", "224*30*2", "Orange", "Appel"),
        Offer("Be 30 000", "30 000 Ar", "SMS vers Orange : 400, Internet : 1 Go, Appel International : 10 min, Bonus Orange Money : 6 000 Ar", "30 jours", "224*30*3", "Orange", "Appel"),
        Offer("Be 50 000", "50 000 Ar", "SMS vers Orange : 600, Internet : 2 Go, Appel International : 15 min, Bonus Orange Money : 10 000 Ar", "30 jours", "224*30*4", "Orange", "Appel"),
        Offer("Be 100 000", "100 000 Ar", "SMS vers Orange : 800, Internet : 4 Go, Appel International : 30 min, Bonus Orange Money : 2 000 Ar", "30 jours", "224*30*5", "Orange", "Appel"),
        Offer("Be 150 000", "150 000 Ar", "SMS vers Orange : 900, Internet : 6 Go, Appel International : 30 min, Bonus Orange Money : 30 000 Ar", "30 jours", "224*30*6", "Orange", "Appel"),
        
        // SMS (Be SMS & Free)
        Offer("Be SMS 1", "250 Ar", "75 SMS vers Orange, 25 SMS vers autres opérateurs. Les SMS vers Orange sont partageables à des numéros Orange. Disponible aussi sur Max it ou composez le 224*1*0", "24 heures", "224*1*0", "Orange", "SMS"),
        Offer("Be SMS 3", "450 Ar", "150 SMS vers Orange, 50 SMS vers autres opérateurs. Les SMS vers Orange sont partageables à des numéros Orange. Disponible aussi sur Max it ou composez le 224*3*0", "3 jours", "224*3*0", "Orange", "SMS"),
        Offer("Be SMS 7", "450 Ar", "450 SMS vers Orange. Les SMS vers Orange sont partageables à des numéros Orange. Disponible aussi sur Max it ou composez le 224*7*0", "7 jours", "224*7*0", "Orange", "SMS"),
        Offer("Be SMS 30", "1 000 Ar", "100 SMS vers tout opérateur. Les SMS vers Orange sont partageables à des numéros Orange. Disponible aussi sur Max it ou composez le 224*30*0", "30 jours", "224*30*0", "Orange", "SMS"),
        Offer("SMS Free 200", "200 Ar TTC", "100 SMS tout opérateur. Disponible aussi sur Max it ou composez le 202*3*1", "24 h", "202*3*1", "Orange", "SMS"),
        Offer("SMS Free 500", "500 Ar TTC", "300 SMS tout opérateur. Disponible aussi sur Max it ou composez le 202*3*2", "3 j", "202*3*2", "Orange", "SMS")
    )
    
    val airtelOffers = listOf(
        // Internet (Airtel Net Mlay)
        Offer("Net Mlay Boost-500", "500 Ar", "500 Mo d'Internet", "1 heure", "*114*500*1#", "Airtel", "Internet"),
        Offer("Net Mlay-100", "100 Ar", "20 Mo d'Internet", "24 heures", "*114*0100#", "Airtel", "Internet"),
        Offer("Net Mlay-200", "200 Ar", "50 Mo d'Internet", "24 heures", "*114*0200#", "Airtel", "Internet"),
        Offer("Net Mlay-500", "500 Ar", "130 Mo d'Internet", "2 jours", "*114*500*2#", "Airtel", "Internet"),
        Offer("Net Mlay-1000", "1 000 Ar", "500 Mo Jour + 500 Mo Nuit d'Internet", "2 jours", "*114*01000#", "Airtel", "Internet"),
        Offer("Net Mlay-2000", "2 000 Ar", "1 Go Jour + 1 Go Nuit d'Internet", "2 jours", "*114*02000#", "Airtel", "Internet"),
        Offer("Net Mlay-3000", "3 000 Ar", "1,4 Go d'Internet", "7 jours", "*114*03000#", "Airtel", "Internet"),
        Offer("Net Mlay-5000", "5 000 Ar", "2,4 Go d'Internet", "7 jours", "*114*05000#", "Airtel", "Internet"),
        Offer("Net Mlay-6000", "6 000 Ar", "2,9 Go d'Internet", "7 jours", "*114*06000#", "Airtel", "Internet"),
        Offer("Net Mlay-10000", "10 000 Ar", "4,8 Go d'Internet", "7 jours", "*114*010000#", "Airtel", "Internet"),
        Offer("Net Mlay-12500", "12 500 Ar", "6 Go d'Internet", "7 jours", "*114*012500#", "Airtel", "Internet"),
        Offer("Net Mlay-15000", "15 000 Ar", "6 Go d'Internet", "30 jours", "*114*015000#", "Airtel", "Internet"),
        Offer("Net Mlay-25000", "25 000 Ar", "10 Go d'Internet", "30 jours", "*114*025000#", "Airtel", "Internet"),
        Offer("Net Mlay-30000", "30 000 Ar", "13 Go d'Internet", "30 jours", "*114*030000#", "Airtel", "Internet"),
        Offer("Net Mlay-49000", "49 000 Ar", "23,5 Go d'Internet", "30 jours", "*114*049000#", "Airtel", "Internet"),
        Offer("Net Mlay-80000", "80 000 Ar", "39 Go d'Internet", "30 jours", "*114*080000#", "Airtel", "Internet"),
        Offer("Net Mlay-125000", "125 000 Ar", "66 Go d'Internet", "30 jours", "*114*0125000#", "Airtel", "Internet"),
        Offer("Net Mlay-199000", "199 000 Ar", "210 Go d'Internet", "30 jours", "*114*0199000#", "Airtel", "Internet"),
        
        // Appel (Airtel M'Lay Jiaby & M'Lay)
        Offer("M'Lay Jiaby 500", "500 Ar", "5 min d'appels tout opérateur + 10 SMS tout opérateur + 25 Mo d'Internet", "24 heures", "*100*00500#", "Airtel", "Appel"),
        Offer("M'Lay Jiaby 1 000", "1 000 Ar", "10 min d'appels tout opérateur + 20 SMS tout opérateur + 50 Mo d'Internet", "24 heures", "*100*001000#", "Airtel", "Appel"),
        Offer("M'Lay Jiaby 2 000", "2 000 Ar", "20 min d'appels tout opérateur + 50 SMS tout opérateur + 100 Mo d'Internet", "2 jours", "*100*002000#", "Airtel", "Appel"),
        Offer("M'Lay Jiaby 10 000", "10 000 Ar", "100 min d'appels tout opérateur + 200 SMS tout opérateur + 500 Mo d'Internet", "30 jours", "*100*0010000#", "Airtel", "Appel"),
        Offer("M'Lay 500", "500 Ar", "500 Ar de crédit d'appels tout opérateur + 25 SMS Airtel + 25 Mo d'Internet", "24 heures", "*100*500#", "Airtel", "Appel"),
        Offer("M'Lay 1 000", "1 000 Ar", "1 000 Ar de crédit d'appels tout opérateur + 75 SMS Airtel + 75 Mo d'Internet", "24 heures", "*100*1000#", "Airtel", "Appel"),
        Offer("M'Lay 3 500", "3 500 Ar", "3 500 Ar de crédit d'appels tout opérateur + 100 SMS Airtel + 100 Mo d'Internet", "7 jours", "*100*3500#", "Airtel", "Appel"),
        Offer("M'Lay 5 000", "5 000 Ar", "5 000 Ar de crédit d'appels tout opérateur + 300 SMS Airtel + 300 Mo d'Internet", "7 jours", "*100*5000#", "Airtel", "Appel"),
        Offer("M'Lay 10 000", "10 000 Ar", "10 000 Ar de crédit d'appels tout opérateur + 300 SMS Airtel + 350 Mo d'Internet + 5 min à l'international", "30 jours", "*100*10000#", "Airtel", "Appel"),
        Offer("M'Lay 15 000", "15 000 Ar", "15 000 Ar de crédit d'appels tout opérateur + 500 SMS Airtel + 500 Mo d'Internet + 5 min à l'international", "30 jours", "*100*15000#", "Airtel", "Appel"),
        Offer("M'Lay 20 000", "20 000 Ar", "20 000 Ar de crédit d'appels tout opérateur + 750 SMS Airtel + 750 Mo d'Internet + 10 min à l'international", "30 jours", "*100*20000#", "Airtel", "Appel"),
        Offer("M'Lay 30 000", "30 000 Ar", "30 000 Ar de crédit d'appels tout opérateur + 1 000 SMS Airtel + 1 024 Mo d'Internet + 10 min à l'international", "30 jours", "*100*30000#", "Airtel", "Appel"),
        Offer("M'Lay 50 000", "50 000 Ar", "50 000 Ar de crédit d'appels tout opérateur + 2 000 SMS Airtel + 2 048 Mo d'Internet + 15 min à l'international", "30 jours", "*100*50000#", "Airtel", "Appel"),
        
        // SMS
        Offer("SMS M'Lay 100", "100 Ar", "20 SMS vers Airtel", "24 heures", "*100*111#", "Airtel", "SMS"),
        Offer("SMS M'Lay 200", "200 Ar", "200 SMS vers Airtel + 10 SMS vers tous les opérateurs", "24 heures", "*100*222#", "Airtel", "SMS"),
        Offer("SMS M'Lay 500", "500 Ar", "1 400 SMS vers Airtel + 70 SMS vers tous les opérateurs", "7 jours", "*100*555#", "Airtel", "SMS"),
        Offer("SMS M'Lay 1 000", "1 000 Ar", "100 SMS vers tous les opérateurs", "30 jours", "*100*1111#", "Airtel", "SMS")
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
                val currentUssdCode = remember(offer, editingOffer) {
                    prefs.getString("custom_ussd_${offer.operatorName}_${offer.category}_${offer.title}", offer.ussdCode) ?: offer.ussdCode
                }

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
                                val formattedCode = formatUssdForDialing(currentUssdCode, offer.operatorName)
                                val useDirectCall = autoValidation && callPermissionState.status.isGranted
                                val intent = if (useDirectCall) {
                                    SimManager.createCallIntent(context, formattedCode, selectedOperator)
                                } else {
                                    Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(formattedCode)))
                                }
                                try {
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(formattedCode)))
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
                            
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                IconButton(
                                    onClick = { editingOffer = offer },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Edit Code",
                                        tint = operatorTheme.mainColor,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                Button(
                                    onClick = {
                                        if (autoValidation && !callPermissionState.status.isGranted) {
                                            callPermissionState.launchPermissionRequest()
                                        } else {
                                            val useDirectCall = autoValidation && callPermissionState.status.isGranted
                                            val intent = if (useDirectCall) {
                                                SimManager.createCallIntent(context, formatUssdForDialing(currentUssdCode, offer.operatorName), selectedOperator)
                                            } else {
                                                Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(formatUssdForDialing(currentUssdCode, offer.operatorName))))
                                            }
                                            try {
                                                context.startActivity(intent)
                                            } catch (e: Exception) {
                                                val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(formatUssdForDialing(currentUssdCode, offer.operatorName))))
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
                                        text = currentUssdCode,
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

    if (editingOffer != null) {
        val offer = editingOffer!!
        val key = "custom_ussd_${offer.operatorName}_${offer.category}_${offer.title}"
        val currentCustom = prefs.getString(key, offer.ussdCode) ?: offer.ussdCode
        var textVal by remember(offer) { mutableStateOf(currentCustom) }
        
        AlertDialog(
            onDismissRequest = { editingOffer = null },
            title = {
                Text(
                    text = when (currentLang) {
                        "mg" -> "Hanova kaody tolotra"
                        "fr" -> "Modifier le code de l'offre"
                        else -> "Edit Offer Code"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = offer.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = operatorTheme.mainColor
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = when (currentLang) {
                            "mg" -> "Soraty ny kaody USSD vaovao ho an'ity tolotra ity."
                            "fr" -> "Saisissez le nouveau code USSD pour cette offre."
                            else -> "Enter the new USSD code for this offer."
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = textVal,
                        onValueChange = { textVal = it },
                        label = {
                            Text(
                                when (currentLang) {
                                    "mg" -> "Kaody USSD"
                                    "fr" -> "Code USSD"
                                    else -> "USSD Code"
                                }
                            )
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = when (currentLang) {
                            "mg" -> "Kaody natoraly: ${offer.ussdCode}"
                            "fr" -> "Code par défaut : ${offer.ussdCode}"
                            else -> "Default code: ${offer.ussdCode}"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val trimmed = textVal.trim()
                        if (trimmed.isEmpty() || trimmed == offer.ussdCode) {
                            prefs.edit().remove(key).apply()
                        } else {
                            prefs.edit().putString(key, trimmed).apply()
                        }
                        editingOffer = null
                    }
                ) {
                    Text(
                        text = when (currentLang) {
                            "mg" -> "Tehirizina"
                            "fr" -> "Enregistrer"
                            else -> "Save"
                        },
                        color = operatorTheme.mainColor
                    )
                }
            },
            dismissButton = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (currentCustom != offer.ussdCode) {
                        TextButton(
                            onClick = {
                                prefs.edit().remove(key).apply()
                                editingOffer = null
                            }
                        ) {
                            Text(
                                text = when (currentLang) {
                                    "mg" -> "Hamerina"
                                    "fr" -> "Rétablir"
                                    else -> "Reset"
                                },
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    TextButton(onClick = { editingOffer = null }) {
                        Text(
                            text = when (currentLang) {
                                "mg" -> "Hanafoana"
                                "fr" -> "Annuler"
                                else -> "Cancel"
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        )
    }
}
