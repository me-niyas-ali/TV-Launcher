package com.minimal.tvlauncher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

/**
 * Custom wallpaper handling: a user image copied into app storage, or a built-in gradient.
 * Light gradients switch the launcher UI to dark text (see Theme.TVLauncher.Light).
 */
object Wallpapers {

    class Preset(val name: String, val isLight: Boolean, val colors: IntArray)

    private fun preset(name: String, isLight: Boolean, from: String, to: String) =
        Preset(name, isLight, intArrayOf(Color.parseColor(from), Color.parseColor(to)))

    /** Index in this list is what gets stored in preferences. Index 0 is the default. */
    val presets: List<Preset> = listOf(
        // Dark gradients
        preset("Midnight", false, "#0B1020", "#1B2A4A"),
        preset("Slate", false, "#15181E", "#2C323D"),
        preset("Dusk", false, "#1A1030", "#4A2A5A"),
        preset("Forest", false, "#0A1A14", "#1F4033"),
        preset("Ocean", false, "#04141F", "#0F4C5C"),
        preset("Ember", false, "#1F0B0B", "#5A2A1A"),
        // Light gradients
        preset("Cloud", true, "#F5F7FA", "#D9E2EC"),
        preset("Peach", true, "#FFF1E6", "#FAD2C0"),
        preset("Mint", true, "#E8F7F0", "#BFE8D4"),
        preset("Sky", true, "#E6F2FF", "#B8D8F8"),
        preset("Lavender", true, "#F1ECFF", "#D5C8F5"),
        preset("Sand", true, "#FAF3E3", "#E8D9B5")
    )

    private const val FILE_NAME = "wallpaper.jpg"
    private const val MAX_SIDE = 1920

    fun imageFile(context: Context): File = File(context.filesDir, FILE_NAME)

    /** Blocking: call from a background thread. */
    fun load(context: Context, prefs: PrefsStore): Drawable {
        if (prefs.wallpaperMode == PrefsStore.MODE_IMAGE) {
            val file = imageFile(context)
            val bitmap = if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
            // A missing image falls back to the dark default so the UI tone stays consistent.
            return if (bitmap != null) BitmapDrawable(context.resources, bitmap) else presetDrawable(0)
        }
        return presetDrawable(prefs.wallpaperPreset)
    }

    fun presetDrawable(index: Int, cornerRadius: Float = 0f): GradientDrawable {
        val preset = presets[index.coerceIn(0, presets.lastIndex)]
        return GradientDrawable(GradientDrawable.Orientation.TL_BR, preset.colors).apply {
            this.cornerRadius = cornerRadius
        }
    }

    /** Blocking: decodes the picked image, scales it down to at most 1920px and stores it. */
    fun importImage(context: Context, uri: Uri): Boolean {
        return try {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val width = info.size.width
                val height = info.size.height
                val longSide = maxOf(width, height)
                if (longSide > MAX_SIDE) {
                    val scale = MAX_SIDE.toFloat() / longSide
                    decoder.setTargetSize(
                        (width * scale).toInt().coerceAtLeast(1),
                        (height * scale).toInt().coerceAtLeast(1)
                    )
                }
            }
            val tmp = File(context.filesDir, "$FILE_NAME.tmp")
            FileOutputStream(tmp).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            val target = imageFile(context)
            if (target.exists()) target.delete()
            tmp.renameTo(target)
        } catch (e: Exception) {
            false
        }
    }

    fun deleteImage(context: Context) {
        imageFile(context).delete()
    }
}
