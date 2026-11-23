package dev.loyaltycards.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.zIndex
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Search
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults.animateIcon
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import kotlinx.coroutines.delay
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import dev.loyaltycards.data.LoyaltyCard

enum class AddMethod { Scan, Photo, Manual }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    cards: List<LoyaltyCard>,
    importing: Boolean,
    selectedId: String?,
    revealId: String?,
    onRevealed: () -> Unit,
    onOpen: (LoyaltyCard) -> Unit,
    onDismiss: () -> Unit,
    onEdit: (LoyaltyCard) -> Unit,
    onDelete: (LoyaltyCard) -> Unit,
    onAdd: (AddMethod) -> Unit,
    onSettings: () -> Unit,
) {
    var fabOpen by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    BackHandler(fabOpen) { fabOpen = false }

    // A fixed order, newest card at the front, so opening a card never reshuffles the wallet
    // and it always drops back into the slot it came from.
    val shown = remember(cards, query) {
        cards
            .filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
            .sortedWith(compareBy<LoyaltyCard> { it.createdAt }.thenBy { it.name.lowercase() })
    }
    val selected = cards.firstOrNull { it.id == selectedId }

    Box(Modifier.fillMaxSize()) {
    // No app bar: the "Cards" title scrolls with the wallet, so the list runs right up to the
    // status bar and a card sliding out of the top pocket isn't clipped by a header.
    Scaffold(
        floatingActionButton = {
            FloatingActionButtonMenu(
                expanded = fabOpen,
                button = {
                    ToggleFloatingActionButton(checked = fabOpen, onCheckedChange = { fabOpen = it }) {
                        val painter = rememberVectorPainter(if (checkedProgress > 0.5f) Icons.Rounded.Close else Icons.Rounded.Add)
                        Icon(painter, contentDescription = "Add card", modifier = Modifier.animateIcon({ checkedProgress }))
                    }
                },
            ) {
                FloatingActionButtonMenuItem(
                    onClick = { fabOpen = false; onAdd(AddMethod.Scan) },
                    icon = { Icon(Icons.Rounded.QrCodeScanner, null) },
                    text = { Text("Scan barcode") },
                )
                FloatingActionButtonMenuItem(
                    onClick = { fabOpen = false; onAdd(AddMethod.Photo) },
                    icon = { Icon(Icons.Rounded.Image, null) },
                    text = { Text("From photos") },
                )
                FloatingActionButtonMenuItem(
                    onClick = { fabOpen = false; onAdd(AddMethod.Manual) },
                    icon = { Icon(Icons.Rounded.Edit, null) },
                    text = { Text("Type it in") },
                )
            }
        },
    ) { padding ->
        if (importing) {
            LinearWavyProgressIndicator(Modifier.fillMaxWidth().padding(top = padding.calculateTopPadding()))
        }
        if (cards.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { EmptyState() }
            // Settings, so a backup can be restored before any cards are added.
            Box(Modifier.fillMaxSize().padding(padding).padding(8.dp), contentAlignment = Alignment.TopEnd) {
                IconButton(onClick = onSettings) { Icon(Icons.Rounded.Settings, "Settings") }
            }
            return@Scaffold
        }
        WalletStack(
            cards = shown,
            selectedId = selectedId,
            revealId = revealId,
            onRevealed = onRevealed,
            onOpen = onOpen,
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 112.dp,
            ),
            header = {
                // No title: the cards speak for themselves. Just search, once there are enough
                // cards to need it, and the way into Settings.
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (cards.size > 6) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            leadingIcon = { Icon(Icons.Rounded.Search, null) },
                            placeholder = { Text("Search cards") },
                            singleLine = true,
                            shape = MaterialTheme.shapes.extraLarge,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    IconButton(onClick = onSettings) { Icon(Icons.Rounded.Settings, "Settings") }
                }
            },
        )
    }

    // The chosen card, lifted out of the wallet over everything else.
    AnimatedVisibility(
        visible = selected != null,
        // The overlay's parts animate themselves; fading the whole layer would fade the card.
        enter = EnterTransition.None,
        exit = ExitTransition.None,
    ) {
        // Keep showing the last card while it animates away.
        val last = remember { mutableStateOf<LoyaltyCard?>(null) }
        if (selected != null) last.value = selected
        last.value?.let { card ->
            CardOverlay(card = card, onDismiss = onDismiss, onEdit = { onEdit(card) }, onDelete = { onDelete(card) })
        }
    }
    }
}

