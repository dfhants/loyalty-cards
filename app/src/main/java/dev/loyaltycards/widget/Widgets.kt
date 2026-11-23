package dev.loyaltycards.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import dev.loyaltycards.MainActivity
import dev.loyaltycards.data.BarcodeRenderer
import dev.loyaltycards.data.CardStore
import dev.loyaltycards.data.CodeFormat
import dev.loyaltycards.data.LoyaltyCard
import dev.loyaltycards.ui.CARD_RATIO
import dev.loyaltycards.ui.contentColorFor

val CARD_ID_KEY = stringPreferencesKey("card_id")

/**
 * What tapping a card in any widget does: pops the card, barcode up, straight over the home
 * screen ([WidgetCardOverlayActivity]) rather than opening the app. With no card (an empty
 * widget, "+N more") it opens the app itself.
 */
internal fun openCard(context: Context, id: String?) = actionStartActivity(
    if (id != null) WidgetCardOverlayActivity.intent(context, id)
    else Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        data = android.net.Uri.parse("loyaltycards://card/home")
    }
)

/** The app's wallet order: newest card at the front. */
internal fun walletOrder(cards: List<LoyaltyCard>) =
    cards.sortedWith(compareBy<LoyaltyCard> { it.createdAt }.thenBy { it.name.lowercase() })

/** Shown in the widget picker's preview when there's no real card to show yet. */
internal fun sampleCard(context: Context): LoyaltyCard =
    walletOrder(CardStore.get(context).cards.value).lastOrNull()
        ?: LoyaltyCard(id = "", name = "Your card", value = "1234567890128", format = CodeFormat.CODE_128, color = 0xFF2E5BFF.toInt())

/** One chosen card, showing its barcode side. The card is picked in [WidgetConfigActivity]. */
class SingleCardWidget : GlanceAppWidget() {
    override val stateDefinition = PreferencesGlanceStateDefinition
    // Exact: lay out for the widget's real size, so the barcode shows whenever there's room.
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val store = CardStore.get(context)
        provideContent {
            val cards by store.cards.collectAsState()
            val cardId = currentState<Preferences>()[CARD_ID_KEY]
            GlanceTheme { BarcodeSide(cards.firstOrNull { it.id == cardId }, emptyText = "Tap to choose a card") }
        }
    }

    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        val card = sampleCard(context)
        provideContent { GlanceTheme { BarcodeSide(card, emptyText = "") } }
    }
}

internal val RADIUS = 20.dp

/** Px for [dp] at the art's reduced scale. */
internal fun artPx(dp: Dp, density: Float) = (dp.value * density * CardArt.ART_SCALE).toInt().coerceAtLeast(1)

/**
 * The card's barcode side, as the card shows once it's turned over in the app: its name small
 * at the top and the barcode on a white panel, on the card's own gradient face.
 */
