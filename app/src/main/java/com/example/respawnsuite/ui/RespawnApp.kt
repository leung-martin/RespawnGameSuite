package com.example.respawnsuite.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.respawnsuite.game.Screen
import com.example.respawnsuite.audio.QuietWhileAway
import com.example.respawnsuite.ui.components.HashBurst
import com.example.respawnsuite.ui.screens.KothGameScreen
import com.example.respawnsuite.ui.screens.KothResultsScreen
import com.example.respawnsuite.ui.screens.GameSetupScreen
import com.example.respawnsuite.ui.screens.SadGameScreen
import com.example.respawnsuite.ui.screens.SadResultsScreen
import com.example.respawnsuite.ui.screens.MainMenuScreen

/** Single-activity router. Screens are few and flat, so state is enough. */
@Composable
fun RespawnApp() {
    var screen by remember { mutableStateOf<Screen>(Screen.MainMenu) }

    // Chosen on the menu and honoured everywhere: a round in progress never
    // re-orients itself, whichever way the phone is pointing. Landscape is the
    // default because that is how the phone sits in its mount; AUTO is one
    // keypress away on the menu for a phone being carried.
    var orientation by rememberSaveable { mutableStateOf(OrientationMode.LANDSCAPE) }
    ApplyOrientation(orientation)

    // Locked from the moment a mode is chosen through to the results: those are
    // the screens a stray gesture must not be able to leave. The main menu is
    // deliberately outside the lock, so the phone can still be put away.
    ScreenLock(active = screen != Screen.MainMenu)

    // Nothing sounds while the phone is asleep or in someone's pocket: the round
    // keeps its own time, but the speaker belongs to whoever is looking at it.
    QuietWhileAway()

    // The display must never time out mid-round; see KeepScreenOn.
    KeepScreenOn()

    // Back still navigates between the menus, but never out of a live round or
    // its results — those leave only by the deliberate hold-to-confirm bar.
    val roundInProgress = screen !is Screen.MainMenu && screen !is Screen.Setup
    BackHandler(enabled = roundInProgress) {
        // Swallowed on purpose: no accidental exit mid-round.
    }
    BackHandler(enabled = !roundInProgress && screen != Screen.MainMenu) {
        screen = Screen.MainMenu
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        val content = Modifier.padding(innerPadding)

        Box(modifier = Modifier.fillMaxSize()) {
        when (val current = screen) {
            Screen.MainMenu -> MainMenuScreen(
                onLaunchGame = { screen = it },
                orientation = orientation,
                onOrientationChange = { orientation = it },
                modifier = content
            )

            is Screen.Setup -> GameSetupScreen(
                mode = current.mode,
                onStart = { config ->
                    screen = if (config.mode.isHillMode) {
                        Screen.KothGame(config)
                    } else {
                        Screen.SadGame(config)
                    }
                },
                onBack = { screen = Screen.MainMenu },
                modifier = content
            )

            is Screen.SadGame -> SadGameScreen(
                config = current.config,
                onEnd = { outcome -> screen = Screen.SadResults(current.config, outcome) },
                modifier = content
            )

            is Screen.SadResults -> SadResultsScreen(
                config = current.config,
                outcome = current.outcome,
                onPlayAgain = { screen = Screen.SadGame(current.config) },
                onExit = { screen = Screen.MainMenu },
                modifier = content
            )

            is Screen.KothGame -> KothGameScreen(
                config = current.config,
                // Both endings land on the results screen; an aborted round
                // still has hill time worth reading out.
                onEnd = { outcome ->
                    screen = Screen.KothResults(current.config, outcome)
                },
                modifier = content
            )

            is Screen.KothResults -> KothResultsScreen(
                config = current.config,
                outcome = current.outcome,
                onPlayAgain = { screen = Screen.KothGame(current.config) },
                onExit = { screen = Screen.MainMenu },
                modifier = content
            )
        }

        // Every round ends with the screen blowing apart, drawn over whichever
        // results screen is arriving. Last in the box so it lands on top; it
        // emits nothing once it has burnt out and takes no touches, so the
        // results stay usable throughout.
        if (screen is Screen.KothResults || screen is Screen.SadResults) {
            HashBurst(modifier = Modifier.fillMaxSize().padding(innerPadding))
        }
        }
    }
}
