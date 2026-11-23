package dev.loyaltycards.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.draw.dropShadow
import kotlin.math.abs
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.draw.shadow
import dev.loyaltycards.data.BarcodeRenderer
import dev.loyaltycards.data.CodeFormat

val CardShape = RoundedCornerShape(28.dp)

/** A real payment card's proportions (85.6 × 54 mm). */
const val CARD_RATIO = 1.586f

/** The coloured card face used on the home grid, in previews and on the detail screen. */
@Composable
fun CardFace(
    name: String,
    color: Int,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    elevation: Dp = 0.dp,
    /** Opacity of the subtitle, read at draw time so it can follow an animation. */
    subtitleAlpha: () -> Float = { 1f },
    /** The card's spin in degrees (0 = face up); past a quarter turn you see its back. */
    flipAngle: () -> Float = { 0f },
    /** Applied to the face itself (background and text together), e.g. to make it travel. */
    faceModifier: Modifier = Modifier,
) {
    val fg = contentColorFor(color)
    Box(modifier.aspectRatio(CARD_RATIO)) {
        FlipCard(
            angle = flipAngle,
            modifier = Modifier.matchParentSize().then(faceModifier),
            back = { CardBack(name, color, elevation) },
        ) {
            CardBackground(
                color,
                elevation,
                Modifier
                    .matchParentSize()
                    // No ripple: it would travel with the card when it lifts out.
                    .then(
                        if (onClick != null) Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onClick,
                        ) else Modifier
                    ),
            )
            Box(Modifier.matchParentSize().padding(16.dp)) {
                CardName(name, fg, Modifier.align(Alignment.TopStart))
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = fg.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.align(Alignment.BottomStart).graphicsLayer { alpha = subtitleAlpha() },
                    )
                }
            }
        }
    }
}

/**
 * A card that can spin over its horizontal middle. While its front faces away, the back is
 * shown instead, turned so it reads the right way round, as with a real card.
 */
@Composable
fun FlipCard(
    angle: () -> Float,
    modifier: Modifier = Modifier,
    back: @Composable BoxScope.() -> Unit,
    front: @Composable BoxScope.() -> Unit,
) {
    fun frontShowing(a: Float): Boolean {
        val n = ((a % 360f) + 360f) % 360f
        return n < 90f || n > 270f
    }
    Box(
        modifier.graphicsLayer {
            val a = angle()
            rotationX = a
            // A distant camera keeps the perspective gentle, so the top edge doesn't loom at you
            // as it swings over.
            cameraDistance = 60f * density
            // The card turns about its bottom edge, which leads the way and never moves backwards;
            // the rest of the card swings over around it. Turned all the way it hangs below that
            // edge, so the edge travels from the card's bottom to its top as it turns, landing it
            // exactly where it should be.
            transformOrigin = TransformOrigin(0.5f, 1f)
            translationY = -size.height * (abs(a) / 180f)
        },
    ) {
        Box(Modifier.matchParentSize().graphicsLayer { alpha = if (frontShowing(angle())) 1f else 0f }, content = front)
        Box(
            Modifier.matchParentSize().graphicsLayer {
                alpha = if (frontShowing(angle())) 0f else 1f
                rotationX = 180f
            },
            content = back,
        )
    }
}

/** The back of a card: same colour, darker, with a magnetic stripe and a signature strip. */
@Composable
fun CardBack(name: String, color: Int, elevation: Dp) {
    val base = Color(color)
    val fg = contentColorFor(color)
    Box(Modifier.fillMaxSize().cardSurface(lerp(base, Color.Black, 0.2f), elevation)) {
        Box(Modifier.padding(top = 20.dp).fillMaxWidth().height(36.dp).background(Color(0xFF151515)))
        Box(
            Modifier
                .padding(start = 16.dp, top = 72.dp)
                .fillMaxWidth(0.6f)
                .height(26.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFFF1EEE6)),
        )
        Text(
            name,
            style = MaterialTheme.typography.labelMedium,
            color = fg.copy(alpha = 0.7f),
            maxLines = 1,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }
}

