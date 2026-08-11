package com.mqttwidgets.app.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.mqttwidgets.app.R
import com.mqttwidgets.app.data.AppDatabase
import com.mqttwidgets.app.data.Card
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class WidgetProviderSmall : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        WidgetUpdater.renderWidgets(context, appWidgetManager, appWidgetIds, "SMALL")
    }
    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        for (id in appWidgetIds) { context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE).edit().remove("widget_$id").apply() }
        super.onDeleted(context, appWidgetIds)
    }
}

class WidgetProviderCompact : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        WidgetUpdater.renderWidgets(context, appWidgetManager, appWidgetIds, "COMPACT")
    }
    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        for (id in appWidgetIds) { context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE).edit().remove("widget_$id").apply() }
        super.onDeleted(context, appWidgetIds)
    }
}

class WidgetProviderWide : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        WidgetUpdater.renderWidgets(context, appWidgetManager, appWidgetIds, "WIDE")
    }
    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        for (id in appWidgetIds) { context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE).edit().remove("widget_$id").apply() }
        super.onDeleted(context, appWidgetIds)
    }
}

object WidgetUpdater {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun renderWidgets(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray, size: String) {
        val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
        val layoutRes = when (size) {
            "SMALL" -> R.layout.widget_small
            "COMPACT" -> R.layout.widget_compact
            "WIDE" -> R.layout.widget_wide
            else -> R.layout.widget_small
        }

