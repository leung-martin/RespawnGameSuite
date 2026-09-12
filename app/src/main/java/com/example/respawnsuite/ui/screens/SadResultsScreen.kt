package com.example.respawnsuite.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.respawnsuite.audio.AudioHeartbeat
import com.example.respawnsuite.audio.Sfx
import com.example.respawnsuite.audio.rememberSfxPlayer
import com.example.respawnsuite.game.GameMode
import com.example.respawnsuite.game.RoundConfig
import com.example.respawnsuite.game.formatClock
import com.example.respawnsuite.game.sad.SadEnding
import com.example.respawnsuite.game.sad.SadOutcome
import com.example.respawnsuite.input.KeypadEvent
import com.example.respawnsuite.input.keypadInput
import com.example.respawnsuite.ui.isLandscape
import com.example.respawnsuite.ui.components.HoldToConfirm
import com.example.respawnsuite.ui.components.TerminalDivider
import com.example.respawnsuite.ui.components.TerminalLine
import com.example.respawnsuite.ui.components.TerminalPanel
import com.example.respawnsuite.ui.components.TerminalScreen
import com.example.respawnsuite.ui.theme.RespawnSuiteTheme

/**
 * How the bomb round ended. The cue matches the verdict — a detonation and a
 * defusal should never sound the same.
 */
@Composable
fun SadResultsScreen(
    config: RoundConfig,
    outcome: SadOutcome,
    onPlayAgain: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sfx = rememberSfxPlayer()
    var cueStarted by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        when (outcome.ending) {
            SadEnding.DETONATED -> sfx.play(Sfx.ROUND_OVER)
            SadEnding.DEFUSED -> sfx.play(Sfx.DEFUSED)
            // A round that never got planted, or was called off, ends quietly.
            SadEnding.NOT_PLANTED, SadEnding.ABORTED -> Unit
        }
        cueStarted = true
    }

    // Then the idle pulse, once whatever ended the round has finished
    // sounding — a quiet ending simply starts it straight away.
    if (cueStarted && !sfx.sounding) {
        AudioHeartbeat()
    }

    val scheme = MaterialTheme.colorScheme
    val detonated = outcome.ending == SadEnding.DETONATED

    // The same two actions on the keypad: hold 0 to run it back, hold ENTER to
    // leave. A tap does nothing — this screen only takes holds.
    var heldKey by remember { mutableStateOf<KeypadEvent?>(null) }

    val headline: @Composable () -> Unit = {
        Column {
            Text(
                text = "//GAME_MODE: " + GameMode.SEARCH_AND_DESTROY.displayName,
                color = scheme.secondary,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "//" + when (outcome.ending) {
                    SadEnding.DETONATED -> "DETONATED"
                    SadEnding.DEFUSED -> "DEFUSED WITH " +
                        formatClock(outcome.remainingMillis) + " ON THE FUSE"
                    SadEnding.NOT_PLANTED -> "NEVER PLANTED"
                    SadEnding.ABORTED -> "ROUND_ABORTED"
                },
                color = if (detonated || outcome.ending == SadEnding.ABORTED) {
                    scheme.tertiary
                } else {
                    scheme.secondary
                },
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }

    val winnerPanel: @Composable () -> Unit = {
        TerminalPanel(
            modifier = Modifier.fillMaxWidth(),
            borderColor = if (detonated) scheme.tertiary else scheme.primary,
            borderWidth = 3,
            contentPadding = 18
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (outcome.winner == null) "NO_RESULT:" else "WINNER:",
                    color = scheme.primary,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = outcome.winner ?: "NOBODY",
                    color = scheme.primary,
                    style = MaterialTheme.typography.displayLarge
                )
            }
        }
    }

    val verdict: @Composable () -> Unit = {
        Column {
            TerminalLine("win condition: " + config.mode.winCondition)
            TerminalLine(
                when (outcome.ending) {
                    SadEnding.DETONATED -> "the fuse ran out with the bomb in place"
                    SadEnding.DEFUSED -> "defence found the code in time"
                    SadEnding.NOT_PLANTED -> "the plant window closed, bomb never armed"
                    SadEnding.ABORTED -> "called off by the marshal"
                }
            )
        }
    }

    val actions: @Composable () -> Unit = {
        Column(modifier = Modifier.fillMaxWidth()) {
            TerminalDivider()
            Spacer(Modifier.height(10.dp))
            HoldToConfirm(
                label = "0 TO RUN IT BACK",
                onConfirm = onPlayAgain,
                heldByKey = heldKey == KeypadEvent.Digit(0)
            )
            Spacer(Modifier.height(8.dp))
            HoldToConfirm(
                label = "ENTER TO EXIT",
                onConfirm = onExit,
                heldByKey = heldKey == KeypadEvent.Enter
            )
        }
    }

    val screenModifier = modifier
        .fillMaxSize()
        .keypadInput(
            onPress = { event, pressed -> heldKey = if (pressed) event else null },
            onEvent = { true }
        )

    if (isLandscape()) {
        TerminalScreen(modifier = screenModifier) {
            Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    headline()
                    Spacer(Modifier.weight(1f))
                    winnerPanel()
                    Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    verdict()
                    Spacer(Modifier.weight(1f))
                    actions()
                }
            }
        }
    } else {
        TerminalScreen(modifier = screenModifier) {
            headline()
            Spacer(Modifier.height(18.dp))
            winnerPanel()
            Spacer(Modifier.height(18.dp))
            verdict()
            Spacer(Modifier.weight(1f))
            actions()
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun SadResultsPreview() {
    RespawnSuiteTheme {
        SadResultsScreen(
            config = RoundConfig(mode = GameMode.SEARCH_AND_DESTROY),
            outcome = SadOutcome(
                ending = SadEnding.DEFUSED,
                remainingMillis = 7_000L
            ),
            onPlayAgain = {},
            onExit = {}
        )
    }
}
