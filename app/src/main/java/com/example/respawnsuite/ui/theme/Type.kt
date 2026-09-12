package com.example.respawnsuite.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.respawnsuite.R

/**
 * The suite ships its own monospace face rather than asking for
 * [FontFamily.Monospace]. That alias resolves to whatever the device calls
 * "monospace", and on phones that have no monospace font installed — Samsung
 * among them — it silently falls back to the proportional system font. Every
 * aligned column, character-drawn rule and progress bar in this app assumes
 * fixed-width cells, so the font cannot be left to chance.
 */
val TerminalFont = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_bold, FontWeight.Bold)
)

/** Everything in this app is a terminal, so everything is monospace. */
private fun terminalStyle(
    size: Int,
    weight: FontWeight = FontWeight.Normal,
    // Fixed-width type is already open; tracking only costs width, which is
    // scarce on a narrow screen at a large accessibility font scale.
    tracking: Double = 0.0
) = TextStyle(
    fontFamily = TerminalFont,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = (size * 1.4).sp,
    letterSpacing = tracking.sp
)

/**
 * Every slot is filled deliberately: an unset style falls back to Material's
 * default, which would bring the proportional system font back in through the
 * one screen that happened to use it.
 */
val Typography = Typography(
    displayLarge = terminalStyle(34, FontWeight.Bold),
    displayMedium = terminalStyle(30, FontWeight.Bold),
    displaySmall = terminalStyle(26, FontWeight.Bold),
    headlineLarge = terminalStyle(26, FontWeight.Bold),
    headlineMedium = terminalStyle(22, FontWeight.Bold),
    headlineSmall = terminalStyle(20, FontWeight.Bold),
    titleLarge = terminalStyle(18, FontWeight.Bold),
    titleMedium = terminalStyle(16, FontWeight.Bold),
    titleSmall = terminalStyle(14, FontWeight.Bold),
    bodyLarge = terminalStyle(16),
    bodyMedium = terminalStyle(14),
    bodySmall = terminalStyle(12),
    labelLarge = terminalStyle(14, FontWeight.Bold),
    labelMedium = terminalStyle(12, FontWeight.Bold),
    labelSmall = terminalStyle(11)
)
