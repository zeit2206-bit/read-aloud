package com.zeit2206.readaloud

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** A contents entry: title and the paragraph it starts at. */
data class TocEntry(val title: String, val para: Int, val level: Int)

/**
 * A book as plain paragraphs, plus a flat word index across the whole book.
 * Word i lives in paragraph [wPara]`[i]` at characters [wStart]`[i]` until [wEnd]`[i]`; [keys]`[i]` is what
 * the recognizer must hear for it (empty for things like "—", which are skipped).
 */
class Book(val id: String, val title: String, val paras: List<String>, val paraPage: IntArray, val toc: List<TocEntry>) {
    val paraFirstWord = IntArray(paras.size + 1)
    val wPara: IntArray
    val wStart: IntArray
    val wEnd: IntArray
    val keys: List<List<String>>
    val size get() = wPara.size

    init {
        val p = ArrayList<Int>(); val s = ArrayList<Int>(); val e = ArrayList<Int>(); val k = ArrayList<List<String>>()
        val word = Regex("\\S+")
        paras.forEachIndexed { pi, text ->
            paraFirstWord[pi] = p.size
            for (m in word.findAll(text)) { p += pi; s += m.range.first; e += m.range.last + 1; k += tokensOf(m.value) }
        }
        paraFirstWord[paras.size] = p.size
        wPara = p.toIntArray(); wStart = s.toIntArray(); wEnd = e.toIntArray(); keys = k
    }

    fun wordText(i: Int) = paras[wPara[i]].substring(wStart[i], wEnd[i])

    /** First word at or after [i] that has something to say. */
    fun speakable(i: Int): Int { var j = i; while (j < size && keys[j].isEmpty()) j++; return j }

    fun toJson() = JSONObject().apply {
        put("title", title)
        put("paras", JSONArray(paras))
        put("pages", JSONArray(paraPage.toList()))
        put("toc", JSONArray(toc.map { JSONObject().put("t", it.title).put("p", it.para).put("l", it.level) }))
    }

    companion object {
        fun fromJson(id: String, o: JSONObject): Book {
            val paras = o.getJSONArray("paras").let { a -> List(a.length()) { a.getString(it) } }
            val pages = o.getJSONArray("pages").let { a -> IntArray(a.length()) { a.getInt(it) } }
            val toc = o.optJSONArray("toc")?.let { a -> List(a.length()) { a.getJSONObject(it).let { t -> TocEntry(t.getString("t"), t.getInt("p"), t.optInt("l")) } } }.orEmpty()
            return Book(id, o.getString("title"), paras, pages, toc)
        }
    }
}

// ---------------------------------------------------------------- PDF → paragraphs

object PdfImport {
    private val PAGE_NUMBER = Regex("^\\W*(\\d{1,4}|[ivxlcdm]{1,6})\\W*$", RegexOption.IGNORE_CASE)
    private val SENTENCE_END = Regex("[.?!:][\"”’)]*$")

    fun displayName(context: Context, uri: Uri): String =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: uri.lastPathSegment ?: "book.pdf"

    /** Reads the whole PDF into a [Book]. [progress] gets (page, pages). Slow for big books: run off the main thread. */
    fun read(context: Context, uri: Uri, id: String, progress: (Int, Int) -> Unit): Book {
        PDFBoxResourceLoader.init(context.applicationContext)
        val name = displayName(context, uri).removeSuffix(".pdf").removeSuffix(".PDF")
        // Big books run out of memory if opened once: copy to a file, then open it in batches of pages,
        // closing it in between so PDFBox's caches are released.
        val tmp = File(context.cacheDir, "import.pdf")
        try {
            context.contentResolver.openInputStream(uri)!!.use { input -> tmp.outputStream().use { input.copyTo(it) } }
            fun open() = PDDocument.load(tmp, MemoryUsageSetting.setupTempFileOnly())
            val (n, outline, docTitle) = open().use { d -> Triple(d.numberOfPages, outline(d), d.documentInformation?.title) }
            val pages = ArrayList<List<String>>(n)
            var from = 1
            while (from <= n) {
                val to = minOf(n, from + 39)
                open().use { doc ->
                    val stripper = PDFTextStripper().apply { paragraphStart = "\u0001"; sortByPosition = true }
                    for (i in from..to) {
                        progress(i, n)
                        stripper.startPage = i; stripper.endPage = i
                        pages += stripper.getText(doc).lines().map { it.trim() }.filter { it.isNotEmpty() && it != "\u0001" }
                    }
                }
                from = to + 1
            }
            val (paras, paraPage) = paragraphs(dropRunningLines(pages))
            val toc = outline.mapNotNull { (title, page, level) ->
                val p = paraPage.indexOfFirst { it >= page }
                if (p < 0) null else TocEntry(title, p, level)
            }
            val title = docTitle?.takeIf { it.isNotBlank() && it.length < 120 } ?: name
            return Book(id, title, paras, paraPage.toIntArray(), toc)
        } finally { tmp.delete() }
    }

