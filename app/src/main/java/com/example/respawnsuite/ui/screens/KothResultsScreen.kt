package com.example.respawnsuite.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import com.example.respawnsuite.game.koth.KothOutcome
import com.example.respawnsuite.game.koth.hillWinners
import com.example.respawnsuite.game.koth.KothResult
import com.example.respawnsuite.game.formatClock
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
 * End of round, whether the clock ran out or the round was aborted early. The
 * screen stays locked here — the scores are the point of the whole exercise, so
 * leaving takes the same deliberate hold as aborting.
 */
@Composable
fun KothResultsScreen(
    config: RoundConfig,
    outcome: KothOutcome,
    onPlayAgain: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    // The round is over, and everyone within earshot should know it.
    val sfx = rememberSfxPlayer()
    var cueStarted by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        sfx.play(Sfx.ROUND_OVER)
        cueStarted = true
    }

    // Once the bang has died away the suite goes back to its idle pulse, the
    // same one the menu keeps: the scores are up, the speaker is still alive.
    if (cueStarted && !sfx.sounding) {
        AudioHeartbeat()
    }

    val aborted = outcome.aborted
    val remainingMillis = outcome.remainingMillis
    val ranked = outcome.results.sortedByDescending { it.heldMillis }
    val winners = hillWinners(config.mode, outcome.results, outcome.finalHolder)

    // The same two actions on the keypad: hold 0 to run it back, hold ENTER to
    // leave. A tap does nothing — this screen only takes holds.
    var heldKey by remember { mutableStateOf<KeypadEvent?>(null) }

    val headline: @Composable () -> Unit = {
        Column {
            Text(
                text = "//GAME_MODE: " + config.mode.displayName,
                color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = if (aborted) {
                    "//ROUND_ABORTED: " + formatClock(remainingMillis) + " UNPLAYED"
                } else {
                    "//ROUND_COMPLETE: " + config.roundMinutes + " MIN"
                },
                color = if (aborted) {
                    MaterialTheme.colorScheme.tertiary
                } else {
                    MaterialTheme.colorScheme.secondary
                },
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }

    val winnerPanel: @Composable () -> Unit = {
        TerminalPanel(
            modifier = Modifier.fillMaxWidth(),
            borderColor = MaterialTheme.colorScheme.primary,
            borderWidth = 3,
            contentPadding = 18
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = when {
                        winners.isEmpty() && config.mode == GameMode.COUNTDOWN -> "HILL_UNHELD:"
                        winners.isEmpty() -> "NO_CAPTURES:"
                        winners.size > 1 -> "TIE:"
                        else -> "WINNER:"
                    },
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = when {
                        winners.isEmpty() -> "NOBODY"
                        else -> winners.joinToString(" / ") { it.team }
                    },
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.displayLarge
                )
            }
        }
    }

    val table: @Composable ColumnScope.() -> Unit = {
        TerminalLine("win condition: " + config.mode.winCondition)
        TerminalLine(
            when {
                // In countdown the table is context, not the verdict, so say so.
                config.mode == GameMode.COUNTDOWN -> "hold time by team // for reference:"
                aborted -> "hold time by team // round cut short:"
                else -> "hold time by team:"
            }
        )
        Spacer(Modifier.height(10.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ranked.forEachIndexed { position, result ->
                // Whoever was on the hill at the end is marked either way; in
                // countdown that is the whole result, in king of the hill it is
                // just a useful last fact about the round.
                val heldAtEnd = result.team == outcome.finalHolder
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = (position + 1).toString() + ". " + result.team +
                            if (heldAtEnd) " <<" else "",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = formatClock(result.heldMillis),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }

    // Straight back into the countdown on the same settings: between rounds
    // nobody wants to walk the setup screen again to change nothing.
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
        // Verdict on the left, the reckoning and the two exits on the right.
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
                    table()
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
            table()
            actions()
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun KothResultsPreview() {
    RespawnSuiteTheme {
        KothResultsScreen(
            config = RoundConfig(teamCount = 3),
            outcome = KothOutcome(
                results = listOf(
                    KothResult("ALPHA", 245_000L),
                    KothResult("BRAVO", 310_000L),
                    KothResult("CHARLIE", 45_000L)
                ),
                finalHolder = "BRAVO",
                aborted = false,
                remainingMillis = 0L
            ),
            onPlayAgain = {},
            onExit = {}
        )
    }
}
