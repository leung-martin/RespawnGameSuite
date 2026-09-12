package com.example.respawnsuite.game

/** Settings chosen on the setup screen and handed to the running game. */
data class RoundConfig(
    val mode: GameMode = GameMode.KING_OF_THE_HILL,
    val teamCount: Int = DEFAULT_TEAMS,
    val roundMinutes: Int = DEFAULT_MINUTES,
    val countdownSeconds: Int = DEFAULT_COUNTDOWN,
    val codeLength: Int = DEFAULT_CODE_LENGTH,
    /** Seconds on the fuse once a bomb is armed. */
    val fuseSeconds: Int = DEFAULT_FUSE,
    val musicMode: Boolean = DEFAULT_MUSIC,
    /** Asset filename per team slot, chosen on the setup screen. */
    val trackByTeam: List<String> = emptyList()
) {
    val roundSeconds: Int get() = roundMinutes * 60
    val roundMillis: Long get() = roundSeconds * 1000L
    val countdownMillis: Long get() = countdownSeconds * 1000L
    val fuseMillis: Long get() = fuseSeconds * 1000L

    /** The track that plays while [teamIndex] holds the hill, if any. */
    fun trackFor(teamIndex: Int): String? =
        if (!musicMode) null else trackByTeam.getOrNull(teamIndex)

    companion object {
        val TEAM_OPTIONS = listOf(2, 3, 4, 5, 6)
        const val DEFAULT_TEAMS = 2
        const val DEFAULT_MINUTES = 10
        const val DEFAULT_MUSIC = false
        const val MIN_MINUTES = 1
        const val MAX_MINUTES = 99

        /** Time for players to reach their positions before the clock starts. */
        const val DEFAULT_COUNTDOWN = 20
        const val MIN_COUNTDOWN = 0
        const val MAX_COUNTDOWN = 120
        const val COUNTDOWN_STEP = 5

        /**
         * Digits per code. Every code in a round is the same length, so no code
         * can be a prefix of another and a full match is always unambiguous.
         * Short codes are quicker to type under fire; long ones are harder to
         * shoulder-surf.
         */
        const val DEFAULT_CODE_LENGTH = 7
        const val MIN_CODE_LENGTH = 3
        const val MAX_CODE_LENGTH = 10

        /**
         * The fuse cannot run longer than the cue that plays over it — the
         * sound is the timer. A shorter fuse starts the cue partway in so its
         * ending still lands on the explosion.
         */
        const val DEFAULT_FUSE = 45
        const val MIN_FUSE = 30
        const val MAX_FUSE = 45
        const val FUSE_STEP = 5
    }
}

/** Callsigns, in slot order. Supports the six team slots the setup screen offers. */
val TeamNames: List<String> = listOf(
    "ALPHA", "BRAVO", "CHARLIE", "DELTA", "ECHO", "FOXTROT"
)

/** `4:53` — a clock as players read it. */
fun formatClock(millis: Long): String {
    val totalSeconds = (millis / 1000L).coerceAtLeast(0L)
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
