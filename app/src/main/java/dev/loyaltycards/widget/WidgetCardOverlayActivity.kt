package dev.loyaltycards.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.loyaltycards.data.CardStore
import dev.loyaltycards.data.LoyaltyCard
import dev.loyaltycards.ui.CARD_RATIO
import dev.loyaltycards.ui.CardBackground
import dev.loyaltycards.ui.CardBarcodePanel
import dev.loyaltycards.ui.LoyaltyTheme
import dev.loyaltycards.ui.WhileShowingCard
import dev.loyaltycards.ui.contentColorFor

/**
 * A card popped straight over the home screen from a widget, without opening the app: the same
 * floating card as the app's (name at the top, the barcode on a white panel), over a dimmed
 * home screen, at full screen brightness while it's open. Tap outside the card or press back
 * to put it away. It runs in a task of its own that's kept out of Recents.
 *
 * It doesn't animate itself: the home screen plays its own animation of opening from the
 * widget, the same as for any app opened from a widget, and the card holds still for it.
 */
class WidgetCardOverlayActivity : ComponentActivity() {
    private var cardId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        show(intent)
        setContent {
            LoyaltyTheme {
                val cards by CardStore.get(this).cards.collectAsState()
                val card = cards.firstOrNull { it.id == cardId }
                if (card == null) {
                    // The card was deleted since the widget last drew it.
                    LaunchedEffect(Unit) { close() }
                } else {
                    FloatingCard(card, onDismiss = ::close)
                }
            }
        }
    }

    /** Another card tapped while one is showing: show that one instead. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        show(intent)
    }

    /** Going home or switching away puts the card away, so it never lingers in the background. */
    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) close()
    }

    private fun show(intent: Intent) {
        val id = intent.getStringExtra(EXTRA_CARD_ID) ?: run { close(); return }
        cardId = id
        CardStore.get(this).markUsed(id)
    }

    private fun close() {
        if (!isFinishing) finishAndRemoveTask()
    }

    companion object {
        const val EXTRA_CARD_ID = "card_id"

        /** The intent a widget fires for [cardId]. Each card gets its own data URI, so its own PendingIntent. */
        fun intent(context: Context, cardId: String) =
            Intent(context, WidgetCardOverlayActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                putExtra(EXTRA_CARD_ID, cardId)
                data = Uri.parse("loyaltycards://overlay/$cardId")
            }
    }
}

/** The card's barcode side in the middle of the dimmed screen, as in the app. */
@Composable
private fun FloatingCard(card: LoyaltyCard, onDismiss: () -> Unit) {
    WhileShowingCard()
    // Putting it away: a quick fade, then close. (Android slides a closing window off the
    // bottom of the screen; by then there's nothing left in it to see.)
    val scope = rememberCoroutineScope()
    val shown = remember { Animatable(1f) }
    val window = (LocalContext.current as? android.app.Activity)?.window
    val dismiss: () -> Unit = {
        if (!shown.isRunning) scope.launch {
            // The card and the system's dimming fade out together, so neither just vanishes
            // whatever the home screen does when the window closes.
            shown.animateTo(0f, tween(200)) {
                window?.let { w -> w.attributes = w.attributes.apply { dimAmount = DIM * value } }
            }
            onDismiss()
        }
    }
    BackHandler(onBack = dismiss)
    val fg = contentColorFor(card.color)
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = shown.value }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = dismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.fillMaxWidth().safeDrawingPadding().padding(horizontal = 16.dp).widthIn(max = 520.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(CARD_RATIO)
                    // Taps on the card itself don't put it away.
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
            ) {
                CardBackground(card.color, elevation = 12.dp, modifier = Modifier.matchParentSize())
                Column(Modifier.matchParentSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(card.name, style = MaterialTheme.typography.labelLarge, color = fg.copy(alpha = 0.85f), maxLines = 1)
                    CardBarcodePanel(card.value, card.format, Modifier.fillMaxWidth().weight(1f))
                }
            }
            if (card.note.isNotBlank()) {
                Text(card.note, style = MaterialTheme.typography.bodyMedium, color = Color.White, textAlign = TextAlign.Center)
            }
        }
    }
}

/** How much the home screen is dimmed behind the card; matches backgroundDimAmount in the theme. */
private const val DIM = 0.62f
