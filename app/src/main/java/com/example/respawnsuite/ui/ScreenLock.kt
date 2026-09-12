package com.example.respawnsuite.ui

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.respawnsuite.kiosk.KioskAdminReceiver

/**
 * Kiosk-locks the app while [active]: system bars are hidden so there is no
 * gesture handle to catch a sleeve, and lock task mode blocks Home, Recents and
 * the system Back gesture outright.
 *
 * Lock task mode needs no special permission for a normal app, but the system
 * only accepts the request from a resumed activity — hence the lifecycle
 * observer rather than a one-shot call. Re-engaging on every resume is also
 * what makes the lock hold for a whole game day: if someone unpins and comes
 * back, the app locks itself again.
 */
@Composable
fun ScreenLock(active: Boolean) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    DisposableEffect(activity, active) {
        if (activity == null) return@DisposableEffect onDispose { }

        val window = activity.window
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        val lifecycle = (activity as? ComponentActivity)?.lifecycle

        fun engage() {
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (!activity.isLockTaskActive()) {
                // On a provisioned handset this makes the lock silent; on an
                // ordinary install it does nothing and the system asks first.
                KioskAdminReceiver.allowSilentLockTask(activity)
                runCatching { activity.startLockTask() }
            }
        }

        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) engage()
        }

        if (active) {
            // Already resumed on a later navigation; not yet on first launch.
            if (lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) == true) {
                engage()
            }
            lifecycle?.addObserver(observer)
        }

        onDispose {
            lifecycle?.removeObserver(observer)
            if (active) {
                if (activity.isLockTaskActive()) {
                    runCatching { activity.stopLockTask() }
                }
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
}

/**
 * Holds the display awake while [active]. A round can run for the best part of
 * an hour with nobody touching the phone — the score has to still be on screen
 * when it ends, so the display timeout must not apply.
 *
 * Applied app-wide today: the menus keep the screen up too, which is what a
 * marshal wants from a device sitting open on a table between rounds. Passing a
 * narrower condition here is all it takes to scope it to the live round.
 */
@Composable
fun KeepScreenOn(active: Boolean = true) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    DisposableEffect(activity, active) {
        val window = activity?.window
        if (active) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            if (active) {
                window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }
}

private fun Activity.isLockTaskActive(): Boolean {
    val manager = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return false
    return manager.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE
}

private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
