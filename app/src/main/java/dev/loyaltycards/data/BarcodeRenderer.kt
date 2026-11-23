package dev.loyaltycards.data

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.content.Context
import androidx.core.graphics.createBitmap
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import android.graphics.ImageDecoder
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object BarcodeRenderer {
    /**
     * Draws [value] as [format]. 1D codes are drawn [width]x[height]; 2D codes are square at
     * the smaller side. Returns null when the value can't be encoded in that format
     * (e.g. letters in an EAN-13).
     *
     * [margin] is the clear space round the code, in modules; null for the usual (1 for 2D
     * codes, 8 for barcodes). Pass less where a white panel already gives the code its space.
     *
     * [snug] draws a barcode at a whole number of pixels per bar, as near [width] as that
     * allows, with no white padding at the sides, for a view that stretches it to fit.
     */
    fun render(value: String, format: CodeFormat, width: Int, height: Int, margin: Int? = null, snug: Boolean = false): Bitmap? = runCatching {
        val hints = mapOf(EncodeHintType.MARGIN to (margin ?: if (format.is2D) 1 else 8), EncodeHintType.CHARACTER_SET to "UTF-8")
        val w: Int
        val h: Int
        if (format.is2D) {
            val s = minOf(width, height); w = s; h = s
        } else if (snug) {
            val modules = MultiFormatWriter().encode(value, format.zxing, 0, 1, hints).width
            w = modules * (width / modules).coerceAtLeast(1); h = height
        } else {
            w = width; h = height
        }
        val matrix = MultiFormatWriter().encode(value, format.zxing, w, h, hints)
        val pixels = IntArray(matrix.width * matrix.height)
        for (y in 0 until matrix.height) {
            val row = y * matrix.width
            for (x in 0 until matrix.width) {
                pixels[row + x] = if (matrix[x, y]) Color.BLACK else Color.WHITE
            }
        }
        createBitmap(matrix.width, matrix.height).apply {
            setPixels(pixels, 0, matrix.width, 0, 0, matrix.width, matrix.height)
        }
    }.getOrNull()

    fun canEncode(value: String, format: CodeFormat): Boolean =
        value.isNotEmpty() && render(value, format, 200, 80) != null
}

/** Result of reading a code, from the camera or a picture. */
data class ScannedCode(
    val value: String,
    val format: CodeFormat,
    val nameGuess: NameGuess? = null,
    /** The card's colour as seen in the picture. */
    val colorGuess: Int? = null,
) {
    /** A known scheme's colour beats one read from the picture. */
    val suggestedColor: Int? get() = nameGuess?.color ?: colorGuess
}

object ImageScanner {
    /** Finds the first supported barcode in a picture, or null if there isn't one. */
    suspend fun scan(context: Context, uri: Uri): ScannedCode? {
        val image = InputImage.fromFilePath(context, uri)
        val client = BarcodeScanning.getClient()
        val reader = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return try {
            val code = client.process(image).awaitResult().firstNotNullOfOrNull { b ->
                val v = b.rawValue ?: return@firstNotNullOfOrNull null
                CodeFormat.fromMlKit(b.format)?.let { ScannedCode(v, it) }
            } ?: return null
            val text = runCatching { reader.process(image).awaitResult() }.getOrNull()
            val colour = withContext(Dispatchers.IO) { runCatching { ColorGuesser.fromBitmap(loadSmall(context, uri)) }.getOrNull() }
            code.copy(nameGuess = NameGuesser.guess(text, code.value), colorGuess = colour)
        } finally {
            client.close()
            reader.close()
        }
    }

    /** A small software bitmap of the picture; plenty for finding its main colour. */
    private fun loadSmall(context: Context, uri: Uri): Bitmap =
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val longest = maxOf(info.size.width, info.size.height)
            decoder.setTargetSampleSize((longest / 400).coerceAtLeast(1))
        }
}

private suspend fun <T> com.google.android.gms.tasks.Task<T>.awaitResult(): T =
    suspendCancellableCoroutine { cont ->
        addOnSuccessListener { cont.resume(it) }
        addOnFailureListener { cont.resumeWithException(it) }
        addOnCanceledListener { cont.cancel() }
    }
