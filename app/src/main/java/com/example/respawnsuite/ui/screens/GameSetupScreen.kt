package com.example.respawnsuite.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.toMutableStateList
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.respawnsuite.audio.AudioHeartbeat
import com.example.respawnsuite.audio.MusicLibrary
import com.example.respawnsuite.audio.Tone
import com.example.respawnsuite.audio.rememberToneEngine
import com.example.respawnsuite.game.GameMode
import com.example.respawnsuite.game.RoundConfig
import com.example.respawnsuite.game.TeamNames
import com.example.respawnsuite.input.KeypadEvent
import com.example.respawnsuite.input.Nav
import com.example.respawnsuite.input.asAdjust
import com.example.respawnsuite.input.asNav
import com.example.respawnsuite.input.keypadInput
import com.example.respawnsuite.ui.isLandscape
import com.example.respawnsuite.ui.components.BlinkingCursor
import com.example.respawnsuite.ui.components.TerminalButton
import com.example.respawnsuite.ui.components.TerminalChip
import com.example.respawnsuite.ui.components.TerminalDivider
import com.example.respawnsuite.ui.components.TerminalLine
import com.example.respawnsuite.ui.components.TerminalPanel
import com.example.respawnsuite.ui.components.TerminalScreen
import com.example.respawnsuite.ui.components.ShrinkToFitText
import com.example.respawnsuite.ui.components.TerminalSectionLabel
import com.example.respawnsuite.ui.components.TerminalStepper
import com.example.respawnsuite.ui.theme.RespawnSuiteTheme

/**
 * One focusable line of the setup form. The list is built per mode, so a mode
 * without music or without a start countdown simply has fewer stops on it.
 */
private sealed interface SetupRow {
    data object Back : SetupRow
    data object Teams : SetupRow
    data object RoundTimer : SetupRow
    data object Countdown : SetupRow
    data object Fuse : SetupRow
    data object CodeLength : SetupRow
    data object MusicMode : SetupRow
    data class Track(val team: Int) : SetupRow
    data object Initiate : SetupRow
}

/** Where a row sits in the viewport, so focus can scroll it back into sight. */
private data class RowSpan(val top: Float, val bottom: Float)

/** Breathing room left above or below a row when scrolling it into view. */
private const val SCROLL_MARGIN = 40f

/** Wide gives the settings column half again what the others get. */
private const val SETTINGS_COLUMN_SHARE = 1.5f

/**
 * Type size for a wide row heading. Chosen so the longest of them still sits on
 * one line, which keeps every heading the same size rather than each shrinking
 * to its own length.
 */
private const val WIDE_LABEL_FONT_SIZE = 13

/** Share of a wide setting row taken by its heading, the rest being control. */
private const val LABEL_WIDTH_SHARE = 0.42f

/**
 * Pre-game configuration, drivable end to end from the numpad: 8 and 2 walk the
 * settings, 4/6 (or the pad's own +/-) change the focused one, and holding 0
 * launches the round. Touch does all the same things for whoever is holding the
 * phone rather than the keypad.
 *
 * The form is one column tall and two columns wide; the settings, their order
 * and their focus behaviour are identical either way.
 */
