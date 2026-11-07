package com.minimal.tvlauncher

import android.content.Intent
import android.graphics.drawable.Drawable

/** One launchable app, as shown on the Favourites row and in All apps. */
class AppInfo(
    val packageName: String,
    val label: String,
    val icon: Drawable,
    val launchIntent: Intent,
    val isSystem: Boolean
)
