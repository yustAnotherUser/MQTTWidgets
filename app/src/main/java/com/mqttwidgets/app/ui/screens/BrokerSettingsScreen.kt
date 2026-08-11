package com.mqttwidgets.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.mqttwidgets.app.data.PrefsManager
import com.mqttwidgets.app.service.MqttService

@Composable
fun BrokerSettingsScreen(modifier: Modifier = Modifier, onBack: () -> Unit) {
    val context = LocalContext.current
    val pm = remember { PrefsManager(context) }
    var host by remember { mutableStateOf(pm.getBrokerHost()) }
    var port by remember { mutableStateOf(pm.getBrokerPort().toString()) }
    var username by remember { mutableStateOf(pm.getUsername()) }
    var password by remember { mutableStateOf(pm.getPassword()) }
    var testState by remember { mutableStateOf<String?>(null) }
    var isTesting by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("MQTT Broker Settings", style = MaterialTheme.typography.headlineSmall)

        OutlinedTextField(value = host, onValueChange = { host = it }, label = { Text("Host") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = port, onValueChange = { port = it.filter { c -> c.isDigit() } }, label = { Text("Port") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("Username (optional)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Password (optional)") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                isTesting = true; testState = null
                Thread {
                    try {
                        val client = org.eclipse.paho.client.mqttv3.MqttClient("tcp://$host:${port.toIntOrNull() ?: 1883}", "mqttwidgets_test_${System.currentTimeMillis()}", org.eclipse.paho.client.mqttv3.persist.MemoryPersistence())
                        client.connect(org.eclipse.paho.client.mqttv3.MqttConnectOptions().apply { connectionTimeout = 10; isCleanSession = true })
                        client.disconnect()
                        testState = "Broker connected"
                    } catch (e: Exception) { testState = "Failed: ${e.message}" }
                    isTesting = false
                }.start()
            }, enabled = !isTesting && host.isNotEmpty()) {
                if (isTesting) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp) else Text("Test Connection")
            }

            Button(onClick = {
                pm.setBrokerHost(host)
                pm.setBrokerPort(port.toIntOrNull() ?: 1883)
                pm.setUsername(username)
                pm.setPassword(password)
                context.sendBroadcast(android.content.Intent("com.mqttwidgets.TOPICS_CHANGED"))
                onBack()
            }, enabled = host.isNotEmpty()) { Text("Save") }
        }

        testState?.let {
            val color = if (it.startsWith("Broker")) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error
            Text(it, color = color, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
