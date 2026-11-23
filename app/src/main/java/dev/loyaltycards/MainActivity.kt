package dev.loyaltycards

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.lifecycleScope
import dev.loyaltycards.widget.SingleCardWidgetReceiver
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.runtime.CompositionLocalProvider
import dev.loyaltycards.ui.LocalSharedScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.loyaltycards.data.CardPalette
import dev.loyaltycards.data.CardStore
import dev.loyaltycards.data.CodeFormat
import dev.loyaltycards.data.ImageScanner
import dev.loyaltycards.data.LoyaltyCard
import dev.loyaltycards.data.ScannedCode
import dev.loyaltycards.ui.AddMethod
import dev.loyaltycards.ui.EditCardScreen
import dev.loyaltycards.ui.HomeScreen
import dev.loyaltycards.ui.LoyaltyTheme
import dev.loyaltycards.ui.ScannerScreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import dev.loyaltycards.ui.SettingsScreen

class MainActivity : ComponentActivity() {
    /** Card a widget asked to open; consumed by the UI. */
    private val openRequest = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) openRequest.value = intent.getStringExtra(EXTRA_CARD_ID)
        // Real previews of the widgets in the home screen's widget picker (Android 15+).
        if (Build.VERSION.SDK_INT >= 35) lifecycleScope.launch {
            runCatching {
                val widgets = GlanceAppWidgetManager(this@MainActivity)
                widgets.setWidgetPreviews(SingleCardWidgetReceiver::class)
            }
            runCatching { dev.loyaltycards.widget.StackPreview.publish(this@MainActivity) }
        }
        setContent {
            LoyaltyTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    val req by openRequest.collectAsStateWithLifecycle()
                    App(req) { openRequest.value = null }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_CARD_ID)?.let { openRequest.value = it }
    }

    companion object {
        const val EXTRA_CARD_ID = "card_id"
    }
}