        for (appWidgetId in appWidgetIds) {
            val prefix = "widget_${appWidgetId}_"
            val label = prefs.getString("${prefix}label", "") ?: ""
            val value = prefs.getString("${prefix}value", "--") ?: "--"
            val time = prefs.getString("${prefix}time", "--:--") ?: "--:--"
            val bgColor = prefs.getInt("${prefix}bgColor", 0xFF424242.toInt())
            val statusColor = prefs.getInt("${prefix}statusColor", 0xFF43A047.toInt())

            val views = RemoteViews(context.packageName, layoutRes)
            views.setTextViewText(R.id.widget_value, value)
            views.setImageViewBitmap(R.id.widget_bg, roundedBackgroundBitmap(bgColor, context.resources.displayMetrics.density))
            val fontSize = prefs.getFloat("${prefix}fontSize", 0f)
            if (fontSize > 0f) {
                views.setTextViewTextSize(R.id.widget_value, android.util.TypedValue.COMPLEX_UNIT_SP, fontSize)
            }
            if (size != "SMALL") { views.setTextViewText(R.id.widget_label, label) }
            if (size == "WIDE") {
                views.setTextViewText(R.id.widget_time, time)
            }

            val intent = Intent(context, com.mqttwidgets.app.MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pi = android.app.PendingIntent.getActivity(context, appWidgetId, intent, android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.widget_container, pi)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    fun linkPendingCard(context: Context, appWidgetId: Int, cardId: String) {
        scope.launch {
            try {
                val db = AppDatabase.getDatabase(context)
                val card = db.cardDao().getCardById(cardId) ?: return@launch
                val existingIds = card.pinnedWidgetIds.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableSet()
                existingIds.add(appWidgetId.toString())
                db.cardDao().updatePinnedWidgetIds(card.cardId, existingIds.joinToString(","))
                saveWidgetConfig(context, appWidgetId, card)
                val mgr = AppWidgetManager.getInstance(context) ?: return@launch
                val size = when (card.size) {
                    com.mqttwidgets.app.data.CardSize.SMALL -> "SMALL"
                    com.mqttwidgets.app.data.CardSize.COMPACT -> "COMPACT"
                    com.mqttwidgets.app.data.CardSize.WIDE -> "WIDE"
                }
                renderWidgets(context, mgr, intArrayOf(appWidgetId), size)
            } catch (_: Exception) {}
        }
    }

    fun saveWidgetConfig(context: Context, appWidgetId: Int, card: Card) {
        val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
        val prefix = "widget_${appWidgetId}_"
        prefs.edit().apply {
            putString("${prefix}label", card.label)
            putString("${prefix}topic", card.topic)
            putString("${prefix}value", card.lastFormattedValue.ifEmpty { "--" })
            putString("${prefix}time", formatTime(card.lastUpdated))
            putInt("${prefix}bgColor", com.mqttwidgets.app.util.WidgetRenderer.resolveColor(card.lastValue, card))
            putInt("${prefix}statusColor", if (card.consecutiveFailures == 0) 0xFF43A047.toInt() else 0xFFE53935.toInt())
            apply()
        }
    }

    fun getWidgetFontSize(context: Context, card: Card): Float {
        val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
        val pinnedIds = card.pinnedWidgetIds.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()
        val normTopic = card.topic.trim().removePrefix("/")
        val mgr = AppWidgetManager.getInstance(context) ?: return 20f
        val providers = listOf(
            ComponentName(context, WidgetProviderSmall::class.java),
            ComponentName(context, WidgetProviderCompact::class.java),
            ComponentName(context, WidgetProviderWide::class.java)
        )
        for (provider in providers) {
            val hosted = try { mgr.getAppWidgetIds(provider) ?: intArrayOf() } catch (_: Exception) { intArrayOf() }
            for (id in hosted) {
                val storedTopic = prefs.getString("widget_${id}_topic", null)
                val matches = id in pinnedIds ||
                    storedTopic == null ||
                    storedTopic.isBlank() ||
                    storedTopic.trim().removePrefix("/") == normTopic
                if (!matches) continue
                return prefs.getFloat("widget_${id}_fontSize", 0f).takeIf { it > 0f } ?: 20f
            }
        }
        return 20f
    }

    fun applyFontSizeToCardWidgets(context: Context, card: Card, sizeSp: Float): Int {
        val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
        val pinnedIds = card.pinnedWidgetIds.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()
        val normTopic = card.topic.trim().removePrefix("/")
        val mgr = AppWidgetManager.getInstance(context) ?: return 0
        val content = com.mqttwidgets.app.util.WidgetRenderer.render(card)
        val providers = listOf(
            Triple(ComponentName(context, WidgetProviderSmall::class.java), R.layout.widget_small, "SMALL"),
            Triple(ComponentName(context, WidgetProviderCompact::class.java), R.layout.widget_compact, "COMPACT"),
            Triple(ComponentName(context, WidgetProviderWide::class.java), R.layout.widget_wide, "WIDE")
        )
        var updated = 0
        for ((provider, layoutRes, size) in providers) {
            val hosted = try {
                mgr.getAppWidgetIds(provider) ?: intArrayOf()
            } catch (e: Exception) {
                android.util.Log.e("MQTTWidgets", "getAppWidgetIds failed for $provider", e)
                intArrayOf()
            }
            for (id in hosted) {
                val storedTopic = prefs.getString("widget_${id}_topic", null)
                val matches = id in pinnedIds ||
                    storedTopic == null ||
                    storedTopic.isBlank() ||
                    storedTopic.trim().removePrefix("/") == normTopic
                if (!matches) continue
                prefs.edit().putFloat("widget_${id}_fontSize", sizeSp).apply()
                try {
                    val views = RemoteViews(context.packageName, layoutRes)
                    views.setTextViewText(R.id.widget_value, content.displayText)
                    views.setImageViewBitmap(R.id.widget_bg, roundedBackgroundBitmap(content.backgroundColor, context.resources.displayMetrics.density))
                    views.setTextViewTextSize(R.id.widget_value, android.util.TypedValue.COMPLEX_UNIT_SP, sizeSp)
                    if (size != "SMALL") { views.setTextViewText(R.id.widget_label, content.labelText) }
                    if (size == "WIDE") { views.setTextViewText(R.id.widget_time, content.timeText) }
                    val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    launchIntent?.let {
                        val pi = android.app.PendingIntent.getActivity(context, 0, it, android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
                        views.setOnClickPendingIntent(R.id.widget_container, pi)
                    }
                    mgr.updateAppWidget(id, views)
                    updated++
                } catch (e: Exception) {
                    android.util.Log.e("MQTTWidgets", "render failed for widget $id", e)
                }
            }
        }
        return updated
    }

    fun roundedBackgroundBitmap(color: Int, density: Float): android.graphics.Bitmap {
        val sizePx = (128 * density).toInt().coerceAtLeast(192)
        val radiusPx = (sizePx / 8).coerceAtLeast(8)
        val bmp = android.graphics.Bitmap.createBitmap(sizePx, sizePx, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bmp)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { setColor(color) }
        canvas.drawRoundRect(0f, 0f, sizePx.toFloat(), sizePx.toFloat(), radiusPx.toFloat(), radiusPx.toFloat(), paint)
        return bmp
    }

    private fun formatTime(ts: Long): String {
        if (ts == 0L) return "--:--"
        val sdf = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(ts))
    }
}
