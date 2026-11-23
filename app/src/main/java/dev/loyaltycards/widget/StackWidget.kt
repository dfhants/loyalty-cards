package dev.loyaltycards.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.compose.ui.graphics.toArgb
import dev.loyaltycards.MainActivity
import dev.loyaltycards.R
import dev.loyaltycards.data.CardStore
import dev.loyaltycards.data.LoyaltyCard
import dev.loyaltycards.ui.CARD_RATIO
import dev.loyaltycards.ui.contentColorFor

/**
 * Where everything sits in the overlapping stack, in dp, worked out from the widget's size so it
 * holds together from 1×1 up. Every card fills the width; how much of each card peeks out, the
 * name's size and the corners all scale with the card. Each row is the visible tab of one card,
 * which starts [GAP] down the row (room for its shadow on the card behind). The front card is
 * shown whole, leaving a sliver of the card behind it showing so it still reads as a stack.
 */
class StackGeometry(val widthDp: Float, heightDp: Float, private val fontScale: Float = 1f) {
    val cardW = widthDp - MARGIN * 2
    val cardH = run {
        val room = heightDp - GAP - BOTTOM
        var h = minOf(cardW / CARD_RATIO, room - PEEK)
        // Better still, a whole tab of the card behind, if the front card can be a little
        // shorter for it and still look like a card (a wide, short widget).
        var withTab = room
        repeat(6) { withTab = room - tabFor(withTab) }
        if (h > withTab && cardW / withTab <= MAX_WIDENESS) h = withTab
        h.coerceAtLeast(24f)
    }
    val nameSize = nameSizeFor(cardH)                        // sp
    /** The name's height on screen, in dp: it grows with the phone's text size setting. */
    private val nameHeight = nameSize * fontScale * 1.2f
    /** How much of each card behind the front one shows: always enough for its name. */
    val tab = tabFor(cardH)
    val radius = minOf(16f, cardH * 0.22f)
    val nameStart = minOf(14f, cardW * 0.08f)
    /** The name sits centred in the part of a tab that shows (below the gap for the shadow). */
    val nameTop = ((tab - GAP - nameHeight) / 2f).coerceAtLeast(1f)

    private fun nameSizeFor(cardH: Float) = ((cardH * 0.42f).coerceIn(20f, 44f) * 0.32f).coerceIn(11f, 14f)
    private fun tabFor(cardH: Float) =
        maxOf((cardH * 0.42f).coerceIn(20f, 44f), nameSizeFor(cardH) * fontScale * 1.2f + GAP + 3f)
    /** Whether the front card has room for the last digits of its number under its name. */
    val showNumber = cardH >= 70f
    /** Short widgets get a shallower fade at the edges, so it never reaches the front card's name. */
    val short = heightDp < 200f

    fun rowHeight(front: Boolean) = if (front) GAP + cardH + BOTTOM else tab

    /** This row's own card. */
    val card get() = RectF(MARGIN, GAP, MARGIN + cardW, GAP + cardH)

    /** The card [k] places back from this row's own, which may still reach down into this row. */
    fun behind(k: Int) = RectF(MARGIN, GAP - tab * k, MARGIN + cardW, GAP - tab * k + cardH)

    /** How many cards behind a row's own can reach into it (their shadows included). */
    val reach get() = ((cardH + SHADOW * 2) / tab).toInt() + 1

    /**
     * Everything drawn in the row for card [index] of [colors] (back of the wallet first): the
     * cards behind that reach into it, then its own, as (rect, colour) back to front.
     */
    fun layers(colors: List<Int>, index: Int): List<Pair<RectF, Int>> =
        (reach downTo 1).mapNotNull { k -> colors.getOrNull(index - k)?.let { behind(k) to it } } + (card to colors[index])

    companion object {
        const val GAP = 5f
        const val MARGIN = 3f
        const val BOTTOM = 6f       // room under the front card for its shadow
        const val PEEK = 8f         // the least of the next card back that shows above the front one
        const val SHADOW = 5f
        /** How much wider than tall the front card may be stretched (a real card is 1.59). */
        const val MAX_WIDENESS = 2.4f


        fun of(context: Context, widgetId: Int): StackGeometry {
            val options = AppWidgetManager.getInstance(context).getAppWidgetOptions(widgetId)
            return StackGeometry(
                options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 172).toFloat(),
                options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 339).toFloat(),
                context.resources.configuration.fontScale,
            )
        }
    }
}

/**
 * 1. Overlapping stack: the app's wallet as a scrolling list of tabs, the newest card whole at
 * the bottom. Built on the home screen's own list rather than Glance so it can open scrolled to
 * the bottom, at the front card, as the app does.
 */
