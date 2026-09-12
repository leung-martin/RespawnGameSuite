package com.example.respawnsuite.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

private const val TAG = "ToneEngine"
private const val SAMPLE_RATE = 44_100

/** Fade in/out either end of a tone; square edges click on cheap speakers. */
private const val RAMP_MILLIS = 6

/** Audio written per pump pass. Also the worst-case delay before a cue starts. */
private const val CHUNK_MILLIS = 20

/** How long the pump keeps feeding silence before standing down. */
private const val IDLE_STOP_MILLIS = 4_000

/**
 * Generates tones on the fly rather than shipping audio files, so the suite can
 * make any sound it needs without assets. Output goes out as media, which is
 * what routes it to a paired Bluetooth speaker.
 *
 * Sound is produced by a pump that writes continuously — real samples when
 * something is playing, silence when not. That matters more than it sounds:
 * an audio track that runs dry gets dropped from the mixer's active list and
 * has to be restarted on the next write, and that restart costs a different
 * few tens of milliseconds every time. Feeding it without gaps keeps the route
 * hot, so evenly scheduled beeps actually come out evenly spaced.
 */
class ToneEngine private constructor() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val lock = Any()
    private val playing = mutableListOf<Voice>()
    private var pumping = false
    private var output: AudioTrack? = null

    /** A sound in flight, and how far through it the pump has read. */
    private class Voice(val samples: ShortArray) {
        var position = 0
    }

    /**
     * Fires and forgets — never blocks the round clock or the UI. Several tones
     * play back to back, which is how the two-note cues are built. Cues that
     * overlap are mixed rather than queued, so a keypress is never held up
     * waiting for a longer sound to finish.
     */
    /**
     * Silences the engine while the phone is asleep or the app is in the
     * background. Rounds keep their own time either way — this only decides
     * whether anyone hears them.
     */
    @Volatile
    var muted: Boolean = false

    fun play(vararg tones: Tone) {
        if (tones.isEmpty() || muted) return
        val voice = Voice(render(tones))
        val start = synchronized(lock) {
            playing.add(voice)
            if (pumping) false else { pumping = true; true }
        }
        if (start) scope.launch { pump() }
    }

    /**
     * Writes without pause until nothing has been playing for a while. Blocking
     * writes pace the loop, so the audio clock — not the coroutine scheduler —
     * decides when samples land.
     */
    private suspend fun pump() {
        val chunkFrames = framesFor(CHUNK_MILLIS)
        val chunk = ShortArray(chunkFrames)
        val mix = IntArray(chunkFrames)
        val idleLimit = IDLE_STOP_MILLIS / CHUNK_MILLIS
        var idleChunks = 0

        try {
            val track = open()
            while (true) {
                java.util.Arrays.fill(mix, 0)
                val voiced = synchronized(lock) { fill(mix) }

                idleChunks = if (voiced) 0 else idleChunks + 1
                if (idleChunks > idleLimit) {
                    synchronized(lock) {
                        // Nothing arrived while standing down; if something did,
                        // keep going rather than dropping it.
                        if (playing.isEmpty()) {
                            pumping = false
                            return
                        }
                    }
                    idleChunks = 0
                }

                for (index in 0 until chunkFrames) {
                    chunk[index] = mix[index].coerceIn(MIN_SAMPLE, MAX_SAMPLE).toShort()
                }
                writeFully(track, chunk)
            }
        } catch (failure: Throwable) {
            // Audio is a cue, never a blocker — but a silent failure is
            // indistinguishable from a muted speaker, so say so.
            Log.w(TAG, "tone playback failed", failure)
            synchronized(lock) {
                pumping = false
                playing.clear()
            }
        }
    }

    /** Sums every live voice into [mix]; returns whether any had samples left. */
    private fun fill(mix: IntArray): Boolean {
        if (playing.isEmpty()) return false
        var voiced = false
        val finished = mutableListOf<Voice>()

        for (voice in playing) {
            val available = min(mix.size, voice.samples.size - voice.position)
            for (index in 0 until available) {
                mix[index] += voice.samples[voice.position + index]
            }
            voice.position += available
            if (available > 0) voiced = true
            if (voice.position >= voice.samples.size) finished.add(voice)
        }
        playing.removeAll(finished)
        return voiced
    }

    private fun open(): AudioTrack {
        output?.let { return it }

        val minBuffer = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(maxOf(minBuffer, framesFor(CHUNK_MILLIS * 4) * Short.SIZE_BYTES))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        track.play()
        output = track
        return track
    }

    private fun writeFully(track: AudioTrack, samples: ShortArray) {
        var offset = 0
        while (offset < samples.size) {
            val written = track.write(samples, offset, samples.size - offset)
            if (written <= 0) break
            offset += written
        }
    }

    private fun framesFor(millis: Int): Int = SAMPLE_RATE * millis / 1000

    /** A cue as PCM: each tone ramped at both ends, gaps rendered as silence. */
    private fun render(tones: Array<out Tone>): ShortArray {
        val total = tones.sumOf { framesFor(it.durationMillis) + framesFor(it.gapAfterMillis) }
        val samples = ShortArray(total)
        var cursor = 0

        for (tone in tones) {
            val frames = framesFor(tone.durationMillis)
            val ramp = min(framesFor(RAMP_MILLIS), frames / 2).coerceAtLeast(1)
            for (index in 0 until frames) {
                val envelope = when {
                    index < ramp -> index.toFloat() / ramp
                    index > frames - ramp -> (frames - index).toFloat() / ramp
                    else -> 1f
                }
                val angle = 2.0 * PI * tone.frequencyHz * index / SAMPLE_RATE
                samples[cursor + index] =
                    (sin(angle) * envelope * tone.volume * Short.MAX_VALUE).toInt().toShort()
            }
            // Silence for the gap is already zeroed by the array's initial state.
            cursor += frames + framesFor(tone.gapAfterMillis)
        }
        return samples
    }

    companion object {
        private const val MIN_SAMPLE = Short.MIN_VALUE.toInt()
        private const val MAX_SAMPLE = Short.MAX_VALUE.toInt()

        /** One output for the whole app: screens come and go, the speaker stays. */
        val Instance: ToneEngine by lazy { ToneEngine() }
    }
}

