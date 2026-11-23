package dev.loyaltycards.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.createBitmap

/**
 * Draws card faces as images for widgets, which can't use the app's drawing effects. The look
 * matches the app's cardSurface: a diagonal gradient (lighter top-left, deeper bottom-right), a
 * faint sheen across the top, and a thin rim that's light along the top edge.
 *
 * Bitmaps are drawn at a reduced scale ([ART_SCALE]) and stretched to fit: they're smooth
 * gradients, so that costs nothing visible and keeps widgets well inside the system's
 * memory budget for widget images.
 */
object CardArt {
    const val ART_SCALE = 0.6f

    /**
     * A card face [w]×[h] px with rounded corners of [radius] px.
     * [roundBottom] false squares off the bottom (a strip whose card continues below).
     * [behind], if set, is the colour of the card behind this one, shown in the rounded top
     * corners and inset by [behindInset] px each side (cards further back are narrower).
     * [shadowBelow] darkens the bottom edge, where the next card overlaps and casts a shadow.
     */
    fun face(
        w: Int,
        h: Int,
        color: Int,
        radius: Float,
        roundBottom: Boolean = true,
        behind: Int? = null,
        behindInset: Float = 0f,
        shadowBelow: Boolean = false,
        cardHeight: Float = h.toFloat(),
    ): Bitmap {
        val bmp = createBitmap(w.coerceAtLeast(1), h.coerceAtLeast(1))
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        if (behind != null) {
            // The card behind, seen through this card's top corners.
            p.shader = gradient(behind, behindInset, 0f, w - behindInset, cardHeight)
            c.drawRect(behindInset, 0f, w - behindInset, radius * 1.2f, p)
            p.shader = null
        }

        val rect = RectF(0f, 0f, w.toFloat(), h.toFloat())
        val bottomR = if (roundBottom) radius else 0f
        val outline = Path().apply {
            addRoundRect(rect, floatArrayOf(radius, radius, radius, radius, bottomR, bottomR, bottomR, bottomR), Path.Direction.CW)
        }
        c.save()
        c.clipPath(outline)
        // Body: diagonal gradient across the whole card (not just this strip of it).
        p.shader = gradient(color, 0f, 0f, w.toFloat(), cardHeight)
        c.drawRect(rect, p)
        // Sheen over the top 40% of the card.
        p.shader = LinearGradient(0f, 0f, 0f, cardHeight * 0.4f, 0x1FFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
        c.drawRect(rect, p)
        // Shadow from the next card, which overlaps this one's lower edge.
        if (shadowBelow) {
            val band = h * 0.22f
            p.shader = LinearGradient(0f, h - band, 0f, h.toFloat(), 0x00000000, 0x40000000, Shader.TileMode.CLAMP)
            c.drawRect(0f, h - band, w.toFloat(), h.toFloat(), p)
        }
        c.restore()
        // Rim: light along the top, fading down.
        p.shader = LinearGradient(0f, 0f, 0f, cardHeight, intArrayOf(0x4DFFFFFF, 0x0FFFFFFF, 0x1A000000), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.5f
        val inset = RectF(rect).apply { inset(0.75f, 0.75f) }
        c.drawPath(Path().apply {
            addRoundRect(inset, floatArrayOf(radius, radius, radius, radius, bottomR, bottomR, bottomR, bottomR), Path.Direction.CW)
        }, p)
        return bmp
    }

    /**
     * A whole stack of cards in one image, drawn back to front as full cards, so each card's
     * soft drop shadow falls naturally onto the card behind it, round its corners too.
     * [slots] are each card's full rectangle in px; later cards are drawn over earlier ones.
     */
    fun stack(w: Int, h: Int, colors: List<Int>, slots: List<RectF>, radius: Float, shadow: Float): Bitmap {
        val bmp = createBitmap(w.coerceAtLeast(1), h.coerceAtLeast(1))
        val c = Canvas(bmp)
        colors.forEachIndexed { i, color -> card(c, slots[i], color, radius, shadow) }
        return bmp
    }

    /**
     * Just the card's shading (gradient and top sheen) as a tiny square-cornered image, for the
     * widget to stretch and clip to rounded corners itself. Smooth gradients stretch without
     * visible loss, and at a few KB each a widget can hold one for every card.
     */
    fun shading(color: Int): Bitmap {
        val w = 64; val h = 40
        val bmp = createBitmap(w, h)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = gradient(color, 0f, 0f, w.toFloat(), h.toFloat())
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        p.shader = LinearGradient(0f, 0f, 0f, h * 0.4f, 0x1FFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        return bmp
    }

    /**
     * One row of a scrolling stack of tabs, [w]×[h] px. A list's rows can't overlap, so each row
     * draws the overlap itself: every card that reaches into the row ([cards], back to front,
     * the row's own card last), each whole with its soft shadow, clipped to the row. So cards and
     * their shadows carry on seamlessly from row to row, down the sides as well.
     */
    fun tab(w: Int, h: Int, cards: List<Pair<RectF, Int>>, radius: Float, shadow: Float): Bitmap {
        val bmp = createBitmap(w.coerceAtLeast(1), h.coerceAtLeast(1))
        val c = Canvas(bmp)
        cards.forEach { (r, color) -> card(c, r, color, radius, shadow) }
        return bmp
    }

    fun drawCard(c: Canvas, r: RectF, color: Int, radius: Float, shadow: Float) = card(c, r, color, radius, shadow)

    private fun card(c: Canvas, r: RectF, color: Int, radius: Float, shadow: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        // Soft shadow, slightly below; software rendering draws setShadowLayer on bitmaps.
        if (shadow > 0f) {
            p.color = color
            p.setShadowLayer(shadow, 0f, shadow * 0.35f, 0x55000000)
            c.drawRoundRect(r, radius, radius, p)
            p.clearShadowLayer()
        }
        p.shader = gradient(color, r.left, r.top, r.right, r.bottom)
        c.drawRoundRect(r, radius, radius, p)
        p.shader = LinearGradient(0f, r.top, 0f, r.top + r.height() * 0.4f, 0x1FFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
        c.drawRoundRect(r, radius, radius, p)
        p.shader = LinearGradient(0f, r.top, 0f, r.bottom, intArrayOf(0x4DFFFFFF, 0x0FFFFFFF, 0x1A000000), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.5f
        c.drawRoundRect(RectF(r).apply { inset(0.75f, 0.75f) }, radius, radius, p)
    }

    private fun gradient(color: Int, x0: Float, y0: Float, x1: Float, y1: Float) = LinearGradient(
        x0, y0, x1, y1,
        intArrayOf(ColorUtils.blendARGB(color, 0xFFFFFFFF.toInt(), 0.10f), color, ColorUtils.blendARGB(color, 0xFF000000.toInt(), 0.22f)),
        floatArrayOf(0f, 0.45f, 1f),
        Shader.TileMode.CLAMP,
    )
}
