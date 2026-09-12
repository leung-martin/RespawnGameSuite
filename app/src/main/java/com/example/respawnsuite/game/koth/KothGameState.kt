package com.example.respawnsuite.game.koth

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.respawnsuite.game.RoundConfig
import com.example.respawnsuite.game.TeamNames
import kotlin.random.Random

/** Where a round is in its life: pre-start, live, or over. */
enum class KothPhase { COUNTDOWN, RUNNING, FINISHED }

/**
 * The whole King of the Hill round: the pre-start countdown, who holds the hill,
 * what each team must type to take it, and how long everyone has held it.
 *
 * A team's code is hidden while they hold the hill and is only replaced once it
 * has actually been used to capture — losing the hill is what earns a team a
 * fresh code, so the other teams' codes stay valid all round.
 */
@Stable
class KothGameState(
    val config: RoundConfig,
    private val random: Random = Random.Default
) {
    val teamNames: List<String> = TeamNames.take(config.teamCount)

    private val codeState = mutableStateListOf<String?>()
    private val heldState = mutableStateListOf<Long>()

    /** Per team: the code to type, or null while that team is holding the hill. */
    val codes: List<String?> get() = codeState

    /** Per team: milliseconds of hill time banked so far. */
    val heldMillis: List<Long> get() = heldState

    /** Index into [teamNames], or null while the hill is uncontested. */
    var holder by mutableStateOf<Int?>(null)
        private set

    /** Digits typed on the keypad so far. */
    var input by mutableStateOf("")
        private set

    var phase by mutableStateOf(
        if (config.countdownSeconds > 0) KothPhase.COUNTDOWN else KothPhase.RUNNING
    )
        private set

    var countdownMillis by mutableLongStateOf(config.countdownMillis)
        private set

    var remainingMillis by mutableLongStateOf(config.roundMillis)
        private set

    val finished: Boolean get() = phase == KothPhase.FINISHED

    private var lastTick = 0L
    private var running = false

    init {
        repeat(config.teamCount) {
            heldState.add(0L)
            codeState.add(null)
        }
        for (index in codeState.indices) {
            codeState[index] = newCode()
        }
    }

    /** [now] is an elapsed-realtime reading; the clocks are driven off wall time
     *  rather than counted frames so they cannot drift over a long round. */
    fun start(now: Long) {
        if (!running) {
            running = true
            lastTick = now
        }
    }

    fun tick(now: Long) {
        if (!running || phase == KothPhase.FINISHED) return
        val delta = (now - lastTick).coerceAtLeast(0L)
        lastTick = now
        if (delta == 0L) return

        when (phase) {
            KothPhase.COUNTDOWN -> {
                countdownMillis -= delta
                if (countdownMillis <= 0L) {
                    // Whatever the countdown overshot by belongs to the round.
                    val carried = -countdownMillis
                    countdownMillis = 0L
                    phase = KothPhase.RUNNING
                    if (carried > 0L) advanceRound(carried)
                }
            }

            KothPhase.RUNNING -> advanceRound(delta)

            KothPhase.FINISHED -> Unit
        }
    }

    private fun advanceRound(delta: Long) {
        val applied = minOf(delta, remainingMillis)
        holder?.let { heldState[it] = heldState[it] + applied }
        remainingMillis -= applied
        if (remainingMillis <= 0L) {
            remainingMillis = 0L
            phase = KothPhase.FINISHED
        }
    }

    /** Feeds one keypad digit in. Completing a code captures the hill. */
    fun onDigit(digit: Int) {
        if (phase != KothPhase.RUNNING) return
        val candidate = input + digit

        val match = codeState.indexOfFirst { it == candidate }
        if (match >= 0) {
            capture(match)
            return
        }

        val single = digit.toString()
        input = when {
            codeState.any { it != null && it.startsWith(candidate) } -> candidate
            // A mistyped digit shouldn't cost the whole sequence, so restart
            // from this digit if it could still begin a code.
            codeState.any { it != null && it.startsWith(single) } -> single
            else -> ""
        }
    }

    fun clearInput() {
        input = ""
    }

    /** Ends the round early; hill time already banked still counts. */
    fun abort(now: Long) {
        tick(now)
        phase = KothPhase.FINISHED
    }

    private fun capture(index: Int) {
        val previous = holder
        codeState[index] = null
        if (previous != null && previous != index) {
            codeState[previous] = newCode()
        }
        holder = index
        input = ""
    }

    fun results(): List<KothResult> =
        teamNames.mapIndexed { index, name -> KothResult(name, heldState[index]) }

    /** Everything the results screen needs, including who was on the hill. */
    fun outcome(aborted: Boolean): KothOutcome = KothOutcome(
        results = results(),
        finalHolder = holder?.let { teamNames[it] },
        aborted = aborted,
        remainingMillis = remainingMillis
    )

    private fun newCode(): String {
        val taken = codeState.filterNotNull().toSet()
        while (true) {
            val code = buildString {
                repeat(config.codeLength) { append(random.nextInt(10)) }
            }
            if (code !in taken) return code
        }
    }
}
