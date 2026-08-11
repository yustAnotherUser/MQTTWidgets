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
fun CreateWidgetDialog(onDismiss: () -> Unit, onCreate: (Card, Float) -> Unit) {
    var label by remember { mutableStateOf("") }
    var topic by remember { mutableStateOf("") }
    var size by remember { mutableStateOf(CardSize.COMPACT) }
    var format by remember { mutableStateOf(CardFormat.RAW) }
    var jsonPath by remember { mutableStateOf("") }
    var decimals by remember { mutableStateOf("1") }
    var unit by remember { mutableStateOf("") }
    var fontSize by remember { mutableStateOf(20f) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Widget") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = label, onValueChange = { label = it }, label = { Text("Label (e.g. Kitchen Temp)") }, modifier = Modifier.fillMaxWidth())

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(CardSize.SMALL to "1x1", CardSize.COMPACT to "2x1", CardSize.WIDE to "3x1").forEach { (s, display) ->
                        FilterChip(selected = size == s, onClick = { size = s }, label = { Text(display) })
                    }
                }

                OutlinedTextField(value = topic, onValueChange = { topic = it }, label = { Text("MQTT Topic") }, modifier = Modifier.fillMaxWidth())

                HorizontalDivider()
                Text("Widget Font Size", style = MaterialTheme.typography.labelMedium)
                WidgetSizePreview(
                    size = size,
                    label = label,
                    value = if (unit.isNotBlank()) "25.4 $unit" else "--",
                    time = "--:--",
                    fontSize = fontSize
                )
                Text("${fontSize.toInt()} sp", style = MaterialTheme.typography.labelSmall)
                Slider(
                    value = fontSize,
                    onValueChange = { fontSize = it },
                    valueRange = 10f..40f,
                    steps = 29
                )

                TestButton(topic = topic, onValueSelected = { f, jp, u ->
                    format = when (f) {
                        "JSON" -> CardFormat.JSON
                        "NUMBER" -> CardFormat.NUMBER
                        else -> CardFormat.RAW
                    }
                    jsonPath = jp
                    if (u.isNotEmpty()) unit = u
                })

                if (format != CardFormat.RAW) {
                    OutlinedTextField(value = decimals, onValueChange = { decimals = it.filter { c -> c.isDigit() } }, label = { Text("Decimal places") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = unit, onValueChange = { unit = it }, label = { Text("Unit (e.g. °C)") }, modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val card = Card(
                        cardId = java.util.UUID.randomUUID().toString(),
                        label = label.trim(), topic = topic.trim(),
                        size = size, format = format, jsonPath = jsonPath,
                        decimals = decimals.toIntOrNull() ?: 1, unit = unit,
                        highThreshold = 30.0, lowThreshold = 5.0,
                        highColor = 0xFFE53935.toInt(), normalColor = 0xFF43A047.toInt(), lowColor = 0xFF1E88E5.toInt(),
                        lastValue = "", lastFormattedValue = "", lastUpdated = 0L, consecutiveFailures = 0, pinnedWidgetIds = ""
                    )
                    onCreate(card, fontSize)
                },
                enabled = label.isNotBlank() && topic.isNotBlank()
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