class OverlapStackWidgetReceiver : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { update(context, manager, it) }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        StackSelection.forget(context, appWidgetIds)
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) {
        update(context, manager, id)
        @Suppress("DEPRECATION") manager.notifyAppWidgetViewDataChanged(id, R.id.stack_list)
    }

    companion object {
        private fun update(context: Context, manager: AppWidgetManager, id: Int) {
            val layout = if (StackGeometry.of(context, id).short) R.layout.widget_stack_short else R.layout.widget_stack
            val views = RemoteViews(context.packageName, layout)
            val service = Intent(context, StackService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }
            @Suppress("DEPRECATION") views.setRemoteAdapter(R.id.stack_list, service)
            views.setEmptyView(R.id.stack_list, R.id.empty)
            // Each card fills in its own id (see the factory); this is the rest of the intent.
            val template = Intent(context, WidgetCardOverlayActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            views.setPendingIntentTemplate(
                R.id.stack_list,
                PendingIntent.getActivity(context, 2, template, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE),
            )
            // Nothing to show: either no cards yet (open the app to add one), or every card chosen
            // for this widget has since been deleted (choose again).
            val chosenGone = StackSelection.get(context, id) != null && CardStore.get(context).cards.value.isNotEmpty()
            val narrow = StackGeometry.of(context, id).widthDp < 120f
            views.setTextViewText(
                R.id.empty,
                when {
                    chosenGone -> if (narrow) "Choose cards" else "Tap to choose cards"
                    else -> if (narrow) "Add a card" else "Add a card to get started"
                },
            )
            val emptyTap = if (chosenGone) {
                Intent(context, StackConfigActivity::class.java)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                    .setData(Uri.parse("loyaltycards://wallet-widget/$id"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            } else Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            views.setOnClickPendingIntent(
                R.id.empty,
                PendingIntent.getActivity(context, 3, emptyTap, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE),
            )
            manager.updateAppWidget(id, views)
        }

        /** Redraws every stack from scratch (layout and cards). */
        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, OverlapStackWidgetReceiver::class.java))
            ids.forEach { update(context, manager, it) }
            @Suppress("DEPRECATION") if (ids.isNotEmpty()) manager.notifyAppWidgetViewDataChanged(ids, R.id.stack_list)
        }

        /** After the cards change: redraws every stack, including what an empty one says. */
        fun refresh(context: Context) = refreshAll(context)
    }
}

class StackService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        Factory(applicationContext, intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0))

    private class Factory(private val context: Context, private val widgetId: Int) : RemoteViewsFactory {
        private var cards: List<LoyaltyCard> = emptyList()
        private var geo = StackGeometry(172f, 339f)

        override fun onCreate() = Unit
        override fun onDestroy() = Unit
        override fun onDataSetChanged() {
            // Back of the wallet first; just the cards chosen for this widget, if it has a choice.
            val chosen = StackSelection.get(context, widgetId)
            cards = walletOrder(CardStore.get(context).cards.value).filter { chosen == null || it.id in chosen }
            geo = StackGeometry.of(context, widgetId)
        }

        override fun getCount() = cards.size
        override fun getViewTypeCount() = 1
        // Not stable: a row's picture depends on its neighbours (the card behind shows in its
        // corners, and only the front card is whole), so a card's row must be redrawn whenever
        // the cards around it change. With stable ids the home screen reuses rows by card and
        // shows stale pictures and numbers after the selection changes.
        override fun hasStableIds() = false
        override fun getItemId(position: Int) = position.toLong()
        override fun getLoadingView(): RemoteViews? = null

        override fun getViewAt(position: Int): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_stack_row)
            val card = cards.getOrNull(position) ?: return views
            val front = position == cards.lastIndex
            val density = context.resources.displayMetrics.density
            val art = density * CardArt.ART_SCALE
            fun RectF.px() = RectF(left * art, top * art, right * art, bottom * art)

            // Each row exactly its picture's height, so a long name or large text can never
            // stretch a row and leave a gap between cards.
            if (android.os.Build.VERSION.SDK_INT >= 31) {
                views.setViewLayoutHeight(R.id.row_root, geo.rowHeight(front), TypedValue.COMPLEX_UNIT_DIP)
                views.setViewLayoutHeight(R.id.row_art, geo.rowHeight(front), TypedValue.COMPLEX_UNIT_DIP)
            }
            views.setImageViewBitmap(
                R.id.row_art,
                CardArt.tab(
                    (geo.widthDp * art).toInt(), (geo.rowHeight(front) * art).toInt(),
                    geo.layers(cards.map { it.color }, position).map { (r, c) -> r.px() to c },
                    geo.radius * art, StackGeometry.SHADOW * art,
                ),
            )

            val fg = contentColorFor(card.color).toArgb()
            val pad = { dp: Float -> (dp * density).toInt() }
            val start = pad(StackGeometry.MARGIN + geo.nameStart)
            views.setTextViewText(R.id.row_name, card.name)
            views.setTextColor(R.id.row_name, fg)
            views.setTextViewTextSize(R.id.row_name, TypedValue.COMPLEX_UNIT_SP, geo.nameSize)
            views.setViewPadding(R.id.row_name, start, pad(StackGeometry.GAP + geo.nameTop), start, 0)
            // Rows are reused as the list scrolls, so set the number either way.
            val number = front && geo.showNumber
            views.setViewVisibility(R.id.row_number, if (number) View.VISIBLE else View.GONE)
            if (number) {
                views.setTextViewText(R.id.row_number, "•••• ${card.value.takeLast(4)}")
                views.setTextColor(R.id.row_number, (fg and 0x00FFFFFF) or (0xD9 shl 24))
                views.setTextViewTextSize(R.id.row_number, TypedValue.COMPLEX_UNIT_SP, 11f)
                views.setViewPadding(R.id.row_number, start, 0, 0, pad(StackGeometry.BOTTOM + geo.nameStart))
            }

            views.setOnClickFillInIntent(
                R.id.row_root,
                Intent().putExtra(WidgetCardOverlayActivity.EXTRA_CARD_ID, card.id)
                    .setData(Uri.parse("loyaltycards://overlay/${card.id}")),
            )
            return views
        }
    }
}

