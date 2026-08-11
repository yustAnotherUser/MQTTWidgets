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

    fun updateAllWidgets(context: Context) {
        val mgr = AppWidgetManager.getInstance(context)
        val small = mgr.getAppWidgetIds(ComponentName(context, WidgetProviderSmall::class.java))
        if (small.isNotEmpty()) renderWidgets(context, mgr, small, "SMALL")
        val compact = mgr.getAppWidgetIds(ComponentName(context, WidgetProviderCompact::class.java))
        if (compact.isNotEmpty()) renderWidgets(context, mgr, compact, "COMPACT")
        val wide = mgr.getAppWidgetIds(ComponentName(context, WidgetProviderWide::class.java))
        if (wide.isNotEmpty()) renderWidgets(context, mgr, wide, "WIDE")
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

    fun roundedBackgroundBitmap(color: Int, density: Float): android.graphics.Bitmap {
        val sizePx = (128 * density).toInt().coerceAtLeast(192)
        val radiusPx = (24 * density).toInt()
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