    private fun key(line: String) = line.removePrefix("\u0001").lowercase().replace(Regex("\\d+"), "#").replace(Regex("\\s+"), " ")

    /** Removes running headers/footers (lines repeated at the top or bottom of many pages), page numbers and URLs. */
    private fun dropRunningLines(pages: List<List<String>>): List<List<String>> {
        val counts = HashMap<String, Int>()
        for (lines in pages) (lines.take(2) + lines.takeLast(2)).map(::key).toSet().forEach { counts[it] = (counts[it] ?: 0) + 1 }
        val limit = maxOf(3, pages.size * 15 / 100)
        val running = if (pages.size >= 6) counts.filterValues { it >= limit }.keys else emptySet()
        return pages.map { lines ->
            lines.filterIndexed { i, l ->
                val edge = i < 2 || i >= lines.size - 2
                val bare = l.removePrefix("\u0001")
                !(edge && key(l) in running) && !(edge && PAGE_NUMBER.matches(bare)) && !bare.contains("http") && !bare.contains("www.")
            }
        }
    }

    /** Joins lines into paragraphs: a paragraph ends on a short line that closes a sentence, before an indented
     *  start (PDFBox marks those with \u0001), or after a heading-like short line. */
    private fun paragraphs(pages: List<List<String>>): Pair<List<String>, List<Int>> {
        val lens = pages.flatten().map { it.length }.filter { it > 20 }.sorted()
        val median = if (lens.isEmpty()) 60 else lens[lens.size / 2]
        val paras = mutableListOf<String>(); val paraPage = mutableListOf<Int>()
        val cur = StringBuilder(); var curPage = 0; var curLines = 0
        fun flush() {
            val t = cur.toString().replace(Regex("\\s+"), " ").trim()
            if (t.any { it.isLetter() }) { paras += t; paraPage += curPage }
            cur.clear(); curLines = 0
        }
        pages.forEachIndexed { pi, lines ->
            for (raw in lines) {
                val marked = raw.startsWith("\u0001")
                val line = raw.removePrefix("\u0001").trim()
                if (line.isEmpty()) continue
                if (marked && cur.isNotEmpty() && SENTENCE_END.containsMatchIn(cur.trimEnd())) flush()
                if (cur.isEmpty()) curPage = pi
                if (cur.endsWith("-") && line.first().isLowerCase()) cur.setLength(cur.length - 1) else if (cur.isNotEmpty()) cur.append(' ')
                cur.append(line); curLines++
                val short = line.length < median * 3 / 4
                val heading = curLines == 1 && line.length < median * 3 / 5 && !SENTENCE_END.containsMatchIn(line) &&
                    !line.endsWith(",") && !line.endsWith("-") && line.first().isUpperCase()
                if ((short && SENTENCE_END.containsMatchIn(line)) || heading) flush()
            }
        }
        flush()
        return paras to paraPage
    }

    /** PDF bookmarks, two levels deep: (title, 0-based page, level). */
    private fun outline(doc: PDDocument): List<Triple<String, Int, Int>> = try {
        val out = mutableListOf<Triple<String, Int, Int>>()
        fun walk(item: PDOutlineItem, level: Int) {
            val page = item.findDestinationPage(doc)?.let { doc.pages.indexOf(it) } ?: -1
            if (page >= 0 && !item.title.isNullOrBlank()) out += Triple(item.title.trim(), page, level)
            if (level < 1) item.children().forEach { walk(it, level + 1) }
        }
        doc.documentCatalog.documentOutline?.children()?.forEach { walk(it, 0) }
        out
    } catch (e: Exception) { emptyList() }
}

// ---------------------------------------------------------------- storage

/** Word status per book, one byte per word. */
object St { const val UNREAD: Byte = 0; const val READ: Byte = 1; const val SKIPPED: Byte = 2 }

data class BookInfo(val id: String, val title: String, val words: Int, val read: Int, val cursor: Int)

data class Saved(val word: String, val ipa: String?, val es: String)

/** All-time reading totals. Accuracy: words said right out of all words passed (right + skipped). */
data class Stats(val words: Int, val skipped: Int, val seconds: Long, val todayWords: Int, val todaySeconds: Long, val streak: Int) {
    val accuracy get() = if (words + skipped == 0) null else 100 * words / (words + skipped)
}

