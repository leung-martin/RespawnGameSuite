package com.example.respawnsuite.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.respawnsuite.audio.HeartbeatIntervalMillis
import com.example.respawnsuite.ui.theme.TerminalFont
import com.example.respawnsuite.ui.theme.TerminalGreenDim
import kotlin.math.hypot
import kotlinx.coroutines.delay

/** A bordered panel, the visual unit everything in the suite is drawn inside. */
@Composable
fun TerminalPanel(
    modifier: Modifier = Modifier,
    borderColor: Color = MaterialTheme.colorScheme.outline,
    borderWidth: Int = 1,
    background: Color = Color.Transparent,
    contentPadding: Int = 12,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .border(BorderStroke(borderWidth.dp, borderColor))
            .background(background)
            .padding(contentPadding.dp)
    ) {
        content()
    }
}

/**
 * A single line of monospace text sized to fill the width it is given, up to
 * [maxFontSize]. The width is measured rather than estimated from a character
 * count, so it lands correctly whatever font and text size the device is set to.
 */
@Composable
fun FittedText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    fontWeight: FontWeight = FontWeight.Bold,
    maxFontSize: Int = 72
) {
    FittedText(
        text = AnnotatedString(text),
        modifier = modifier,
        color = color,
        fontWeight = fontWeight,
        maxFontSize = maxFontSize
    )
}

/**
 * As above, for text carrying its own spans — a code with the digits already
 * typed picked out, for instance. Sizing measures the plain characters, which
 * is what decides the width.
 */
@Composable
fun FittedText(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    fontWeight: FontWeight = FontWeight.Bold,
    maxFontSize: Int = 72
) {
    val measurer = rememberTextMeasurer()

    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val probeWidth = measurer.measure(
            text = AnnotatedString(text.text),
            style = TextStyle(
                fontFamily = TerminalFont,
                fontWeight = fontWeight,
                fontSize = PROBE_FONT_SIZE.sp,
                letterSpacing = 0.sp
            ),
            softWrap = false,
            maxLines = 1
        ).size.width.toFloat()

        val fitted = if (probeWidth <= 0f) {
            maxFontSize.toFloat()
        } else {
            PROBE_FONT_SIZE * (constraints.maxWidth / probeWidth) * FIT_MARGIN
        }

        Text(
            text = text,
            color = color,
            fontFamily = TerminalFont,
            fontWeight = fontWeight,
            fontSize = fitted.coerceIn(MIN_FITTED_FONT_SIZE, maxFontSize.toFloat()).sp,
            letterSpacing = 0.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false
        )
    }
}

/**
 * A line that starts at [maxFontSize] and steps down until it stops overflowing.
 *
 * Where [FittedText] sizes from a measurement of the space it expects, this one
 * reacts to the layout it actually got, which is what a value sitting in a
 * bordered box inside a narrow column needs: no arithmetic about padding can be
 * wrong, because nothing is being predicted.
 */
@Composable
fun ShrinkToFitText(
    text: String,
    maxFontSize: Int,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    minFontSize: Float = MIN_FITTED_FONT_SIZE,
    textAlign: TextAlign = TextAlign.Center,
    fontWeight: FontWeight = FontWeight.Bold
) {
    var size by remember(text, maxFontSize) { mutableStateOf(maxFontSize.toFloat()) }
    Text(
        text = text,
        modifier = modifier,
        color = color,
        fontFamily = TerminalFont,
        fontWeight = fontWeight,
        fontSize = size.sp,
        letterSpacing = 0.sp,
        textAlign = textAlign,
        maxLines = 1,
        softWrap = false,
        onTextLayout = { layout ->
            if (layout.hasVisualOverflow && size > minFontSize) {
                size = (size * SHRINK_STEP).coerceAtLeast(minFontSize)
            }
        }
    )
}

/** How much a line gives up each time it is found to be overflowing. */
private const val SHRINK_STEP = 0.92f

/** Reference size for width measurement; large enough to scale down precisely. */
private const val PROBE_FONT_SIZE = 100f

