package dev.loyaltycards.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What happens while a card's barcode is showing, in the app and over the home screen. */
data class Settings(
    /** Full screen brightness, so till scanners can read the barcode. */
    val brighten: Boolean = true,
    /** The screen doesn't time out. */
    val keepScreenOn: Boolean = true,
    /** Contactless payments can't go off when the phone is held to a scanner. */
    val pauseNfc: Boolean = true,
)

/** The app's settings, kept in shared preferences and offered as a flow. */
class AppSettings private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    fun update(change: (Settings) -> Settings) {
        val next = change(_settings.value)
        prefs.edit()
            .putBoolean(BRIGHTEN, next.brighten)
            .putBoolean(KEEP_ON, next.keepScreenOn)
            .putBoolean(PAUSE_NFC, next.pauseNfc)
            .apply()
        _settings.value = next
    }

    private fun read() = Settings(
        brighten = prefs.getBoolean(BRIGHTEN, true),
        keepScreenOn = prefs.getBoolean(KEEP_ON, true),
        pauseNfc = prefs.getBoolean(PAUSE_NFC, true),
    )

    companion object {
        private const val BRIGHTEN = "brighten"
        private const val KEEP_ON = "keep_screen_on"
        private const val PAUSE_NFC = "pause_nfc"

        @Volatile private var instance: AppSettings? = null
        fun get(context: Context): AppSettings =
            instance ?: synchronized(this) {
                instance ?: AppSettings(context.applicationContext).also { instance = it }
            }
    }
}
