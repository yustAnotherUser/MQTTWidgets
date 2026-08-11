package com.mqttwidgets.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mqttwidgets.app.data.PrefsManager

@Composable
fun FirstRunScreen(onComplete: () -> Unit) {
    val context = LocalContext.current
    val pm = remember { PrefsManager(context) }
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("1883") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var testResult by remember { mutableStateOf<String?>(null) }
    var isTesting by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Welcome to MQTT Widgets", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Set up your MQTT broker to get started", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(24.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = host, onValueChange = { host = it }, label = { Text("Broker Host") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = port, onValueChange = { port = it.filter { c -> c.isDigit() } }, label = { Text("Port") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("Username (optional)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Password (optional)") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        isTesting = true; testResult = null
                        Thread {
                            try {
                                val c = org.eclipse.paho.client.mqttv3.MqttClient("tcp://$host:${port.toIntOrNull() ?: 1883}", "mqttwidgets_setup_${System.currentTimeMillis()}", org.eclipse.paho.client.mqttv3.persist.MemoryPersistence())
                                c.connect(org.eclipse.paho.client.mqttv3.MqttConnectOptions().apply { connectionTimeout = 10; isCleanSession = true })
                                c.disconnect()
                                testResult = "Broker connected"
                            } catch (e: Exception) { testResult = "Failed: ${e.message}" }
                            isTesting = false
                        }.start()
                    }, enabled = !isTesting && host.isNotEmpty()) {
                        if (isTesting) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp) else Text("Test")
                    }
                    Button(onClick = {
                        pm.setBrokerHost(host)
                        pm.setBrokerPort(port.toIntOrNull() ?: 1883)
                        pm.setUsername(username)
                        pm.setPassword(password)
                        pm.setWizardCompleted(true)
                        onComplete()
                    }, enabled = host.isNotEmpty()) { Text("Save & Continue") }
                }
                testResult?.let {
                    val color = if (it.startsWith("Broker")) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error
                    Text(it, color = color, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        TextButton(onClick = {
            pm.setWizardCompleted(true)
            onComplete()
        }) { Text("Skip for now") }
    }
}
