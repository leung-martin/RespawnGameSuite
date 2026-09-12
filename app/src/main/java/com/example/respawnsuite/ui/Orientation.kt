package com.example.respawnsuite.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext

/**
 * How the suite sits in its mount, chosen once on the main menu and held from
 * there through the round and the results — a game never forces a rotation of
 * its own, because the phone is usually strapped to something by then.
 *
 * [AUTO] leaves the phone free to follow gravity, which is what an unmounted
 * phone passed hand to hand wants. The other two pin it, for a phone that is
 * bolted into a box on its side.
 */
enum class OrientationMode(val label: String) {
    AUTO("AUTO"),
    PORTRAIT("TALL"),
    LANDSCAPE("WIDE");

    /** The next mode along, wrapping, so one key can cycle the three. */
    fun stepped(step: Int): OrientationMode {
        val all = entries
        return all[((ordinal + step) % all.size + all.size) % all.size]
    }
}

private val OrientationMode.request: Int
    get() = when (this) {
        OrientationMode.AUTO -> ActivityInfo.SCREEN_ORIENTATION_SENSOR
        OrientationMode.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        OrientationMode.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    }

/**
 * Asks the activity to sit the way [mode] says. Rotating this way never rebuilds
 * the activity — the manifest handles the configuration change itself — so a
 * round survives being turned on its side mid-game.
 */
@Composable
fun ApplyOrientation(mode: OrientationMode) {
    val activity = LocalContext.current.findActivity() ?: return
    DisposableEffect(activity, mode) {
        activity.requestedOrientation = mode.request
        onDispose { }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * True when the screen is wider than it is tall. Every screen in the suite has
 * a layout for each, since a phone on its side has room for two columns and no
 * room to stack them.
 */
@Composable
fun isLandscape(): Boolean =
    LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
