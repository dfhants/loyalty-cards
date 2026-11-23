package dev.loyaltycards.data

import com.google.mlkit.vision.text.Text

/** A name (and maybe a colour) suggested from what's printed on the card. */
data class NameGuess(val name: String, val color: Int?)

/**
 * Picks a card name from the text ML Kit read off a card.
 *
 * 1. A known scheme's keyword anywhere in the text ("Clubcard", "Nectar", "Boots"…) wins, with its colour.
 * 2. Otherwise the most prominent line of words: tallest text first, which on a loyalty card is
 *    almost always the logo or shop name. Lines that are the card number, mostly digits, web
 *    addresses, or generic words like "loyalty card" are skipped.
 */
object NameGuesser {
    private val generic = setOf(
        "card", "cards", "loyalty", "loyalty card", "member", "membership", "rewards", "reward",
        "points", "scan", "scan me", "valid", "valid from", "expires", "expiry", "customer",
        "number", "card number", "no", "barcode", "the", "and", "terms", "conditions", "apply",
        "terms and conditions apply", "this card", "sign", "signature", "name", "issued",
    )

    fun guess(text: Text?, barcodeValue: String): NameGuess? {
        Brands.match(barcodeValue)?.let { return NameGuess(it.name, it.color) }
        if (text == null || text.text.isBlank()) return null

        val all = text.text.lowercase()
        Brands.all.firstOrNull { b -> b.keywords.any { all.containsWord(it) } }
            ?.let { return NameGuess(it.name, it.color) }

        val digits = barcodeValue.filter(Char::isDigit)
        val best = text.textBlocks
            .flatMap { it.lines }
            .mapNotNull { line ->
                val s = clean(line.text) ?: return@mapNotNull null
                if (digits.length >= 4 && s.filter(Char::isDigit).let { it.length >= 4 && digits.contains(it) }) return@mapNotNull null
                val height = line.boundingBox?.height() ?: 0
                s to height
            }
            .maxByOrNull { (_, h) -> h }
            ?.first
            ?: return null
        return NameGuess(tidyCase(best), null)
    }

    private fun clean(raw: String): String? {
        val s = raw.trim().trim('.', ',', ':', ';', '-', '*', '•', '|').replace(Regex("\\s+"), " ")
        if (s.length !in 2..28) return null
        val lower = s.lowercase()
        if (lower in generic) return null
        if (lower.startsWith("www") || lower.contains(".co") || lower.contains("http") || lower.contains('@')) return null
        val letters = s.count(Char::isLetter)
        val digitCount = s.count(Char::isDigit)
        if (letters < 2 || digitCount > letters) return null
        // Mostly letters and spaces; a line of symbols is noise from the barcode or a pattern.
        if (letters + s.count { it == ' ' || it == '&' || it == '\'' || it == '-' } < s.length * 0.75) return null
        return s
    }

    /** "TESCO" → "Tesco", but leave mixed-case logos ("myWaitrose") alone. */
    private fun tidyCase(s: String): String =
        if (s == s.uppercase() && s.length > 3)
            s.lowercase().split(' ').joinToString(" ") { w -> w.replaceFirstChar(Char::titlecase) }
        else s

    private fun String.containsWord(word: String): Boolean =
        Regex("(^|[^a-z])" + Regex.escape(word) + "($|[^a-z])").containsMatchIn(this)
}
