package dev.loyaltycards.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.loyaltycards.data.AppSettings

/**
 * Settings: what happens while a card is showing, and backing up the cards.
 * Backups go through the system file picker, so they can be saved anywhere: Drive, a USB stick,
 * or the phone's Downloads.
 */
@Composable
fun SettingsScreen(
    cardCount: Int,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onBack: () -> Unit,
) {
    val store = AppSettings.get(LocalContext.current)
    val settings by store.settings.collectAsState()
    val scroll = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                },
                scrollBehavior = scroll,
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            Section("While a card is showing")
            Toggle(
                "Brighten screen",
                "Full brightness, so till scanners can read the barcode",
                settings.brighten,
            ) { v -> store.update { it.copy(brighten = v) } }
            Toggle(
                "Keep screen on",
                "The screen won't time out while you're queueing",
                settings.keepScreenOn,
            ) { v -> store.update { it.copy(keepScreenOn = v) } }
            Toggle(
                "Pause contactless payments",
                "Stops Google Wallet paying by accident when you hold the phone to a scanner",
                settings.pauseNfc,
            ) { v -> store.update { it.copy(pauseNfc = v) } }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Section("Backup")
            ListItem(
                headlineContent = { Text("Export cards") },
                supportingContent = {
                    Text(if (cardCount == 1) "Save your card to a file" else "Save all $cardCount cards to a file")
                },
                leadingContent = { Icon(Icons.Rounded.FileUpload, null) },
                modifier = Modifier.clickable(enabled = cardCount > 0, onClick = onExport),
            )
            ListItem(
                headlineContent = { Text("Import cards") },
                supportingContent = { Text("Add cards from a backup file. Cards you already have are skipped.") },
                leadingContent = { Icon(Icons.Rounded.FileDownload, null) },
                modifier = Modifier.clickable(onClick = onImport),
            )
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun Toggle(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(detail) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        colors = ListItemDefaults.colors(),
        modifier = Modifier.clickable { onChange(!checked) },
    )
}
