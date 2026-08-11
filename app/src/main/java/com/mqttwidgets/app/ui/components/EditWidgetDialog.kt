package com.mqttwidgets.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mqttwidgets.app.data.Card
import com.mqttwidgets.app.data.CardFormat
import com.mqttwidgets.app.data.CardSize

@Composable
fun EditWidgetDialog(card: Card, initialFontSize: Float, onDismiss: () -> Unit, onSave: (Card) -> Unit, onDelete: () -> Unit, onSaveFontSize: (Float) -> Unit) {
    var label by remember { mutableStateOf(card.label) }
    var topic by remember { mutableStateOf(card.topic) }
    var size by remember { mutableStateOf(card.size) }
    var format by remember { mutableStateOf(card.format) }
    var jsonPath by remember { mutableStateOf(card.jsonPath) }
    var decimals by remember { mutableStateOf(card.decimals.toString()) }
    var unit by remember { mutableStateOf(card.unit) }
    var highThreshold by remember { mutableStateOf(if (card.highThreshold < 1e10) card.highThreshold.toString() else "") }
    var lowThreshold by remember { mutableStateOf(if (card.lowThreshold > -1e10) card.lowThreshold.toString() else "") }
    var fontSize by remember { mutableStateOf(initialFontSize) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Widget") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = label, onValueChange = { label = it }, label = { Text("Label") }, modifier = Modifier.fillMaxWidth())

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(CardSize.SMALL to "1x1", CardSize.COMPACT to "2x1", CardSize.WIDE to "3x1").forEach { (s, display) ->
                        FilterChip(selected = size == s, onClick = { size = s }, label = { Text(display) })
                    }
                }

                OutlinedTextField(value = topic, onValueChange = { topic = it }, label = { Text("MQTT Topic") }, modifier = Modifier.fillMaxWidth())

                if (card.lastValue.isNotBlank()) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text("Raw payload (live):", style = MaterialTheme.typography.labelSmall)
                            Text(card.lastValue, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }

                TestButton(topic = topic, onValueSelected = { f, jp, u ->
                    format = when (f) {
                        "JSON" -> CardFormat.JSON
                        "NUMBER" -> CardFormat.NUMBER
                        else -> CardFormat.RAW
                    }
                    jsonPath = jp
                    if (u.isNotEmpty()) unit = u
                })

                HorizontalDivider()
                Text("Widget Font Size", style = MaterialTheme.typography.labelMedium)
                Text("${fontSize.toInt()} sp", style = MaterialTheme.typography.labelSmall)
                Slider(
                    value = fontSize,
                    onValueChange = { fontSize = it },
                    valueRange = 10f..40f,
                    steps = 29
                )

                HorizontalDivider()
                Text("State Colors", style = MaterialTheme.typography.labelMedium)
                OutlinedTextField(value = highThreshold, onValueChange = { highThreshold = it }, label = { Text("High threshold (e.g. 30)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = lowThreshold, onValueChange = { lowThreshold = it }, label = { Text("Low threshold (e.g. 5)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())

                if (format != CardFormat.RAW) {
                    OutlinedTextField(value = decimals, onValueChange = { decimals = it.filter { c -> c.isDigit() } }, label = { Text("Decimal places") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = unit, onValueChange = { unit = it }, label = { Text("Unit (e.g. °C)") }, modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    onSaveFontSize(fontSize)
                    onSave(card.copy(
                        label = label, topic = topic, size = size, format = format,
                        jsonPath = jsonPath, decimals = decimals.toIntOrNull() ?: 1, unit = unit,
                        highThreshold = highThreshold.toDoubleOrNull() ?: 1e10,
                        lowThreshold = lowThreshold.toDoubleOrNull() ?: -1e10
                    ))
                }, enabled = label.isNotBlank() && topic.isNotBlank()) { Text("Save") }
                Button(onClick = { showDeleteConfirm = true }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Delete") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Widget") },
            text = { Text("Delete '${card.label}'? This cannot be undone.") },
            confirmButton = { Button(onClick = { showDeleteConfirm = false; onDelete() }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } }
        )
    }
}