@Composable
private fun BarcodeSide(card: LoyaltyCard?, emptyText: String) {
    val context = LocalContext.current
    val size = LocalSize.current
    if (card == null) {
        Box(
            GlanceModifier.fillMaxSize().cornerRadius(RADIUS)
                .background(GlanceTheme.colors.primaryContainer)
                .clickable(openCard(context, null))
                .padding(12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(emptyText, style = TextStyle(color = GlanceTheme.colors.onPrimaryContainer))
        }
        return
    }
    val density = context.resources.displayMetrics.density
    val fg = contentColorFor(card.color)
    val face = remember(card.color, size) {
        CardArt.face(artPx(size.width, density), artPx(size.height, density), card.color, artPx(RADIUS, density).toFloat())
    }
    val layout = remember(card.value, card.format, size) { BarcodeLayout(card.value, card.format, size.width, size.height) }
    val barcode = remember(card.value, card.format, size) { layout.render(density) }

    Box(GlanceModifier.fillMaxSize().clickable(openCard(context, card.id))) {
        Image(ImageProvider(face), contentDescription = null, contentScale = ContentScale.FillBounds, modifier = GlanceModifier.fillMaxSize())
        Column(GlanceModifier.fillMaxSize().padding(layout.pad)) {
            if (layout.showName) Text(
                card.name,
                maxLines = 1,
                style = TextStyle(color = ColorProvider(fg.copy(alpha = 0.9f)), fontWeight = FontWeight.Medium, fontSize = layout.nameSize),
                modifier = GlanceModifier.padding(start = layout.nameIndent, bottom = layout.nameGap),
            )
            if (barcode != null) {
                Column(
                    GlanceModifier.fillMaxWidth().defaultWeight().cornerRadius(layout.panelRadius).background(Color.White)
                        .padding(horizontal = layout.panelPadH, vertical = layout.panelPadV),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Image(
                        ImageProvider(barcode),
                        contentDescription = "Barcode for ${card.name}",
                        // A barcode reads the same stretched either way, so it fills its panel
                        // whatever shape the home screen actually gives the widget. Square codes
                        // must stay square.
                        contentScale = if (card.format.is2D) ContentScale.Fit else ContentScale.FillBounds,
                        modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                    )
                    if (layout.showNumber) {
                        Text(card.value, maxLines = 1, style = TextStyle(color = ColorProvider(Color(0xFF1B1B1F)), fontSize = 11.sp))
                    }
                }
            } else {
                Text(card.value, maxLines = 1, style = TextStyle(color = ColorProvider(fg), fontSize = 12.sp), modifier = GlanceModifier.padding(start = layout.nameIndent))
            }
        }
    }
}

/**
 * How the one-card widget lays out its barcode for a widget of [w]×[h], from 1×1 up:
 * - small widgets get tighter padding and a smaller name, so the barcode keeps the room;
 * - a barcode in a tall, narrow space is turned on its side to run its length (tills read
 *   barcodes either way round), so a 1×2 widget gets a long, readable barcode;
 * - QR and other square codes simply fill the largest square there is;
 * - the number shows under the barcode only where it fits in full.
 */
internal class BarcodeLayout(private val value: String, private val format: CodeFormat, w: Dp, h: Dp) {
    private val compact = w < 120.dp || h < 120.dp
    private val tiny = w < 100.dp
    val pad = if (tiny) 5.dp else if (compact) 8.dp else 12.dp
    val nameSize = if (compact) 12.sp else 13.sp
    val nameIndent = if (compact) 2.dp else 4.dp
    val nameGap = if (compact) 5.dp else 8.dp
    val panelRadius = if (tiny) 8.dp else if (compact) 10.dp else 14.dp
    val panelPadH = if (tiny) 3.dp else if (compact) 6.dp else 10.dp
    val panelPadV = if (tiny) 3.dp else if (compact) 6.dp else 8.dp
    /** At 1×1 the whole face goes to the code; the card's colour round it says which card it is. */
    val showName = !(tiny && h < 120.dp)
    private val nameBlock = if (showName) (if (compact) 15.dp else 17.dp) + nameGap else 0.dp

    /** Room for the code itself inside the white panel. */
    private val innerW = w - pad * 2 - panelPadH * 2
    private val innerH0 = h - pad * 2 - nameBlock - panelPadV * 2

    /** The number fits in full: about 5.8dp a digit at 11sp, plus a line. */
    val showNumber = innerW >= (value.length * 5.8f).dp && innerH0 >= 70.dp
    private val innerH = innerH0 - if (showNumber) 16.dp else 0.dp

    /** Long side of the bars running down a tall, narrow panel. */
    private val sideways = !format.is2D && innerH > innerW * (if (showName) 1.3f else 1.1f)

    /** The panel's own padding is the code's clear space at small sizes, so it needs little of its own. */
    private val margin = if (compact) 2 else null

    /** The code drawn to fill its room, or null if there's no room for one. */
    fun render(density: Float): android.graphics.Bitmap? {
        if (innerW < 24.dp || innerH < 20.dp) return null
        val wPx = (innerW.value * density).toInt()
        val hPx = (innerH.value * density).toInt()
        return when {
            format.is2D -> minOf(wPx, hPx).let { BarcodeRenderer.render(value, format, it, it, if (compact) 1 else null) }
            // On its side the code fills the panel exactly, so nothing is left over round it.
            sideways -> BarcodeRenderer.render(value, format, hPx, wPx, margin, snug = true)?.let { b ->
                val turn = android.graphics.Matrix().apply { postRotate(90f) }
                android.graphics.Bitmap.createBitmap(b, 0, 0, b.width, b.height, turn, false)
            }
            // Small widgets: fill the panel. Larger ones keep card-like proportions.
            else -> BarcodeRenderer.render(value, format, wPx, if (compact) hPx else minOf(hPx, (wPx * 0.6f).toInt()), margin, snug = true)
        }
    }
}

class SingleCardWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SingleCardWidget()
}

/** A still picture of the one-card widget (Tesco sample), drawn like [BarcodeSide], for the widget picker. */
object SinglePreview {
    fun render(context: Context, widthDp: Float = 156f, heightDp: Float = 206f): android.graphics.Bitmap {
        val scale = context.resources.displayMetrics.density * 0.8f
        val w = (widthDp * scale).toInt(); val h = (heightDp * scale).toInt()
        val color = 0xFF00539F.toInt()
        val bmp = CardArt.face(w, h, color, RADIUS.value * scale)
        val c = android.graphics.Canvas(bmp)
        val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        val pad = 12f * scale
        p.color = android.graphics.Color.argb(230, 255, 255, 255)
        p.textSize = 13f * scale
        p.typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        c.drawText("Tesco Clubcard", pad + 4 * scale, pad - p.ascent(), p)
        val top = pad + 13f * scale + 8f * scale + 4f * scale
        val panel = android.graphics.RectF(pad, top, w - pad, h - pad)
        p.color = android.graphics.Color.WHITE
        c.drawRoundRect(panel, 14f * scale, 14f * scale, p)
        val value = "634004012345678901"
        val bw = (panel.width() - 20f * scale).toInt()
        val bh = minOf((panel.height() - 34f * scale).toInt(), (bw * 0.45f).toInt())
        BarcodeRenderer.render(value, CodeFormat.CODE_128, bw, bh)?.let {
            val left = panel.centerX() - bw / 2f
            val btop = panel.top + (panel.height() - 18f * scale - bh) / 2f
            c.drawBitmap(it, null, android.graphics.RectF(left, btop, left + bw, btop + bh), null)
        }
        p.color = 0xFF1B1B1F.toInt(); p.textSize = 11f * scale; p.typeface = android.graphics.Typeface.DEFAULT
        p.textAlign = android.graphics.Paint.Align.CENTER
        c.drawText(value, panel.centerX(), panel.bottom - 8f * scale - p.descent(), p)
        return bmp
    }
}