/** The card's name as printed on its face; identical in the wallet and the overlay. */
@Composable
fun CardName(name: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        name.ifBlank { "New card" },
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = color,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/** The card's coloured, rounded face with its shadow. Same everywhere so it can fly between screens. */
@Composable
fun CardBackground(color: Int, elevation: Dp, modifier: Modifier = Modifier, shape: Shape = CardShape) {
    Box(modifier.cardSurface(Color(color), elevation, shape))
}

/**
 * Makes a box look like a physical card:
 * - a soft drop shadow all the way round (not the system elevation shadow, which is lit from
 *   above and stops in a hard line at the card's top corners), with a tighter contact shadow
 *   tinted with the card's own colour;
 * - a gentle diagonal gradient, lighter top-left to deeper bottom-right;
 * - a faint sheen across the top, as if catching the light;
 * - a thin rim that's light along the top edge and fades towards the bottom.
 */
fun Modifier.cardSurface(base: Color, elevation: Dp, shape: Shape = CardShape): Modifier {
    val lift = elevation.value / 12f            // shadows scale with how raised the card is
    return this
        .then(
            if (lift <= 0f) Modifier else Modifier
                .dropShadow(shape, Shadow(radius = 20.dp * lift, color = Color.Black.copy(alpha = 0.18f), offset = DpOffset(0.dp, 8.dp * lift)))
                .dropShadow(shape, Shadow(radius = 4.dp * lift, color = lerp(base, Color.Black, 0.55f).copy(alpha = 0.35f), offset = DpOffset(0.dp, 1.5.dp * lift)))
        )
        .clip(shape)
        .background(
            Brush.linearGradient(
                0f to lerp(base, Color.White, 0.10f),
                0.45f to base,
                1f to lerp(base, Color.Black, 0.22f),
            )
        )
        .background(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.12f), 0.4f to Color.Transparent))
        .border(
            1.dp,
            Brush.verticalGradient(
                0f to Color.White.copy(alpha = 0.30f),
                0.5f to Color.White.copy(alpha = 0.06f),
                1f to Color.Black.copy(alpha = 0.10f),
            ),
            shape,
        )
}

private fun lerp(a: Color, b: Color, t: Float) = Color(
    red = a.red + (b.red - a.red) * t,
    green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t,
)

/**
 * The barcode as printed on the card: a white panel with the code and its number, sized to
 * fill whatever room the card face leaves. Black on white so tills read it reliably.
 */
@Composable
fun CardBarcodePanel(value: String, format: CodeFormat, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            val density = LocalDensity.current
            val wPx = with(density) { maxWidth.roundToPx() }
            val hPx = with(density) { maxHeight.roundToPx() }
            val bmp = remember(value, format, wPx, hPx) {
                if (format.is2D) BarcodeRenderer.render(value, format, hPx, hPx)
                else BarcodeRenderer.render(value, format, wPx, hPx)
            }
            if (bmp != null) {
                Image(
                    bmp.asImageBitmap(),
                    contentDescription = "${format.label} for $value",
                    filterQuality = FilterQuality.None,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Text(
            value,
            style = MaterialTheme.typography.labelLarge,
            fontFamily = FontFamily.Monospace,
            color = Color(0xFF1B1B1F),
            maxLines = 1,
        )
    }
}

/** The scannable code itself, always black on white for reliable reading at the till. */
@Composable
fun BarcodeImage(value: String, format: CodeFormat, modifier: Modifier = Modifier) {
    BoxWithConstraints(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White)
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        val density = LocalDensity.current
        val wPx = with(density) { maxWidth.roundToPx() }
        val hPx = if (format.is2D) wPx else (wPx * 0.38f).toInt()
        val bmp = remember(value, format, wPx) { BarcodeRenderer.render(value, format, wPx, hPx) }
        if (bmp != null) {
            Image(
                bmp.asImageBitmap(),
                contentDescription = "${format.label} for $value",
                filterQuality = FilterQuality.None,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth(if (format.is2D) 0.8f else 1f)
                    .aspectRatio(bmp.width.toFloat() / bmp.height),
            )
        } else {
            Text(
                "This number can't be drawn as ${format.label}. Try another format.",
                color = Color(0xFF410E0B),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorPicker(colors: List<Int>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        colors.forEach { c ->
            val isSel = c == selected
            val scale by animateFloatAsState(if (isSel) 1.12f else 1f, label = "swatch")
            Box(
                Modifier
                    .size(44.dp)
                    .scale(scale)
                    .clip(if (isSel) RoundedCornerShape(14.dp) else CircleShape)
                    .background(Color(c))
                    .border(
                        width = if (isSel) 3.dp else 0.dp,
                        color = MaterialTheme.colorScheme.onSurface,
                        shape = if (isSel) RoundedCornerShape(14.dp) else CircleShape,
                    )
                    .clickable { onSelect(c) },
                contentAlignment = Alignment.Center,
            ) {
                if (isSel) Icon(Icons.Rounded.Check, contentDescription = "Selected", tint = contentColorFor(c))
            }
        }
    }
}

@Composable
fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CardFace("Your cards live here", MaterialTheme.colorScheme.primary.toArgbInt(), Modifier.fillMaxWidth(0.7f))
        Text("No cards yet", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Tap + to scan a card's barcode, pick a photo of one, or type the number in.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

fun Color.toArgbInt(): Int = android.graphics.Color.argb(
    (alpha * 255).toInt(), (red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt(),
)
