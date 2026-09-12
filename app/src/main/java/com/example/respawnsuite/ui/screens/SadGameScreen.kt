package com.example.respawnsuite.ui.screens

import android.os.SystemClock
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.respawnsuite.audio.Sfx
import com.example.respawnsuite.audio.Tone
import com.example.respawnsuite.audio.rememberSfxPlayer
import com.example.respawnsuite.audio.rememberToneEngine
import com.example.respawnsuite.game.RoundConfig
import com.example.respawnsuite.game.GameMode
import com.example.respawnsuite.game.formatClock
import com.example.respawnsuite.game.sad.SadGameState
import com.example.respawnsuite.game.sad.SadOutcome
import com.example.respawnsuite.game.sad.SadPhase
import com.example.respawnsuite.game.sad.SadTeamNames
import com.example.respawnsuite.input.KeypadEvent
import com.example.respawnsuite.input.keypadInput
import com.example.respawnsuite.ui.isLandscape
import com.example.respawnsuite.ui.components.AsciiProgressBar
import com.example.respawnsuite.ui.components.CountdownBody
import com.example.respawnsuite.ui.components.FittedText
import com.example.respawnsuite.ui.components.HoldToConfirm
import com.example.respawnsuite.ui.components.TerminalLine
import com.example.respawnsuite.ui.components.TerminalPanel
import com.example.respawnsuite.ui.components.TerminalScreen
import com.example.respawnsuite.ui.components.TerminalSectionLabel
import com.example.respawnsuite.ui.theme.RespawnSuiteTheme
import kotlinx.coroutines.delay

private const val TICK_MILLIS = 200L

/** Lit backing behind digits already typed into a code. */
private const val HIGHLIGHT_ALPHA = 0.25f

/** On, then off: a hard blink, like everything else that blinks in this suite. */
private const val ARMED_FLASH_MILLIS = 450L

/**
 * A bomb round. Attack carries the phone with the arm code on it; once planted,
 * the fuse runs and the disarm code appears for defence.
 *
 * The keypad answers in both phases; the arming cue is what announces the bomb
 * to everyone else, and it only starts once the bomb is down.
 */
@Composable
fun SadGameScreen(
    config: RoundConfig,
    onEnd: (SadOutcome) -> Unit,
    modifier: Modifier = Modifier
) {
    val state = remember(config) { SadGameState(config) }
    val currentOnEnd by rememberUpdatedState(onEnd)
    val tones = rememberToneEngine()
    val sfx = rememberSfxPlayer()

    // Pre-round countdown beeps as usual; nothing sounds during the carry.
    LaunchedEffect(state) {
        while (state.phase == SadPhase.COUNTDOWN) {
            val remaining = state.countdownMillis
            if (remaining <= 0L) break
            tones.play(com.example.respawnsuite.audio.CountdownCadence.tone(remaining))
            delay(com.example.respawnsuite.audio.CountdownCadence.intervalMillis(remaining))
        }
    }

    // The arming cue is the fuse. A fuse shorter than the recording starts the
    // cue that far in, so the recording still ends on the detonation.
    LaunchedEffect(state.phase) {
        if (state.phase == SadPhase.ARMED) {
            sfx.play(
                asset = Sfx.FINAL_COUNTDOWN,
                startMillis = (Sfx.FINAL_COUNTDOWN_MILLIS - config.fuseMillis).coerceAtLeast(0L)
            )
        }
    }

    LaunchedEffect(state) {
        state.start(SystemClock.elapsedRealtime())
        while (!state.finished) {
            delay(TICK_MILLIS)
            state.tick(SystemClock.elapsedRealtime())
        }
        currentOnEnd(state.outcome())
    }

    fun abort() {
        state.abort(SystemClock.elapsedRealtime())
        currentOnEnd(state.outcome())
    }

    // Held ENTER ends the round, running the same two-second fill the abort
    // bar uses under a finger.
    var heldKey by remember { mutableStateOf<KeypadEvent?>(null) }

    // An armed bomb pulses the whole screen edge in amber. From across a room
    // that is the only thing anyone needs to read.
    val armed = state.phase == SadPhase.ARMED
    var flash by remember { mutableStateOf(false) }
    LaunchedEffect(armed) {
        if (!armed) {
            flash = false
            return@LaunchedEffect
        }
        while (true) {
            flash = !flash
            delay(ARMED_FLASH_MILLIS)
        }
    }

    TerminalScreen(
        modifier = modifier
            .fillMaxSize()
            .border(
                BorderStroke(
                    if (armed) 6.dp else 0.dp,
                    if (armed && flash) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        Color.Transparent
                    }
                )
            )
            .keypadInput(
                onPress = { event, pressed -> heldKey = if (pressed) event else null }
            ) { event ->
                when (event) {
                    is KeypadEvent.Digit -> {
                        val phaseBefore = state.phase
                        state.onDigit(event.value)
                        // The keypad talks back in both phases: whoever is
                        // typing needs to know the digit landed, and by the
                        // time anyone is at the keypad they have been found.
                        when {
                            phaseBefore == SadPhase.COUNTDOWN -> Unit
                            // Planted, or defused: both are the big moment.
                            state.phase != phaseBefore ->
                                tones.play(Tone.CaptureLow, Tone.CaptureHigh)

                            state.input.isEmpty() -> tones.play(Tone.KeyReject)
                            else -> tones.play(Tone.KeyPress)
                        }
                        true
                    }

                    KeypadEvent.Clear -> {
                        state.clearInput()
                        true
                    }

                    KeypadEvent.Back -> true

                    // Navigation keys have nothing to steer in a live round;
                    // they are swallowed so a stray press cannot leak out.
                    KeypadEvent.Enter, KeypadEvent.Up, KeypadEvent.Down,
                    KeypadEvent.Left, KeypadEvent.Right,
                    KeypadEvent.Plus, KeypadEvent.Minus -> true
                }
            }
    ) {
        Text(
            text = "//GAME_MODE: " + GameMode.SEARCH_AND_DESTROY.displayName,
            color = MaterialTheme.colorScheme.secondary,
            style = MaterialTheme.typography.labelSmall
        )

        when (state.phase) {
            SadPhase.COUNTDOWN -> CountdownBody(
                countdownMillis = state.countdownMillis,
                totalMillis = config.countdownMillis,
                lines = listOf(
                    "sides .......... ATTACK v DEFENCE",
                    "plant window ... " + config.roundMinutes + " min",
                    "fuse ........... " + config.fuseSeconds + " sec"
                ),
                modifier = Modifier.weight(1f)
            )

            else -> BombBody(state = state, config = config, modifier = Modifier.weight(1f))
        }

        Spacer(Modifier.height(12.dp))
        HoldToConfirm(
            label = "ENTER TO END ROUND",
            onConfirm = ::abort,
            heldByKey = heldKey == KeypadEvent.Enter,
            muted = true
        )
    }
}

