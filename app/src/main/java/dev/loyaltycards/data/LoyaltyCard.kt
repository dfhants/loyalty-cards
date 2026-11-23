package dev.loyaltycards.data

import com.google.mlkit.vision.barcode.common.Barcode
import com.google.zxing.BarcodeFormat as ZxingFormat

/** Barcode symbologies the app can both read (ML Kit) and draw (ZXing). */
enum class CodeFormat(val label: String, val is2D: Boolean, val zxing: ZxingFormat) {
    CODE_128("Code 128", false, ZxingFormat.CODE_128),
    EAN_13("EAN-13", false, ZxingFormat.EAN_13),
    EAN_8("EAN-8", false, ZxingFormat.EAN_8),
    UPC_A("UPC-A", false, ZxingFormat.UPC_A),
    UPC_E("UPC-E", false, ZxingFormat.UPC_E),
    CODE_39("Code 39", false, ZxingFormat.CODE_39),
    CODE_93("Code 93", false, ZxingFormat.CODE_93),
    CODABAR("Codabar", false, ZxingFormat.CODABAR),
    ITF("ITF", false, ZxingFormat.ITF),
    QR_CODE("QR code", true, ZxingFormat.QR_CODE),
    DATA_MATRIX("Data Matrix", true, ZxingFormat.DATA_MATRIX),
    AZTEC("Aztec", true, ZxingFormat.AZTEC),
    PDF_417("PDF417", false, ZxingFormat.PDF_417);

    companion object {
        fun fromMlKit(format: Int): CodeFormat? = when (format) {
            Barcode.FORMAT_CODE_128 -> CODE_128
            Barcode.FORMAT_EAN_13 -> EAN_13
            Barcode.FORMAT_EAN_8 -> EAN_8
            Barcode.FORMAT_UPC_A -> UPC_A
            Barcode.FORMAT_UPC_E -> UPC_E
            Barcode.FORMAT_CODE_39 -> CODE_39
            Barcode.FORMAT_CODE_93 -> CODE_93
            Barcode.FORMAT_CODABAR -> CODABAR
            Barcode.FORMAT_ITF -> ITF
            Barcode.FORMAT_QR_CODE -> QR_CODE
            Barcode.FORMAT_DATA_MATRIX -> DATA_MATRIX
            Barcode.FORMAT_AZTEC -> AZTEC
            Barcode.FORMAT_PDF417 -> PDF_417
            else -> null
        }

        fun fromName(name: String?): CodeFormat =
            entries.firstOrNull { it.name == name } ?: CODE_128
    }
}

data class LoyaltyCard(
    val id: String,
    val name: String,
    val value: String,
    val format: CodeFormat,
    /** ARGB colour of the card face. */
    val color: Int,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val lastUsedAt: Long = 0L,
)

/** A well-known scheme: sets a sensible name, colour and default format when entering by hand. */
data class Brand(
    val name: String,
    val color: Int,
    val format: CodeFormat,
    /** Card-number prefixes used to recognise a scanned card. */
    val prefixes: List<String> = emptyList(),
    /** Lower-case words printed on the card that identify the scheme. */
    val keywords: List<String> = emptyList(),
)

object Brands {
    val all = listOf(
        Brand("Tesco Clubcard", 0xFF00539F.toInt(), CodeFormat.CODE_128, listOf("634004"), listOf("tesco", "clubcard")),
        Brand("Nectar", 0xFF7B2C83.toInt(), CodeFormat.CODE_128, listOf("98263000", "8263000"), listOf("nectar")),
        Brand("Boots Advantage", 0xFF05054B.toInt(), CodeFormat.CODE_128, emptyList(), listOf("boots", "advantage card")),
        Brand("Morrisons More", 0xFF00563F.toInt(), CodeFormat.CODE_128, emptyList(), listOf("morrisons")),
        Brand("myWaitrose", 0xFF5C8A2E.toInt(), CodeFormat.CODE_128, emptyList(), listOf("waitrose")),
        Brand("Co-op", 0xFF00B1E7.toInt(), CodeFormat.CODE_128, emptyList(), listOf("co-op", "coop", "co op")),
        Brand("Lidl Plus", 0xFF0050AA.toInt(), CodeFormat.QR_CODE, emptyList(), listOf("lidl")),
        Brand("Costa Club", 0xFF6D1F37.toInt(), CodeFormat.QR_CODE, emptyList(), listOf("costa")),
        Brand("Superdrug", 0xFFE5007E.toInt(), CodeFormat.CODE_128, emptyList(), listOf("superdrug")),
        Brand("IKEA Family", 0xFF0058A3.toInt(), CodeFormat.CODE_128, emptyList(), listOf("ikea")),
    )

    fun match(value: String): Brand? {
        val digits = value.filter(Char::isDigit)
        return all.firstOrNull { b -> b.prefixes.any { digits.startsWith(it) } }
    }
}

/** Palette offered in the colour picker. */
val CardPalette = listOf(
    0xFF00539F, 0xFF7B2C83, 0xFF2E5BFF, 0xFF006A6A, 0xFF00563F, 0xFF5C8A2E,
    0xFFE5007E, 0xFFB3261E, 0xFFE8590C, 0xFFF4B400, 0xFF6D4C41, 0xFF37474F,
    0xFF1B1B1F, 0xFF8E24AA, 0xFF00897B, 0xFFC2185B,
).map { it.toInt() }
