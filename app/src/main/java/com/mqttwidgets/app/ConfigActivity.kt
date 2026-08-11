package com.mqttwidgets.app

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
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

        val prefix = "widget_${widgetId}_"
        val initialSize = prefs.getFloat("${prefix}fontSize", 0f)
            .takeIf { it > 0f } ?: 20f

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 40, 48, 40)
            gravity = Gravity.CENTER_HORIZONTAL
        }

        root.addView(TextView(this).apply {
            text = "Widget font size"
            textSize = 20f
            setTextColor(0xFFE2E8F0.toInt())
            setPadding(0, 0, 0, 8)
        })

        root.addView(TextView(this).apply {
            text = "Adjust the size of the value text"
            textSize = 13f
            setTextColor(0xFF94A3B8.toInt())
            setPadding(0, 0, 0, 24)
        })

        val previewBg = GradientDrawable().apply {
            cornerRadius = 12f * resources.displayMetrics.density
            setColor(0xFF2C2C2C.toInt())
        }
        val preview = FrameLayout(this).apply {
            background = previewBg
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (88 * resources.displayMetrics.density).toInt()
            )
        }
        val previewText = TextView(this).apply {
            text = "25.4 °C"
            textSize = initialSize
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        preview.addView(previewText, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))
        root.addView(preview)

        val sizeLabel = TextView(this).apply {
            text = "${initialSize.toInt()} sp"
            textSize = 14f
            setTextColor(0xFFE2E8F0.toInt())
            gravity = Gravity.CENTER
            setPadding(0, 16, 0, 8)
        }
        root.addView(sizeLabel)

        val seekBar = SeekBar(this).apply {
            max = 30
            progress = (initialSize - 10).toInt().coerceIn(0, 30)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val sp = progress + 10f
                    previewText.textSize = sp
                    sizeLabel.text = "${sp.toInt()} sp"
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        root.addView(seekBar)

        root.addView(Button(this).apply {
            text = "Save"
            setOnClickListener {
                val sp = seekBar.progress + 10f
                prefs.edit().putFloat("${prefix}fontSize", sp).apply()
                renderWidget(widgetId)
                val resultIntent = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                setResult(RESULT_OK, resultIntent)
                finish()
            }
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = 24 })

        window.decorView.setBackgroundColor(0xFF0F172A.toInt())
        setContentView(root)
    }

    private fun renderWidget(widgetId: Int) {
        val manager = AppWidgetManager.getInstance(this) ?: return
        val info = manager.getAppWidgetInfo(widgetId)
        val size = when {
            info?.provider?.className?.contains("WidgetProviderCompact") == true -> "COMPACT"
            info?.provider?.className?.contains("WidgetProviderWide") == true -> "WIDE"
            else -> "SMALL"
        }
        WidgetUpdater.renderWidgets(this, manager, intArrayOf(widgetId), size)
    }
}
