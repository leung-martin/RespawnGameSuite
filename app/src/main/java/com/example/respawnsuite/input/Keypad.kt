package com.example.respawnsuite.input

import androidx.compose.foundation.focusable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The vocabulary of the USB-C keypad players use in the field. Games only ever
 * react to these, so a numpad, a full keyboard and the touchscreen all agree.
 */
sealed interface KeypadEvent {
    data class Digit(val value: Int) : KeypadEvent
    data object Enter : KeypadEvent
    data object Back : KeypadEvent
    data object Up : KeypadEvent
    data object Down : KeypadEvent
    data object Left : KeypadEvent
    data object Right : KeypadEvent
    data object Plus : KeypadEvent
    data object Minus : KeypadEvent
    data object Clear : KeypadEvent
}

/** Maps both the numpad block and the number row, since either may be plugged in. */
fun KeyEvent.toKeypadEvent(): KeypadEvent? = when (key) {
    Key.NumPad0, Key.Zero -> KeypadEvent.Digit(0)
    Key.NumPad1, Key.One -> KeypadEvent.Digit(1)
    Key.NumPad2, Key.Two -> KeypadEvent.Digit(2)
    Key.NumPad3, Key.Three -> KeypadEvent.Digit(3)
    Key.NumPad4, Key.Four -> KeypadEvent.Digit(4)
    Key.NumPad5, Key.Five -> KeypadEvent.Digit(5)
    Key.NumPad6, Key.Six -> KeypadEvent.Digit(6)
    Key.NumPad7, Key.Seven -> KeypadEvent.Digit(7)
    Key.NumPad8, Key.Eight -> KeypadEvent.Digit(8)
    Key.NumPad9, Key.Nine -> KeypadEvent.Digit(9)
    Key.Enter, Key.NumPadEnter, Key.DirectionCenter, Key.Spacebar -> KeypadEvent.Enter
    Key.Escape, Key.Back -> KeypadEvent.Back
    Key.Backspace, Key.Delete, Key.NumPadDot, Key.Period -> KeypadEvent.Clear
    Key.NumPadAdd, Key.Plus, Key.Equals, Key.NumPadEquals -> KeypadEvent.Plus
    Key.NumPadSubtract, Key.Minus -> KeypadEvent.Minus
    Key.DirectionUp -> KeypadEvent.Up
    Key.DirectionDown -> KeypadEvent.Down
    Key.DirectionLeft -> KeypadEvent.Left
    Key.DirectionRight -> KeypadEvent.Right
    else -> null
}

/** A direction on a menu, however the player asked for it. */
enum class Nav { Up, Down, Left, Right }

/**
 * The navigation reading of a key. A numpad has no arrow cluster, so the ring of
 * digits around 5 stands in for one — 8/2/4/6 point the way they sit on the pad.
 * In-round screens read digits as digits and never call this, so a code
 * containing an 8 is still just a code.
 */
fun KeypadEvent.asNav(): Nav? = when (this) {
    is KeypadEvent.Digit -> when (value) {
        8 -> Nav.Up
        2 -> Nav.Down
        4 -> Nav.Left
        6 -> Nav.Right
        else -> null
    }

    KeypadEvent.Up -> Nav.Up
    KeypadEvent.Down -> Nav.Down
    KeypadEvent.Left -> Nav.Left
    KeypadEvent.Right -> Nav.Right
    else -> null
}

/**
 * Which way this key moves a setting: `+1`, `-1`, or null for keys that are not
 * asking for a change. Both the sideways navigation keys and the pad's own
 * plus/minus land here, so either habit works.
 */
fun KeypadEvent.asAdjust(): Int? = when {
    this == KeypadEvent.Plus -> 1
    this == KeypadEvent.Minus -> -1
    asNav() == Nav.Right -> 1
    asNav() == Nav.Left -> -1
    else -> null
}

/**
 * How long a key-up is held in reserve before it counts as a release.
 *
 * Keypads do not agree on what a held key looks like. Some raise the repeat
 * count on a stream of key-downs; the USB pads this suite is built around send a
 * fresh down/up pair per repeat instead — one keystroke, a pause of about four
 * hundred milliseconds, then a burst every fifty. Read literally, that pad can
 * never complete a press-and-hold: the first gap looks like a release and resets
 * the bar. So a release is only believed once the key has been quiet for longer
 * than that initial pause.
 */
private const val FIRST_GAP_GRACE_MILLIS = 600L

/**
 * The same window once the repeats are actually streaming, where the gaps are
 * short. Letting go mid-hold is noticed this quickly, so a hold can only
 * complete within a breath of the key still being down.
 */
private const val REPEAT_GAP_GRACE_MILLIS = 250L

/**
 * Grabs hardware key focus for the screen this is applied to. Return true from
 * [onEvent] to consume the key, false to let it fall through.
 */
@Composable
fun Modifier.keypadInput(
    /**
     * Called as a key goes down and again as it comes up, which is what lets a
     * screen offer press-and-hold on the keypad the same way it does on glass.
     */
    onPress: (KeypadEvent, pressed: Boolean) -> Unit = { _, _ -> },
    onEvent: (KeypadEvent) -> Boolean
): Modifier {
    val focusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    // One pending release per key, so two keys can be held at once without
    // either one cancelling the other's window.
    val pendingRelease = remember { mutableMapOf<KeypadEvent, Job>() }
    // How many downs this press has produced, which is how the long first gap
    // is told apart from the short ones inside a repeat burst.
    val downCount = remember { mutableMapOf<KeypadEvent, Int>() }

    LaunchedEffect(focusRequester) {
        runCatching { focusRequester.requestFocus() }
    }
    return this
        .focusRequester(focusRequester)
        .focusable()
        .onKeyEvent { event ->
            val mapped = event.toKeypadEvent() ?: return@onKeyEvent false
            when (event.type) {
                KeyEventType.KeyDown -> {
                    // Still down: whatever release was waiting was only the gap
                    // before, or between, two repeats.
                    pendingRelease.remove(mapped)?.cancel()
                    downCount[mapped] = (downCount[mapped] ?: 0) + 1

                    // A key held long enough to auto-repeat reports it here;
                    // those repeats are swallowed so leaning on a digit cannot
                    // type it a dozen times. A pad that repeats by sending whole
                    // down/up pairs is not swallowed, because at that point a
                    // repeat and a fast second tap are the same event — and a
                    // digit typed twice is visible and clearable, where a digit
                    // silently dropped is not.
                    if (event.nativeKeyEvent.repeatCount > 0) return@onKeyEvent true
                    onPress(mapped, true)
                    onEvent(mapped)
                }

                KeyEventType.KeyUp -> {
                    // Reported late on purpose; see the grace constants.
                    pendingRelease.remove(mapped)?.cancel()
                    val grace = if ((downCount[mapped] ?: 0) > 1) {
                        REPEAT_GAP_GRACE_MILLIS
                    } else {
                        FIRST_GAP_GRACE_MILLIS
                    }
                    pendingRelease[mapped] = scope.launch {
                        delay(grace)
                        pendingRelease.remove(mapped)
                        downCount.remove(mapped)
                        onPress(mapped, false)
                    }
                    true
                }

                else -> false
            }
        }
}