fun duration(seconds: Long): String = when {
    seconds < 60 -> "${seconds}s"
    seconds < 3600 -> "${seconds / 60} min"
    else -> "${seconds / 3600} h ${seconds % 3600 / 60} min"
}

fun clock(seconds: Long) = "%d:%02d".format(seconds / 60, seconds % 60)

class Store(context: Context) {
    private val dir = File(context.filesDir, "books").apply { mkdirs() }
    private val prefs = context.getSharedPreferences("store", Context.MODE_PRIVATE)

    fun bookIds(): List<String> = dir.listFiles { f -> f.name.endsWith(".json") }.orEmpty().sortedByDescending { it.lastModified() }.map { it.name.removeSuffix(".json") }
    fun has(id: String) = File(dir, "$id.json").exists()
    fun save(book: Book) = File(dir, "${book.id}.json").writeText(book.toJson().toString())
    fun load(id: String) = Book.fromJson(id, JSONObject(File(dir, "$id.json").readText()))

    fun delete(id: String) {
        File(dir, "$id.json").delete(); File(dir, "$id.status").delete()
        prefs.edit().remove("cursor_$id").remove("title_$id").remove("words_$id").remove("read_$id").apply()
    }

    fun status(id: String, size: Int): ByteArray =
        File(dir, "$id.status").takeIf { it.exists() }?.readBytes()?.copyOf(size) ?: ByteArray(size)

    fun saveProgress(book: Book, status: ByteArray, cursor: Int) {
        File(dir, "${book.id}.status").writeBytes(status)
        prefs.edit().putInt("cursor_${book.id}", cursor).putString("title_${book.id}", book.title)
            .putInt("words_${book.id}", book.size).putInt("read_${book.id}", status.count { it == St.READ }).apply()
    }

    fun cursor(id: String) = prefs.getInt("cursor_$id", 0)

    fun info(id: String) = BookInfo(id, prefs.getString("title_$id", null) ?: id, prefs.getInt("words_$id", 0), prefs.getInt("read_$id", 0), cursor(id))

    // ---- reading sessions: one entry per reader visit with something done, for the home stats

    /** Writes (or updates) session [id] — called while reading, so nothing is lost if the app is killed. */
    @Synchronized
    fun saveSession(id: Long, bookId: String, words: Int, skipped: Int, seconds: Long) {
        if (words == 0 && skipped == 0 && seconds < 10) return
        val a = JSONArray(prefs.getString("sessions", "[]"))
        val entry = JSONObject().put("id", id).put("b", bookId).put("d", java.time.LocalDate.now().toString()).put("w", words).put("k", skipped).put("s", seconds)
        val i = (0 until a.length()).firstOrNull { a.getJSONObject(it).optLong("id") == id }
        if (i != null) a.put(i, entry) else a.put(entry)
        prefs.edit().putString("sessions", a.toString()).putLong("opened_$bookId", System.currentTimeMillis()).apply()
    }

    fun touch(bookId: String) = prefs.edit().putLong("opened_$bookId", System.currentTimeMillis()).apply()
    fun lastOpened(bookId: String) = prefs.getLong("opened_$bookId", 0L)

    fun stats(): Stats {
        val a = JSONArray(prefs.getString("sessions", "[]"))
        var words = 0; var skipped = 0; var seconds = 0L; var todayWords = 0; var todaySeconds = 0L
        val days = HashSet<String>(); val today = java.time.LocalDate.now()
        for (i in 0 until a.length()) {
            val o = a.getJSONObject(i)
            words += o.getInt("w"); skipped += o.getInt("k"); seconds += o.getLong("s")
            if (o.getInt("w") > 0) days += o.getString("d")
            if (o.getString("d") == today.toString()) { todayWords += o.getInt("w"); todaySeconds += o.getLong("s") }
        }
        // Streak: consecutive days with reading, ending today (or yesterday, so it isn't lost before today's reading).
        var d = if (today.toString() in days) today else today.minusDays(1)
        var streak = 0
        while (d.toString() in days) { streak++; d = d.minusDays(1) }
        return Stats(words, skipped, seconds, todayWords, todaySeconds, streak)
    }

    fun saved(): List<Saved> = JSONArray(prefs.getString("saved", "[]")).let { a ->
        List(a.length()) { a.getJSONObject(it).let { o -> Saved(o.getString("w"), o.optString("i").ifEmpty { null }, o.optString("e")) } }
    }

    fun setSaved(list: List<Saved>) = prefs.edit().putString("saved", JSONArray(list.map {
        JSONObject().put("w", it.word).put("i", it.ipa ?: "").put("e", it.es)
    }).toString()).apply()
}
