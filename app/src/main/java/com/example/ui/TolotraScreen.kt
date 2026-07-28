package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.DefaultData
import com.example.data.model.Offer
import com.example.data.model.Operator
import com.example.data.model.ScanHistory
import com.example.telephony.NumberUtils
import com.example.telephony.UssdFormatter
import com.example.ui.components.EmptyState
import com.example.ui.components.SimIndicator
import com.example.ui.components.SimLauncher
import com.example.ui.components.SimPickerDialog
import com.example.ui.util.LanguageManager
import com.example.ui.util.OperatorColorUtils
import com.example.viewmodel.AppViewModel

/**
 * The plans (forfaits) of every operator.
 *
 * Plans live in the database: each one can be edited, duplicated or deleted, and
 * new ones can be created, so a change of code by an operator never makes the
 * app useless. A plan can be bought for oneself or offered to another number,
 * with the SIM chosen automatically from the operator of that plan.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TolotraScreen(
    app: AppViewModel,
    launcher: SimLauncher,
    onOpenShare: () -> Unit
) {
    val currentLang by LanguageManager.currentLang
    val operators by app.operators.collectAsState()
    val offers by app.offers.collectAsState()
    val sims by app.sims.collectAsState()

    var selectedOperatorName by remember { mutableStateOf<String?>(null) }
    var selectedCategory by remember { mutableStateOf(DefaultData.CATEGORY_INTERNET) }
    var query by remember { mutableStateOf("") }
    var showFavoritesOnly by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Offer?>(null) }
    var sharing by remember { mutableStateOf<Offer?>(null) }
    var deleting by remember { mutableStateOf<Offer?>(null) }
    var showSimPicker by remember { mutableStateOf(false) }

    val operator = remember(operators, selectedOperatorName) {
        operators.firstOrNull { it.name == selectedOperatorName } ?: operators.firstOrNull()
    }
    val theme = OperatorColorUtils.themeOf(operator)
    val routing = remember(operator, sims) { app.routeFor(operator) }

    val categories = remember(offers, operator) {
        val fromOffers = offers.filter { it.operatorName.equals(operator?.name, true) }
            .map { it.category }
            .distinct()
        (DefaultData.categories + fromOffers).distinct()
    }

    val visibleOffers = remember(offers, operator, selectedCategory, query, showFavoritesOnly) {
        offers.filter { offer ->
            offer.operatorName.equals(operator?.name, ignoreCase = true) &&
                (showFavoritesOnly || offer.category == selectedCategory) &&
                (!showFavoritesOnly || offer.isFavorite) &&
                (query.isBlank() ||
                    offer.title.contains(query, true) ||
                    offer.description.contains(query, true) ||
                    offer.ussdCode.contains(query, true))
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editing = Offer(
                        operatorName = operator?.name.orEmpty(),
                        category = selectedCategory,
                        title = "",
                        price = "",
                        description = "",
                        validity = "",
                        ussdCode = "",
                        isCustom = true
                    )
                },
                containerColor = theme.mainColor,
                contentColor = theme.accentColor,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = LanguageManager.getString("add_offer"))
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
        ) {
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
                    fontWeight = FontWeight.ExtraBold,
                    color = theme.mainColor,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onOpenShare) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        tint = theme.mainColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = LanguageManager.getString("nav_share"),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = theme.mainColor
                    )
                }
            }

            if (operators.isEmpty()) {
                EmptyState(
                    icon = Icons.Default.LocalOffer,
                    title = LanguageManager.getString("no_operators_configured"),
                    description = LanguageManager.getString("add_operator_desc")
                )
                return@Column
            }

            TabRow(
                selectedTabIndex = operators.indexOfFirst { it.name == operator?.name }
                    .coerceAtLeast(0),
                containerColor = theme.mainColor,
                contentColor = theme.accentColor
            ) {
                operators.forEach { item ->
                    Tab(
                        selected = item.name == operator?.name,
                        onClick = { selectedOperatorName = item.name },
                        text = { Text(item.name, fontWeight = FontWeight.Bold) },
                        selectedContentColor = theme.accentColor,
                        unselectedContentColor = theme.unselectedColor
                    )
                }
            }

            TabRow(
                selectedTabIndex = categories.indexOf(selectedCategory).coerceAtLeast(0),
                containerColor = theme.containerColor,
                contentColor = theme.accentColor
            ) {
                categories.forEach { category ->
                    Tab(
                        selected = !showFavoritesOnly && category == selectedCategory,
                        onClick = {
                            selectedCategory = category
                            showFavoritesOnly = false
                        },
                        text = {
                            Text(
                                text = categoryLabel(category),
                                fontWeight = FontWeight.Medium
                            )
                        },
                        selectedContentColor = theme.accentColor,
                        unselectedContentColor = theme.unselectedColor
                    )
                }
                Tab(
                    selected = showFavoritesOnly,
                    onClick = { showFavoritesOnly = true },
                    text = { Icon(Icons.Default.Star, contentDescription = LanguageManager.getString("favorites")) },
                    selectedContentColor = theme.accentColor,
                    unselectedContentColor = theme.unselectedColor
                )
            }

            SimIndicator(
                routing = routing,
                color = theme.mainColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                onClick = { showSimPicker = true }
            )

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                placeholder = { Text(LanguageManager.getString("search_offer")) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )

            if (visibleOffers.isEmpty()) {
                EmptyState(
                    icon = Icons.Default.LocalOffer,
                    title = LanguageManager.getString("no_offers")
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(visibleOffers, key = { it.id }) { offer ->
                        OfferCard(
                            offer = offer,
                            operator = operator,
                            onBuy = {
                                launcher.runCode(
                                    code = UssdFormatter.format(offer.ussdCode, operator),
                                    operator = operator,
                                    kind = ScanHistory.KIND_OFFER,
                                    label = offer.title,
                                    rawCode = offer.ussdCode
                                )
                            },
                            onShare = { sharing = offer },
                            onEdit = { editing = offer },
                            onDelete = { deleting = offer },
                            onToggleFavorite = { app.toggleFavorite(offer) }
                        )
                    }
                }
            }
        }
    }

    if (showSimPicker) {
        SimPickerDialog(
            sims = routing.candidates.ifEmpty { sims },
            operator = operator,
            selected = routing.sim,
            onDismiss = { showSimPicker = false },
            onPick = { sim, save ->
                showSimPicker = false
                if (save) app.setPreferredSim(operator, sim)
            }
        )
    }

    editing?.let { offer ->
        OfferEditorDialog(
            offer = offer,
            operators = operators,
            onDismiss = { editing = null },
            onSave = {
                app.saveOffer(it)
                editing = null
            }
        )
    }

    deleting?.let { offer ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(LanguageManager.getString("confirm_delete"), fontWeight = FontWeight.Bold) },
            text = { Text(offer.title) },
            confirmButton = {
                Button(
                    onClick = {
                        app.deleteOffer(offer)
                        deleting = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) { Text(LanguageManager.getString("delete")) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) {
                    Text(LanguageManager.getString("cancel"))
                }
            }
        )
    }

    sharing?.let { offer ->
        ShareOfferDialog(
            offer = offer,
            operator = operator,
            onDismiss = { sharing = null },
            onEditOffer = {
                sharing = null
                editing = offer
            },
            onSend = { code, number ->
                sharing = null
                launcher.runCode(
                    code = UssdFormatter.format(code, operator),
                    operator = operator,
                    kind = ScanHistory.KIND_OFFER,
                    label = offer.title + " → " + number,
                    rawCode = offer.shareCode,
                    requireConfirm = true,
                    warning = if (operator?.templatesVerified == false) {
                        LanguageManager.getString("verify_code_warning")
                    } else {
                        null
                    }
                )
            }
        )
    }
}

private fun categoryLabel(category: String): String = when (category) {
    DefaultData.CATEGORY_INTERNET -> LanguageManager.getString("plan_internet")
    DefaultData.CATEGORY_CALL -> LanguageManager.getString("plan_appel")
    DefaultData.CATEGORY_SMS -> LanguageManager.getString("plan_sms")
    else -> category
}

@Composable
private fun OfferCard(
    offer: Offer,
    operator: Operator?,
    onBuy: () -> Unit,
    onShare: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    val theme = OperatorColorUtils.themeOf(operator)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = offer.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = theme.mainColor,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onToggleFavorite, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = if (offer.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = LanguageManager.getString("favorites"),
                        tint = theme.mainColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = offer.price,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold
                )
                if (offer.validity.isNotBlank()) {
                    Text(
                        text = offer.validity,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (offer.description.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = offer.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = offer.ussdCode,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = LanguageManager.getString("edit"),
                        tint = theme.mainColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
                if (offer.isCustom) {
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = LanguageManager.getString("delete"),
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onBuy,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = theme.mainColor,
                        contentColor = theme.accentColor
                    )
                ) {
                    Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(LanguageManager.getString("buy_for_me"), style = MaterialTheme.typography.labelMedium)
                }
                Button(
                    onClick = onShare,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = theme.mainColor.copy(alpha = 0.15f),
                        contentColor = theme.mainColor
                    )
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = LanguageManager.getString("offer_to_someone"),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun ShareOfferDialog(
    offer: Offer,
    operator: Operator?,
    onDismiss: () -> Unit,
    onEditOffer: () -> Unit,
    onSend: (code: String, number: String) -> Unit
) {
    var number by remember { mutableStateOf("") }
    val template = offer.shareCode.ifBlank { operator?.shareTemplate.orEmpty() }
    val code = remember(number, template) {
        UssdFormatter.fill(template, number = NumberUtils.toNational(number), amount = offer.price.filter { it.isDigit() })
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        title = { Text(LanguageManager.getString("offer_to_someone"), fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(offer.title, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                if (template.isBlank()) {
                    Text(
                        text = LanguageManager.getString(
                            "template_missing",
                            "operator" to (operator?.name ?: "")
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    OutlinedTextField(
                        value = number,
                        onValueChange = { number = it },
                        label = { Text(LanguageManager.getString("recipient_number")) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = LanguageManager.getString("code_preview") + " : " + code,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        },
        confirmButton = {
            if (template.isBlank()) {
                Button(onClick = onEditOffer) { Text(LanguageManager.getString("edit")) }
            } else {
                Button(
                    onClick = { onSend(code, number) },
                    enabled = number.isNotBlank()
                ) { Text(LanguageManager.getString("execute")) }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(LanguageManager.getString("cancel")) }
        }
    )
}

@Composable
private fun OfferEditorDialog(
    offer: Offer,
    operators: List<Operator>,
    onDismiss: () -> Unit,
    onSave: (Offer) -> Unit
) {
    var title by remember { mutableStateOf(offer.title) }
    var price by remember { mutableStateOf(offer.price) }
    var description by remember { mutableStateOf(offer.description) }
    var validity by remember { mutableStateOf(offer.validity) }
    var code by remember { mutableStateOf(offer.ussdCode) }
    var shareCode by remember { mutableStateOf(offer.shareCode) }
    var category by remember { mutableStateOf(offer.category) }
    var operatorName by remember { mutableStateOf(offer.operatorName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(
                text = if (offer.id == 0) {
                    LanguageManager.getString("add_offer")
                } else {
                    LanguageManager.getString("edit_offer_full")
                },
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    operators.forEach { item ->
                        val selected = item.name == operatorName
                        TextButton(
                            onClick = { operatorName = item.name },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = if (selected) {
                                    OperatorColorUtils.colorOf(item)
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        ) {
                            Text(
                                text = item.name,
                                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Normal
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DefaultData.categories.forEach { item ->
                        val selected = item == category
                        TextButton(onClick = { category = item }) {
                            Text(
                                text = categoryLabel(item),
                                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Normal
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(LanguageManager.getString("offer_title")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = price,
                    onValueChange = { price = it },
                    label = { Text(LanguageManager.getString("offer_price")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = validity,
                    onValueChange = { validity = it },
                    label = { Text(LanguageManager.getString("offer_validity")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(LanguageManager.getString("offer_desc")) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text(LanguageManager.getString("offer_code")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = shareCode,
                    onValueChange = { shareCode = it },
                    label = { Text(LanguageManager.getString("offer_share_code")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        offer.copy(
                            title = title.trim(),
                            price = price.trim(),
                            description = description.trim(),
                            validity = validity.trim(),
                            ussdCode = code.trim(),
                            shareCode = shareCode.trim(),
                            category = category,
                            operatorName = operatorName
                        )
                    )
                },
                enabled = title.isNotBlank() && code.isNotBlank()
            ) { Text(LanguageManager.getString("save")) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(LanguageManager.getString("cancel")) }
        }
    )
}
