package dev.loyaltycards.baselineprofile

import android.content.Intent
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Records the code that runs on the paths that matter most for speed:
 * - a card popping up from a widget (the pop-up activity, its card and barcode drawing);
 * - opening the app and lifting a card out of the wallet.
 *
 * Needs a device with at least one card saved; pass its id with
 * `-Pandroid.testInstrumentationRunnerArguments.cardId=<id>` (default "t0").
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() {
        val cardId = InstrumentationRegistry.getArguments().getString("cardId") ?: "t0"
        rule.collect(packageName = PACKAGE, includeInStartupProfile = true) {
            // The pop-up, opened the way a widget opens it, from a stopped app each time.
            repeat(3) {
                killProcess()
                startActivityAndWait(
                    Intent()
                        .setClassName(PACKAGE, "$PACKAGE.widget.WidgetCardOverlayActivity")
                        .putExtra("card_id", cardId)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
                device.waitForIdle()
                device.pressBack()
                device.waitForIdle()
            }
            // The app: open the wallet and lift the front card out.
            pressHome()
            startActivityAndWait()
            device.waitForIdle()
            val h = device.displayHeight
            device.click(device.displayWidth / 2, (h * 0.72).toInt())
            device.waitForIdle()
            Thread.sleep(1500)
            device.pressBack()
            device.waitForIdle()
        }
    }

    private companion object {
        const val PACKAGE = "dev.loyaltycards"
    }
}