/**
 * Floor for measured text. Low enough that a value in a narrow column at a
 * large system font scale shrinks rather than running past its box.
 */
private const val MIN_FITTED_FONT_SIZE = 9f

/** Leaves a hair of slack so rounding can never clip the last glyph. */
private const val FIT_MARGIN = 0.99f

/** Type size for the character-drawn rules and bars. */
private const val RULE_FONT_SIZE = 12

/** Width of one monospace cell at [fontSize], in pixels, as actually rendered. */
@Composable
private fun monospaceCellPx(fontSize: Int): Float {
    val measurer = rememberTextMeasurer()
    return measurer.measure(
        text = AnnotatedString("#"),
        style = TextStyle(
            fontFamily = TerminalFont,
            fontSize = fontSize.sp,
            letterSpacing = 0.sp
        ),
        softWrap = false,
        maxLines = 1
    ).size.width.toFloat().coerceAtLeast(1f)
}

/**
 * How long the caret spends on, then off. A full blink is two of these — kept
 * equal to the audio heartbeat, so the beep lands as the caret appears.
 */
private val CURSOR_PHASE_MILLIS = HeartbeatIntervalMillis / 2

/**
 * A caret that blinks the way a real console caret does: on, then off, with no
 * fade between — a hardware terminal has no half-lit state.
 */
@Composable
fun BlinkingCursor(
    modifier: Modifier = Modifier,
    glyph: String = "_",
    color: Color = MaterialTheme.colorScheme.primary,
    style: TextStyle = MaterialTheme.typography.bodyLarge
) {
    var visible by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(CURSOR_PHASE_MILLIS)
            visible = !visible
        }
    }
    Text(
        text = glyph,
        modifier = modifier,
        color = if (visible) color else Color.Transparent,
        style = style
    )
}

/** `> some status line` — the running commentary down the side of a screen. */
@Composable
fun TerminalLine(
    text: String,
    modifier: Modifier = Modifier,
    prompt: String = ">",
    color: Color = TerminalGreenDim
) {
    Text(
        text = "$prompt $text",
        modifier = modifier,
        color = color,
        style = MaterialTheme.typography.bodySmall
    )
}

/**
 * One selectable row of a terminal menu. The selected row wears a caret as well
 * as the inverted fill, so which row ENTER will take is readable at a glance
 * even at arm's length in daylight.
 */
