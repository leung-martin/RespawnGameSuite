package com.example.respawnsuite.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val TerminalColorScheme = darkColorScheme(
    primary = TerminalGreen,
    onPrimary = TerminalBlack,
    primaryContainer = TerminalGreenFaint,
    onPrimaryContainer = TerminalGreen,
    secondary = TerminalGreenDim,
    onSecondary = TerminalBlack,
    tertiary = TerminalAmber,
    onTertiary = TerminalBlack,
    background = TerminalBlack,
    onBackground = TerminalGreen,
    surface = TerminalBlack,
    onSurface = TerminalGreen,
    surfaceVariant = TerminalBlack,
    onSurfaceVariant = TerminalGreenDim,
    outline = TerminalGreenDim,
    outlineVariant = TerminalGreenFaint,
    error = TerminalRed,
    onError = TerminalBlack
)

/** No dynamic color, no light mode. The terminal looks the same everywhere. */
@Composable
fun RespawnSuiteTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = TerminalColorScheme,
        typography = Typography,
        content = content
    )
}
