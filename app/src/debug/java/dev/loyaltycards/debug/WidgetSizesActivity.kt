package dev.loyaltycards.debug

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.SizeF
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.lifecycle.lifecycleScope
import dev.loyaltycards.data.CardStore
import dev.loyaltycards.widget.CARD_ID_KEY
import dev.loyaltycards.widget.OverlapStackWidgetReceiver
import dev.loyaltycards.widget.SingleCardWidget
import dev.loyaltycards.widget.SingleCardWidgetReceiver
import kotlinx.coroutines.launch

/**
 * Debug only. Hosts real copies of both widgets at each standard home-screen size, so every
 * size can be checked at once. Needs `adb shell appwidget grantbind --package dev.loyaltycards`.
 * Pass `--es widget stack` or `--es widget single`.
 */
class WidgetSizesActivity : androidx.activity.ComponentActivity() {
    private lateinit var host: AppWidgetHost

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.getStringExtra("widget") == "previews") { exportPreviews(); finish(); host = AppWidgetHost(this, 4242); return }
        host = AppWidgetHost(this, 4242)
        host.deleteHost()
        host.startListening()
        val manager = AppWidgetManager.getInstance(this)
        val which = intent.getStringExtra("widget") ?: "stack"
        val provider = ComponentName(this, if (which == "single") SingleCardWidgetReceiver::class.java else OverlapStackWidgetReceiver::class.java)
        val info = manager.installedProviders.first { it.provider == provider }
        val d = resources.displayMetrics.density
        // Pixel-style cells: about 80dp wide and 105dp tall, with 12dp between them.
        fun w(n: Int) = n * 80 + (n - 1) * 12
        fun h(n: Int) = n * 105 + (n - 1) * 12
        val sizes = (intent.getStringExtra("sizes") ?: "1x1,1x2,2x1,2x2,2x3,3x2,4x1,4x2,2x4,4x3")
            .split(",").map { it.split("x").let { (c, r) -> c.toInt() to r.toInt() } }

        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding((12 * d).toInt(), (40 * d).toInt(), 0, (40 * d).toInt()) }
        val ids = mutableListOf<Int>()
        for ((c, r) in sizes) {
            val id = host.allocateAppWidgetId()
            if (!manager.bindAppWidgetIdIfAllowed(id, provider)) error("Run: adb shell appwidget grantbind --package $packageName")
            ids += id
            column.addView(TextView(this).apply { text = "${c}×${r}  (${w(c)}×${h(r)} dp)"; setTextColor(Color.WHITE); setPadding(0, (14 * d).toInt(), 0, (6 * d).toInt()) })
            val view = host.createView(this, id, info)
            view.updateAppWidgetSize(Bundle(), listOf(SizeF(w(c).toFloat(), h(r).toFloat())))
            // "actual" lays the widget out at a different size from the one it's told, as home
            // screens can (e.g. "1.15x0.9" scales width and height).
            val (ax, ay) = (intent.getStringExtra("actual") ?: "1x1").split("x").map { it.toFloat() }
            column.addView(FrameLayout(this).apply { addView(view, FrameLayout.LayoutParams((w(c) * d * ax).toInt(), (h(r) * d * ay).toInt())) },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        setContentView(ScrollView(this).apply {
            background = if (intent.getStringExtra("bg") == "light") GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(0xFFF4F1EA.toInt(), 0xFFDCE3EC.toInt()))
                else GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(0xFF6B7F99.toInt(), 0xFF2A3342.toInt()))
            addView(column)
        })
        lifecycleScope.launch {
            if (which == "single") {
                val want = intent.getStringExtra("card") ?: "Tesco"
                val card = CardStore.get(this@WidgetSizesActivity).cards.value.firstOrNull { it.name.contains(want) }
                    ?: CardStore.get(this@WidgetSizesActivity).cards.value.first()
                val glance = GlanceAppWidgetManager(this@WidgetSizesActivity)
                for (id in ids) {
                    val g = glance.getGlanceIdBy(id)
                    updateAppWidgetState(this@WidgetSizesActivity, g) { it[CARD_ID_KEY] = card.id }
                    SingleCardWidget().update(this@WidgetSizesActivity, g)
                }
            } else {
                ids.forEach { dev.loyaltycards.widget.StackSelection.set(this@WidgetSizesActivity, it, null) }
                OverlapStackWidgetReceiver.refreshAll(this@WidgetSizesActivity)
                // "select": after a moment, narrow every stack to these cards, as the picker does.
                intent.getStringExtra("select")?.let { names ->
                    kotlinx.coroutines.delay(4000)
                    val cards = CardStore.get(this@WidgetSizesActivity).cards.value
                    val chosen = names.split(",").mapNotNull { n -> cards.firstOrNull { it.name == n }?.id }.toSet()
                    ids.forEach { dev.loyaltycards.widget.StackSelection.set(this@WidgetSizesActivity, it, chosen) }
                    OverlapStackWidgetReceiver.refreshAll(this@WidgetSizesActivity)
                }
            }
        }
    }

    /** Writes the widget picker's static pictures, from sample cards, to the cache folder. */
    private fun exportPreviews() {
        fun save(name: String, bmp: android.graphics.Bitmap) =
            java.io.File(cacheDir, name).outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        save("widget_preview_stack.png", dev.loyaltycards.widget.StackPreview.render(this, samplesOnly = true))
        save("widget_preview_single.png", dev.loyaltycards.widget.SinglePreview.render(this))
    }

    override fun onDestroy() {
        host.stopListening()
        host.deleteHost()
        super.onDestroy()
    }
}
