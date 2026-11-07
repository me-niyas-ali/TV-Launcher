package com.minimal.tvlauncher

import android.content.Context
import org.json.JSONArray

/** Small SharedPreferences wrapper: favourites (ordered), hidden apps and wallpaper choice. */
class PrefsStore(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    var favorites: List<String>
        get() {
            val raw = prefs.getString(KEY_FAVORITES, null) ?: return emptyList()
            return try {
                val array = JSONArray(raw)
                List(array.length()) { array.getString(it) }
            } catch (e: Exception) {
                emptyList()
            }
        }
        set(value) {
            prefs.edit().putString(KEY_FAVORITES, JSONArray(value).toString()).apply()
        }

    var hidden: Set<String>
        get() = prefs.getStringSet(KEY_HIDDEN, emptySet())?.toSet() ?: emptySet()
        set(value) {
            prefs.edit().putStringSet(KEY_HIDDEN, value.toSet()).apply()
        }

    var wallpaperMode: String
        get() = prefs.getString(KEY_WALLPAPER_MODE, MODE_PRESET) ?: MODE_PRESET
        set(value) {
            prefs.edit().putString(KEY_WALLPAPER_MODE, value).apply()
        }

    var wallpaperPreset: Int
        get() = prefs.getInt(KEY_WALLPAPER_PRESET, 0)
        set(value) {
            prefs.edit().putInt(KEY_WALLPAPER_PRESET, value).apply()
        }

    /** Package of the device's own settings app, when the standard Settings screen is missing. */
    var settingsPackage: String?
        get() = prefs.getString(KEY_SETTINGS_PACKAGE, null)
        set(value) {
            prefs.edit().putString(KEY_SETTINGS_PACKAGE, value).apply()
        }

    fun isFavorite(packageName: String): Boolean = packageName in favorites

    /** Returns true if the app was added, false if it was removed. */
    fun toggleFavorite(packageName: String): Boolean {
        val list = favorites.toMutableList()
        val added = if (list.remove(packageName)) {
            false
        } else {
            list.add(packageName)
            true
        }
        favorites = list
        return added
    }

    fun moveFavorite(packageName: String, delta: Int) {
        val list = favorites.toMutableList()
        val from = list.indexOf(packageName)
        if (from < 0) return
        val to = (from + delta).coerceIn(0, list.lastIndex)
        if (from == to) return
        list.removeAt(from)
        list.add(to, packageName)
        favorites = list
    }

    fun hide(packageName: String) {
        hidden = hidden + packageName
    }

    companion object {
        private const val FILE = "launcher_prefs"
        private const val KEY_FAVORITES = "favorites"
        private const val KEY_HIDDEN = "hidden"
        private const val KEY_WALLPAPER_MODE = "wallpaper_mode"
        private const val KEY_WALLPAPER_PRESET = "wallpaper_preset"
        private const val KEY_SETTINGS_PACKAGE = "settings_package"

        const val MODE_PRESET = "preset"
        const val MODE_IMAGE = "image"
    }
}