@Composable
fun TerminalMenuRow(
    label: String,
    enabled: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    note: String? = null
) {
    val scheme = MaterialTheme.colorScheme
    val contentColor = when {
        !enabled -> scheme.outlineVariant
        selected -> scheme.onPrimary
        else -> scheme.primary
    }
    val borderColor = when {
        !enabled -> scheme.outlineVariant
        selected -> scheme.primary
        else -> scheme.outline
    }
    val fill = if (selected && enabled) scheme.primary else Color.Transparent

    Row(
        modifier = modifier
            .fillMaxWidth()
            .border(BorderStroke(1.dp, borderColor))
            .background(fill)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // The name owns the row and the tag takes what is left, so a long name
        // on a narrow screen can never push the tag into wrapping on top of it.
        Text(
            text = (if (selected) "> " else "  ") + label,
            color = contentColor,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (note != null) {
            Text(
                text = note,
                color = contentColor,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

/** A full-width rule made of a repeated character. */
@Composable
fun TerminalDivider(
    modifier: Modifier = Modifier,
    glyph: Char = '=',
    color: Color = TerminalGreenDim
) {
    val cellPx = monospaceCellPx(RULE_FONT_SIZE)
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val count = (constraints.maxWidth / cellPx).toInt().coerceIn(8, 400)
        Text(
            text = glyph.toString().repeat(count),
            color = color,
            fontFamily = TerminalFont,
            fontSize = RULE_FONT_SIZE.sp,
            maxLines = 1,
            softWrap = false
        )
    }
}

/** Standard screen chrome: black ground, uniform padding, vertical stack. */
@Composable
fun TerminalScreen(
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        content = content
    )
}

/**
 * A section heading above a block of settings: `-- TEAM_COUNT`. A [focused]
 * heading swaps its dashes for a caret, which is how a keypad-driven form says
 * "this is the setting the next keypress lands on".
 */
@Composable
fun TerminalSectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    focused: Boolean = false
) {
    Text(
        text = (if (focused) ">> " else "-- ") + text,
        modifier = modifier,
        color = color,
        style = MaterialTheme.typography.labelLarge
    )
}

/**
 * A small square-cornered option box. Selected boxes invert to solid green.
 * [compact] trims the padding for a screen on its side, where the whole form has
 * about four hundred dp of height to live in and nothing may be cut off.
 */
@Composable
fun TerminalChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    compact: Boolean = false
) {
    val scheme = MaterialTheme.colorScheme
    val contentColor = when {
        !enabled -> scheme.outlineVariant
        selected -> scheme.onPrimary
        else -> scheme.primary
    }
    Box(
        modifier = modifier
            .border(
                BorderStroke(1.dp, if (enabled) scheme.outline else scheme.outlineVariant)
            )
            .background(if (selected && enabled) scheme.primary else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(
                horizontal = if (compact) 10.dp else 14.dp,
                vertical = if (compact) 7.dp else 12.dp
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = contentColor,
            style = MaterialTheme.typography.titleMedium
        )
    }
}

/**
 * The big primary action at the bottom of a screen. A tap fires it; so does
 * holding the keypad key bound to it for [holdMillis], which sweeps a bar
 * across the button so the hold is visibly doing something.
 */
@Composable
fun TerminalButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    /** True while the keypad key bound to this button is held down. */
    heldByKey: Boolean = false,
    holdMillis: Int = 1200,
    compact: Boolean = false
) {
    val scheme = MaterialTheme.colorScheme
    val currentOnClick by rememberUpdatedState(onClick)
    val progress = remember { Animatable(0f) }

    LaunchedEffect(heldByKey, enabled) {
        if (heldByKey && enabled) {
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = holdMillis, easing = LinearEasing)
            )
            currentOnClick()
        } else {
            progress.snapTo(0f)
        }
    }

    Box(
        modifier = modifier
            .border(BorderStroke(2.dp, if (enabled) scheme.primary else scheme.outlineVariant))
            .background(if (enabled) scheme.primary else Color.Transparent)
            // Darkens left to right as the hold completes: the button charging
            // up rather than draining away.
            .drawBehind {
                if (progress.value > 0f) {
                    drawRect(
                        color = scheme.background.copy(alpha = 0.5f),
                        size = Size(size.width * progress.value, size.height)
                    )
                }
            }
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = if (compact) 13.dp else 20.dp),
        contentAlignment = Alignment.Center
    ) {
        // The label keeps to one line whatever width the button is given: a
        // two-line INITIATE SEQUENCE looks like a mistake, not a button.
        ShrinkToFitText(
            text = label,
            maxFontSize = 22,
            color = if (enabled) scheme.onPrimary else scheme.outlineVariant
        )
    }
}

/** `[-] 010 MIN [+]` — integer stepper for numeric settings. */
@Composable
fun TerminalStepper(
    value: String,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    modifier: Modifier = Modifier,
    decrementEnabled: Boolean = true,
    incrementEnabled: Boolean = true,
    compact: Boolean = false
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp)
    ) {
        TerminalChip(
            label = "-",
            selected = false,
            enabled = decrementEnabled,
            onClick = onDecrement,
            compact = compact
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline))
                .padding(
                    horizontal = 6.dp,
                    vertical = if (compact) 7.dp else 12.dp
                ),
            contentAlignment = Alignment.Center
        ) {
            // Sized against its own box rather than styled: in a narrow column,
            // or at a large system font scale, `7 DIGITS` gives up a point or
            // two rather than running past its own border.
            ShrinkToFitText(
                text = value,
                maxFontSize = if (compact) 18 else 22
            )
        }
        TerminalChip(
            label = "+",
            selected = false,
            enabled = incrementEnabled,
            onClick = onIncrement,
            compact = compact
        )
    }
}

