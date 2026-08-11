package com.mqttwidgets.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mqttwidgets.app.data.CardSize

@Composable
fun WidgetSizePreview(
    size: CardSize,
    label: String,
    value: String,
    time: String,
    fontSize: Float,
    modifier: Modifier = Modifier
) {
    val ratio = when (size) {
        CardSize.SMALL -> 1f
        CardSize.COMPACT -> 2f
        CardSize.WIDE -> 3f
    }
    val small = size == CardSize.SMALL
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .aspectRatio(ratio)
                .clip(RoundedCornerShape(if (small) 8.dp else 12.dp))
                .background(Color(0xFF2C2C2C))
                .padding(if (small) 10.dp else 12.dp),
            contentAlignment = if (small) Alignment.Center else Alignment.CenterStart
        ) {
            if (small) {
                Text(
                    value.ifBlank { "--" },
                    color = Color.White,
                    fontSize = fontSize.sp,
                    fontWeight = FontWeight.Bold
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(horizontalAlignment = Alignment.Start) {
                        Text(label.ifBlank { "Label" }, color = Color(0xB0FFFFFF), style = MaterialTheme.typography.labelSmall)
                        Text(
                            value.ifBlank { "--" },
                            color = Color.White,
                            fontSize = fontSize.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (size == CardSize.WIDE) {
                        Spacer(modifier = Modifier.weight(1f))
                        Text(time, color = Color(0xB0FFFFFF), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}