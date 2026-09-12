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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.respawnsuite.audio.AudioHeartbeat
import com.example.respawnsuite.audio.Tone
import com.example.respawnsuite.audio.rememberToneEngine
import com.example.respawnsuite.game.GameCatalog
import com.example.respawnsuite.game.GameSlot
import com.example.respawnsuite.game.Screen
import com.example.respawnsuite.input.KeypadEvent
import com.example.respawnsuite.input.Nav
import com.example.respawnsuite.input.asAdjust
import com.example.respawnsuite.input.asNav
import com.example.respawnsuite.input.keypadInput
import com.example.respawnsuite.ui.OrientationMode
import com.example.respawnsuite.ui.isLandscape
import com.example.respawnsuite.ui.components.FittedText
import com.example.respawnsuite.ui.components.BlinkingCursor
import com.example.respawnsuite.ui.components.TerminalChip
import com.example.respawnsuite.ui.components.TerminalDivider
import com.example.respawnsuite.ui.components.TerminalLine
import com.example.respawnsuite.ui.components.TerminalMenuRow
import com.example.respawnsuite.ui.components.TerminalPanel
import com.example.respawnsuite.ui.components.TerminalScreen
import com.example.respawnsuite.ui.components.TerminalSectionLabel
import com.example.respawnsuite.ui.theme.RespawnSuiteTheme

private const val SUITE_VERSION = "v0.1.0"

/**
 * Module picker, and the one place the suite's orientation is chosen. Driven by
 * the same numpad grammar as every other screen: 8 and 2 walk the rows, 4/6 or
 * +/- change the display setting, ENTER launches.
 */
@Composable
fun MainMenuScreen(
    onLaunchGame: (Screen) -> Unit,
    modifier: Modifier = Modifier,
    orientation: OrientationMode = OrientationMode.AUTO,
    onOrientationChange: (OrientationMode) -> Unit = {},
    slots: List<GameSlot> = GameCatalog
) {
    // One soft beep per caret blink, so the audio check keeps time with the
    // cursor next to READY.
    AudioHeartbeat()
    val tones = rememberToneEngine()

    // The display row sits at the end of the same list the modules are on, so
    // one pair of keys walks everything on the screen.
    val displayRow = slots.size
    val stops = remember(slots) {
        slots.indices.filter { slots[it].isAvailable } + slots.size
    }
    var selectedIndex by remember(slots) {
        mutableIntStateOf(stops.firstOrNull() ?: 0)
    }

    fun launch(slot: GameSlot) {
        slot.screen?.let(onLaunchGame)
    }

    fun moveSelection(step: Int): Boolean {
        if (stops.isEmpty()) return false
        val current = stops.indexOf(selectedIndex).coerceAtLeast(0)
        selectedIndex = stops[(current + step + stops.size) % stops.size]
        tones.play(Tone.KeyPress)
        return true
    }

    fun cycleOrientation(step: Int) {
        onOrientationChange(orientation.stepped(step))
        tones.play(Tone.KeyPress)
    }

    fun activate() {
        if (selectedIndex == displayRow) {
            cycleOrientation(1)
            return
        }
        val slot = slots.getOrNull(selectedIndex)
        if (slot != null && slot.isAvailable) {
            tones.play(Tone.KeyPress)
            launch(slot)
        } else {
            tones.play(Tone.KeyReject)
        }
    }

    val keypad = Modifier.keypadInput { event ->
        val adjustment = event.asAdjust()
        when {
            event.asNav() == Nav.Up -> moveSelection(-1)
            event.asNav() == Nav.Down -> moveSelection(1)
            // Sideways only means something on the display row; elsewhere it is
            // still the player driving the menu, so it never falls through.
            adjustment != null && selectedIndex == displayRow -> cycleOrientation(adjustment)
            event == KeypadEvent.Enter -> activate()
            else -> Unit
        }
        true
    }

    val modules: @Composable ColumnScope.() -> Unit = {
        slots.forEachIndexed { index, slot ->
            TerminalMenuRow(
                label = slot.name,
                note = slot.tagline,
                enabled = slot.isAvailable,
                selected = index == selectedIndex,
                onClick = {
                    selectedIndex = index
                    launch(slot)
                }
            )
        }
    }

    val display: @Composable () -> Unit = {
        DisplayRow(
            orientation = orientation,
            focused = selectedIndex == displayRow,
            onPick = {
                selectedIndex = displayRow
                onOrientationChange(it)
            }
        )
    }

    if (isLandscape()) {
        LandscapeMenu(
            modifier = modifier.fillMaxSize().then(keypad),
            modules = modules,
            display = display
        )
    } else {
        PortraitMenu(
            modifier = modifier.fillMaxSize().then(keypad),
            modules = modules,
            display = display
        )
    }
}

/** Stacked: banner, boot lines, modules, then the display setting. */
@Composable
private fun PortraitMenu(
    modules: @Composable ColumnScope.() -> Unit,
    display: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    TerminalScreen(modifier = modifier) {
        Banner()

        Spacer(Modifier.height(14.dp))
        BootLines()

        Spacer(Modifier.height(10.dp))
        TerminalDivider()
        Spacer(Modifier.height(10.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = modules
        )

        Spacer(Modifier.height(18.dp))
        display()

        Spacer(Modifier.weight(1f))
        TerminalDivider(glyph = '-')
        Spacer(Modifier.height(8.dp))
        ReadyLine()
    }
}

/**
 * On its side there is no room to stack a banner over four rows, so the
 * identity column stays on the left and the modules take the right.
 */
@Composable
private fun LandscapeMenu(
    modules: @Composable ColumnScope.() -> Unit,
    display: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    TerminalScreen(modifier = modifier) {
        Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                Banner()
                Spacer(Modifier.height(12.dp))
                BootLines()
                Spacer(Modifier.weight(1f))
                TerminalDivider(glyph = '-')
                Spacer(Modifier.height(8.dp))
                ReadyLine()
            }

            Spacer(Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.Center
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    content = modules
                )
                Spacer(Modifier.height(16.dp))
                display()
            }
        }
    }
}

@Composable
private fun Banner(modifier: Modifier = Modifier) {
    TerminalPanel(
        modifier = modifier.fillMaxWidth(),
        borderColor = MaterialTheme.colorScheme.primary,
        borderWidth = 2,
        contentPadding = 14
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            FittedText(text = "RESPAWN", modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            Text(
                text = "[ G A M E S   S U I T E ]",
                color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun BootLines(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        TerminalLine("system online // build $SUITE_VERSION")
        TerminalLine("input: usb-c keypad / touch")
        TerminalLine("select game module:")
    }
}

@Composable
private fun ReadyLine(modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "READY",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium
            )
            BlinkingCursor(modifier = Modifier.padding(start = 6.dp))
        }
        Spacer(Modifier.height(4.dp))
        TerminalLine("8/2 select // ENTER launches", prompt = "#")
    }
}

/**
 * Which way the phone is asked to sit. AUTO follows gravity; the other two pin
 * it, which is what a phone bolted sideways into a box needs.
 */
@Composable
private fun DisplayRow(
    orientation: OrientationMode,
    focused: Boolean,
    onPick: (OrientationMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        TerminalSectionLabel("DISPLAY", focused = focused)
        Spacer(Modifier.height(4.dp))
        TerminalLine("auto follows gravity / tall / wide")
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OrientationMode.entries.forEach { option ->
                TerminalChip(
                    label = option.label,
                    selected = orientation == option,
                    onClick = { onPick(option) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun MainMenuPreview() {
    RespawnSuiteTheme {
        MainMenuScreen(onLaunchGame = {})
    }
}
