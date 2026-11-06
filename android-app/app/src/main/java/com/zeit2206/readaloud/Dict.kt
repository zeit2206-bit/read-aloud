package com.zeit2206.readaloud

import android.content.Context
import java.text.Normalizer

/** One lookup result, as in the PDF IPA Reader extension. [approx]: found through a base form (shown with `*`). */
data class Entry(val word: String, val ipa: String?, val approx: Boolean, val es: List<String>, val esApprox: Boolean)

/**
 * Offline dictionaries, same data and rules as the Chrome extension (pdf-ipa-extension/shared/lookup.js):
 * CMU dictionary in IPA (assets/ipa.tsv) and FreeDict English–Spanish (assets/es.tsv, glosses joined by '|').
 */
object Dict {
    @Volatile private var ipaMap: HashMap<String, String>? = null
    @Volatile private var esMap: HashMap<String, String>? = null
    @Volatile private var sorted: List<String> = emptyList()
    val ready get() = ipaMap != null && esMap != null

    @Synchronized
    fun load(context: Context) {
        if (ready) return
        ipaMap = readTsv(context, "ipa.tsv", 130_000)
        esMap = readTsv(context, "es.tsv", 56_000)
        sorted = ipaMap!!.keys.filter { k -> k.all { it in 'a'..'z' || it == '\'' || it == '-' } }.sorted()
    }

    private fun readTsv(context: Context, name: String, size: Int): HashMap<String, String> {
        val m = HashMap<String, String>(size * 4 / 3)
        val s = context.assets.open(name).use { it.readBytes() }.decodeToString()
        var p = 0
        while (p < s.length) {
            val tab = s.indexOf('\t', p); if (tab < 0) break
            var nl = s.indexOf('\n', tab); if (nl < 0) nl = s.length
            m[s.substring(p, tab)] = s.substring(tab + 1, nl)
            p = nl + 1
        }
        return m
    }

    /** Lowercase, strip surrounding punctuation, keep internal hyphens/apostrophes. */
    fun normalize(raw: String): String = Normalizer.normalize(raw.lowercase(), Normalizer.Form.NFKD)
        .replace(Regex("[‘’ʼ]"), "'")
        .replace(Regex("^[^a-z']+"), "")
        .replace(Regex("[^a-z']+$"), "")

    /** Regular inflections a dictionary may not list verbatim. */
    private fun baseForms(w: String): List<String> {
        val f = mutableListOf<String>()
        if (w.endsWith("'s") || w.endsWith("s'")) f += w.dropLast(2)
        if (w.endsWith("ies") && w.length > 4) f += w.dropLast(3) + "y"
        if (w.endsWith("es") && w.length > 3) f += w.dropLast(2)
        if (w.endsWith("s") && w.length > 3) f += w.dropLast(1)
        // "stared" -> "stare"/"stared"; never "star", which is a different word.
        if (w.endsWith("ed") && w.length > 4) { f += w.dropLast(1); f += w.dropLast(2) }
        if (w.endsWith("ing") && w.length > 5) { f += w.dropLast(3) + "e"; f += w.dropLast(3) }
        if (w.endsWith("ly") && w.length > 4) f += w.dropLast(2)
        if ('-' in w) f += w.replace("-", "")
        return f
    }

    private fun find(table: Map<String, String>?, w: String): Pair<String, Boolean>? {
        table ?: return null
        table[w]?.let { return it to false }
        for (b in baseForms(w)) table[b]?.let { return it to true }
        return null
    }

    fun lookup(raw: String): Entry {
        val w = normalize(raw)
        if (w.isEmpty()) return Entry(raw, null, false, emptyList(), false)
        val ipa = find(ipaMap, w)
        val es = find(esMap, w)
        return Entry(w, ipa?.first, ipa?.second ?: false, es?.first?.split('|').orEmpty(), es?.second ?: false)
    }

    /** Words for a partial or misspelled entry: prefix matches first, then close spellings. */
    fun suggest(raw: String, limit: Int = 8): List<String> {
        val q = normalize(raw)
        if (q.length < 2) return emptyList()
        val keys = sorted
        var i = keys.binarySearch(q).let { if (it < 0) -it - 1 else it }
        val out = mutableListOf<String>()
        while (i < keys.size && keys[i].startsWith(q) && out.size < limit) { if (keys[i] != q) out += keys[i]; i++ }
        if (out.size < limit && q.length >= 4) {
            keys.asSequence().filter { it.firstOrNull() == q[0] && kotlin.math.abs(it.length - q.length) <= 2 && it != q && it !in out }
                .map { it to editDistance(q, it) }.filter { it.second <= 2 }.sortedBy { it.second }
                .take(limit - out.size).forEach { out += it.first }
        }
        return out
    }
}

