@file:OptIn(ExperimentalMaterial3Api::class)

package com.mqttwidgets.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mqttwidgets.app.data.PrefsManager
import com.mqttwidgets.app.ui.screens.*
import com.mqttwidgets.app.ui.theme.MQTTWidgetsTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MQTTWidgetsTheme {
                val context = this@MainActivity
                val pm = remember { PrefsManager(context) }
                var wizardCompleted by remember { mutableStateOf(pm.hasWizardCompleted()) }
                var currentScreen by remember { mutableStateOf(Screen.HOME) }
                var showCreateDialog by remember { mutableStateOf(false) }
                val drawerState = rememberDrawerState(DrawerValue.Closed)
                val scope = rememberCoroutineScope()

                if (!wizardCompleted) {
                    FirstRunScreen(onComplete = { wizardCompleted = true })
                } else {
                    ModalNavigationDrawer(
                        drawerState = drawerState,
                        drawerContent = {
                            ModalDrawerSheet {
                                Spacer(modifier = Modifier.height(24.dp))
                                Text("MQTT Widgets", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                                HorizontalDivider()
                                NavigationDrawerItem(label = { Text("Broker Settings") }, selected = currentScreen == Screen.BROKER, onClick = { currentScreen = Screen.BROKER; scope.launch { drawerState.close() } }, modifier = Modifier.padding(horizontal = 12.dp))
                                NavigationDrawerItem(label = { Text("About") }, selected = currentScreen == Screen.ABOUT, onClick = { currentScreen = Screen.ABOUT; scope.launch { drawerState.close() } }, modifier = Modifier.padding(horizontal = 12.dp))
                            }
                        }
                    ) {
                        Scaffold(
                            topBar = {
                                TopAppBar(
                                    title = { Text("MQTT Widgets") },
                                    navigationIcon = {
                                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                            Icon(Icons.Default.Menu, contentDescription = "Menu")
                                        }
                                    },
                                    actions = {
                                        if (currentScreen == Screen.HOME) {
                                            IconButton(onClick = { showCreateDialog = true }) {
                                                Icon(Icons.Default.Add, contentDescription = "Add Widget")
                                            }
                                        }
                                    }
                                )
                            }
                        ) { innerPadding ->
                            when (currentScreen) {
                                Screen.HOME -> HomeScreen(
                                    modifier = Modifier.padding(innerPadding),
                                    showCreateDialog = showCreateDialog,
                                    onDismissCreateDialog = { showCreateDialog = false }
                                )
                                Screen.BROKER -> BrokerSettingsScreen(
                                    modifier = Modifier.padding(innerPadding),
                                    onBack = { currentScreen = Screen.HOME }
                                )
                                Screen.ABOUT -> AboutScreen(modifier = Modifier.padding(innerPadding))
                                Screen.CREATE -> currentScreen = Screen.HOME
                            }
                        }
                    }
                }
            }
        }
    }
}

enum class Screen { HOME, BROKER, ABOUT, CREATE }