@Composable
fun GameSetupScreen(
    mode: GameMode,
    onStart: (RoundConfig) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Keeps the speaker check running right up to the moment the round starts,
    // in step with the caret beside the module name.
    AudioHeartbeat()
    val tones = rememberToneEngine()

    val context = LocalContext.current
    val tracks = remember(context) { MusicLibrary.tracks(context) }

    // Shuffled once per visit so teams get different music without anyone
    // having to pick, then held for all six slots so changing the team count
    // never loses a choice already made.
    val assignments = rememberSaveable(
        saver = listSaver(save = { it.toList() }, restore = { it.toMutableStateList() })
    ) {
        val shuffled = tracks.shuffled()
        List(TeamNames.size) { slot ->
            shuffled.getOrNull(slot % shuffled.size.coerceAtLeast(1)).orEmpty()
        }.toMutableStateList()
    }

    var teamCount by rememberSaveable { mutableStateOf(RoundConfig.DEFAULT_TEAMS) }
    var roundMinutes by rememberSaveable(mode) { mutableStateOf(mode.defaultRoundMinutes) }
    var countdownSeconds by rememberSaveable(mode) {
        mutableStateOf(if (mode.startCountdownConfigurable) RoundConfig.DEFAULT_COUNTDOWN else 0)
    }
    var codeLength by rememberSaveable { mutableStateOf(RoundConfig.DEFAULT_CODE_LENGTH) }
    var fuseSeconds by rememberSaveable { mutableStateOf(RoundConfig.DEFAULT_FUSE) }
    var musicMode by rememberSaveable { mutableStateOf(RoundConfig.DEFAULT_MUSIC) }

    // Which setting the keypad is pointed at, and where every row sits so it can
    // be scrolled to. The positions are read only when focus moves, so they live
    // in a plain map rather than snapshot state: no recomposition per frame
    // while the form is being scrolled.
    var focus by remember(mode) {
        mutableStateOf<SetupRow>(
            if (mode.teamsConfigurable) SetupRow.Teams else SetupRow.RoundTimer
        )
    }
    val spans = remember { mutableMapOf<SetupRow, RowSpan>() }
    val scrollState = rememberScrollState()
    var viewportTop by remember { mutableFloatStateOf(0f) }
    var viewportHeight by remember { mutableIntStateOf(0) }

    val rows = buildList {
        add(SetupRow.Back)
        if (mode.teamsConfigurable) add(SetupRow.Teams)
        add(SetupRow.RoundTimer)
        if (mode.startCountdownConfigurable) add(SetupRow.Countdown)
        if (mode.fuseConfigurable) add(SetupRow.Fuse)
        add(SetupRow.CodeLength)
        if (mode.musicSupported) {
            add(SetupRow.MusicMode)
            if (musicMode && tracks.isNotEmpty()) {
                for (team in 0 until teamCount) add(SetupRow.Track(team))
            }
        }
        add(SetupRow.Initiate)
    }

    LaunchedEffect(focus, viewportHeight) {
        val span = spans[focus] ?: return@LaunchedEffect
        if (viewportHeight <= 0) return@LaunchedEffect
        when {
            span.top - SCROLL_MARGIN < 0f ->
                scrollState.animateScrollBy(span.top - SCROLL_MARGIN)

            span.bottom + SCROLL_MARGIN > viewportHeight ->
                scrollState.animateScrollBy(span.bottom + SCROLL_MARGIN - viewportHeight)
        }
    }

    fun start() {
        onStart(
            RoundConfig(
                mode = mode,
                teamCount = teamCount,
                roundMinutes = roundMinutes,
                countdownSeconds = countdownSeconds,
                codeLength = codeLength,
                fuseSeconds = fuseSeconds,
                musicMode = musicMode,
                trackByTeam = assignments.toList()
            )
        )
    }

    fun move(step: Int) {
        val index = rows.indexOf(focus).coerceAtLeast(0)
        focus = rows[(index + step + rows.size) % rows.size]
        tones.play(Tone.KeyPress)
    }

    fun cycleTrack(team: Int, delta: Int) {
        if (tracks.isEmpty()) return
        val index = tracks.indexOf(assignments[team]).coerceAtLeast(0)
        assignments[team] = tracks[(index + delta + tracks.size) % tracks.size]
    }

    /** Moves the focused setting one notch, [delta] being +1 or -1. */
    fun adjust(delta: Int) {
        when (val row = focus) {
            SetupRow.Teams -> {
                val options = RoundConfig.TEAM_OPTIONS
                val index = options.indexOf(teamCount).coerceAtLeast(0)
                teamCount = options[(index + delta).coerceIn(0, options.lastIndex)]
            }

            SetupRow.RoundTimer -> roundMinutes = (roundMinutes + delta)
                .coerceIn(RoundConfig.MIN_MINUTES, RoundConfig.MAX_MINUTES)

            SetupRow.Countdown -> countdownSeconds =
                (countdownSeconds + delta * RoundConfig.COUNTDOWN_STEP)
                    .coerceIn(RoundConfig.MIN_COUNTDOWN, RoundConfig.MAX_COUNTDOWN)

            SetupRow.Fuse -> fuseSeconds = (fuseSeconds + delta * RoundConfig.FUSE_STEP)
                .coerceIn(RoundConfig.MIN_FUSE, RoundConfig.MAX_FUSE)

            SetupRow.CodeLength -> codeLength = (codeLength + delta)
                .coerceIn(RoundConfig.MIN_CODE_LENGTH, RoundConfig.MAX_CODE_LENGTH)

            // Right is on, left is off, matching how the two chips sit.
            SetupRow.MusicMode -> musicMode = delta > 0

            is SetupRow.Track -> cycleTrack(row.team, delta)

            // BACK and INITIATE are not settings; ENTER is what acts on them.
            else -> return
        }
        tones.play(Tone.KeyPress)
    }

    /**
     * A tap of ENTER only leaves the screen. Launching is a hold, so a pressed
     * ENTER must not also change the setting under the caret on its way there —
     * that is what 4/6 and +/- are for.
     */
    fun activate() {
        if (focus == SetupRow.Back) onBack()
    }

    // Holding 0 launches the round — the same key that runs it back on the
    // results screen. Across this suite, 0 held down means go.
    var heldKey by remember { mutableStateOf<KeypadEvent?>(null) }

    val recordSpan: (SetupRow, RowSpan) -> Unit = { row, span -> spans[row] = span }

    // A phone on its side has roughly four hundred dp of height for the whole
    // form, so on that layout every control is built at its tighter size. The
    // form is then dealt into three columns that fit without scrolling.
    val wide = isLandscape()

    // Every field is built once here and placed by whichever layout is running,
    // so the two orientations can never drift apart in what they offer.

    val backField: @Composable () -> Unit = {
        // The system back gesture is swallowed by the kiosk lock, so the way out
        // of setup has to be on the screen — and on the pad.
        SetupField(
            row = SetupRow.Back,
            focused = focus == SetupRow.Back,
            viewportTop = viewportTop,
            onSpan = recordSpan,
            inlineLabel = wide,
            compact = wide
        ) {
            TerminalChip(
                label = "< BACK",
                selected = focus == SetupRow.Back,
                onClick = {
                    focus = SetupRow.Back
                    onBack()
                },
                compact = wide
            )
        }
    }

    val header: @Composable () -> Unit = {
        val nameStyle = if (wide) {
            MaterialTheme.typography.headlineSmall
        } else {
            MaterialTheme.typography.headlineMedium
        }
        TerminalPanel(
            modifier = Modifier.fillMaxWidth(),
            borderColor = MaterialTheme.colorScheme.primary,
            borderWidth = 2,
            contentPadding = if (wide) 8 else 12
        ) {
            Column {
                Text(
                    text = mode.moduleLabel + " // SETUP",
                    color = MaterialTheme.colorScheme.secondary,
                    style = MaterialTheme.typography.labelSmall
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = mode.displayName,
                        color = MaterialTheme.colorScheme.primary,
                        style = nameStyle
                    )
                    BlinkingCursor(
                        modifier = Modifier.padding(start = 8.dp),
                        style = nameStyle
                    )
                }
            }
        }
    }

    val teamsField: @Composable () -> Unit = {
        if (mode.teamsConfigurable) {
            SetupField(
                row = SetupRow.Teams,
                focused = focus == SetupRow.Teams,
                viewportTop = viewportTop,
                onSpan = recordSpan,
                inlineLabel = wide,
                label = "NUMBER_OF_TEAMS",
                compact = wide
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(if (wide) 6.dp else 8.dp)
                ) {
                    RoundConfig.TEAM_OPTIONS.forEach { option ->
                        TerminalChip(
                            label = option.toString(),
                            selected = teamCount == option,
                            onClick = {
                                focus = SetupRow.Teams
                                teamCount = option
                            },
                            modifier = Modifier.weight(1f),
                            compact = wide
                        )
                    }
                }
            }
        } else {
            // Fixed sides: say who is playing rather than offering a count.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = if (wide) 5.dp else 8.dp)
            ) {
                TerminalSectionLabel("SIDES")
                Spacer(Modifier.height(if (wide) 4.dp else 8.dp))
                TerminalLine("ATTACK plants / DEFENCE defuses")
            }
        }
    }

    val roundTimerField: @Composable () -> Unit = {
        SetupField(
            row = SetupRow.RoundTimer,
            focused = focus == SetupRow.RoundTimer,
            viewportTop = viewportTop,
            onSpan = recordSpan,
            inlineLabel = wide,
            label = mode.timerLabel,
            compact = wide
        ) {
            TerminalStepper(
                compact = wide,
                value = "${roundMinutes.toString().padStart(2, '0')} MIN",
                onDecrement = {
                    focus = SetupRow.RoundTimer
                    roundMinutes = (roundMinutes - 1).coerceAtLeast(RoundConfig.MIN_MINUTES)
                },
                onIncrement = {
                    focus = SetupRow.RoundTimer
                    roundMinutes = (roundMinutes + 1).coerceAtMost(RoundConfig.MAX_MINUTES)
                },
                decrementEnabled = roundMinutes > RoundConfig.MIN_MINUTES,
                incrementEnabled = roundMinutes < RoundConfig.MAX_MINUTES
            )
        }
    }

    val countdownField: @Composable () -> Unit = {
        if (mode.startCountdownConfigurable) {
            SetupField(
                row = SetupRow.Countdown,
                focused = focus == SetupRow.Countdown,
                viewportTop = viewportTop,
                onSpan = recordSpan,
                inlineLabel = wide,
                label = "START_COUNTDOWN",
                compact = wide
            ) {
                TerminalStepper(
                compact = wide,
                    value = if (countdownSeconds == 0) {
                        "NONE"
                    } else {
                        countdownSeconds.toString().padStart(2, '0') + " SEC"
                    },
                    onDecrement = {
                        focus = SetupRow.Countdown
                        countdownSeconds = (countdownSeconds - RoundConfig.COUNTDOWN_STEP)
                            .coerceAtLeast(RoundConfig.MIN_COUNTDOWN)
                    },
                    onIncrement = {
                        focus = SetupRow.Countdown
                        countdownSeconds = (countdownSeconds + RoundConfig.COUNTDOWN_STEP)
                            .coerceAtMost(RoundConfig.MAX_COUNTDOWN)
                    },
                    decrementEnabled = countdownSeconds > RoundConfig.MIN_COUNTDOWN,
                    incrementEnabled = countdownSeconds < RoundConfig.MAX_COUNTDOWN
                )
            }
        }
    }

    val fuseField: @Composable () -> Unit = {
        if (mode.fuseConfigurable) {
            SetupField(
                row = SetupRow.Fuse,
                focused = focus == SetupRow.Fuse,
                viewportTop = viewportTop,
                onSpan = recordSpan,
                inlineLabel = wide,
                label = "FUSE_TIMER",
                compact = wide
            ) {
                TerminalStepper(
                compact = wide,
                    value = fuseSeconds.toString() + " SEC",
                    onDecrement = {
                        focus = SetupRow.Fuse
                        fuseSeconds = (fuseSeconds - RoundConfig.FUSE_STEP)
                            .coerceAtLeast(RoundConfig.MIN_FUSE)
                    },
                    onIncrement = {
                        focus = SetupRow.Fuse
                        fuseSeconds = (fuseSeconds + RoundConfig.FUSE_STEP)
                            .coerceAtMost(RoundConfig.MAX_FUSE)
                    },
                    decrementEnabled = fuseSeconds > RoundConfig.MIN_FUSE,
                    incrementEnabled = fuseSeconds < RoundConfig.MAX_FUSE
                )
            }
        }
    }

    val codeLengthField: @Composable () -> Unit = {
        SetupField(
            row = SetupRow.CodeLength,
            focused = focus == SetupRow.CodeLength,
            viewportTop = viewportTop,
            onSpan = recordSpan,
            inlineLabel = wide,
            label = "CODE_LENGTH",
            compact = wide
        ) {
            TerminalStepper(
                compact = wide,
                value = codeLength.toString() + " DIGITS",
                onDecrement = {
                    focus = SetupRow.CodeLength
                    codeLength = (codeLength - 1).coerceAtLeast(RoundConfig.MIN_CODE_LENGTH)
                },
                onIncrement = {
                    focus = SetupRow.CodeLength
                    codeLength = (codeLength + 1).coerceAtMost(RoundConfig.MAX_CODE_LENGTH)
                },
                decrementEnabled = codeLength > RoundConfig.MIN_CODE_LENGTH,
                incrementEnabled = codeLength < RoundConfig.MAX_CODE_LENGTH
            )
        }
    }

    val musicModeField: @Composable () -> Unit = {
        if (mode.musicSupported) {
            Column(modifier = Modifier.fillMaxWidth()) {
                SetupField(
                    row = SetupRow.MusicMode,
                    focused = focus == SetupRow.MusicMode,
                    viewportTop = viewportTop,
                    onSpan = recordSpan,
                    inlineLabel = wide,
                    label = "MUSIC_MODE",
                    compact = wide
                ) {
                    // OFF sits left of ON so the two ways of changing this agree
                    // with the row: right and plus both move towards ON, left
                    // and minus back towards OFF.
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TerminalChip(
                            label = "OFF",
                            selected = !musicMode,
                            onClick = {
                                focus = SetupRow.MusicMode
                                musicMode = false
                            },
                            modifier = Modifier.weight(1f),
                            compact = wide
                        )
                        TerminalChip(
                            label = "ON",
                            selected = musicMode,
                            onClick = {
                                focus = SetupRow.MusicMode
                                musicMode = true
                            },
                            modifier = Modifier.weight(1f),
                            compact = wide
                        )
                    }
                }
            }
        }
    }

    /** Shown only when there is music to assign. */
    val tracksVisible = mode.musicSupported && musicMode && tracks.isNotEmpty()

    val trackHeader: @Composable () -> Unit = {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            TerminalSectionLabel("TEAM_MUSIC")
            // On its side the column is carrying track rows already, and the key
            // hints live under the launch button anyway.
            if (!wide) {
                Spacer(Modifier.height(4.dp))
                TerminalLine("4/6 or +/- changes the focused team's track")
            }
            Spacer(Modifier.height(if (wide) 4.dp else 8.dp))
        }
    }

    // Two teams sharing a track is legal but rarely intended, so the clash is
    // called out rather than silently allowed.
    val shared = assignments.take(teamCount)
        .groupingBy { it }
        .eachCount()
        .filterValues { it > 1 }
        .keys

    val trackFields: List<@Composable () -> Unit> = if (!tracksVisible) {
        emptyList()
    } else {
        (0 until teamCount).map { team ->
            {
                val trackRow = SetupRow.Track(team)
                SetupField(
                    row = trackRow,
                    focused = focus == trackRow,
                    viewportTop = viewportTop,
                    onSpan = recordSpan,
                    inlineLabel = wide,
                    compact = wide,
                    dense = wide
                ) {
                    TeamTrackRow(
                        compact = wide,
                        team = TeamNames[team],
                        track = assignments[team],
                        clashes = assignments[team] in shared,
                        onCycle = {
                            focus = trackRow
                            cycleTrack(team, 1)
                        }
                    )
                }
            }
        }
    }

    val summary: @Composable () -> Unit = {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            TerminalDivider()
            Spacer(Modifier.height(if (wide) 6.dp else 10.dp))
            TerminalLine("win condition: " + mode.winCondition)
            Spacer(Modifier.height(4.dp))
            TerminalLine(
                buildString {
                    append(if (mode.teamsConfigurable) "$teamCount teams" else "2 sides")
                    append(" / $roundMinutes min / $codeLength-digit codes / ")
                    if (mode.startCountdownConfigurable) {
                        append(
                            if (countdownSeconds == 0) {
                                "no countdown"
                            } else {
                                "${countdownSeconds}s countdown"
                            }
                        )
                    } else {
                        append("${fuseSeconds}s fuse once armed")
                    }
                    if (mode.musicSupported) {
                        append(" / music ")
                        append(if (musicMode) "on" else "off")
                    }
                },
                prompt = ">"
            )
        }
    }

    val initiate: @Composable () -> Unit = {
        Column(modifier = Modifier.fillMaxWidth()) {
            SetupField(
                row = SetupRow.Initiate,
                focused = focus == SetupRow.Initiate,
                viewportTop = viewportTop,
                onSpan = recordSpan,
                inlineLabel = wide,
                compact = wide
            ) {
                TerminalButton(
                    label = "INITIATE SEQUENCE",
                    onClick = ::start,
                    // Either key launches: ENTER because it is the confirm key
                    // everywhere else, 0 because it is the one the suite reaches
                    // for when the phone is already in a mount.
                    heldByKey = heldKey == KeypadEvent.Digit(0) ||
                        heldKey == KeypadEvent.Enter,
                    modifier = Modifier.fillMaxWidth(),
                    compact = wide
                )
            }
            // Wide puts the key hints on one full-width line under the form,
            // where they have room to stay on one line; a narrow column would
            // wrap them into four.
            if (!wide) {
                Spacer(Modifier.height(6.dp))
                Column(modifier = Modifier.padding(horizontal = 8.dp)) {
                    TerminalLine("8/2 move // 4/6 or +/- adjust", prompt = "#")
                    TerminalLine("hold ENTER or 0 to initiate", prompt = "#")
                }
            }
        }
    }

    val screenModifier = modifier
        .fillMaxSize()
        // Measured outside the scroll, so it is the window the rows are
        // scrolled within rather than the content that moves inside it.
        .onGloballyPositioned { coords ->
            viewportTop = coords.positionInRoot().y
            viewportHeight = coords.size.height
        }
        .keypadInput(
            onPress = { event, pressed -> heldKey = if (pressed) event else null }
        ) { event ->
            val nav = event.asNav()
            val adjustment = event.asAdjust()
            when {
                nav == Nav.Up -> move(-1)
                nav == Nav.Down -> move(1)
                adjustment != null -> adjust(adjustment)
                event == KeypadEvent.Enter -> activate()
                event == KeypadEvent.Back -> onBack()
                else -> Unit
            }
            true
        }
        // Tall scrolls, wide does not: the three-column form is built to fit the
        // screen it is on, and a column can only take its share of the height if
        // that height is a real number rather than an open-ended scroll.
        .then(if (wide) Modifier else Modifier.verticalScroll(scrollState))

    if (wide) {
        TerminalScreen(modifier = screenModifier) {
            // The back chip and the module card share the top line. Both are
            // given an explicit share of the width: a field fills whatever it
            // is handed, so an unweighted chip would take the whole row and
            // leave the card a single character wide.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(0.24f)) { backField() }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(0.76f)) { header() }
            }

            Spacer(Modifier.height(8.dp))

            // Every setting is one line here — heading left, control right — so
            // the whole form is a short stack rather than a grid of squeezed
            // boxes. That leaves the settings a wide column of their own, with
            // the music beside it and the launch on the end.
            Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                Column(
                    modifier = Modifier.weight(SETTINGS_COLUMN_SHARE).fillMaxHeight(),
                    verticalArrangement = Arrangement.Center
                ) {
                    teamsField()
                    roundTimerField()
                    countdownField()
                    fuseField()
                    codeLengthField()
                    // With no tracks to show, the music switch is just another
                    // setting; it only earns a column when it brings a list.
                    if (!tracksVisible) musicModeField()
                }

                if (tracksVisible) {
                    Spacer(Modifier.width(14.dp))
                    Column(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        verticalArrangement = Arrangement.Center
                    ) {
                        musicModeField()
                        trackHeader()
                        trackFields.forEach { it() }
                    }
                }

                Spacer(Modifier.width(14.dp))
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    verticalArrangement = Arrangement.Center
                ) {
                    summary()
                    Spacer(Modifier.height(12.dp))
                    initiate()
                }
            }

            TerminalLine(
                "8/2 move // 4/6 or +/- adjust // hold ENTER or 0 to initiate",
                prompt = "#",
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    } else {
        TerminalScreen(modifier = screenModifier) {
            backField()
            Spacer(Modifier.height(10.dp))
            header()
            Spacer(Modifier.height(14.dp))
            teamsField()
            roundTimerField()
            countdownField()
            fuseField()
            codeLengthField()
            musicModeField()
            if (tracksVisible) {
                trackHeader()
                trackFields.forEach { it() }
            }
            Spacer(Modifier.height(12.dp))
            summary()
            Spacer(Modifier.height(14.dp))
            initiate()
            Spacer(Modifier.height(12.dp))
        }
    }
}