fun editDistance(a: String, b: String): Int {
    val d = IntArray(b.length + 1) { it }
    for (i in 1..a.length) {
        var prev = d[0]; d[0] = i
        for (j in 1..b.length) {
            val tmp = d[j]
            d[j] = minOf(d[j] + 1, d[j - 1] + 1, prev + if (a[i - 1] == b[j - 1]) 0 else 1)
            prev = tmp
        }
    }
    return d[b.length]
}

/** Two-character symbols that are one sound; everything else is one character. */
private val DIGRAPHS = setOf("aɪ", "aʊ", "ɔɪ", "oʊ", "eɪ", "tʃ", "dʒ")

fun ipaSymbols(ipa: String): List<String> {
    val out = mutableListOf<String>(); var i = 0
    while (i < ipa.length) {
        val two = if (i + 2 <= ipa.length) ipa.substring(i, i + 2) else ""
        val s = if (two in DIGRAPHS) two else ipa.substring(i, i + 1)
        out += s; i += s.length
    }
    return out
}

// ---------------------------------------------------------------- numbers → words (from Pronunciation Coach)

private val ONES = listOf("zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten",
    "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen")
private val TENS = listOf("", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety")

private fun under100(n: Int): String = if (n < 20) ONES[n] else TENS[n / 10] + if (n % 10 > 0) "-" + ONES[n % 10] else ""
private fun under1000(n: Int): String {
    val h = n / 100; val r = n % 100
    return listOfNotNull(if (h > 0) ONES[h] + " hundred" else null, if (r > 0 || h == 0) under100(r) else null).joinToString(" ")
}

fun numberWords(n: Long): String {
    if (n < 1000) return under1000(n.toInt())
    val parts = mutableListOf<String>()
    var rest = n
    for ((size, name) in listOf(1_000_000_000L to "billion", 1_000_000L to "million", 1000L to "thousand")) {
        if (rest >= size) { parts += under1000((rest / size).toInt()) + " " + name; rest %= size }
    }
    if (rest > 0) parts += under1000(rest.toInt())
    return parts.joinToString(" ")
}

private fun yearWords(n: Int): String {
    val hi = n / 100; val lo = n % 100
    return when {
        lo == 0 -> under100(hi) + " hundred"
        lo < 10 -> under100(hi) + " oh " + ONES[lo]
        else -> under100(hi) + " " + under100(lo)
    }
}

private fun ordinal(words: String): String {
    val i = maxOf(words.lastIndexOf(' '), words.lastIndexOf('-'))
    val head = words.substring(0, i + 1); val last = words.substring(i + 1)
    val o = when (last) {
        "one" -> "first"; "two" -> "second"; "three" -> "third"; "five" -> "fifth"; "eight" -> "eighth"
        "nine" -> "ninth"; "twelve" -> "twelfth"
        else -> if (last.endsWith("y")) last.dropLast(1) + "ieth" else last + "th"
    }
    return head + o
}

private val NUMBER = Regex("""(\d[\d,]*(?:\.\d+)?)(st|nd|rd|th|s)?""")

/** "In 1879, 2,500 people (35%) came 3rd" → words, the way a reader says it. */
fun spokenForm(text: String): String {
    var t = text.replace("%", " percent").replace("&", " and ")
    t = NUMBER.replace(t) { m ->
        val raw = m.groupValues[1]; val suf = m.groupValues[2]
        val digits = raw.replace(",", "")
        val plural = { w: String -> if (suf != "s") w else if (w.endsWith("y")) w.dropLast(1) + "ies" else w + "s" }
        try {
            val year = "," !in raw && digits.length == 4 && digits.toInt().let { it in 1100..1999 || it in 2010..2099 }
            when {
                "." in digits -> numberWords(digits.substringBefore('.').toLong()) + " point " +
                    digits.substringAfter('.').map { ONES[it - '0'] }.joinToString(" ")
                suf in setOf("st", "nd", "rd", "th") -> ordinal(numberWords(digits.toLong()))
                year -> plural(yearWords(digits.toInt()))
                else -> plural(numberWords(digits.toLong()))
            }
        } catch (e: Exception) { m.value }
    }
    return t.replace(Regex("\\s{2,}"), " ").trim()
}

private val NON_TOKEN = Regex("[^a-z0-9']")
private val SPLIT = Regex("[\\s\\-–—/]+")

fun normalizeToken(s: String) = s.lowercase().replace('’', '\'').replace(NON_TOKEN, "").trim('\'')

/** What the recognizer should hear for one printed word: "1879," → [eighteen, seventy, nine]; "—" → []. */
fun tokensOf(text: String): List<String> =
    (if (text.any { it.isDigit() || it == '%' || it == '&' }) spokenForm(text) else text).split(SPLIT).map(::normalizeToken).filter { it.isNotEmpty() }
