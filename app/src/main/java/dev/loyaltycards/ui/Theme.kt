package dev.loyaltycards.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.expressiveLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Seed = Color(0xFF2E5BFF)

/** Material 3 Expressive, using the wallpaper-based dynamic palette where the device supports it. */
@Composable
fun LoyaltyTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme(primary = Color(0xFFB8C3FF), secondary = Color(0xFFC1C5DD))
        else -> expressiveLightColorScheme().copy(primary = Seed)
    }
    MaterialExpressiveTheme(
        colorScheme = scheme,
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}

/** Black or white, whichever reads better on [background]. */
fun contentColorFor(background: Int): Color {
    val c = Color(background)
    val lum = 0.2126f * c.red + 0.7152f * c.green + 0.0722f * c.blue
    return if (lum > 0.6f) Color(0xFF1B1B1F) else Color.White
}
