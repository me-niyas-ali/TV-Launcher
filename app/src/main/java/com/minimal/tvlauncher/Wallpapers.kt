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

/** Custom wallpaper handling: a user image copied into app storage, or a built-in colour preset. */
object Wallpapers {

    class Preset(val name: String, val colors: IntArray)

    val presets: List<Preset> = listOf(
        Preset("Midnight", intArrayOf(Color.parseColor("#0B1020"), Color.parseColor("#1B2A4A"))),
        Preset("Slate", intArrayOf(Color.parseColor("#15181E"), Color.parseColor("#2C323D"))),
        Preset("Dusk", intArrayOf(Color.parseColor("#1A1030"), Color.parseColor("#4A2A5A"))),
        Preset("Forest", intArrayOf(Color.parseColor("#0A1A14"), Color.parseColor("#1F4033"))),
        Preset("Black", intArrayOf(Color.parseColor("#000000"), Color.parseColor("#000000")))
    )

    private const val FILE_NAME = "wallpaper.jpg"
    private const val MAX_SIDE = 1920

    fun imageFile(context: Context): File = File(context.filesDir, FILE_NAME)

    /** Blocking: call from a background thread. */
    fun load(context: Context, prefs: PrefsStore): Drawable {
        if (prefs.wallpaperMode == PrefsStore.MODE_IMAGE) {
            val file = imageFile(context)
            if (file.exists()) {
                BitmapFactory.decodeFile(file.absolutePath)?.let {
                    return BitmapDrawable(context.resources, it)
                }
            }
        }
        return presetDrawable(prefs.wallpaperPreset)
    }

    fun presetDrawable(index: Int): Drawable {
        val preset = presets[index.coerceIn(0, presets.lastIndex)]
        return GradientDrawable(GradientDrawable.Orientation.TL_BR, preset.colors)
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
