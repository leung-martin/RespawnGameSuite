package com.example.respawnsuite.kiosk

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context

/**
 * Present only so the app can be made a device owner on a phone dedicated to
 * scorekeeping. Nothing here runs on an ordinary install.
 *
 * Provision a spare handset once, over adb, with no accounts signed in:
 *
 *     adb shell dpm set-device-owner \
 *         com.example.respawnsuite/com.example.respawnsuite.kiosk.KioskAdminReceiver
 *
 * After that the app pins itself silently — no system confirmation dialog on
 * launch, and no unpin gesture for a player to stumble into. Without it the app
 * still pins, it just asks the first time each launch.
 */
class KioskAdminReceiver : DeviceAdminReceiver() {

    companion object {

        private fun component(context: Context) =
            ComponentName(context.applicationContext, KioskAdminReceiver::class.java)

        /**
         * Allow-lists this app for lock task mode. Returns true when the app
         * owns the device and may therefore lock without prompting.
         */
        fun allowSilentLockTask(context: Context): Boolean {
            val policy = context.getSystemService(DevicePolicyManager::class.java)
                ?: return false
            if (!policy.isDeviceOwnerApp(context.packageName)) return false

            return runCatching {
                policy.setLockTaskPackages(component(context), arrayOf(context.packageName))
                true
            }.getOrDefault(false)
        }
    }
}
