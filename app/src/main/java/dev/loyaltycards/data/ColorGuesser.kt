package dev.loyaltycards.data

import android.graphics.Bitmap
import androidx.palette.graphics.Palette

/**
 * The card's own colour, taken from a picture of it: the most common clearly-coloured
 * region. Whites, greys, blacks (the barcode panel, text, the table it's lying on) are
 * ignored, so a red card with a white barcode panel comes out red.
 */
object ColorGuesser {
    fun fromBitmap(bitmap: Bitmap?): Int? {
        if (bitmap == null) return null
        val palette = Palette.from(bitmap)
            .clearFilters()
            .addFilter { _, hsl -> hsl[1] >= 0.22f && hsl[2] in 0.12f..0.85f }
            .maximumColorCount(16)
            .generate()
        val swatches = palette.swatches
        val total = swatches.sumOf { it.population }.takeIf { it > 0 } ?: return null
        val best = swatches.maxByOrNull { it.population } ?: return null
        // A few stray coloured pixels on a plain white card isn't a card colour.
        if (best.population < total * 0.15 && swatches.size > 3) return null
        return best.rgb or 0xFF000000.toInt()
    }

    /** The middle of a camera frame, roughly where the on-screen guide frames the card. */
    fun cropToGuide(frame: Bitmap): Bitmap {
        val w = (frame.width * 0.82f).toInt()
        val h = (w / 1.4f).toInt().coerceAtMost(frame.height)
        return Bitmap.createBitmap(frame, (frame.width - w) / 2, (frame.height - h) / 2, w, h)
    }
}