/**
 * One stop on the form: its heading, its control, and a box around it while the
 * keypad is pointed here. Each field reports where it ended up so that focus
 * landing off-screen can scroll it back.
 */
@Composable
private fun SetupField(
    row: SetupRow,
    focused: Boolean,
    viewportTop: Float,
    onSpan: (SetupRow, RowSpan) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    compact: Boolean = false,
    /** Trims a row to the bone: used for the track list, which can run to six. */
    dense: Boolean = false,
    /**
     * Puts the heading beside its control instead of above it. A phone on its
     * side has width to spare and no height at all, so a setting there is one
     * line — the shape that lets every value stay full size.
     */
    inlineLabel: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { coords ->
                val top = coords.positionInRoot().y - viewportTop
                onSpan(row, RowSpan(top, top + coords.size.height))
            }
            // The border is always there, transparent when unfocused, so nothing
            // shifts by a pixel as focus moves down the form.
            .border(
                BorderStroke(
                    1.dp,
                    if (focused) MaterialTheme.colorScheme.primary else Color.Transparent
                )
            )
            .padding(
                horizontal = 8.dp,
                vertical = when {
                    dense -> 2.dp
                    compact -> 5.dp
                    else -> 8.dp
                }
            )
    ) {
        if (inlineLabel && label != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FieldLabel(
                    label = label,
                    focused = focused,
                    modifier = Modifier.weight(LABEL_WIDTH_SHARE)
                )
                Column(
                    modifier = Modifier.weight(1f - LABEL_WIDTH_SHARE),
                    content = content
                )
            }
        } else {
            if (label != null) {
                TerminalSectionLabel(label, focused = focused)
                Spacer(Modifier.height(if (compact) 5.dp else 8.dp))
            }
            content()
        }
    }
}

