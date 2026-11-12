package kr.drivelog.app

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Typeface

/** Reuse fonts and decoded photographs when rebuilding a screen. */
internal class DesignAssets(private val assets: AssetManager) {
    val regular: Typeface = Typeface.createFromAsset(assets, "fonts/regular.ttf")
    val bold: Typeface = Typeface.createFromAsset(assets, "fonts/bold.ttf")
    val heavy: Typeface = Typeface.createFromAsset(assets, "fonts/heavy.ttf")
    val numbers: Typeface = Typeface.createFromAsset(assets, "fonts/numbers.ttf")
    private val images = mutableMapOf<String, Bitmap>()

    fun image(name: String): Bitmap =
        images.getOrPut(name) {
            assets.open("images/$name").use { stream ->
                requireNotNull(BitmapFactory.decodeStream(stream)) {
                    "Invalid bundled image: $name"
                }
            }
        }
}
