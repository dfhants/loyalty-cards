package dev.loyaltycards.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.loyaltycards.data.CardStore
import dev.loyaltycards.ui.LoyaltyTheme
import dev.loyaltycards.ui.cardSurface

/**
 * Which cards each wallet widget shows: all of them (the default, which takes in new cards as
 * they're added), or a chosen few. Kept per widget, so two wallets can hold different cards.
 */
object StackSelection {
    private fun prefs(context: Context) = context.getSharedPreferences("wallet_widgets", Context.MODE_PRIVATE)
    private fun key(widgetId: Int) = "cards_$widgetId"

    /** The chosen card ids, or null for all cards. */
    fun get(context: Context, widgetId: Int): Set<String>? = prefs(context).getStringSet(key(widgetId), null)

    fun set(context: Context, widgetId: Int, ids: Set<String>?) {
        prefs(context).edit().apply { if (ids == null) remove(key(widgetId)) else putStringSet(key(widgetId), ids) }.apply()
    }

    fun forget(context: Context, widgetIds: IntArray) {
        prefs(context).edit().apply { widgetIds.forEach { remove(key(it)) } }.apply()
    }
}

/** Shown when a wallet widget is placed or reconfigured: choose which cards go in it. */
class StackConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val widgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        setResult(RESULT_CANCELED, result)
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }

        val store = CardStore.get(this)
        val saved = StackSelection.get(this, widgetId)
        setContent {
            LoyaltyTheme {
                val cards by store.cards.collectAsStateWithLifecycle()
                // Front of the wallet first, as you'd look through it.
                val ordered = walletOrder(cards).asReversed()
                var all by rememberSaveable { mutableStateOf(saved == null) }
                var chosen by rememberSaveable { mutableStateOf(saved?.toList() ?: emptyList()) }
                val canSave = all || chosen.any { id -> cards.any { it.id == id } }

                Scaffold(
                    topBar = { TopAppBar(title = { Text("Cards in this widget") }) },
                    bottomBar = {
                        Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)) {
                            Button(
                                onClick = {
                                    StackSelection.set(this@StackConfigActivity, widgetId, if (all) null else chosen.toSet())
                                    OverlapStackWidgetReceiver.refreshAll(this@StackConfigActivity)
                                    setResult(RESULT_OK, result)
                                    finish()
                                },
                                enabled = canSave,
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Done") }
                        }
                    },
                ) { padding ->
                    LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                        item {
                            ListItem(
                                headlineContent = { Text("All my cards") },
                                supportingContent = {
                                    Text(if (all) "New cards are added to the widget as you add them" else "Tick the cards to show in this widget")
                                },
                                trailingContent = { Switch(checked = all, onCheckedChange = null) },
                                modifier = Modifier.clickable { all = !all },
                            )
                            HorizontalDivider()
                        }
                        items(ordered, key = { it.id }) { card ->
                            val ticked = if (all) true else card.id in chosen
                            ListItem(
                                headlineContent = { Text(card.name) },
                                leadingContent = {
                                    Box(Modifier.size(width = 44.dp, height = 28.dp).cardSurface(Color(card.color), 0.dp, RoundedCornerShape(6.dp)))
                                },
                                trailingContent = { Checkbox(checked = ticked, onCheckedChange = null, enabled = !all) },
                                modifier = Modifier.clickable(enabled = !all) {
                                    chosen = if (card.id in chosen) chosen - card.id else chosen + card.id
                                },
                            )
                        }
                        if (cards.isEmpty()) item {
                            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                Text("Your cards will appear here once you add some in the app.")
                            }
                        }
                    }
                }
            }
        }
    }
}