/**
 * Cards stacked like a wallet: each one overlaps the one behind it, showing just its top
 * strip, and the front card is shown whole.
 *
 * - Scrolling tucks cards away under the top edge: they pin, shrink a touch and fade as the
 *   next card slides over them.
 * - Pulling down at the top fans the cards apart on a rubber band, springing back on release.
 * - The open card leaves a gap where it was and flies back into it when dismissed.
 */
/** How small a card is at the very top of the screen, the furthest point of the wallet. */
private const val FAR_SCALE = 0.84f

/**
 * How near you a card is, from where it sits on screen: 0 at the top of the list, 1 at
 * NATURAL_SIZE_AT of the way down (where it's drawn at its natural size), and on past 1 below
 * that, so cards keep growing right to the bottom edge with no cap. Read during drawing, so it
 * follows scrolling frame by frame without recomposing.
 */
private fun nearness(listState: LazyListState, key: Any, peekPx: Int): Float {
    val info = listState.layoutInfo
    val item = info.visibleItemsInfo.firstOrNull { it.key == key } ?: return 1f
    val height = (info.viewportEndOffset - info.viewportStartOffset).coerceAtLeast(1)
    val y = item.offset - info.viewportStartOffset + peekPx / 2f
    return (y / (height * NATURAL_SIZE_AT)).coerceAtLeast(0f)
}

/** Where on screen (fraction of the way down) a card is drawn at its natural size. */
private const val NATURAL_SIZE_AT = 0.7f

/** A slight lean on the cards further back. */
private const val TILT_DEGREES = 3f