private const val HOME = "home"
private const val SCAN = "scan"
private const val SETTINGS = "settings"
private const val EDIT_NEW = "edit:"
private fun edit(id: String) = "edit:$id"

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun App(openRequest: String?, onOpenHandled: () -> Unit) {
    val context = LocalContext.current
    val store = remember { CardStore.get(context) }
    val cards by store.cards.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val stateHolder = rememberSaveableStateHolder()

    val stack = rememberSaveable(saver = androidx.compose.runtime.saveable.listSaver(
        save = { it.toList() },
        restore = { it.toMutableStateList() },
    )) { mutableListOf(HOME).toMutableStateList() }
    var scanned by remember { mutableStateOf<ScannedCode?>(null) }
    /** The card lifted out of the wallet, if any. */
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    /** A card to bring out with the full animation once the wallet is showing (from a widget). */
    var revealId by rememberSaveable { mutableStateOf<String?>(null) }
    fun showCard(id: String) {
        store.markUsed(id)
        stack.filter { it.startsWith("edit:") }.forEach(stateHolder::removeState)
        scanned = null
        stack.clear(); stack.add(HOME)
        selectedId = id
    }

    fun push(s: String) { stack.add(s) }
    fun pop() {
        if (stack.size > 1) {
            val gone = stack.removeAt(stack.lastIndex)
            if (gone.startsWith("edit:")) { stateHolder.removeState(gone); scanned = null }
        }
    }
    val top = stack.last()

    /** A code arrived (camera or photo): feed the open editor, or start a new card with it. */
    fun deliver(code: ScannedCode) {
        if (stack.last() == SCAN) stack.removeAt(stack.lastIndex)
        // Not editing and it's already saved: show the existing card rather than make a copy.
        val existing = store.findDuplicate(code.value)
        if (existing != null && !stack.last().startsWith("edit:")) {
            Toast.makeText(context, "Already saved as ${existing.name}", Toast.LENGTH_SHORT).show()
            showCard(existing.id)
            return
        }
        scanned = code
        if (!stack.last().startsWith("edit:")) push(EDIT_NEW)
    }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val code = runCatching { ImageScanner.scan(context, uri) }.getOrNull()
            if (code != null) deliver(code)
            else Toast.makeText(context, "No barcode found in that picture", Toast.LENGTH_LONG).show()
        }
    }
    fun pickPhoto() = photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))

    // Several photos at once: one picture opens the editor as usual; more are added straight away.
    var importing by remember { mutableStateOf(false) }
    val multiPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(maxItems = 30)) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            if (uris.size == 1) {
                val code = runCatching { ImageScanner.scan(context, uris[0]) }.getOrNull()
                if (code != null) deliver(code)
                else Toast.makeText(context, "No barcode found in that picture", Toast.LENGTH_LONG).show()
                return@launch
            }
            importing = true
            val result = importPhotos(context, store, uris)
            importing = false
            Toast.makeText(context, result, Toast.LENGTH_LONG).show()
        }
    }
    fun pickPhotos() = multiPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))

    // Backups, through the system's file picker.
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri, "wt")!!.bufferedWriter().use { it.write(store.exportJson()) }
                }.isSuccess
            }
            val n = store.cards.value.size
            Toast.makeText(context, if (ok) "Saved $n card${if (n == 1) "" else "s"}" else "Couldn't save the backup", Toast.LENGTH_SHORT).show()
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val text = context.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
                    store.importJson(text)
                }.getOrNull()
            }
            val message = when {
                result == null -> "That file isn't a Loyalty Cards backup"
                result.added == 0 && result.alreadySaved > 0 -> "You already have all ${result.alreadySaved} of those cards"
                result.alreadySaved == 0 -> "Added ${result.added} card${if (result.added == 1) "" else "s"}"
                else -> "Added ${result.added}, skipped ${result.alreadySaved} you already have"
            }
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(openRequest) {
        val id = openRequest ?: return@LaunchedEffect
        if (store.get(id) != null) {
            // From a widget: show the wallet, then let the card slide out and turn over as if
            // tapped, so it arrives showing the same barcode side the widget shows.
            stack.filter { it.startsWith("edit:") }.forEach(stateHolder::removeState)
            scanned = null
            stack.clear(); stack.add(HOME)
            selectedId = null
            revealId = id
        }
        onOpenHandled()
    }

    BackHandler(stack.size > 1) { pop() }

    SharedTransitionLayout {
    val sharedScope = this
    AnimatedContent(
        targetState = top,
        transitionSpec = {
            (fadeIn() + scaleIn(initialScale = 0.96f)) togetherWith fadeOut()
        },
        label = "nav",
    ) { screen ->
        CompositionLocalProvider(LocalSharedScope provides sharedScope) {
        stateHolder.SaveableStateProvider(screen) {
            when {
                screen == HOME -> HomeScreen(
                    cards = cards,
                    importing = importing,
                    selectedId = selectedId,
                    revealId = revealId,
                    onRevealed = { revealId = null },
                    onOpen = { showCard(it.id) },
                    onDismiss = { selectedId = null },
                    onEdit = { push(edit(it.id)) },
                    onDelete = { selectedId = null; store.delete(it.id) },
                    onSettings = { push(SETTINGS) },
                    onAdd = { method ->
                        when (method) {
                            AddMethod.Scan -> push(SCAN)
                            AddMethod.Photo -> pickPhotos()
                            AddMethod.Manual -> { scanned = null; push(EDIT_NEW) }
                        }
                    },
                )

                screen == SETTINGS -> SettingsScreen(
                    cardCount = cards.size,
                    onExport = { exporter.launch("loyalty-cards-${java.time.LocalDate.now()}.json") },
                    onImport = { importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                    onBack = ::pop,
                )

                screen == SCAN -> ScannerScreen(
                    onResult = ::deliver,
                    onPickPhoto = ::pickPhoto,
                    onClose = ::pop,
                )

                screen.startsWith("edit:") -> {
                    val id = screen.removePrefix("edit:")
                    val existing = store.get(id)
                    val initial = existing ?: remember {
                        LoyaltyCard(id = "", name = "", value = "", format = CodeFormat.CODE_128, color = CardPalette.random())
                    }
                    EditCardScreen(
                        initial = initial,
                        isNew = existing == null,
                        scanned = scanned,
                        duplicateOf = { v -> store.findDuplicate(v, existing?.id ?: "") },
                        onScan = { push(SCAN) },
                        onPickPhoto = ::pickPhoto,
                        onCancel = ::pop,
                        onSave = { card ->
                            store.save(card)
                            // New cards land back on the card list; edits return to the card.
                            pop()
                        },
                    )
                }
            }
        }
        }
    }
    }
}

/** Reads each picture and saves a card for every new barcode found. Returns a summary for the user. */
private suspend fun importPhotos(context: android.content.Context, store: CardStore, uris: List<android.net.Uri>): String {
    var added = 0
    var duplicates = 0
    var missing = 0
    for (uri in uris) {
        val code = runCatching { ImageScanner.scan(context, uri) }.getOrNull()
        when {
            code == null -> missing++
            store.findDuplicate(code.value) != null -> duplicates++
            else -> {
                store.save(
                    LoyaltyCard(
                        id = "",
                        name = code.nameGuess?.name ?: "Card ${code.value.takeLast(4)}",
                        value = code.value,
                        format = code.format,
                        color = code.suggestedColor ?: CardPalette.random(),
                    )
                )
                added++
            }
        }
    }
    return buildList {
        add(if (added == 1) "Added 1 card" else "Added $added cards")
        if (duplicates > 0) add("$duplicates already saved")
        if (missing > 0) add(if (missing == 1) "1 picture had no barcode" else "$missing pictures had no barcode")
    }.joinToString(" · ")
}
