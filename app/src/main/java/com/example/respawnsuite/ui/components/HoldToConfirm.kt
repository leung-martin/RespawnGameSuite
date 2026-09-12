package com.example.respawnsuite.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.example.respawnsuite.ui.theme.TerminalGray
import com.example.respawnsuite.ui.theme.TerminalGrayDim
import com.example.respawnsuite.ui.theme.TerminalGrayFaint
import com.example.respawnsuite.ui.theme.TerminalGreenFaint

/**
 * A press-and-hold bar. Nothing happens until the bar has been held for
 * [holdMillis], which is what makes it safe to leave on a locked game screen —
 * a brush against the glass cannot trigger it, and letting go resets it.
 */
@Composable
fun HoldToConfirm(
    label: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    holdMillis: Int = 2000,
    /**
     * True while a keypad key bound to this action is held down. It runs the
     * same fill and the same timing as a finger would, so the two ways of
     * confirming behave identically.
     */
    heldByKey: Boolean = false,
    /**
     * Draws the bar in grey rather than phosphor green. Used under a live round,
     * where ending early is always available but should never be the thing the
     * eye lands on.
     */
    muted: Boolean = false
) {
    val currentOnConfirm by rememberUpdatedState(onConfirm)
    var heldByTouch by remember { mutableStateOf(false) }
    val progress = remember { Animatable(0f) }
    val held = heldByTouch || heldByKey

    LaunchedEffect(held) {
        if (held) {
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = holdMillis, easing = LinearEasing)
            )
            currentOnConfirm()
        } else {
            progress.snapTo(0f)
        }
    }

    val contentColor = if (muted) TerminalGray else MaterialTheme.colorScheme.primary
    val borderColor = if (muted) TerminalGrayDim else MaterialTheme.colorScheme.outline
    val fillColor = if (muted) TerminalGrayFaint else TerminalGreenFaint

    // A rule and a footnote rather than a slab: on a landscape phone the height
    // this used to take was height the round needed, and "hold" is already said
    // by the bar filling under your thumb.
    Column(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        heldByTouch = true
                        tryAwaitRelease()
                        heldByTouch = false
                    }
                )
            }
            .padding(vertical = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(BAR_HEIGHT)
                .border(BorderStroke(1.dp, borderColor))
                // Fill sweeps left to right as the hold completes.
                .drawBehind {
                    drawRect(
                        color = fillColor,
                        size = Size(size.width * progress.value, size.height)
                    )
                }
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            color = contentColor,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

/** Thin enough to read as a rule, thick enough to watch fill. */
private val BAR_HEIGHT = 8.dp
