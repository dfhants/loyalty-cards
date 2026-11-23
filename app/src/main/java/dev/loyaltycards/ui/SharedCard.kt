package dev.loyaltycards.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/*
 * The card's trip, as one physical motion in 3D.
 *
 * Trip position runs from 0 (sitting in its pocket in the wallet) to 1 (held up in front of
 * you). The first part, up to SLIDE_PART, the card slides straight up out of its pocket, flat,
 * still behind the cards in front of it. The rest, it travels to the middle of the screen while
 * turning over bottom-away, while moving towards you so the bottom edge never gets further
 * away, and lands showing its back.
 *
 * Putting it back runs the very same path in reverse: it turns back the other way, arrives
 * above its pocket, and slides down into it.
 *
 * The card is a rigid card the whole way: same shape at both ends, spinning about its middle.
 */

/** Provided by the app so a card can fly between its place in the wallet and the overlay. */
@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedScope = compositionLocalOf<SharedTransitionScope?> { null }

/** Length of the card's trip, either way. */
const val SLOT_MS = 390

/** How long the dimming behind an open card takes to lift. */
const val SCRIM_OUT_MS = 80

/** The share of the trip spent sliding out of (or into) the pocket. */
const val SLIDE_PART = 0.3f

/** How far the card slides up out of its pocket before it turns. */
private val LIFT = 110.dp

/**
 * One smooth curve over the whole trip, symmetrical so going out and coming back feel the same:
 * it eases away from rest and eases into rest, with no stall where the slide meets the turn.
 */
/** The trip's curve, shared with anything that moves in step with the card. */
val SlotEasing get() = Ease

private val Ease = CubicBezierEasing(0.45f, 0f, 0.3f, 1f)

/** Time (0..1) at which [Ease] reaches [y]. */
private fun easeInverse(y: Float): Float {
    var lo = 0f; var hi = 1f
    repeat(30) { val mid = (lo + hi) / 2; if (Ease.transform(mid) < y) lo = mid else hi = mid }
    return lo
}

/*
 * The card is edge-on, and invisible, halfway through its turn. That's where it
 * changes hands between the wallet's copy (drawn inside the stack, so it can be behind the
 * cards in front) and the overlay's copy (drawn on top of everything).
 */
/**
 * The turn starts almost as soon as the card starts to slide out, and is complete by
 * TURN_END; the rest of the trip is the card settling into place.
 */
const val TURN_START = 0.04f
const val TURN_END = 0.8f

internal const val SWAP_TRIP = TURN_START + (TURN_END - TURN_START) * 0.5f
internal val OPEN_SWAP_MS = (easeInverse(SWAP_TRIP) * SLOT_MS).toInt()
private val CLOSE_SWAP_MS = (easeInverse(1f - SWAP_TRIP) * SLOT_MS).toInt()

/** When the dimming behind an opening card starts: once it's on top. */
val SCRIM_IN_DELAY_MS = OPEN_SWAP_MS

/** Which copy of a card this is. */
enum class CardSide { Wallet, Overlay }

private fun AnimatedVisibilityScope.opening(side: CardSide): Boolean {
    val arriving = transition.targetState == EnterExitState.Visible
    return if (side == CardSide.Overlay) arriving else !arriving
}

/** The card's box at trip position [trip], between its pocket and the overlay. */
private fun pathAt(trip: Float, pocket: Rect, overlay: Rect, liftPx: Float): Rect {
    val lifted = pocket.translate(0f, -liftPx)
    return if (trip <= SLIDE_PART) lerpRect(pocket, lifted, trip / SLIDE_PART)
    else lerpRect(lifted, overlay, (trip - SLIDE_PART) / (1f - SLIDE_PART))
}

private fun lerpRect(a: Rect, b: Rect, t: Float) = Rect(
    a.left + (b.left - a.left) * t,
    a.top + (b.top - a.top) * t,
    a.right + (b.right - a.right) * t,
    a.bottom + (b.bottom - a.bottom) * t,
)

/**
 * Marks this element as card [id]. Its on-screen box follows the trip: up out of the pocket,
 * then across to the overlay (or the reverse). The copies hand over while the card is edge-on.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedCard(id: String, side: CardSide, visibility: AnimatedVisibilityScope): Modifier {
    val shared = LocalSharedScope.current ?: return this
    val liftPx = with(LocalDensity.current) { LIFT.toPx() }
    val opening = visibility.opening(side)
    val (enterAt, exitAt) = when (side) {
        CardSide.Wallet -> CLOSE_SWAP_MS to OPEN_SWAP_MS
        CardSide.Overlay -> OPEN_SWAP_MS to CLOSE_SWAP_MS
    }
    return with(shared) {
        this@sharedCard.sharedBounds(
            sharedContentState = rememberSharedContentState("card-$id"),
            animatedVisibilityScope = visibility,
            enter = fadeIn(tween(1, delayMillis = enterAt)),
            exit = fadeOut(tween(1, delayMillis = exitAt)),
            // The box follows the same eased trip as the turn, sampled finely, so the slide and
            // the turn stay locked together.
            boundsTransform = { from, to ->
                val pocket = if (opening) from else to
                val overlay = if (opening) to else from
                keyframes {
                    durationMillis = SLOT_MS
                    val steps = 32
                    for (k in 0..steps) {
                        val t = k / steps.toFloat()
                        val trip = if (opening) Ease.transform(t) else 1f - Ease.transform(t)
                        pathAt(trip, pocket, overlay, liftPx) at (t * SLOT_MS).toInt()
                    }
                }
            },
            resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
            renderInOverlayDuringTransition = false,
        )
    }
}

/** Where this copy of the card is on its trip: 0 in the pocket, 1 in front of you. */
@Composable
fun AnimatedVisibilityScope.tripPosition(side: CardSide): State<Float> {
    fun at(state: EnterExitState): Float = when (side) {
        CardSide.Wallet -> if (state == EnterExitState.Visible) 0f else 1f
        CardSide.Overlay -> if (state == EnterExitState.Visible) 1f else 0f
    }
    return transition.animateFloat(
        transitionSpec = { tween(SLOT_MS, easing = Ease) },
        label = "trip",
    ) { at(it) }
}

/** How far out of its pocket the card has slid, 0..1. */
fun slideOut(trip: Float): Float = (trip / SLIDE_PART).coerceIn(0f, 1f)

/**
 * The card's turn at this point of the trip, in degrees: half a turn, bottom edge tipping back,
 * so it arrives showing its back, where the barcode is printed. (FlipCard moves the card towards
 * you as it turns so the bottom edge never actually gets further away.)
 */
fun turnAngle(trip: Float): Float =
    -180f * ((trip - TURN_START) / (TURN_END - TURN_START)).coerceIn(0f, 1f)

