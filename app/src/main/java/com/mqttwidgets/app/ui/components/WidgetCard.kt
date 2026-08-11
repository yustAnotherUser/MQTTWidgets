package com.mqttwidgets.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mqttwidgets.app.data.Card
import com.mqttwidgets.app.util.WidgetRenderer
import kotlinx.coroutines.launch

@Composable
fun WidgetCard(card: Card, onClick: () -> Unit, onPin: () -> Unit, onDelete: () -> Unit) {
    val content = remember(card.lastFormattedValue, card.lastUpdated, card.consecutiveFailures) {
        WidgetRenderer.render(card)
    }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(content.backgroundColor))
                        .padding(horizontal = if (card.size.name == "SMALL") 20.dp else 12.dp, vertical = if (card.size.name == "SMALL") 20.dp else 8.dp)
                ) {
                    Column(horizontalAlignment = if (card.size.name == "SMALL") Alignment.CenterHorizontally else Alignment.Start) {
                        if (card.size.name != "SMALL") {
                            Text(card.label, color = Color(0xB0FFFFFF), style = MaterialTheme.typography.labelSmall)
                        }
                        Text(
                            content.displayText,
                            color = Color.White,
                            style = if (card.size.name == "SMALL") MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        if (card.size.name == "WIDE") {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(content.timeText, color = Color(0xB0FFFFFF), style = MaterialTheme.typography.labelSmall)
                                Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(Color(content.statusColor)))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = onPin, contentPadding = PaddingValues(8.dp)) { Text("📌") }
                    TextButton(onClick = { showDeleteConfirm = true }, contentPadding = PaddingValues(8.dp)) { Text("🗑️") }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(card.topic, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Widget") },
            text = { Text("Delete '${card.label}'? This cannot be undone.") },
            confirmButton = {
                Button(onClick = { showDeleteConfirm = false; onDelete() }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                    Text("Delete")
                }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } }
        )
    }
}
