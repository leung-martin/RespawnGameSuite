package com.example.respawnsuite.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import android.util.Log
import android.view.ViewTreeObserver
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

private const val QUIET_TAG = "Quiet"

/**
 * Whether anything is allowed to make a sound right now.
 *
 * A kiosk-locked app holding the screen awake is not reliably stopped when the
 * power button is pressed, so the lifecycle alone cannot answer "is anyone
 * looking at this". Whether the phone is interactive can, and that is what a
 * player means by "the phone is off". Window focus answers the other half: a
 * notification shade or a system dialog leaves the activity resumed while no
 * longer being the thing in use, and the speaker should follow the attention.
 */
object Audible {
    var value by mutableStateOf(true)
        internal set
}

/**
 * Keeps [Audible] in step with the phone: silent while the screen is off, the
 * app is in the background, or something else has the foreground — audible
 * again the moment it comes back. Rounds are wall-clock driven and run on
 * regardless; this only decides whether the speaker is allowed to speak.
 */
@Composable
fun QuietWhileAway() {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val view = LocalView.current

    DisposableEffect(context, owner, view) {
        val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager

        fun apply() {
            val visible = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            val awake = power?.isInteractive ?: true
            val focused = view.hasWindowFocus()
            val audible = visible && awake && focused
            if (audible != Audible.value) {
                Log.i(
                    QUIET_TAG,
                    "audible=$audible (awake=$awake visible=$visible focused=$focused)"
                )
            }
            Audible.value = audible
            ToneEngine.Instance.muted = !audible
        }

        val screen = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) = apply()
        }
        ContextCompat.registerReceiver(
            context,
            screen,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        val lifecycleObserver = LifecycleEventObserver { _, _ -> apply() }
        owner.lifecycle.addObserver(lifecycleObserver)

        // Pulling down the shade never touches the lifecycle, so focus has to be
        // listened for in its own right.
        val focusListener = ViewTreeObserver.OnWindowFocusChangeListener { apply() }
        view.viewTreeObserver.addOnWindowFocusChangeListener(focusListener)

        apply()

        onDispose {
            runCatching { context.unregisterReceiver(screen) }
            owner.lifecycle.removeObserver(lifecycleObserver)
            runCatching {
                view.viewTreeObserver.removeOnWindowFocusChangeListener(focusListener)
            }
            Audible.value = true
            ToneEngine.Instance.muted = false
        }
    }
}
