package com.example.respawnsuite.game.koth

import com.example.respawnsuite.game.GameMode

/** How long one team held the hill over a whole round. */
data class KothResult(
    val team: String,
    val heldMillis: Long
)

/** How a hill round ended, and everything the results screen needs to judge it. */
data class KothOutcome(
    val results: List<KothResult>,
    /** The team on the hill when the clock stopped, if any. */
    val finalHolder: String?,
    val aborted: Boolean,
    val remainingMillis: Long
)

/**
 * Every team that wins under [mode]'s rule; empty if nobody did. The two hill
 * modes play identically and differ only here.
 */
fun hillWinners(
    mode: GameMode,
    results: List<KothResult>,
    finalHolder: String?
): List<KothResult> = when (mode) {
    GameMode.KING_OF_THE_HILL -> {
        val best = results.maxOfOrNull { it.heldMillis } ?: 0L
        if (best > 0L) results.filter { it.heldMillis == best } else emptyList()
    }

    // Hill time is worth nothing here; only the last capture counts.
    GameMode.COUNTDOWN -> results.filter { it.team == finalHolder }

    GameMode.SEARCH_AND_DESTROY -> emptyList()
}