/** One synthesized note, optionally followed by a pause before the next. */
data class Tone(
    val frequencyHz: Double,
    val durationMillis: Int,
    val volume: Float = 0.85f,
    val gapAfterMillis: Int = 0
) {
    companion object {
        /** Per-second pip while the start countdown runs. */
        val CountdownPip = Tone(frequencyHz = 880.0, durationMillis = 110)

        /** The last three seconds, pitched up so they read as "nearly there". */
        val CountdownPipFinal = Tone(frequencyHz = 1046.5, durationMillis = 150, volume = 0.95f)

        /** Long tone the moment the round clock starts — the "go" signal. */
        val RoundStart = Tone(frequencyHz = 1568.0, durationMillis = 2500, volume = 1f)

        /** One accepted keypad digit. Short and dry so fast typing stays crisp. */
        val KeyPress = Tone(frequencyHz = 660.0, durationMillis = 45, volume = 0.55f)

        /** A digit that can't continue any code, so the sequence was dropped. */
        val KeyReject = Tone(frequencyHz = 196.0, durationMillis = 170, volume = 0.6f)

        /** Rising pair on a successful capture — audible across a field. */
        val CaptureLow =
            Tone(frequencyHz = 784.0, durationMillis = 90, volume = 1f, gapAfterMillis = 20)
        val CaptureHigh = Tone(frequencyHz = 1318.5, durationMillis = 240, volume = 1f)

        /**
         * The suite's heartbeat: proof the speaker is connected and awake.
         * Every screen that pulses uses this one beep, so the sound means the
         * same thing wherever it is heard.
         */
        val Heartbeat = Tone(frequencyHz = 987.77, durationMillis = 55, volume = 0.3f)
    }
}

/**
 * Pacing for a start countdown: one beep a second while there is time to spare,
 * tightening smoothly into a stutter as zero approaches, so players hear the
 * start coming without having to watch the screen.
 */
object CountdownCadence {

    /** Beeps run at one a second until the countdown drops inside this window. */
    private const val ACCEL_WINDOW_MILLIS = 10_000f
    private const val SLOW_INTERVAL_MILLIS = 1_000f
    private const val FAST_INTERVAL_MILLIS = 130f

    /** Inside the last stretch the pips also pitch up. */
    private const val FINAL_STRETCH_MILLIS = 3_000L

    /** Gap kept between one pip ending and the next starting. */
    private const val PIP_GAP_MILLIS = 50L

    /** Time from this beep to the next, given what's left on the clock. */
    fun intervalMillis(remainingMillis: Long): Long {
        val ratio = (remainingMillis / ACCEL_WINDOW_MILLIS).coerceIn(0f, 1f)
        val interval = FAST_INTERVAL_MILLIS +
            (SLOW_INTERVAL_MILLIS - FAST_INTERVAL_MILLIS) * ratio
        return interval.toLong()
    }

    /** The pip itself, shortened as the gaps close so beeps stay distinct. */
    fun tone(remainingMillis: Long): Tone {
        val base =
            if (remainingMillis <= FINAL_STRETCH_MILLIS) Tone.CountdownPipFinal
            else Tone.CountdownPip
        val room = intervalMillis(remainingMillis) - PIP_GAP_MILLIS
        return base.copy(
            durationMillis = room.coerceIn(40L, base.durationMillis.toLong()).toInt()
        )
    }
}

/**
 * The beat the whole suite pulses to — the heartbeat beep, and the rate the
 * terminal caret blinks at, so the two always agree.
 */
const val HeartbeatIntervalMillis = 800L

/** The one pattern: a single soft beep. */
private val SingleBeep = listOf(Tone.Heartbeat)

/**
 * A soft beep on a loop, for the screens where nothing else makes noise. It is
 * how a marshal knows the Bluetooth speaker is still paired and audible before
 * a round starts, rather than finding out mid-game.
 *
 * Call it with no arguments: every screen pulses identically by construction.
 */
@Composable
fun AudioHeartbeat(
    pattern: List<Tone> = SingleBeep,
    intervalMillis: Long = HeartbeatIntervalMillis,
    tones: ToneEngine = rememberToneEngine()
) {
    LaunchedEffect(tones, intervalMillis, pattern) {
        while (true) {
            tones.play(*pattern.toTypedArray())
            delay(intervalMillis)
        }
    }
}

/** The app-wide output. Composables take it by this name for readability. */
@Composable
fun rememberToneEngine(): ToneEngine = ToneEngine.Instance
