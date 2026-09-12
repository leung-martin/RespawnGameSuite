package com.example.respawnsuite.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlin.math.abs

private const val MUSIC_TAG = "Music"

/** Folder inside the app's assets that holds the round music. */
private const val MUSIC_DIR = "music"

/** Volume changes smaller than this are not worth a call into the player. */
private const val LEVEL_EPSILON = 0.005f

/**
 * The tracks shipped with the app, read from the assets folder at runtime —
 * dropping another file into `assets/music` is all it takes to add one, no code
 * change needed.
 */
object MusicLibrary {

    fun tracks(context: Context): List<String> =
        runCatching { context.assets.list(MUSIC_DIR)?.sorted().orEmpty() }
            .onFailure { Log.w(MUSIC_TAG, "could not list music assets", it) }
            .getOrDefault(emptyList())

    /** `DOOM_OST.mp3` reads as `DOOM_OST` on screen. */
    fun displayName(track: String): String = track.substringBeforeLast('.')
}

/**
 * Plays one looping track at a time — the music belonging to whichever team
 * currently holds the hill.
 */
class MusicPlayer(private val context: Context) {

    private var player: MediaPlayer? = null
    private var current: String? = null

    /**
     * True while a track is loaded and running. Screens watch this to know
     * whether the round is making any noise of its own.
     */
    var sounding by mutableStateOf(false)
        private set

    /** 0..1, applied to whatever is playing now and whatever starts next. */
    private var level = 1f

    /**
     * Sets the music volume. A track started later picks the level up too, so a
     * capture part-way through a fade comes in at the level the fade has reached
     * rather than jumping back to full.
     */
    fun setLevel(value: Float) {
        val clamped = value.coerceIn(0f, 1f)
        if (abs(clamped - level) < LEVEL_EPSILON) return
        level = clamped
        runCatching { player?.setVolume(level, level) }
    }

    /** Starts [track] looping. Re-requesting the playing track is a no-op. */
    fun play(track: String) {
        if (track == current && player != null) return
        stop()

        runCatching {
            val descriptor = context.assets.openFd("$MUSIC_DIR/$track")
            val media = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                setDataSource(
                    descriptor.fileDescriptor,
                    descriptor.startOffset,
                    descriptor.length
                )
                isLooping = true
                setVolume(level, level)
                // Prepared off the calling thread: a round must never stutter
                // because a file took a moment to open.
                setOnPreparedListener {
                    it.setVolume(level, level)
                    it.start()
                }
                prepareAsync()
            }
            descriptor.close()
            player = media
            current = track
            sounding = true
        }.onFailure { failure ->
            Log.w(MUSIC_TAG, "could not play $track", failure)
            current = null
            sounding = false
        }
    }

    /** Holds the track where it is; [resume] picks it up from the same bar. */
    fun pause() {
        runCatching { player?.takeIf { it.isPlaying }?.pause() }
    }

    fun resume() {
        runCatching { player?.takeIf { !it.isPlaying }?.start() }
    }

    fun stop() {
        player?.let { media ->
            runCatching {
                if (media.isPlaying) media.stop()
            }
            runCatching { media.release() }
        }
        player = null
        current = null
        sounding = false
    }
}

@Composable
fun rememberMusicPlayer(): MusicPlayer {
    val context = LocalContext.current
    val player = remember(context) { MusicPlayer(context) }
    PauseWhileAway(onPause = player::pause, onResume = player::resume)
    DisposableEffect(player) {
        onDispose { player.stop() }
    }
    return player
}

/**
 * Holds a player silent while the phone is asleep or the app is away, and picks
 * it up again when it comes back. See [Audible].
 */
@Composable
private fun PauseWhileAway(onPause: () -> Unit, onResume: () -> Unit) {
    val audible = Audible.value
    val pause by rememberUpdatedState(onPause)
    val resume by rememberUpdatedState(onResume)
    LaunchedEffect(audible) {
        if (audible) resume() else pause()
    }
}

/** One-shot cues that punctuate a round, held in `assets/sfx`. */
object Sfx {
    /** Exactly 45 seconds long, so it lands on zero if started at 0:45. */
    const val FINAL_COUNTDOWN = "45sCountdown.mp3"

