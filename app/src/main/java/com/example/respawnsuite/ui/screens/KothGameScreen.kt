package com.example.respawnsuite.ui.screens

import android.os.SystemClock
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.respawnsuite.audio.AudioHeartbeat
import com.example.respawnsuite.audio.CountdownCadence
import com.example.respawnsuite.audio.Sfx
import com.example.respawnsuite.audio.rememberMusicPlayer
import com.example.respawnsuite.audio.rememberSfxPlayer
import com.example.respawnsuite.audio.Tone
import com.example.respawnsuite.audio.rememberToneEngine
import com.example.respawnsuite.game.RoundConfig
import com.example.respawnsuite.game.koth.KothGameState
import com.example.respawnsuite.game.koth.KothPhase
import com.example.respawnsuite.game.koth.KothOutcome
import com.example.respawnsuite.game.formatClock
import com.example.respawnsuite.input.KeypadEvent
import com.example.respawnsuite.input.keypadInput
import com.example.respawnsuite.ui.isLandscape
import com.example.respawnsuite.ui.components.AsciiProgressBar
import com.example.respawnsuite.ui.components.BlinkingCursor
import com.example.respawnsuite.ui.components.CountdownBody
import com.example.respawnsuite.ui.components.HoldToConfirm
import com.example.respawnsuite.ui.components.TerminalLine
import com.example.respawnsuite.ui.components.TerminalPanel
import com.example.respawnsuite.ui.components.TerminalScreen
import com.example.respawnsuite.ui.components.TerminalSectionLabel
import com.example.respawnsuite.ui.theme.RespawnSuiteTheme
import com.example.respawnsuite.ui.theme.TerminalFont
import com.example.respawnsuite.ui.theme.TerminalGreenFaint
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.delay

private const val TICK_MILLIS = 200L

/** Longest callsign, so names and codes line up in the monospace column. */
private const val NAME_COLUMN = 7

/**
 * The live round, plus the countdown that precedes it. The only touch target is
 * the hold-to-abort bar; scoring is keypad-only, so a stray tap can never change
 * who holds the hill.
 *
 * [onEnd] fires for both endings — clock expiry and an early abort — because a
 * round that was cut short still has hill time worth showing.
 */
