package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.telephony.RoutingStatus
import com.example.telephony.SimCard
import com.example.telephony.SimRouting
import com.example.ui.util.LanguageManager

/**
 * The compact form of the SIM chooser: a single SIM icon badged with the slot
 * that will carry the operation, and a popup menu to change it.
 *
 * The dialer and the messaging screens use it — there, the destination is
 * already on screen and a full card would only push the content down. Screens
 * that spend credit keep [SimIndicator], where reading the card before dialling
 * is the point.
 */
@Composable
fun SimSelector(
    routing: SimRouting,
    sims: List<SimCard>,
    onPick: (SimCard) -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary
) {
    var expanded by remember { mutableStateOf(false) }
    val choices = routing.candidates.ifEmpty { sims }
    val warning = routing.status == RoutingStatus.NO_SIM ||
        routing.status == RoutingStatus.NO_SIM_FOR_OPERATOR ||
        routing.isFallback
    val color = if (warning) MaterialTheme.colorScheme.error else tint
    val current = routing.sim

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        IconButton(onClick = { expanded = true }) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (routing.status == RoutingStatus.NO_SIM) {
                        Icons.Default.Warning
                    } else {
                        Icons.Default.SimCard
                    },
                    contentDescription = LanguageManager.getString("choose_sim"),
                    tint = color,
                    modifier = Modifier.size(24.dp)
                )
                val badge = when {
                    routing.status == RoutingStatus.NO_SIM -> ""
                    warning -> "!"
                    current != null -> (current.slotIndex + 1).toString()
                    else -> "?"
                }
                if (badge.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = 7.dp, y = 5.dp)
                            .size(15.dp)
                            .background(color, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = badge,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                    }
                }
            }
        }

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Text(
                text = LanguageManager.getString("sim_used"),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
            if (choices.isEmpty()) {
                DropdownMenuItem(
                    text = {
                        Text(
                            text = LanguageManager.getString("no_sim_detected"),
                            color = MaterialTheme.colorScheme.error
                        )
                    },
                    enabled = false,
                    onClick = {}
                )
            }
            choices.forEach { sim ->
                val selected = sim.subscriptionId == current?.subscriptionId
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(
                                text = sim.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium
                            )
                            Text(
                                text = sim.subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.SimCard,
                            contentDescription = null,
                            tint = if (selected) tint else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingIcon = {
                        if (selected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = tint
                            )
                        }
                    },
                    onClick = {
                        expanded = false
                        onPick(sim)
                    }
                )
            }
        }
    }
}
