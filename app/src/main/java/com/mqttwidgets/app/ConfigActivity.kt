package com.mqttwidgets.app

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.mqttwidgets.app.data.AppDatabase
import com.mqttwidgets.app.widget.WidgetUpdater
import kotlinx.coroutines.runBlocking

class ConfigActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val widgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            setResult(RESULT_CANCELED)
            finish()
            return
        }

        val prefs = getSharedPreferences("widget_prefs", MODE_PRIVATE)
        val pendingCardId = prefs.getString("pending_card_id", null)

        if (pendingCardId != null) {
            runBlocking {
                val db = AppDatabase.getDatabase(this@ConfigActivity)
                val card = db.cardDao().getCardById(pendingCardId)
                if (card != null) {
                    val existingIds = card.pinnedWidgetIds
                        .split(",")
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .toMutableSet()
                    existingIds.add(widgetId.toString())
                    db.cardDao().updatePinnedWidgetIds(card.cardId, existingIds.joinToString(","))
                    WidgetUpdater.saveWidgetConfig(this@ConfigActivity, widgetId, card)
                }
            }
            prefs.edit().remove("pending_card_id").apply()
        }

        val resultIntent = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        setResult(RESULT_OK, resultIntent)
        finish()
    }
}
