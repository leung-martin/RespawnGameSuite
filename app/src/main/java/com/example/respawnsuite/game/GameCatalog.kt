package com.example.respawnsuite.game

import com.example.respawnsuite.game.koth.KothOutcome
import com.example.respawnsuite.game.sad.SadOutcome

/** Every screen the app can be showing. Games get added here as they are built. */
sealed interface Screen {
    data object MainMenu : Screen
    data class Setup(val mode: GameMode) : Screen
    data class KothGame(val config: RoundConfig) : Screen
    data class KothResults(
        val config: RoundConfig,
        val outcome: KothOutcome
    ) : Screen

    data class SadGame(val config: RoundConfig) : Screen
    data class SadResults(
        val config: RoundConfig,
        val outcome: SadOutcome
    ) : Screen
}

/**
 * A slot on the main menu. [screen] is null while the module is still
 * unimplemented, which is what greys the row out.
 */
data class GameSlot(
    val name: String,
    val tagline: String,
    val screen: Screen?
) {
    val isAvailable: Boolean get() = screen != null
}

/** Builds the menu row for a mode, so its name and tag live in one place. */
private fun slotFor(mode: GameMode) = GameSlot(
    name = mode.displayName,
    tagline = mode.tagline,
    screen = Screen.Setup(mode)
)

val GameCatalog: List<GameSlot> = listOf(
    slotFor(GameMode.KING_OF_THE_HILL),
    slotFor(GameMode.COUNTDOWN),
    slotFor(GameMode.SEARCH_AND_DESTROY),
    GameSlot(
        name = "ERROR_NOT_FOUND",
        tagline = "LOCKED",
        screen = null
    )
)
