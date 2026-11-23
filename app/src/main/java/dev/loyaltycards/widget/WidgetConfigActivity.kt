package dev.loyaltycards.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import dev.loyaltycards.data.CardStore
import dev.loyaltycards.ui.CardFace
import dev.loyaltycards.ui.LoyaltyTheme
import kotlinx.coroutines.launch

/** Shown when a "Loyalty card" widget is placed or reconfigured: pick which card it shows. */
class WidgetConfigActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val appWidgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        val resultIntent = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        setResult(RESULT_CANCELED, resultIntent)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }

        val store = CardStore.get(this)
        setContent {
            LoyaltyTheme {
                val cards by store.cards.collectAsStateWithLifecycle()
                Scaffold(topBar = { TopAppBar(title = { Text("Choose a card") }) }) { padding ->
                    if (cards.isEmpty()) {
                        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                            Text("Add a card in the app first.")
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(150.dp),
                            contentPadding = PaddingValues(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(padding),
                        ) {
                            items(cards, key = { it.id }) { card ->
                                CardFace(card.name, card.color, onClick = {
                                    lifecycleScope.launch {
                                        val glanceId = GlanceAppWidgetManager(this@WidgetConfigActivity).getGlanceIdBy(appWidgetId)
                                        updateAppWidgetState(this@WidgetConfigActivity, glanceId) { it[CARD_ID_KEY] = card.id }
                                        SingleCardWidget().update(this@WidgetConfigActivity, glanceId)
                                        setResult(RESULT_OK, resultIntent)
                                        finish()
                                    }
                                })
                            }
                        }
                    }
                }
            }
        }
    }
}
