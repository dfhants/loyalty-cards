package dev.loyaltycards.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageAnalysis
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FlashlightOff
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dev.loyaltycards.data.NameGuesser
import dev.loyaltycards.data.ColorGuesser
import dev.loyaltycards.data.CodeFormat
import dev.loyaltycards.data.ScannedCode

@Composable
fun ScannerScreen(
    onResult: (ScannedCode) -> Unit,
    onPickPhoto: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(Manifest.permission.CAMERA) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (granted) {
            CameraPreview(onResult)
        } else {
            Column(
                Modifier.align(Alignment.Center).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    "Camera access is needed to scan. You can also read a barcode from a photo instead.",
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )
                Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) { Text("Allow camera") }
            }
        }

        // Viewfinder guide
        Box(
            Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.82f)
                .aspectRatio(1.4f)
                .border(3.dp, Color.White.copy(alpha = 0.9f), RoundedCornerShape(28.dp)),
        )

        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)) {
            FilledTonalIconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Close") }
            Box(Modifier.weight(1f))
            Text(
                "Point at a barcode or QR code",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Button(
                onClick = onPickPhoto,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 16.dp),
            ) {
                Icon(Icons.Rounded.Image, null); Text("  Use a photo instead")
            }
        }
    }
}

@Composable
private fun CameraPreview(onResult: (ScannedCode) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var torch by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }

    val controller = remember { LifecycleCameraController(context) }
    val scanner = remember { BarcodeScanning.getClient() }
    val reader = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    DisposableEffect(lifecycleOwner) {
        val executor = ContextCompat.getMainExecutor(context)
        controller.setImageAnalysisAnalyzer(
            executor,
            MlKitAnalyzer(listOf(scanner, reader), ImageAnalysis.COORDINATE_SYSTEM_VIEW_REFERENCED, executor) { result ->
                if (done) return@MlKitAnalyzer
                val code = result.getValue(scanner)?.firstNotNullOfOrNull { b ->
                    val v = b.rawValue ?: return@firstNotNullOfOrNull null
                    CodeFormat.fromMlKit(b.format)?.let { ScannedCode(v, it) }
                }
                if (code != null) {
                    done = true
                    // Text read from the same frame names the card.
                    val frame = previewView?.bitmap?.let(ColorGuesser::cropToGuide)
                    onResult(
                        code.copy(
                            nameGuess = NameGuesser.guess(result.getValue(reader), code.value),
                            colorGuess = runCatching { ColorGuesser.fromBitmap(frame) }.getOrNull(),
                        )
                    )
                }
            },
        )
        controller.bindToLifecycle(lifecycleOwner)
        onDispose {
            controller.unbind()
            scanner.close()
            reader.close()
        }
    }
    LaunchedEffect(torch) { controller.enableTorch(torch) }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    this.controller = controller
                }.also { previewView = it }
            },
            modifier = Modifier.fillMaxSize(),
        )
        FilledTonalIconButton(
            onClick = { torch = !torch },
            modifier = Modifier.align(Alignment.TopEnd).safeDrawingPadding().padding(16.dp),
        ) {
            Icon(if (torch) Icons.Rounded.FlashlightOff else Icons.Rounded.FlashlightOn, "Torch")
        }
    }
}
