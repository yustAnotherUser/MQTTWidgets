package com.mqttwidgets.app.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = MQTTBlue,
    onPrimary = Color.White,
    secondary = MQTTGreen,
    background = MQTTDark,
    surface = MQTTSurface,
    onBackground = MQTTOnSurface,
    onSurface = MQTTOnSurface,
    onSurfaceVariant = MQTTOnSurfaceVariant,
    error = MQTTRed
)

@Composable
fun MQTTWidgetsTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColorScheme, content = content)
}