    /** The round is over. */
    const val ROUND_OVER = "kaboom.mp3"

    /** The bomb was beaten. */
    const val DEFUSED = "defusal.mp3"

    const val DIR = "sfx"

    /** Cue length, which is also when in the round it has to start. */
    const val FINAL_COUNTDOWN_MILLIS = 45_000L

    /** How long the music takes to fade away once the cue starts. */
    const val MUSIC_FADE_MILLIS = 30_000L

    /**
     * Music level for a given amount of clock left: full until the final cue
     * begins, then down to silence over [MUSIC_FADE_MILLIS], leaving the last
     * stretch of the round to the countdown alone.
     *
     * Derived from the clock rather than run as a one-shot animation, so a team
     * capturing part-way through the fade comes in at the level the fade has
     * already reached instead of blasting back to full.
     */
    fun musicLevelFor(remainingMillis: Long): Float {
        val silentAt = FINAL_COUNTDOWN_MILLIS - MUSIC_FADE_MILLIS
        return when {
            remainingMillis >= FINAL_COUNTDOWN_MILLIS -> 1f
            remainingMillis <= silentAt -> 0f
            else -> (remainingMillis - silentAt).toFloat() / MUSIC_FADE_MILLIS
        }
    }
}

/**
 * Plays one-shot cues over whatever else is sounding — a cue never interrupts
 * the holding team's music, it lands on top of it.
 */
class SfxPlayer(private val context: Context) {

    private val active = mutableListOf<MediaPlayer>()

    /**
     * True while any cue is still sounding. A screen that fills its silence
     * with the idle pulse waits on this rather than on a guessed duration, so
     * a cue started part-way in still holds the pulse off for exactly as long
     * as it actually runs.
     */
    var sounding by mutableStateOf(false)
        private set

    /**
     * Plays [asset], optionally skipping [startMillis] into it. Starting a cue
     * partway in is how a shorter fuse still ends on the same bang: the tail of
     * the recording is what matters, so the head is what gets dropped.
     */
    fun play(asset: String, startMillis: Long = 0L) {
        runCatching {
            val descriptor = context.assets.openFd("${Sfx.DIR}/$asset")
            val media = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(
                    descriptor.fileDescriptor,
                    descriptor.startOffset,
                    descriptor.length
                )
                setOnCompletionListener { finished ->
                    val remaining = synchronized(active) {
                        active.remove(finished)
                        active.size
                    }
                    sounding = remaining > 0
                    runCatching { finished.release() }
                }
                setOnPreparedListener { prepared ->
                    if (startMillis > 0L) {
                        // SEEK_CLOSEST rather than the default keyframe seek:
                        // landing a second early would put the bang a second
                        // late.
                        prepared.seekTo(startMillis, MediaPlayer.SEEK_CLOSEST)
                    }
                    prepared.start()
                }
                prepareAsync()
            }
            descriptor.close()
            synchronized(active) { active.add(media) }
            sounding = true
        }.onFailure { failure ->
            Log.w(MUSIC_TAG, "could not play cue $asset", failure)
        }
    }

    /** Holds every cue in flight, for a phone that has just gone to sleep. */
    fun pause() {
        synchronized(active) {
            active.forEach { media -> runCatching { if (media.isPlaying) media.pause() } }
        }
    }

    fun resume() {
        synchronized(active) {
            active.forEach { media -> runCatching { if (!media.isPlaying) media.start() } }
        }
    }

    fun stopAll() {
        synchronized(active) {
            active.forEach { media ->
                runCatching { if (media.isPlaying) media.stop() }
                runCatching { media.release() }
            }
            active.clear()
        }
        sounding = false
    }
}

@Composable
fun rememberSfxPlayer(): SfxPlayer {
    val context = LocalContext.current
    val player = remember(context) { SfxPlayer(context) }
    PauseWhileAway(onPause = player::pause, onResume = player::resume)
    DisposableEffect(player) {
        onDispose { player.stopAll() }
    }
    return player
}
