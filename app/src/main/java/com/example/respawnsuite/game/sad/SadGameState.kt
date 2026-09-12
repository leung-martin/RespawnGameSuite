package com.example.respawnsuite.game.sad

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.respawnsuite.game.RoundConfig
import kotlin.random.Random

/** The two sides of a bomb round. */
val SadTeamNames: List<String> = listOf("ATTACK", "DEFENCE")

/**
 * How long a part-typed code may sit unfinished. Long enough to finish a code
 * you are already reading, short enough that a bomb abandoned mid-plant is no
 * use to whoever picks the phone up next.
 */
const val ARM_WINDOW_MILLIS = 10_000L

/**
 * Where a bomb round is: waiting to start, being carried, ticking, or over.
 * There is no "running" phase in the hill sense — the bomb is either in a
 * pocket or on the ground counting down.
 */
enum class SadPhase { COUNTDOWN, ATTACK, ARMED, FINISHED }

/** How a bomb round finished. */
enum class SadEnding {
    /** The fuse ran out. Attack wins. */
    DETONATED,

    /** Defence typed the disarm code in time. Defence wins. */
    DEFUSED,

    /** The plant window closed with the bomb still in a pocket. Defence wins. */
    NOT_PLANTED,

    /** Called off by the marshal; nobody wins. */
    ABORTED
}

/** The verdict, plus the numbers behind it. */
data class SadOutcome(
    val ending: SadEnding,
    /** Whatever was left on the clock when the round stopped. */
    val remainingMillis: Long
) {
    val winner: String?
        get() = when (ending) {
            SadEnding.DETONATED -> SadTeamNames[0]
            SadEnding.DEFUSED, SadEnding.NOT_PLANTED -> SadTeamNames[1]
            SadEnding.ABORTED -> null
        }
}

/**
 * A bomb round. Attack carries the phone to a site and types the arm code;
 * that starts a fixed fuse and reveals the disarm code for defence to find and
 * type. The fuse is the length of the arming cue that plays over it, so what
 * players hear and what the screen says can never disagree.
 */
@Stable
class SadGameState(
    val config: RoundConfig,
    private val random: Random = Random.Default
) {
    /** Typed by attack to plant. Hidden once the bomb is armed. */
    var armCode by mutableStateOf(newCode(null))
        private set

    /** Revealed to defence only once the bomb is ticking. */
    var disarmCode by mutableStateOf<String?>(null)
        private set

    var input by mutableStateOf("")
        private set

    var phase by mutableStateOf(
        if (config.countdownSeconds > 0) SadPhase.COUNTDOWN else SadPhase.ATTACK
    )
        private set

    var countdownMillis by mutableLongStateOf(config.countdownMillis)
        private set

    /**
     * One clock for the whole round. It starts as the plant window and, the
     * instant the bomb is armed, is reset to the fuse — so there is never a
     * second timer to reconcile, and the number on screen always means "time
     * until this round is decided".
     */
    var remainingMillis by mutableLongStateOf(config.roundMillis)
        private set

    var ending by mutableStateOf<SadEnding?>(null)
        private set

    /**
     * Time left to finish an arm code that has been started, or null when
     * nothing is part-typed. Walking away from a half-typed code cannot leave it
     * waiting on the ground for someone else to finish, so the window closes on
     * its own and the code is scrambled.
     */
    var armWindowMillis by mutableStateOf<Long?>(null)
        private set

    /** How much of that window is left, 0..1, for a bar to drain. */
    val armWindowFraction: Float
        get() = armWindowMillis?.let { it.toFloat() / ARM_WINDOW_MILLIS } ?: 0f

    val finished: Boolean get() = phase == SadPhase.FINISHED

    private var lastTick = 0L
    private var running = false

    fun start(now: Long) {
        if (!running) {
            running = true
            lastTick = now
        }
    }

    fun tick(now: Long) {
        if (!running || phase == SadPhase.FINISHED) return
        val delta = (now - lastTick).coerceAtLeast(0L)
        lastTick = now
        if (delta == 0L) return

        when (phase) {
            SadPhase.COUNTDOWN -> {
                countdownMillis -= delta
                if (countdownMillis <= 0L) {
                    val carried = -countdownMillis
                    countdownMillis = 0L
                    phase = SadPhase.ATTACK
                    if (carried > 0L) advanceClock(carried)
                }
            }

            SadPhase.ATTACK, SadPhase.ARMED -> {
                advanceClock(delta)
                countArmWindow(delta)
            }

            SadPhase.FINISHED -> Unit
        }
    }

    /**
     * Runs down the window on a part-typed arm code. When it closes the code is
     * scrambled as well as cleared: the digits already read off the screen are
     * no longer worth anything.
     */
    private fun countArmWindow(delta: Long) {
        val left = armWindowMillis ?: return
        val remaining = left - delta
        if (remaining > 0L) {
            armWindowMillis = remaining
            return
        }
        armWindowMillis = null
        input = ""
        if (phase == SadPhase.ATTACK) armCode = newCode(armCode)
    }

    /** Zero means different things either side of the plant, but it is one clock. */
    private fun advanceClock(delta: Long) {
        remainingMillis -= delta
        if (remainingMillis <= 0L) {
            remainingMillis = 0L
            end(
                if (phase == SadPhase.ARMED) SadEnding.DETONATED else SadEnding.NOT_PLANTED
            )
        }
    }

    /**
     * Feeds one keypad digit in. Which code it is matched against depends on
     * the phase, so the same keypad serves both sides without a mode switch.
     */
    fun onDigit(digit: Int) {
        val target = when (phase) {
            SadPhase.ATTACK -> armCode
            SadPhase.ARMED -> disarmCode
            else -> null
        } ?: return

        val candidate = input + digit
        if (candidate == target) {
            when (phase) {
                SadPhase.ATTACK -> arm()
                SadPhase.ARMED -> end(SadEnding.DEFUSED)
                else -> Unit
            }
            input = ""
            armWindowMillis = null
            return
        }

        val single = digit.toString()
        input = when {
            target.startsWith(candidate) -> candidate
            // A mistyped digit shouldn't cost the whole sequence.
            target.startsWith(single) -> single
            else -> ""
        }

        // The clock on a part-typed code runs from the first digit, not the
        // last: leaning on the keypad cannot hold the window open forever.
        armWindowMillis = when {
            input.isEmpty() -> null
            armWindowMillis == null -> ARM_WINDOW_MILLIS
            else -> armWindowMillis
        }
    }

    fun clearInput() {
        input = ""
        armWindowMillis = null
    }

    private fun arm() {
        // The disarm code is generated at plant time, so it cannot be read off
        // the phone before the bomb is down.
        disarmCode = newCode(armCode)
        // However much of the plant window was left, the fuse is the fuse.
        remainingMillis = config.fuseMillis
        phase = SadPhase.ARMED
    }

    /** Ends the round early. Nobody wins a round that was called off. */
    fun abort(now: Long) {
        tick(now)
        if (phase != SadPhase.FINISHED) end(SadEnding.ABORTED)
    }

    private fun end(reason: SadEnding) {
        ending = reason
        phase = SadPhase.FINISHED
    }

    fun outcome(): SadOutcome = SadOutcome(
        ending = ending ?: SadEnding.ABORTED,
        remainingMillis = remainingMillis
    )

    private fun newCode(other: String?): String {
        while (true) {
            val code = buildString {
                repeat(config.codeLength) { append(random.nextInt(10)) }
            }
            if (code != other) return code
        }
    }
}
