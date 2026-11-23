package dev.loyaltycards.data

import android.content.Context
import androidx.glance.appwidget.updateAll
import dev.loyaltycards.widget.SingleCardWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Cards live in a small JSON file in app-private storage. The whole list is held in memory
 * (a wallet is a few dozen cards at most) and rewritten on each change. Widgets read the same
 * store, and are refreshed after every write.
 */
class CardStore private constructor(private val context: Context) {
    private val file = File(context.filesDir, "cards.json")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _cards = MutableStateFlow(load())
    val cards: StateFlow<List<LoyaltyCard>> = _cards.asStateFlow()

    fun get(id: String?): LoyaltyCard? = id?.let { _cards.value.firstOrNull { c -> c.id == it } }

    fun mostRecent(): LoyaltyCard? =
        _cards.value.filter { it.lastUsedAt > 0 }.maxByOrNull { it.lastUsedAt }
            ?: _cards.value.firstOrNull()

    /** Another saved card with the same number, if any. Spaces and case are ignored. */
    fun findDuplicate(value: String, exceptId: String = ""): LoyaltyCard? {
        val key = normalise(value)
        if (key.isEmpty()) return null
        return _cards.value.firstOrNull { it.id != exceptId && normalise(it.value) == key }
    }

    /** Saves [card]; if its number is already saved under another card, returns that one unchanged. */
    fun save(card: LoyaltyCard): LoyaltyCard {
        findDuplicate(card.value, card.id)?.let { return it }
        // New cards go to the front of the wallet.
        val c = if (card.id.isBlank()) card.copy(id = UUID.randomUUID().toString(), lastUsedAt = System.currentTimeMillis()) else card
        _cards.update { list ->
            if (list.any { it.id == c.id }) list.map { if (it.id == c.id) c else it } else list + c
        }
        persist()
        return c
    }

    fun delete(id: String) {
        _cards.update { list -> list.filterNot { it.id == id } }
        persist()
    }

    fun markUsed(id: String) {
        val now = System.currentTimeMillis()
        _cards.update { list -> list.map { if (it.id == id) it.copy(lastUsedAt = now) else it } }
        // No widget shows when a card was last used, so they're left alone: redrawing the stack
        // would also scroll it back to the front card while you're using it.
        persist(refreshWidgets = false)
    }

    private fun persist(refreshWidgets: Boolean = true) {
        val snapshot = _cards.value
        scope.launch {
            synchronized(file) {
                val tmp = File(file.parentFile, "cards.json.tmp")
                tmp.writeText(toJson(snapshot))
                tmp.renameTo(file)
            }
            if (!refreshWidgets) return@launch
            SingleCardWidget().updateAll(context)
            dev.loyaltycards.widget.OverlapStackWidgetReceiver.refresh(context)
        }
    }

    /** A backup of every card, as a small JSON document. */
    fun exportJson(): String = JSONObject().apply {
        put("app", "dev.loyaltycards")
        put("version", 1)
        put("exportedAt", System.currentTimeMillis())
        put("cards", JSONArray(toJson(_cards.value)))
    }.toString(2)

    /** What an import did. */
    data class ImportResult(val added: Int, val alreadySaved: Int)

    /**
     * Adds the cards in a backup made by [exportJson]. Cards whose number is already saved are
     * left alone, so importing the same backup twice changes nothing. Throws if [text] isn't a
     * backup.
     */
    fun importJson(text: String): ImportResult {
        val trimmed = text.trim()
        val arr = if (trimmed.startsWith("[")) JSONArray(trimmed) else JSONObject(trimmed).getJSONArray("cards")
        val incoming = parse(arr)
        require(incoming.isNotEmpty() || arr.length() == 0) { "Not a backup" }
        var added = 0
        var skipped = 0
        _cards.update { list ->
            val out = list.toMutableList()
            val ids = out.map { it.id }.toMutableSet()
            val numbers = out.map { normalise(it.value) }.toMutableSet()
            for (card in incoming) {
                val key = normalise(card.value)
                if (key.isEmpty() || key in numbers) { skipped++; continue }
                val c = if (card.id in ids || card.id.isBlank()) card.copy(id = UUID.randomUUID().toString()) else card
                out += c; ids += c.id; numbers += key; added++
            }
            out
        }
        if (added > 0) persist()
        return ImportResult(added, skipped)
    }

    private fun load(): List<LoyaltyCard> = runCatching {
        if (!file.exists()) return emptyList()
        parse(JSONArray(file.readText()))
    }.getOrDefault(emptyList())

    private fun parse(arr: JSONArray): List<LoyaltyCard> = (0 until arr.length()).mapNotNull { i ->
        val o = arr.optJSONObject(i) ?: return@mapNotNull null
        if (!o.has("value")) return@mapNotNull null
        LoyaltyCard(
            id = o.optString("id"),
            name = o.optString("name"),
            value = o.optString("value"),
            format = CodeFormat.fromName(o.optString("format")),
            color = o.optInt("color", CardPalette.first()),
            note = o.optString("note"),
            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
            lastUsedAt = o.optLong("lastUsedAt"),
        )
    }

    private fun toJson(list: List<LoyaltyCard>): String = JSONArray().apply {
        list.forEach { c ->
            put(JSONObject().apply {
                put("id", c.id)
                put("name", c.name)
                put("value", c.value)
                put("format", c.format.name)
                put("color", c.color)
                put("note", c.note)
                put("createdAt", c.createdAt)
                put("lastUsedAt", c.lastUsedAt)
            })
        }
    }.toString()

    private fun normalise(v: String) = v.filterNot(Char::isWhitespace).uppercase()

    companion object {
        @Volatile private var instance: CardStore? = null
        fun get(context: Context): CardStore =
            instance ?: synchronized(this) {
                instance ?: CardStore(context.applicationContext).also { instance = it }
            }
    }
}
