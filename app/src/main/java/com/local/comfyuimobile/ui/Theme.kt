package com.local.comfyuimobile.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val dark = darkColorScheme(
    primary = Color(0xFFB9ADFF),
    onPrimary = Color(0xFF271A74),
    primaryContainer = Color(0xFF3E3290),
    secondary = Color(0xFF65DCC6),
    background = Color(0xFF111318),
    surface = Color(0xFF191B21),
    surfaceVariant = Color(0xFF252830),
)

private val light = lightColorScheme(
    primary = Color(0xFF5747B8),
    secondary = Color(0xFF006B5E),
    background = Color(0xFFF7F6FC),
    surface = Color.White,
)

@Composable
fun ComfyMobileTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) dark else light, content = content)
}
