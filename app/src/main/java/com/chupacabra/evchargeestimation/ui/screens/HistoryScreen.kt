package com.chupacabra.evchargeestimation.ui.screens

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.outlined.ElectricBolt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chupacabra.evchargeestimation.data.ChargeHistoryEntry
import com.chupacabra.evchargeestimation.domain.ChargeEstimator
import com.chupacabra.evchargeestimation.ui.components.GlassCard
import com.chupacabra.evchargeestimation.ui.components.NeonAccentBar
import com.chupacabra.evchargeestimation.ui.theme.NeonMint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    entries: List<ChargeHistoryEntry>,
    onDelete: (String) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showClearConfirm by remember { mutableStateOf(false) }
    val dateFormat = remember {
        SimpleDateFormat("MMM d, yyyy · HH:mm", Locale.getDefault())
    }
    val scheme = MaterialTheme.colorScheme
    val dark = isSystemInDarkTheme()

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = scheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "History",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                NeonAccentBar()
                Text(
                    text = "${entries.size} calculation${if (entries.size == 1) "" else "s"} logged",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant
                )
            }
            if (entries.isNotEmpty()) {
                IconButton(onClick = { showClearConfirm = true }) {
                    Icon(
                        Icons.Default.DeleteSweep,
                        contentDescription = "Clear all history",
                        tint = scheme.onSurfaceVariant
                    )
                }
            }
        }

        if (entries.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Outlined.ElectricBolt,
                    contentDescription = null,
                    tint = scheme.primary.copy(alpha = 0.45f),
                    modifier = Modifier.size(56.dp)
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Nothing saved yet",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "When you work out a charge time, it shows up here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(entries, key = { it.id }) { entry ->
                    HistoryCard(
                        entry = entry,
                        formattedTime = dateFormat.format(Date(entry.timestampMillis)),
                        onDelete = { onDelete(entry.id) },
                        dark = dark
                    )
                }
                item { Spacer(Modifier.height(12.dp)) }
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear history?") },
            text = { Text("This removes all saved calculations from this device.") },
            confirmButton = {
                TextButton(onClick = {
                    onClearAll()
                    showClearConfirm = false
                }) {
                    Text("Clear all", color = scheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun HistoryCard(
    entry: ChargeHistoryEntry,
    formattedTime: String,
    onDelete: () -> Unit,
    dark: Boolean
) {
    val scheme = MaterialTheme.colorScheme

    GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 18.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = formattedTime.uppercase(Locale.getDefault()),
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.primary,
                    letterSpacing = 0.8.sp
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "${entry.currentPercent}%  →  ${entry.desiredPercent}%",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Car to full: ${ChargeEstimator.formatDuration(entry.minutesToFull)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant
                )
                Text(
                    text = "Ready in ${ChargeEstimator.formatDuration(entry.resultMinutes)}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (dark) NeonMint else scheme.secondary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (entry.source == "ocr") "From scan" else "Typed in",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant.copy(alpha = 0.8f),
                    letterSpacing = 0.6.sp
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete entry",
                    tint = scheme.error.copy(alpha = 0.85f)
                )
            }
        }
    }
}
