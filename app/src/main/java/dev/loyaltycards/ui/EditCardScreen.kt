package dev.loyaltycards.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.loyaltycards.data.BarcodeRenderer
import dev.loyaltycards.data.Brands
import dev.loyaltycards.data.CardPalette
import dev.loyaltycards.data.CodeFormat
import dev.loyaltycards.data.LoyaltyCard
import dev.loyaltycards.data.ScannedCode

/**
 * Add or edit a card. [initial] carries whatever is already known: an existing card, or a
 * blank card pre-filled from a scan.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditCardScreen(
    initial: LoyaltyCard,
    isNew: Boolean,
    scanned: ScannedCode?,
    duplicateOf: (String) -> LoyaltyCard?,
    onScan: () -> Unit,
    onPickPhoto: () -> Unit,
    onCancel: () -> Unit,
    onSave: (LoyaltyCard) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initial.name) }
    var value by rememberSaveable { mutableStateOf(initial.value) }
    var format by rememberSaveable { mutableStateOf(initial.format) }
    var color by rememberSaveable { mutableIntStateOf(initial.color) }
    var note by rememberSaveable { mutableStateOf(initial.note) }
    var lastScan by rememberSaveable { mutableStateOf<String?>(null) }

    // A fresh scan while this screen is open replaces the number and format.
    if (scanned != null && scanned.value != lastScan) {
        lastScan = scanned.value
        value = scanned.value
        format = scanned.format
        if (name.isBlank()) {
            scanned.nameGuess?.let { name = it.name }
            scanned.suggestedColor?.let { color = it }
        }
    }

    val encodable = BarcodeRenderer.canEncode(value, format)
    val duplicate = duplicateOf(value.trim())
    val canSave = name.isNotBlank() && encodable && duplicate == null

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) "New card" else "Edit card") },
                navigationIcon = { IconButton(onClick = onCancel) { Icon(Icons.Rounded.Close, "Cancel") } },
                actions = {
                    Button(
                        onClick = {
                            onSave(initial.copy(name = name.trim(), value = value.trim(), format = format, color = color, note = note.trim()))
                        },
                        enabled = canSave,
                        modifier = Modifier.padding(end = 8.dp),
                    ) { Text("Save") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CardFace(name, color, Modifier.fillMaxWidth(), subtitle = value.ifBlank { null })

            Text("Quick pick", style = MaterialTheme.typography.titleSmall)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Brands.all.forEach { b ->
                    FilterChip(
                        selected = name == b.name,
                        onClick = {
                            name = b.name
                            color = b.color
                            // Keep a scanned format; it is read from the real card.
                            if (scanned == null && initial.value.isBlank()) format = b.format
                        },
                        label = { Text(b.name) },
                    )
                }
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Card name") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text("Card number") },
                singleLine = true,
                isError = (value.isNotEmpty() && !encodable) || duplicate != null,
                supportingText = {
                    when {
                        duplicate != null -> Text("Already saved as ${duplicate.name}")
                        value.isNotEmpty() && !encodable -> Text("Not valid for ${format.label}")
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = if (format.is2D) KeyboardType.Text else KeyboardType.Ascii),
                modifier = Modifier.fillMaxWidth(),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onScan) {
                    Icon(Icons.Rounded.QrCodeScanner, null); Text("  Scan")
                }
                OutlinedButton(onClick = onPickPhoto) {
                    Icon(Icons.Rounded.Image, null); Text("  From photo")
                }
            }

            FormatDropdown(format) { format = it }

            Text("Colour", style = MaterialTheme.typography.titleSmall)
            ColorPicker(
                colors = (listOf(color) + CardPalette).distinct(),
                selected = color,
                onSelect = { color = it },
            )

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Note (optional)") },
                modifier = Modifier.fillMaxWidth(),
            )

            if (value.isNotBlank() && encodable) {
                Text("Preview", style = MaterialTheme.typography.titleSmall)
                BarcodeImage(value, format, Modifier.fillMaxWidth())
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FormatDropdown(format: CodeFormat, onChange: (CodeFormat) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
        OutlinedTextField(
            value = format.label,
            onValueChange = {},
            readOnly = true,
            label = { Text("Barcode type") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            CodeFormat.entries.forEach { f ->
                DropdownMenuItem(text = { Text(f.label) }, onClick = { onChange(f); open = false })
            }
        }
    }
}
