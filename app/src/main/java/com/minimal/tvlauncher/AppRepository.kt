package com.minimal.tvlauncher

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import java.util.Locale

/** Reads every launchable app on the device (Leanback first, then regular launcher apps). */
class AppRepository(private val context: Context) {

    @Suppress("DEPRECATION")
    fun loadLaunchableApps(): List<AppInfo> {
        val pm = context.packageManager
        val apps = LinkedHashMap<String, AppInfo>()

        // Leanback entries come first so TV apps launch their TV activity.
        val categories = listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)
        for (category in categories) {
            val query = Intent(Intent.ACTION_MAIN).addCategory(category)
            for (info in pm.queryIntentActivities(query, 0)) {
                val activity = info.activityInfo ?: continue
                val pkg = activity.packageName
                if (pkg == context.packageName || apps.containsKey(pkg)) continue

                val launch = Intent(Intent.ACTION_MAIN)
                    .addCategory(category)
                    .setClassName(pkg, activity.name)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)

                val isSystem = (activity.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0

                apps[pkg] = AppInfo(
                    packageName = pkg,
                    label = info.loadLabel(pm).toString(),
                    icon = info.loadIcon(pm),
                    launchIntent = launch,
                    isSystem = isSystem
                )
            }
        }
        return apps.values.sortedBy { it.label.lowercase(Locale.getDefault()) }
    }
}
