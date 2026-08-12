package com.mqttwidgets.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mqttwidgets.app.data.Card
import com.mqttwidgets.app.util.WidgetRenderer

@Composable
fun WidgetPreview(card: Card, modifier: Modifier = Modifier) {
    val content = remember(card.lastFormattedValue, card.lastUpdated, card.consecutiveFailures) {
        WidgetRenderer.render(card)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Color(content.backgroundColor))
            .padding(horizontal = if (card.size.name == "SMALL") 20.dp else 12.dp, vertical = if (card.size.name == "SMALL") 20.dp else 8.dp)
    ) {
        Column(horizontalAlignment = if (card.size.name == "SMALL") Alignment.CenterHorizontally else Alignment.Start) {
            if (card.size.name != "SMALL") {
                Text(card.label, color = Color(0xB0FFFFFF), style = MaterialTheme.typography.labelSmall)
            }
            Text(
                content.displayText,
                color = Color.White,
                style = if (card.size.name == "SMALL") MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            if (card.size.name == "WIDE") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(content.timeText, color = Color(0xB0FFFFFF), style = MaterialTheme.typography.labelSmall)
                    Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(Color(content.statusColor)))
                }
            }
        }
    }
}
