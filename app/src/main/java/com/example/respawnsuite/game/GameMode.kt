package com.example.respawnsuite.game

/**
 * Every game the suite can run. The metadata here drives the menu row, the
 * screen headers and which settings the setup screen offers, so adding a mode
 * is mostly a matter of describing it.
 */
enum class GameMode(
    val displayName: String,
    val moduleLabel: String,
    val tagline: String,
    /** How the round is won, in the fewest words that fit under a heading. */
    val winCondition: String,
    /** Modes with fixed sides do not offer a team count. */
    val teamsConfigurable: Boolean = true,
    /** A bomb that plays music would be a poor bomb. */
    val musicSupported: Boolean = true,
    /** What the round timer means for this mode. */
    val timerLabel: String = "ROUND_TIMER",
    /** Where the round timer starts, before anyone changes it. */
    val defaultRoundMinutes: Int = 10,
    /** A bomb round starts the moment it starts; there is nothing to walk to. */
    val startCountdownConfigurable: Boolean = true,
    /** Only the bomb has a fuse to set. */
    val fuseConfigurable: Boolean = false
) {
    KING_OF_THE_HILL(
        displayName = "KING_OF_THE_HILL",
        moduleLabel = "MODULE_01",
        tagline = "HOLD/TIMER",
        winCondition = "most hill time wins"
    ),
    COUNTDOWN(
        displayName = "COUNTDOWN",
        moduleLabel = "MODULE_02",
        tagline = "HOLD/FINAL",
        winCondition = "holding at zero wins"
    ),
    SEARCH_AND_DESTROY(
        displayName = "SEARCH_AND_DESTROY",
        moduleLabel = "MODULE_03",
        tagline = "BOMB/45s",
        winCondition = "detonate or defuse",
        teamsConfigurable = false,
        musicSupported = false,
        timerLabel = "PLANT_WINDOW",
        defaultRoundMinutes = 2,
        startCountdownConfigurable = false,
        fuseConfigurable = true
    );

    /** The two modes that share the hill engine. */
    val isHillMode: Boolean
        get() = this == KING_OF_THE_HILL || this == COUNTDOWN
}