@Composable
fun KothGameScreen(
    config: RoundConfig,
    onEnd: (KothOutcome) -> Unit,
    modifier: Modifier = Modifier
) {
    val state = remember(config) { KothGameState(config) }
    val currentOnEnd by rememberUpdatedState(onEnd)
    val tones = rememberToneEngine()

    // Countdown beeps, accelerating as zero approaches. Driven off the clock
    // rather than the displayed second so the pacing can be finer than 1Hz.
    LaunchedEffect(state) {
        while (state.phase == KothPhase.COUNTDOWN) {
            val remaining = state.countdownMillis
            if (remaining <= 0L) break
            tones.play(CountdownCadence.tone(remaining))
            delay(CountdownCadence.intervalMillis(remaining))
        }
    }

    // One long tone the instant the hill goes live.
    LaunchedEffect(state.phase) {
        if (state.phase == KothPhase.RUNNING && config.countdownSeconds > 0) {
            tones.play(Tone.RoundStart)
        }
    }

    // The holding team's music plays for exactly as long as they hold. Keyed on
    // holder and phase, so it starts on capture, hands over on a steal, and
    // stops when the round ends or is aborted.
    val music = rememberMusicPlayer()
    val sfx = rememberSfxPlayer()
    LaunchedEffect(state.holder, state.phase) {
        val track = state.holder
            ?.takeIf { state.phase == KothPhase.RUNNING }
            ?.let { config.trackFor(it) }
        if (track.isNullOrEmpty()) music.stop() else music.play(track)
    }

    // The pulse fills whatever silence the round leaves it — an uncontested
    // hill, or a held hill with music off — and steps aside the moment a track
    // or the final-45 cue takes over, so there is only ever one thing sounding.
    if (state.phase == KothPhase.RUNNING && !music.sounding && !sfx.sounding) {
        AudioHeartbeat(tones = tones)
    }

    // A round ends exactly once. Aborting also stops the clock, so without this
    // the tick loop would report the same round a second time as a normal
    // completion and overwrite the abort's results.
    val ended = remember(state) { AtomicBoolean(false) }

    fun finish(aborted: Boolean) {
        if (ended.compareAndSet(false, true)) {
            currentOnEnd(state.outcome(aborted))
        }
    }

    // Wall-clock driven so the round cannot drift, however busy the UI gets.
    LaunchedEffect(state) {
        state.start(SystemClock.elapsedRealtime())
        var finalCueStarted = false
        while (!state.finished) {
            delay(TICK_MILLIS)
            state.tick(SystemClock.elapsedRealtime())

            // The cue is exactly as long as the stretch it covers, so starting
            // it when that much clock remains lands its end on zero.
            if (!finalCueStarted &&
                state.phase == KothPhase.RUNNING &&
                state.remainingMillis <= Sfx.FINAL_COUNTDOWN_MILLIS
            ) {
                finalCueStarted = true
                sfx.play(Sfx.FINAL_COUNTDOWN)
            }

            // Music ducks away under the cue rather than fighting it. Applied
            // every tick, which is fine enough to be inaudible as steps.
            if (state.phase == KothPhase.RUNNING) {
                music.setLevel(Sfx.musicLevelFor(state.remainingMillis))
            }
        }
        finish(aborted = false)
    }

    fun abort() {
        state.abort(SystemClock.elapsedRealtime())
        finish(aborted = true)
    }

    // Held ENTER ends the round, running the same two-second fill the abort
    // bar uses under a finger.
    var heldKey by remember { mutableStateOf<KeypadEvent?>(null) }

    val abortLabel = "ENTER TO END ROUND"

    TerminalScreen(
        modifier = modifier
            .fillMaxSize()
            .keypadInput(
                onPress = { event, pressed -> heldKey = if (pressed) event else null }
            ) { event ->
                when (event) {
                    is KeypadEvent.Digit -> {
                        val holderBefore = state.holder
                        val live = state.phase == KothPhase.RUNNING
                        state.onDigit(event.value)
                        // The tone tells the player what the digit did without
                        // them having to look at the phone.
                        when {
                            !live -> Unit
                            state.holder != holderBefore ->
                                tones.play(Tone.CaptureLow, Tone.CaptureHigh)

                            state.input.isEmpty() ->
                                tones.play(Tone.KeyReject)

                            else -> tones.play(Tone.KeyPress)
                        }
                        true
                    }

                    KeypadEvent.Clear -> {
                        if (state.input.isNotEmpty()) tones.play(Tone.KeyReject)
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
            text = "//GAME_MODE: " + config.mode.displayName,
            color = MaterialTheme.colorScheme.secondary,
            style = MaterialTheme.typography.labelSmall
        )

        if (state.phase == KothPhase.COUNTDOWN) {
            CountdownBody(
                countdownMillis = state.countdownMillis,
                totalMillis = config.countdownMillis,
                lines = listOf(
                    "teams .......... " + config.teamCount,
                    "round timer .... " + config.roundMinutes + " min",
                    "music mode ..... " + if (config.musicMode) "ON" else "OFF"
                ),
                modifier = Modifier.weight(1f)
            )
        } else {
            RoundBody(state = state, config = config, modifier = Modifier.weight(1f))
        }

        Spacer(Modifier.height(12.dp))
        HoldToConfirm(
            label = abortLabel,
            onConfirm = ::abort,
            heldByKey = heldKey == KeypadEvent.Enter,
            muted = true
        )
    }
}

/**
 * The live round: clock, holder, codes and the input prompt. Stacked in
 * portrait; on its side the standings move beside the codes so both keep the
 * full height of the screen.
 */
@Composable
private fun RoundBody(
    state: KothGameState,
    config: RoundConfig,
    modifier: Modifier = Modifier
) {
    val clock: @Composable () -> Unit = {
        RoundClock(
            remainingMillis = state.remainingMillis,
            totalMillis = config.roundMillis
        )
    }

    val landscape = isLandscape()
    val holder: @Composable () -> Unit = {
        HolderPanel(
            holderName = state.holder?.let { state.teamNames[it] },
            holderMillis = state.holder?.let { state.heldMillis[it] } ?: 0L,
            compact = landscape
        )
    }

    // The code list owns whatever height it is given; rows and their type scale
    // to fill it, however many teams there are.
    val codes: @Composable ColumnScope.() -> Unit = {
        TerminalSectionLabel("CAPTURE_CODES")
        Spacer(Modifier.height(8.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            state.teamNames.forEachIndexed { index, name ->
                TeamCodeRow(
                    team = name,
                    code = state.codes[index],
                    typed = state.input,
                    heldMillis = state.heldMillis[index],
                    // A holding team's row is where the music is coming from, so
                    // it says which track that is. Null everywhere else: only
                    // one team is ever sounding.
                    nowPlaying = config.trackFor(index)
                        ?.takeIf { state.codes[index] == null && it.isNotEmpty() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            }
        }
    }

    if (isLandscape()) {
        // The prompt rides with the codes rather than under the holder panel:
        // the code list is the one part of this screen that can give up height,
        // so anything stacked with it can never be squeezed off the screen.
        Row(modifier = modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                clock()
                Spacer(Modifier.height(12.dp))
                holder()
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                codes()
            }
        }
    } else {
        Column(modifier = modifier.fillMaxWidth()) {
            Spacer(Modifier.height(10.dp))
            clock()

            Spacer(Modifier.height(14.dp))
            holder()

            Spacer(Modifier.height(16.dp))
            codes()
        }
    }
}

/** Round clock plus a bar that drains as the round does. */
@Composable
private fun RoundClock(
    remainingMillis: Long,
    totalMillis: Long,
    modifier: Modifier = Modifier
) {
    val fraction = if (totalMillis <= 0L) 0f else remainingMillis.toFloat() / totalMillis
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "TIME_LEFT",
                color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.labelSmall
            )
            Text(
                text = formatClock(remainingMillis),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.headlineMedium
            )
        }
        Spacer(Modifier.height(6.dp))
        AsciiProgressBar(fraction = fraction)
    }
}

/** The headline: who owns the hill right now, and for how long. */
@Composable
private fun HolderPanel(
    holderName: String?,
    holderMillis: Long,
    modifier: Modifier = Modifier,
    /** Sized for a screen on its side, where height is the scarce dimension. */
    compact: Boolean = false
) {
    val scheme = MaterialTheme.colorScheme
    TerminalPanel(
        modifier = modifier.fillMaxWidth(),
        borderColor = if (holderName != null) scheme.primary else scheme.outline,
        borderWidth = 3,
        contentPadding = if (compact) 12 else 16
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "CURRENT_HOLDING_TEAM",
                color = scheme.secondary,
                style = MaterialTheme.typography.labelSmall
            )
            Spacer(Modifier.height(if (compact) 6.dp else 10.dp))
            Text(
                text = holderName ?: "UNCLAIMED",
                color = if (holderName != null) scheme.primary else scheme.outline,
                style = if (compact) {
                    MaterialTheme.typography.headlineLarge
                } else {
                    MaterialTheme.typography.displayLarge
                }
            )
            Spacer(Modifier.height(if (compact) 4.dp else 8.dp))
            Text(
                text = if (holderName != null) {
                    "HOLD_TIME " + formatClock(holderMillis)
                } else {
                    "AWAITING FIRST CAPTURE"
                },
                color = scheme.secondary,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

/**
 * `ALPHA   :: 2917828` with each team's banked time on the right. Digits already
 * typed are lifted out in bright bold underline; teams whose code cannot match
 * what is being typed fade back so the live one stands alone.
 *
 * The row sizes its own type to whatever height and width it was given, so two
 * teams read like a scoreboard and six still fit.
 */
@Composable
private fun TeamCodeRow(
    team: String,
    code: String?,
    typed: String,
    heldMillis: Long,
    modifier: Modifier = Modifier,
    /** Track sounding for this team right now, if music mode is on. */
    nowPlaying: String? = null
) {
    val scheme = MaterialTheme.colorScheme
    val holding = code == null
    val matching = code != null && typed.isNotEmpty() && code.startsWith(typed)
    val muted = code != null && typed.isNotEmpty() && !matching

    val borderColor = when {
        holding -> scheme.primary
        matching -> scheme.primary
        muted -> scheme.outlineVariant
        else -> scheme.outline
    }
    val bodyColor = when {
        holding -> scheme.onPrimary
        muted -> scheme.outlineVariant
        else -> scheme.secondary
    }

    BoxWithConstraints(
        modifier = modifier
            .border(BorderStroke(if (matching) 2.dp else 1.dp, borderColor))
            .background(if (holding) scheme.primary else Color.Transparent)
    ) {
        // `NAME    :: 1234567` is 18 monospace cells, the banked clock another
        // four at 62%; solving both against the row keeps the line on one line
        // with a gap left between them.
        val byWidth = (maxWidth.value - 40f) / 14f
        // A row carrying the track name underneath gives that second line its
        // share of the height rather than growing the row.
        val byHeight = maxHeight.value * if (nowPlaying != null) 0.26f else 0.34f
        val fontSize = minOf(byWidth, byHeight).coerceIn(11f, 30f)
        val textStyle = MaterialTheme.typography.bodyLarge.copy(
            fontSize = fontSize.sp,
            lineHeight = (fontSize * 1.25f).sp
        )

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f, fill = false)) {
            Text(
                text = buildAnnotatedString {
                    withStyle(
                        SpanStyle(
                            color = if (holding) scheme.onPrimary else scheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    ) {
                        append(team.padEnd(NAME_COLUMN))
                    }
                    append(" :: ")
                    if (code == null) {
                        append("HOLDING")
                    } else if (matching) {
                        // The deepest emphasis on the screen: bright, bold,
                        // underlined and on a lit background.
                        withStyle(
                            SpanStyle(
                                color = scheme.primary,
                                fontWeight = FontWeight.Bold,
                                background = TerminalGreenFaint,
                                textDecoration = TextDecoration.Underline
                            )
                        ) {
                            append(code.take(typed.length))
                        }
                        append(code.drop(typed.length))
                    } else {
                        append(code)
                    }
                },
                color = bodyColor,
                style = textStyle,
                maxLines = 1
            )
                if (nowPlaying != null) {
                    Text(
                        text = "NOW_PLAYING: $nowPlaying",
                        color = if (holding) scheme.onPrimary else scheme.secondary,
                        style = textStyle.copy(
                            fontSize = (fontSize * 0.55f).sp,
                            lineHeight = (fontSize * 0.75f).sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Text(
                text = formatClock(heldMillis),
                color = if (holding) scheme.onPrimary else scheme.outline,
                style = textStyle.copy(fontSize = (fontSize * 0.62f).sp),
                maxLines = 1
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun KothGamePreview() {
    RespawnSuiteTheme {
        KothGameScreen(
            config = RoundConfig(teamCount = 4, countdownSeconds = 0),
            onEnd = {}
        )
    }
}