/**
 * The widget picker's picture of the stack, drawn the same way as the widget itself from your
 * own cards (or a few sample ones), at its default 2×3 size.
 */
object StackPreview {
    private val samples = listOf(
        "Co-op" to 0xFF00B1E7.toInt(), "Costa Club" to 0xFF6D1F37.toInt(), "myWaitrose" to 0xFF5C8F2E.toInt(),
        "Boots Advantage" to 0xFF1A1A5E.toInt(), "Nectar" to 0xFF7B2D8E.toInt(),
        "parkrun" to 0xFFE0692C.toInt(), "Superdrug" to 0xFFE5007E.toInt(), "Tesco Clubcard" to 0xFF00539F.toInt(),
    )

    fun render(context: Context, widthDp: Float = 172f, heightDp: Float = 339f, samplesOnly: Boolean = false): android.graphics.Bitmap {
        val density = context.resources.displayMetrics.density
        val geo = StackGeometry(widthDp - 16f, heightDp - 16f)   // less the home screen's padding
        val real = walletOrder(CardStore.get(context).cards.value).map { it.name to it.color }
        val cards = if (samplesOnly) samples else real.ifEmpty { samples }
        val numberText = if (samplesOnly) "8901" else walletOrder(CardStore.get(context).cards.value).lastOrNull()?.value?.takeLast(4) ?: "8901"
        val scale = density * 0.8f
        val w = ((widthDp - 16f) * scale).toInt()
        val h = ((heightDp - 16f) * scale).toInt()
        val bmp = androidx.core.graphics.createBitmap(w, h)
        val c = android.graphics.Canvas(bmp)
        fun RectF.px(top: Float) = RectF(left * scale, (this.top + top) * scale, right * scale, (bottom + top) * scale)
        val text = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        // Rows from the front card at the bottom upwards, as the list shows them.
        var bottom = h / scale
        for (i in cards.indices.reversed()) {
            val front = i == cards.lastIndex
            val rowTop = bottom - geo.rowHeight(front)
            val (name, color) = cards[i]
            c.save()
            c.clipRect(0f, rowTop * scale, w.toFloat(), bottom * scale)
            for ((r, col) in geo.layers(cards.map { it.second }, i)) {
                CardArt.drawCard(c, r.px(rowTop), col, geo.radius * scale, StackGeometry.SHADOW * scale)
            }
            val fg = contentColorFor(color).toArgb()
            text.color = fg
            text.textSize = geo.nameSize * scale
            val x = (StackGeometry.MARGIN + geo.nameStart) * scale
            val baseline = (rowTop + StackGeometry.GAP + geo.nameTop) * scale - text.ascent()
            c.drawText(android.text.TextUtils.ellipsize(name, android.text.TextPaint(text), w - 2 * x, android.text.TextUtils.TruncateAt.END).toString(), x, baseline, text)
            if (front && geo.showNumber) {
                text.typeface = android.graphics.Typeface.DEFAULT
                text.textSize = 11f * scale
                text.color = (fg and 0x00FFFFFF) or (0xD9 shl 24)
                c.drawText("•••• $numberText", x, (bottom - StackGeometry.BOTTOM - geo.nameStart) * scale - text.descent(), text)
                text.typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
            c.restore()
            bottom = rowTop
            if (bottom <= 0f) break
        }
        return bmp
    }

    /** Gives the widget picker (Android 15+) a picture of the stack made from your own cards. */
    fun publish(context: Context) {
        if (android.os.Build.VERSION.SDK_INT < 35) return
        val views = RemoteViews(context.packageName, R.layout.widget_stack_generated_preview)
        views.setImageViewBitmap(R.id.preview_image, render(context))
        runCatching {
            AppWidgetManager.getInstance(context).setWidgetPreview(
                ComponentName(context, OverlapStackWidgetReceiver::class.java),
                android.appwidget.AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN,
                views,
            )
        }
    }
}
