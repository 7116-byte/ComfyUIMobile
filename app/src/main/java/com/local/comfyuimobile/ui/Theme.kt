package com.local.comfyuimobile.ui

import android.app.Activity
import android.content.ContextWrapper
import androidx.core.view.WindowCompat
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val dark = darkColorScheme(
    primary = Color(0xFFB4C2FF),
    onPrimary = Color(0xFF182B72),
    primaryContainer = Color(0xFF263963),
    onPrimaryContainer = Color(0xFFE0E6FF),
    secondary = Color(0xFF8FD6C6),
    secondaryContainer = Color(0xFF203C36),
    background = Color(0xFF101318),
    surface = Color(0xFF191D25),
    surfaceVariant = Color(0xFF252B36),
    onSurface = Color(0xFFE9ECF3),
    onSurfaceVariant = Color(0xFFA8B2C5),
    outline = Color(0xFF687284),
    outlineVariant = Color(0xFF343D4C),
)

private val light = lightColorScheme(
    primary = Color(0xFF4D63CA),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE9EDFF),
    onPrimaryContainer = Color(0xFF263875),
    secondary = Color(0xFF277D6D),
    secondaryContainer = Color(0xFFE0F1EA),
    background = Color(0xFFF5F6FA),
    surface = Color.White,
    surfaceVariant = Color(0xFFEDF0F6),
    onSurface = Color(0xFF202737),
    onSurfaceVariant = Color(0xFF697387),
    outline = Color(0xFF8791A3),
    outlineVariant = Color(0xFFDCE1EB),
)

private val typography = Typography().let {
    it.copy(
        headlineLarge = it.headlineLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.6).sp),
        headlineMedium = it.headlineMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp),
        titleLarge = it.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = it.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        titleSmall = it.titleSmall.copy(fontWeight = FontWeight.Medium),
        bodySmall = it.bodySmall.copy(lineHeight = 18.sp),
    )
}

@Composable
fun ComfyMobileTheme(content: @Composable () -> Unit) {
    val darkTheme = isSystemInDarkTheme()
    val colors = if (darkTheme) dark else light
    val view = LocalView.current
    if (!view.isInEditMode) SideEffect {
        val activity = generateSequence(view.context) { (it as? ContextWrapper)?.baseContext }
            .filterIsInstance<Activity>().firstOrNull()
        activity?.window?.let { window ->
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
            @Suppress("DEPRECATION")
            window.statusBarColor = colors.background.toArgb()
            @Suppress("DEPRECATION")
            window.navigationBarColor = colors.background.toArgb()
        }
    }
    MaterialTheme(
        colorScheme = colors,
        typography = typography,
        shapes = Shapes(
            extraSmall = RoundedCornerShape(6.dp), small = RoundedCornerShape(10.dp),
            medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(24.dp), extraLarge = RoundedCornerShape(32.dp),
        ),
        content = content,
    )
}
