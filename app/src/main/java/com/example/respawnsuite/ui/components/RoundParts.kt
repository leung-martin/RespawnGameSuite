package com.example.respawnsuite.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.respawnsuite.ui.isLandscape
import com.example.respawnsuite.ui.theme.TerminalFont

/**
 * The pre-round clock every game mode walks out to: a big number over a
 * draining bar, with whatever the mode wants to say about the round underneath.
 */
@Composable
fun CountdownBody(
    countdownMillis: Long,
    totalMillis: Long,
    lines: List<String>,
    modifier: Modifier = Modifier,
    footer: String = "KEYPAD_LOCKED",
    landscape: Boolean = isLandscape()
) {
    val scheme = MaterialTheme.colorScheme
    val seconds = ((countdownMillis + 999L) / 1000L).toInt()
    val fraction = if (totalMillis <= 0L) 0f else countdownMillis.toFloat() / totalMillis

    val clock: @Composable ColumnScope.() -> Unit = {
        Text(
            text = "ROUND_STARTS_IN",
            color = scheme.secondary,
            style = MaterialTheme.typography.labelSmall
        )
        Spacer(Modifier.height(18.dp))
        Text(
            text = seconds.toString().padStart(2, '0'),
            color = scheme.primary,
            fontFamily = TerminalFont,
            fontWeight = FontWeight.Bold,
            // The number is the thing you read across a field, so it takes the
            // room the orientation can spare: on its side that is a half-width
            // column with the whole height to itself.
            fontSize = if (landscape) 150.sp else 132.sp,
            lineHeight = if (landscape) 158.sp else 140.sp
        )
        Spacer(Modifier.height(18.dp))
        AsciiProgressBar(fraction = fraction)
    }

    val briefing: @Composable ColumnScope.() -> Unit = {
        Text(
            text = "GET TO YOUR POSITIONS",
            color = scheme.primary,
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(20.dp))
        // Grouped so the prompt lines align with each other, not with the
        // centred headline above them.
        Column {
            lines.forEach { TerminalLine(it) }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = footer,
                color = scheme.tertiary,
                style = MaterialTheme.typography.labelLarge
            )
            BlinkingCursor(modifier = Modifier.padding(start = 6.dp))
        }
    }

    if (landscape) {
        // Clock left, briefing right: on its side there is no room to put a
        // hundred-point number above six lines of text.
        Row(
            modifier = modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                content = clock
            )
            Spacer(Modifier.width(20.dp))
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                content = briefing
            )
        }
    } else {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            clock()
            Spacer(Modifier.height(28.dp))
            briefing()
        }
    }
}

/**
 * `5 4 6 _ _ _ _` — digits typed so far against the length still expected, so a
 * player can see how much of a code is in. [note] names what the sequence is
 * heading for once that is unambiguous.
 */
@Composable
fun InputSequence(
    typed: String,
    codeLength: Int,
    modifier: Modifier = Modifier,
    note: String? = null
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .border(
                BorderStroke(
                    if (note != null) 2.dp else 1.dp,
                    if (note != null) scheme.primary else scheme.outline
                )
            )
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = scheme.secondary)) { append("> ") }
                for (slot in 0 until codeLength) {
                    if (slot > 0) append(" ")
                    if (slot < typed.length) {
                        withStyle(
                            SpanStyle(color = scheme.primary, fontWeight = FontWeight.Bold)
                        ) {
                            append(typed[slot])
                        }
                    } else {
                        withStyle(SpanStyle(color = scheme.outline)) { append("_") }
                    }
                }
            },
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1
        )
        if (note != null) {
            Text(
                text = ">> $note",
                color = scheme.primary,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1
            )
        } else {
            BlinkingCursor(modifier = Modifier.padding(start = 8.dp))
        }
    }
}
