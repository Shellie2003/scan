package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Code
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.DatabaseProvider
import com.example.data.model.Operator
import com.example.ui.util.OperatorColorUtils
import com.example.ui.util.LanguageManager
import com.example.viewmodel.OperatorsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OperatorsScreen() {
    val context = LocalContext.current
    val viewModel: OperatorsViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return OperatorsViewModel(DatabaseProvider.getDatabase(context).operatorDao()) as T
            }
        }
    )

    val operators by viewModel.operators.collectAsState()
    val currentLang by LanguageManager.currentLang

    var showDialog by remember { mutableStateOf(false) }
    var selectedOperatorForEdit by remember { mutableStateOf<Operator?>(null) }
    var operatorName by remember { mutableStateOf("") }
    var operatorTemplate by remember { mutableStateOf("") }
    
    var showDeleteConfirm by remember { mutableStateOf<Operator?>(null) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { 
                    showDialog = true
                    selectedOperatorForEdit = null
                    operatorName = ""
                    operatorTemplate = ""
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = LanguageManager.getString("new_operator"))
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
        ) {
            // Hero Header Section with Language Selector
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.04f))
                    .padding(horizontal = 20.dp, vertical = 20.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = LanguageManager.getString("my_operators"),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        LanguageManager.LanguageSelector()
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = LanguageManager.getString("operators_desc"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = LanguageManager.getString("operators_configured", "count" to operators.size),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            if (operators.isEmpty()) {
                // Empty state view
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier.size(72.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Phone,
                                    contentDescription = LanguageManager.getString("no_operators_configured"),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = LanguageManager.getString("no_operators_configured"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = LanguageManager.getString("add_operator_desc"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { 
                                showDialog = true
                                selectedOperatorForEdit = null
                                operatorName = ""
                                operatorTemplate = ""
                            }
                        ) {
                            Text(LanguageManager.getString("add_first_operator"))
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(operators) { operator ->
                        val operatorColor = OperatorColorUtils.getOperatorColor(operator.name)
                        
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp)),
                            shape = RoundedCornerShape(16.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(IntrinsicSize.Min)
                            ) {
                                // Dynamic brand left indicator
                                Box(
                                    modifier = Modifier
                                        .width(8.dp)
                                        .fillMaxHeight()
                                        .background(operatorColor)
                                )
                                
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(16.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = operator.name,
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = operatorColor
                                        )
                                        
                                        // Quick Actions Row
                                        Row {
                                            IconButton(
                                                onClick = {
                                                    selectedOperatorForEdit = operator
                                                    operatorName = operator.name
                                                    operatorTemplate = operator.template
                                                    showDialog = true
                                                }
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Edit,
                                                    contentDescription = LanguageManager.getString("edit_operator"),
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                            IconButton(
                                                onClick = { showDeleteConfirm = operator }
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = LanguageManager.getString("delete"),
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    }
                                    
                                    Spacer(modifier = Modifier.height(4.dp))
                                    
                                    Text(
                                        text = LanguageManager.getString("recharge_template"),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    
                                    Spacer(modifier = Modifier.height(4.dp))
                                    
                                    // Code-Styled Badge for USSD Template
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                                    ) {
                                        Text(
                                            text = operator.template,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold
                                            ),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Add / Edit Dialog
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            shape = RoundedCornerShape(24.dp),
            title = { 
                Text(
                    text = if (selectedOperatorForEdit == null) LanguageManager.getString("new_operator") else LanguageManager.getString("edit_operator"),
                    fontWeight = FontWeight.Bold
                ) 
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        text = LanguageManager.getString("operator_dialog_desc"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = operatorName,
                        onValueChange = { operatorName = it },
                        label = { Text(LanguageManager.getString("operator_name")) },
                        placeholder = { Text("Ex: Yas, Orange, Airtel") },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    OutlinedTextField(
                        value = operatorTemplate,
                        onValueChange = { operatorTemplate = it },
                        label = { Text(LanguageManager.getString("ussd_template")) },
                        placeholder = { Text("Ex: #322*{CODE}#") },
                        leadingIcon = { Icon(Icons.Default.Code, contentDescription = null) },
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // live template preview box
                    if (operatorTemplate.isNotEmpty()) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = LanguageManager.getString("preview_generated"),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                val previewUssd = operatorTemplate.replace("{CODE}", "12345678901234")
                                Text(
                                    text = previewUssd,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (operatorName.isNotBlank() && operatorTemplate.isNotBlank()) {
                            if (selectedOperatorForEdit == null) {
                                viewModel.insertOperator(operatorName, operatorTemplate)
                            } else {
                                viewModel.updateOperator(selectedOperatorForEdit!!.copy(name = operatorName, template = operatorTemplate))
                            }
                            showDialog = false
                            selectedOperatorForEdit = null
                            operatorName = ""
                            operatorTemplate = ""
                        }
                    },
                    shape = RoundedCornerShape(12.dp)
                ) { 
                    Text(if (selectedOperatorForEdit == null) LanguageManager.getString("add") else LanguageManager.getString("save")) 
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(LanguageManager.getString("cancel"))
                }
            }
        )
    }

    // Delete Confirmation Dialog
    if (showDeleteConfirm != null) {
        val opToDelete = showDeleteConfirm!!
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text(LanguageManager.getString("confirm_delete"), fontWeight = FontWeight.Bold) },
            text = { 
                Text(LanguageManager.getString("confirm_delete_desc", "operator" to opToDelete.name)) 
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteOperator(opToDelete.id)
                        showDeleteConfirm = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(LanguageManager.getString("delete"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) {
                    Text(LanguageManager.getString("cancel"))
                }
            }
        )
    }
}