@Composable
private fun WalletStack(
    cards: List<LoyaltyCard>,
    selectedId: String?,
    revealId: String?,
    onRevealed: () -> Unit,
    onOpen: (LoyaltyCard) -> Unit,
    contentPadding: PaddingValues,
    header: @Composable () -> Unit,
) {
    // Open on the front of the wallet (the newest card, shown whole) from the very first frame;
    // or, when opened from a widget, already at that widget's card.
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = cards.indexOfFirst { it.id == revealId }
            .let { i -> if (i >= 0) (i + 1 - 3).coerceAtLeast(0) else cards.size },
    )
    // Opened from a widget: bring the card into view with a little of the wallet above it, let
    // the wallet settle for a moment, then take the card out exactly as a tap would.
    LaunchedEffect(revealId) {
        val id = revealId ?: return@LaunchedEffect
        val i = cards.indexOfFirst { it.id == id }
        if (i >= 0) {
            withFrameNanos { }
            listState.scrollToItem((i + 1 - 3).coerceAtLeast(0))   // +1: the header item
            withFrameNanos { }
            delay(120)
            onOpen(cards[i])
        }
        onRevealed()
    }
    val density = LocalDensity.current
    val peekPx = with(density) { 84.dp.roundToPx() }
    val maxStretch = with(density) { 260.dp.toPx() }
    var stretch by remember { mutableFloatStateOf(0f) }
    // While a card is out, the cards in front of it slide down out of its way, and come back
    // up as it slots in. Remember which card it was so they know to return.
    var lastSelected by remember { mutableStateOf<String?>(null) }
    if (selectedId != null) lastSelected = selectedId
    val selectedIndex = cards.indexOfFirst { it.id == lastSelected }
    // The neighbours make room in step with the card: same length, same curve.
    val aside by animateFloatAsState(
        if (selectedId != null) 1f else 0f,
        animationSpec = tween(SLOT_MS, easing = SlotEasing),
        label = "aside",
    )
    // Just enough room for the card to start turning: the cards in front dip (the nearest most),
    // the cards behind lift a touch. The card gets out by turning, not by shoving others away.
    val frontBudgePx = with(density) { 40.dp.toPx() }
    val behindBudgePx = with(density) { 14.dp.toPx() }

    val rubberBand = remember(listState) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // While fanned out, pushing back up closes the fan before the list scrolls.
                if (stretch > 0f && available.y < 0f) {
                    val next = (stretch + available.y).coerceAtLeast(0f)
                    val used = next - stretch
                    stretch = next
                    return Offset(0f, used)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y > 0f && source == NestedScrollSource.UserInput) {
                    // Resistance grows the further it's pulled.
                    val resistance = 1f - (stretch / maxStretch).coerceIn(0f, 1f)
                    stretch += available.y * 0.55f * resistance
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (stretch == 0f) return Velocity.Zero
                animate(stretch, 0f, initialVelocity = available.y * 0.3f, animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow)) { v, _ -> stretch = v }
                return available
            }
        }
    }

    LazyColumn(
        state = listState,
        contentPadding = contentPadding,
        modifier = Modifier.fillMaxSize().nestedScroll(rubberBand),
    ) {
        item(key = "header") { Column { header() } }
        itemsIndexed(cards, key = { _, c -> c.id }) { i, card ->
            val front = i == cards.lastIndex
            Box(
                Modifier
                    .animateItem()
                    .zIndex(i.toFloat())
                    // Report only the peeking strip to the list, so the next card lands on top.
                    .layout { m, c ->
                        val p = m.measure(c)
                        val h = if (front) p.height else minOf(peekPx, p.height)
                        layout(p.width, h) { p.place(0, 0) }
                    }
                    .graphicsLayer {
                        // Fan out: each card moves a little further than the one behind it.
                        translationY = stretch * (0.35f + 0.22f * i)
                        if (selectedIndex >= 0) {
                            val gap = i - selectedIndex
                            if (gap > 0) translationY += aside * frontBudgePx * (1f - 0.18f * (gap - 1)).coerceAtLeast(0.45f)
                            if (gap < 0) translationY -= aside * behindBudgePx / -gap
                        }
                        val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == card.id }
                        val gone = info?.offset?.let { -it.toFloat() } ?: 0f
                        if (gone > 0f) {
                            val t = (gone / peekPx).coerceIn(0f, 1f)
                            val eased = t * t
                            translationY += gone - peekPx * 0.18f * eased
                            scaleX = 1f - 0.07f * eased
                            scaleY = scaleX
                            alpha = 1f - eased
                        }
                    }
                    .fillMaxWidth()
                    .aspectRatio(1.586f),
            ) {
                AnimatedVisibility(
                    visible = card.id != selectedId,
                    enter = EnterTransition.None,
                    exit = ExitTransition.None,
                ) {
                    val trip by tripPosition(CardSide.Wallet)
                    CardFace(
                        name = card.name,
                        color = card.color,
                        subtitle = card.value.takeLast(4).let { "•••• $it" },
                        onClick = { onOpen(card) },
                        elevation = 12.dp,
                        subtitleAlpha = { 1f - slideOut(trip) },
                        flipAngle = { turnAngle(trip) },
                        modifier = Modifier.fillMaxWidth(),
                        faceModifier = Modifier
                            .sharedCard(card.id, CardSide.Wallet, this)
                            .graphicsLayer {
                                // At rest: further back = smaller and leaning slightly. As it
                                // slides out it comes up to full size and straightens.
                                val out = slideOut(trip)
                                // Perspective by where the card is on screen: near the top it's
                                // further away (smaller, leaning back a touch); it grows to full
                                // size as it comes down to the front. Scrolling moves cards
                                // through that, so they grow and shrink as they travel.
                                val near = nearness(listState, card.id, peekPx)
                                val rest = FAR_SCALE + (1f - FAR_SCALE) * near
                                val s = rest + (1f - rest) * out
                                scaleX = s; scaleY = s
                                transformOrigin = TransformOrigin(0.5f, 0f)
                                rotationX = TILT_DEGREES * (1f - near.coerceAtMost(1f)) * (1f - out)
                            },
                    )
                }
            }
        }
    }
}
