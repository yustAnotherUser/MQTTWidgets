package com.mqttwidgets.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class CardSize {
    SMALL, COMPACT, WIDE
}

enum class CardFormat {
    RAW, NUMBER, JSON
}

@Entity(tableName = "cards")
data class Card(
    @PrimaryKey
    val cardId: String,
    val label: String,
    val topic: String,
    val size: CardSize,
    val format: CardFormat,
    val jsonPath: String = "",
    val decimals: Int = 2,
    val unit: String = "",
    val highThreshold: Double = Double.MAX_VALUE,
    val lowThreshold: Double = Double.MIN_VALUE,
    val highColor: Int = 0xFFFF4444.toInt(),
    val normalColor: Int = 0xFF4CAF50.toInt(),
    val lowColor: Int = 0xFF2196F3.toInt(),
    val lastValue: String = "",
    val lastFormattedValue: String = "",
    val lastUpdated: Long = 0L,
    val consecutiveFailures: Int = 0,
    val pinnedWidgetIds: String = ""
)
