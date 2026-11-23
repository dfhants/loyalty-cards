package dev.loyaltycards.ui

import android.app.Activity
import android.nfc.NfcAdapter
import android.view.WindowManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.collectAsState
import dev.loyaltycards.data.AppSettings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.loyaltycards.data.LoyaltyCard
import kotlinx.coroutines.launch

/**
 * A card lifted out of the wallet: it floats over a dimmed wallet with its barcode showing.
 * Tap outside it, swipe it down, or press back to drop it back in.
 */
@Composable
fun AnimatedVisibilityScope.CardOverlay(
    card: LoyaltyCard,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    WhileShowingCard()
    BackHandler(onBack = onDismiss)
    val fg = contentColorFor(card.color)
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val dismissPx = with(LocalDensity.current) { 140.dp.toPx() }
    var dragY by remember { mutableFloatStateOf(0f) }
    var confirmDelete by remember { mutableStateOf(false) }

    // Scrim follows the enter/exit animation, and lightens as the card is dragged away.
    val scrim by transition.animateFloat(
        transitionSpec = {
            // Dim only once the overlay's own copy of the card has taken over, so the
            // travelling copy underneath is never darkened.
            if (targetState == EnterExitState.Visible) tween(180, delayMillis = SCRIM_IN_DELAY_MS)
            else tween(SCRIM_OUT_MS)
        },
        label = "scrim",
    ) { if (it == EnterExitState.Visible) 0.62f else 0f }
    val trip by tripPosition(CardSide.Overlay)

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = 1f - (dragY / (dismissPx * 2.5f)).coerceIn(0f, 0.6f) }
            .background(Color.Black.copy(alpha = scrim))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
    ) {
        Column(
            Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .safeDrawingPadding()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Box(
                Modifier
                    .graphicsLayer {
                        translationY = dragY
                        val s = 1f - (dragY / (dismissPx * 8f)).coerceIn(0f, 0.08f)
                        scaleX = s; scaleY = s
                    }
                    .draggable(
                        orientation = Orientation.Vertical,
                        state = rememberDraggableState { dragY = (dragY + it).coerceAtLeast(0f) },
                        onDragStopped = { v ->
                            if (dragY > dismissPx || v > 2500f) onDismiss()
                            else scope.launch { animate(dragY, 0f, animationSpec = spring(0.7f)) { x, _ -> dragY = x } }
                        },
                    )
                    .fillMaxWidth()
                    .aspectRatio(CARD_RATIO),
            ) {
                // The card itself, the same shape as in the wallet, with its barcode printed on
                // the front. It turns over on its way here and back.
                FlipCard(
                    angle = { turnAngle(trip) },
                    modifier = Modifier
                        .matchParentSize()
                        .sharedCard(card.id, CardSide.Overlay, this@CardOverlay),
                    // The back, which faces you once the card is turned over: the barcode.
                    back = {
                        CardBackground(
                            card.color,
                            elevation = 12.dp,
                            modifier = Modifier
                                .matchParentSize()
                                // Swallow taps on the card so they don't dismiss it.
                                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
                        )
                        Column(Modifier.matchParentSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(card.name, style = MaterialTheme.typography.labelLarge, color = fg.copy(alpha = 0.85f), maxLines = 1)
                            CardBarcodePanel(card.value, card.format, Modifier.fillMaxWidth().weight(1f))
                        }
                    },
                ) {
                    // The front, as it looks in the wallet: just the name.
                    CardBackground(card.color, elevation = 12.dp, modifier = Modifier.matchParentSize())
                    CardName(card.name, fg, Modifier.padding(16.dp))
                }
            }
            if (card.note.isNotBlank()) {
                Text(
                    card.note,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.animateEnterExit(enter = fadeIn(tween(160, delayMillis = SLOT_MS - 40)), exit = fadeOut(tween(60))),
                )
            }

            Row(
                Modifier.animateEnterExit(
                    enter = fadeIn(tween(160, delayMillis = SLOT_MS - 40)) + slideInVertically(tween(200, delayMillis = SLOT_MS - 40)) { it },
                    exit = fadeOut(tween(60)) + slideOutVertically(tween(80)) { it },
                ),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                FilledTonalIconButton(onClick = { clipboard.setText(AnnotatedString(card.value)) }) {
                    Icon(Icons.Rounded.ContentCopy, "Copy number")
                }
                FilledTonalIconButton(onClick = onEdit) { Icon(Icons.Rounded.Edit, "Edit") }
                FilledTonalIconButton(onClick = { confirmDelete = true }) { Icon(Icons.Rounded.Delete, "Delete") }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${card.name}?") },
            text = { Text("The card is removed from this phone and any widgets showing it.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

/**
 * What the phone does while a barcode is showing, as chosen in Settings:
 * - full screen brightness, so till scanners can read it;
 * - the screen kept on;
 * - contactless payments paused. Apps can't switch NFC off, but while the app holds the NFC
 *   reader for itself (reader mode), the phone stops acting as a contactless card, so holding
 *   it to a scanner can't set off Google Wallet. That's only possible while the card is on
 *   screen, which is the only time it matters.
 */
@Composable
internal fun WhileShowingCard() {
    val activity = LocalContext.current as? Activity ?: return
    val settings by AppSettings.get(activity).settings.collectAsState()
    DisposableEffect(activity, settings.brighten, settings.keepScreenOn) {
        val window = activity.window
        val previous = window.attributes.screenBrightness
        if (settings.brighten) window.attributes = window.attributes.apply {
            screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
        }
        if (settings.keepScreenOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window.attributes = window.attributes.apply { screenBrightness = previous }
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
    if (settings.pauseNfc) PauseNfc(activity)
}

/** Holds the NFC reader while the activity is in front (reader mode only works then). */
@Composable
private fun PauseNfc(activity: Activity) {
    val nfc = remember(activity) { NfcAdapter.getDefaultAdapter(activity) } ?: return
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(nfc, lifecycle) {
        val flags = NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or
            NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V or
            NfcAdapter.FLAG_READER_NFC_BARCODE or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK or
            NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS
        fun hold() = runCatching { nfc.enableReaderMode(activity, { /* ignore anything it reads */ }, flags, null) }
        fun release() = runCatching { nfc.disableReaderMode(activity) }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> hold()
                Lifecycle.Event.ON_PAUSE -> release()
                else -> Unit
            }
        }
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) hold()
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            release()
        }
    }
}
