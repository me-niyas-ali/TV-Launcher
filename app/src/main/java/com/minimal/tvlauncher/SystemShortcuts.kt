package com.minimal.tvlauncher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings

/**
 * Shortcuts into Android's own settings screens. Each shortcut tries the standard action first,
 * then the Android TV settings package, then the main Settings screen as a last resort.
 */
object SystemShortcuts {

    private const val TV_SETTINGS = "com.android.tv.settings"

    fun openWifi(context: Context): Boolean = open(
        context,
        Intent(Settings.ACTION_WIFI_SETTINGS),
        component("$TV_SETTINGS.connectivity.NetworkActivity"),
        Intent(Settings.ACTION_SETTINGS)
    )

    fun openBluetooth(context: Context): Boolean = open(
        context,
        Intent(Settings.ACTION_BLUETOOTH_SETTINGS),
        component("$TV_SETTINGS.accessories.AddAccessoryActivity"),
        Intent(Settings.ACTION_SETTINGS)
    )

    fun openDateTime(context: Context): Boolean = open(
        context,
        Intent(Settings.ACTION_DATE_SETTINGS),
        component("$TV_SETTINGS.device.datetime.DateTimeActivity"),
        Intent(Settings.ACTION_SETTINGS)
    )

    fun openSettings(context: Context): Boolean = open(
        context,
        Intent(Settings.ACTION_SETTINGS)
    )

    /** System-wide search (Google TV / Android TV search). False if the device has none. */
    fun openGlobalSearch(context: Context): Boolean = open(
        context,
        Intent("android.search.action.GLOBAL_SEARCH")
    )

    private fun component(className: String): Intent =
        Intent().setComponent(ComponentName(TV_SETTINGS, className))

    private fun open(context: Context, vararg candidates: Intent): Boolean {
        for (intent in candidates) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(intent)
                return true
            } catch (e: Exception) {
                // Not available on this device (ActivityNotFound / Security); try the next one.
            }
        }
        return false
    }
}