/**
 * `[########------------]` — a progress bar drawn out of characters, so it
 * belongs to the same world as everything else on screen. [fraction] is clamped
 * to 0..1 and the bar sizes itself to the available width.
 */
@Composable
fun AsciiProgressBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    filledGlyph: Char = '#',
    emptyGlyph: Char = '-',
    filledColor: Color = MaterialTheme.colorScheme.primary,
    emptyColor: Color = TerminalGreenDim
) {
    val cellPx = monospaceCellPx(RULE_FONT_SIZE)
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        // Two cells go to the brackets; the rest is bar.
        val cells = ((constraints.maxWidth / cellPx).toInt() - 2).coerceIn(4, 400)
        val filled = (cells * fraction.coerceIn(0f, 1f)).toInt()
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = emptyColor)) { append("[") }
                withStyle(SpanStyle(color = filledColor)) {
                    append(filledGlyph.toString().repeat(filled))
                }
                withStyle(SpanStyle(color = emptyColor)) {
                    append(emptyGlyph.toString().repeat(cells - filled))
                    append("]")
                }
            },
            fontFamily = TerminalFont,
            fontSize = RULE_FONT_SIZE.sp,
            maxLines = 1,
            softWrap = false
        )
    }
}

/** Type size of the burst characters. Chunky reads better than fine. */
private const val BURST_FONT_SIZE = 22

/** How long the wave takes to reach the edges, then how long it lingers. */
private const val BURST_FILL_MILLIS = 550
private const val BURST_FADE_MILLIS = 650

/**
 * A blast of characters from the middle of the screen outward, for the moment a
 * round ends. Drawn as text on a character grid rather than with graphics, so
 * the explosion belongs to the same terminal as everything else.
 */
@Composable
fun HashBurst(
    modifier: Modifier = Modifier,
    glyph: Char = '#',
    color: Color = MaterialTheme.colorScheme.primary
) {
    val fill = remember { Animatable(0f) }
    val alpha = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        fill.animateTo(1f, tween(BURST_FILL_MILLIS, easing = LinearEasing))
        alpha.animateTo(0f, tween(BURST_FADE_MILLIS, easing = LinearEasing))
    }
    if (alpha.value <= 0f) return

    val cellPx = monospaceCellPx(BURST_FONT_SIZE)
    // A monospace cell is about 0.6em wide, which is enough to work back to the
    // line height without measuring twice.
    val linePx = cellPx / 0.6f * 1.15f

    BoxWithConstraints(modifier = modifier) {
        val columns = (constraints.maxWidth / cellPx).toInt().coerceIn(1, 200)
        val rows = (constraints.maxHeight / linePx).toInt().coerceIn(1, 200)

        Text(
            text = burstFrame(columns, rows, fill.value, glyph),
            color = color.copy(alpha = alpha.value),
            fontFamily = TerminalFont,
            fontSize = BURST_FONT_SIZE.sp,
            lineHeight = (BURST_FONT_SIZE * 1.15f).sp,
            letterSpacing = 0.sp,
            softWrap = false
        )
    }
}

/**
 * One frame of the blast: every cell inside the wavefront is lit, with the
 * ragged edge coming from a per-cell threshold rather than a clean circle.
 */
private fun burstFrame(columns: Int, rows: Int, fill: Float, glyph: Char): String {
    val centreX = (columns - 1) / 2f
    val centreY = (rows - 1) / 2f
    val maxDistance = hypot(centreX, centreY).coerceAtLeast(1f)

    return buildString(rows * (columns + 1)) {
        for (row in 0 until rows) {
            for (column in 0 until columns) {
                val distance = hypot(column - centreX, row - centreY) / maxDistance
                // Deterministic per-cell jitter: the same cell always frays at
                // the same point, so the edge crawls outward instead of boiling.
                val jitter = ((row * 31 + column * 17) % 7) / 28f
                append(if (distance <= fill - jitter) glyph else ' ')
            }
            if (row < rows - 1) append('\n')
        }
    }
}
