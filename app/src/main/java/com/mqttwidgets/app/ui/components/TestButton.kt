package com.mqttwidgets.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mqttwidgets.app.data.PrefsManager
import com.mqttwidgets.app.util.PayloadParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun TestButton(topic: String, onValueSelected: (format: String, jsonPath: String, unit: String) -> Unit) {
    val context = LocalContext.current
    val pm = remember { PrefsManager(context) }
    var isActive by remember { mutableStateOf(false) }
    var phase by remember { mutableIntStateOf(0) }
    var phaseText by remember { mutableStateOf("") }
    var rawPayload by remember { mutableStateOf("") }
    var jsonKeys by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var selectedKey by remember { mutableStateOf<String?>(null) }
    var timeout by remember { mutableIntStateOf(10) }

    if (!isActive) {
        OutlinedButton(onClick = {
            if (topic.isBlank()) return@OutlinedButton
            isActive = true; phase = 1; phaseText = "Connecting to broker..."; rawPayload = ""; jsonKeys = emptyList(); selectedKey = null
        }) { Text("Test") }
        return
    }

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        Text(phaseText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        if (phase >= 2 && phase < 4) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Timeout:", style = MaterialTheme.typography.labelSmall)
                listOf(10, 30, 60).forEach { t ->
                    FilterChip(selected = timeout == t, onClick = { timeout = t }, label = { Text("${t}s") })
                }
            }
        }

        if (rawPayload.isNotEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text("Raw payload:", style = MaterialTheme.typography.labelSmall)
                    Text(rawPayload, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 4.dp))

                    if (jsonKeys.isNotEmpty()) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        Text("Select a value:", style = MaterialTheme.typography.labelSmall)
                        jsonKeys.forEach { (path, value) ->
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = selectedKey == path, onClick = { selectedKey = path; onValueSelected("JSON", path, ""); isActive = false })
                                Text("$path = $value", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    } else {
                        val detected = try { rawPayload.toDouble(); "NUMBER" } catch (_: Exception) { "RAW" }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { onValueSelected(detected, "", ""); isActive = false }) { Text("Use as $detected") }
                            OutlinedButton(onClick = { isActive = false }) { Text("Cancel") }
                        }
                    }
                }
            }
        }

        if (phase >= 4) { TextButton(onClick = { isActive = false }) { Text("Close") } }
    }

    LaunchedEffect(isActive, topic) {
        if (!isActive || topic.isBlank()) return@LaunchedEffect
        val host = pm.getBrokerHost(); val port = pm.getBrokerPort()
        if (host.isEmpty()) { phaseText = "Broker not configured"; isActive = false; return@LaunchedEffect }
        withContext(Dispatchers.IO) {
            try {
                val base = topic.trim()
                val variants = if (base.startsWith("/")) listOf(base, base.removePrefix("/")) else listOf(base, "/$base")
                val client = org.eclipse.paho.client.mqttv3.MqttClient("tcp://$host:$port", "mqttwidgets_test_${System.currentTimeMillis()}", org.eclipse.paho.client.mqttv3.persist.MemoryPersistence())
                client.connect(org.eclipse.paho.client.mqttv3.MqttConnectOptions().apply { connectionTimeout = 10; isCleanSession = true })
                phase = 2; phaseText = "Broker connected ✓"
                phaseText = "Subscribed to $topic, waiting for message..."
                var received = false
                val listener = org.eclipse.paho.client.mqttv3.IMqttMessageListener { _, msg -> rawPayload = String(msg.payload); received = true; jsonKeys = PayloadParser.extractJsonKeys(rawPayload) }
                for (v in variants) client.subscribe(v, listener)
                val start = System.currentTimeMillis()
                while (!received && (System.currentTimeMillis() - start) < timeout * 1000) delay(100)
                if (!received) { phaseText = "No message received within ${timeout}s."; phase = 4 } else { phase = 3; phaseText = "Message received!" }
                client.disconnect()
            } catch (e: Exception) { phaseText = "Error: ${e.message}"; phase = 4 }
        }
    }
}
