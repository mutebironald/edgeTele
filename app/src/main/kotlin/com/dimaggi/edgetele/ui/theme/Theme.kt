package com.dimaggi.edgetele.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Brand green — resilience, life, field
val EdgeTeleGreen = Color(0xFF2E7D32)
val EdgeTeleGreenLight = Color(0xFF60AD5E)
val EdgeTeleGreenDark = Color(0xFF005005)

val ConfidenceGreen = Color(0xFF2E7D32)
val ConfidenceYellow = Color(0xFFF57F17)
val ConfidenceRed = Color(0xFFC62828)

val SurfaceOffWhite = Color(0xFFF5F7F5)
val OnSurfaceDark = Color(0xFF1A1C1A)

private val LightColorScheme = lightColorScheme(
    primary = EdgeTeleGreen,
    onPrimary = Color.White,
    primaryContainer = EdgeTeleGreenLight,
    onPrimaryContainer = EdgeTeleGreenDark,
    secondary = Color(0xFF526350),
    onSecondary = Color.White,
    background = SurfaceOffWhite,
    onBackground = OnSurfaceDark,
    surface = Color.White,
    onSurface = OnSurfaceDark,
    error = ConfidenceRed,
    onError = Color.White
)

@Composable
fun EdgeTeleTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = MaterialTheme.typography,
        content = content
    )
}