/** Carry phase and fuse phase share a shape: a clock, a code, and the prompt. */
@Composable
private fun BombBody(
    state: SadGameState,
    config: RoundConfig,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val armed = state.phase == SadPhase.ARMED
    val clockMillis = state.remainingMillis
    val totalMillis = if (armed) config.fuseMillis else config.roundMillis
    val code = if (armed) state.disarmCode.orEmpty() else state.armCode

    val codeColor = if (armed) scheme.tertiary else scheme.primary
    val typed = state.input.takeIf { code.startsWith(it) }.orEmpty()

    val clock: @Composable () -> Unit = {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (armed) "FUSE" else "TIME_TO_PLANT",
                    color = scheme.secondary,
                    style = MaterialTheme.typography.labelSmall
                )
                Text(
                    text = formatClock(clockMillis),
                    // Amber once it is ticking: this clock ends in an explosion.
                    color = codeColor,
                    style = MaterialTheme.typography.headlineMedium
                )
            }
            Spacer(Modifier.height(6.dp))
            AsciiProgressBar(
                fraction = if (totalMillis <= 0L) 0f else clockMillis.toFloat() / totalMillis,
                filledColor = codeColor
            )
        }
    }

    val status: @Composable () -> Unit = {
        TerminalPanel(
            modifier = Modifier.fillMaxWidth(),
            borderColor = codeColor,
            borderWidth = 3,
            contentPadding = 16
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (armed) "BOMB_ARMED" else "BOMB_INACTIVE",
                    color = scheme.secondary,
                    style = MaterialTheme.typography.labelSmall
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = if (armed) SadTeamNames[1] + " TO DEFUSE" else SadTeamNames[0] +
                        " TO PLANT",
                    color = codeColor,
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }

    // The code is the one thing someone reads off this screen at a run, so it
    // gets the whole width it is given. Digits already typed are lifted out of
    // it the same way the hill modes mark a capture in progress.
    val codePanel: @Composable (Int) -> Unit = { maxFontSize ->
        Column(modifier = Modifier.fillMaxWidth()) {
            TerminalSectionLabel(if (armed) "DISARM_CODE" else "ARM_CODE")
            Spacer(Modifier.height(8.dp))
            FittedText(
                text = buildAnnotatedString {
                    withStyle(
                        SpanStyle(
                            background = codeColor.copy(alpha = HIGHLIGHT_ALPHA),
                            textDecoration = TextDecoration.Underline
                        )
                    ) {
                        append(code.take(typed.length))
                    }
                    append(code.drop(typed.length))
                },
                modifier = Modifier.fillMaxWidth(),
                color = codeColor,
                maxFontSize = maxFontSize
            )
        }
    }

    val prompt: @Composable () -> Unit = {
        TerminalLine(
            if (armed) "defence: type the disarm code" else "attack: type the arm code",
            prompt = "#"
        )
    }

    // A code left half-typed does not wait: the window drains in view of
    // whoever started it, and when it empties the code is scrambled.
    val armWindow: @Composable () -> Unit = {
        val left = state.armWindowMillis
        if (left != null) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Spacer(Modifier.height(10.dp))
                TerminalSectionLabel(
                    "FINISH_CODE // " + ((left + 999L) / 1000L) + "s",
                    color = scheme.tertiary
                )
                Spacer(Modifier.height(6.dp))
                AsciiProgressBar(
                    fraction = state.armWindowFraction,
                    filledColor = scheme.tertiary
                )
            }
        }
    }

    if (isLandscape()) {
        Row(modifier = modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                clock()
                Spacer(Modifier.height(14.dp))
                status()
                Spacer(Modifier.weight(1f))
                prompt()
            }
            Spacer(Modifier.width(16.dp))
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.Center
            ) {
                codePanel(56)
                armWindow()
            }
        }
    } else {
        Column(modifier = modifier.fillMaxWidth()) {
            Spacer(Modifier.height(10.dp))
            clock()
            Spacer(Modifier.height(14.dp))
            status()
            Spacer(Modifier.height(16.dp))
            codePanel(64)
            armWindow()
            Spacer(Modifier.weight(1f))
            prompt()
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun SadGamePreview() {
    RespawnSuiteTheme {
        SadGameScreen(
            config = RoundConfig(mode = GameMode.SEARCH_AND_DESTROY, countdownSeconds = 0),
            onEnd = {}
        )
    }
}