/** `>> ROUND_TIMER` beside its control, giving up type size before it wraps. */
@Composable
private fun FieldLabel(
    label: String,
    focused: Boolean,
    modifier: Modifier = Modifier
) {
    ShrinkToFitText(
        text = (if (focused) ">> " else "-- ") + label,
        maxFontSize = WIDE_LABEL_FONT_SIZE,
        modifier = modifier.padding(end = 10.dp),
        textAlign = TextAlign.Start
    )
}

/** `ALPHA :: DOOM_OST` — tap to move this team to the next track. */
@Composable
private fun TeamTrackRow(
    team: String,
    track: String,
    clashes: Boolean,
    onCycle: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            // Six of these have to stack inside half the height of a landscape
            // phone, so the wide one drops to a bare line — the focus box the
            // field draws around it is boundary enough.
            .then(
                if (compact) {
                    Modifier
                } else {
                    Modifier.border(
                        BorderStroke(1.dp, if (clashes) scheme.tertiary else scheme.outline)
                    )
                }
            )
            .clickable(onClick = onCycle)
            .padding(
                horizontal = if (compact) 4.dp else 12.dp,
                vertical = if (compact) 2.dp else 12.dp
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = team,
            color = scheme.primary,
            style = if (compact) {
                MaterialTheme.typography.bodySmall
            } else {
                MaterialTheme.typography.titleMedium
            },
            maxLines = 1
        )
        Text(
            text = MusicLibrary.displayName(track),
            // Amber: this track is on more than one team.
            color = if (clashes) scheme.tertiary else scheme.secondary,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            softWrap = false,
            // A long track name in a narrow column trails off rather than
            // running under the edge of the row.
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 8.dp).weight(1f, fill = false)
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun GameSetupPreview() {
    RespawnSuiteTheme {
        GameSetupScreen(mode = GameMode.KING_OF_THE_HILL, onStart = {}, onBack = {})
    }
}
