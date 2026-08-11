package com.mqttwidgets.app.util

import com.mqttwidgets.app.data.Card
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class WidgetContent(
    val backgroundColor: Int,
    val displayText: String,
    val labelText: String,
    val timeText: String,
    val statusColor: Int
)

object WidgetRenderer {

    private const val COLOR_GREEN = 0xFF4CAF50.toInt()
    private const val COLOR_RED = 0xFFFF4444.toInt()
    private const val COLOR_GRAY = 0xFF9E9E9E.toInt()
    private const val COLOR_DEFAULT_BG = 0xFF2C2C2C.toInt()

    fun resolveColor(value: String, card: Card): Int {
        return PayloadParser.resolveColor(value, card)
    }

    fun render(card: Card): WidgetContent {
        val backgroundColor = if (card.lastValue.isNotBlank() && PayloadParser.extractNumber(card.lastValue, card) != null) {
            PayloadParser.resolveColor(card.lastValue, card)
        } else {
            COLOR_DEFAULT_BG
        }

        val displayText = card.lastFormattedValue.ifBlank { "--" }

        val labelText = card.label

        val timeText = if (card.lastUpdated > 0L) {
            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
            sdf.format(Date(card.lastUpdated))
        } else {
            "never"
        }

        val statusColor = if (card.lastValue.isNotBlank() && card.consecutiveFailures == 0) {
            COLOR_GREEN
        } else {
            COLOR_RED
        }

        return WidgetContent(
            backgroundColor = backgroundColor,
            displayText = displayText,
            labelText = labelText,
            timeText = timeText,
            statusColor = statusColor
        )
    }
}
