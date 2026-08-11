package com.mqttwidgets.app.ui.screens

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mqttwidgets.app.data.AppDatabase
import com.mqttwidgets.app.data.Card
import com.mqttwidgets.app.data.CardSize
import com.mqttwidgets.app.service.MqttService
import com.mqttwidgets.app.ui.components.CreateWidgetDialog
import com.mqttwidgets.app.ui.components.EditWidgetDialog
import com.mqttwidgets.app.ui.components.WidgetCard
import com.mqttwidgets.app.widget.WidgetUpdater
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(modifier: Modifier = Modifier, showCreateDialog: Boolean, onDismissCreateDialog: () -> Unit) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getDatabase(context) }
    val cards by db.cardDao().getAllCards().collectAsState(initial = emptyList())
    var editingCardId by remember { mutableStateOf<String?>(null) }
    val editingCard = editingCardId?.let { id -> cards.find { it.cardId == id } }
    val scope = rememberCoroutineScope()

    fun startMqttService() {
        val intent = Intent(context, MqttService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun requestPinWidget(componentName: ComponentName, width: Int) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        if (appWidgetManager.isRequestPinAppWidgetSupported) {
            appWidgetManager.requestPinAppWidget(componentName, null, null)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (cards.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("No widgets yet", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Tap + to create your first widget", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(cards, key = { it.cardId }) { card ->
                    WidgetCard(
                        card = card,
                        onClick = { editingCardId = card.cardId },
                        onPin = {
                            val component = when (card.size) {
                                CardSize.SMALL -> ComponentName(context, "com.mqttwidgets.app.widget.WidgetProviderSmall")
                                CardSize.COMPACT -> ComponentName(context, "com.mqttwidgets.app.widget.WidgetProviderCompact")
                                CardSize.WIDE -> ComponentName(context, "com.mqttwidgets.app.widget.WidgetProviderWide")
                            }
                            val width = when (card.size) {
                                CardSize.SMALL -> 80
                                CardSize.COMPACT -> 250
                                CardSize.WIDE -> 320
                            }
                            context.getSharedPreferences("widget_prefs", android.content.Context.MODE_PRIVATE)
                                .edit().putString("pending_card_id", card.cardId).apply()
                            requestPinWidget(component, width)
                        },
                        onDelete = {
                            scope.launch {
                                db.cardDao().deleteCard(card.cardId)
                                context.sendBroadcast(Intent("com.mqttwidgets.TOPICS_CHANGED"))
                            }
                        }
                    )
                }
            }
        }

        if (showCreateDialog) {
            CreateWidgetDialog(
                onDismiss = onDismissCreateDialog,
                onCreate = { newCard ->
                    scope.launch {
                        db.cardDao().insertCard(newCard)
                        context.sendBroadcast(Intent("com.mqttwidgets.TOPICS_CHANGED"))
                        startMqttService()
                        onDismissCreateDialog()
                    }
                }
            )
        }

        editingCard?.let { card ->
            EditWidgetDialog(
                card = card,
                initialFontSize = WidgetUpdater.getWidgetFontSize(context, card),
                onDismiss = { editingCardId = null },
                onSave = { updatedCard ->
                    scope.launch {
                        db.cardDao().updateCard(updatedCard)
                        context.sendBroadcast(Intent("com.mqttwidgets.TOPICS_CHANGED"))
                        editingCardId = null
                    }
                },
                onSaveFontSize = { size -> WidgetUpdater.setPinnedWidgetFontSize(context, card.cardId, size) },
                onDelete = {
                    scope.launch {
                        db.cardDao().deleteCard(card.cardId)
                        context.sendBroadcast(Intent("com.mqttwidgets.TOPICS_CHANGED"))
                        editingCardId = null
                    }
                }
            )
        }
    }
}
