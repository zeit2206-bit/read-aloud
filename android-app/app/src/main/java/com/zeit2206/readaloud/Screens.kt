package com.zeit2206.readaloud

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ---------------------------------------------------------------- small parts

@Composable
fun IconBtn(icon: ImageVector, desc: String, tint: Color = P.ink, onClick: () -> Unit) =
    IconButton(onClick = onClick) { Icon(icon, desc, tint = tint, modifier = Modifier.size(22.dp)) }

@Composable
fun Pill(text: String, icon: ImageVector? = null, filled: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier.clip(RoundedCornerShape(50)).background(if (filled) P.primary else P.primarySoft).clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (icon != null) Icon(icon, null, tint = if (filled) P.onPrimary else P.primary, modifier = Modifier.size(18.dp))
        Text(text, style = lx(14, 500, if (filled) P.onPrimary else P.primary))
    }
}

@Composable
fun TopBar(title: String, sub: String? = null, back: (() -> Unit)?, actions: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (back != null) IconBtn(Ic.back, "Back", onClick = back) else Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = lx(18, 600), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sub != null) Text(sub, style = lx(12, 400, P.sub), maxLines = 1)
        }
        actions()
        val context = LocalContext.current
        IconBtn(if (P.dark) Ic.sun else Ic.moon, if (P.dark) "Light theme" else "Dark theme") {
            P.dark = !P.dark
            context.getSharedPreferences("store", android.content.Context.MODE_PRIVATE).edit().putBoolean("dark", P.dark).apply()
        }
    }
}

// ---------------------------------------------------------------- library

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(store: Store, open: (String) -> Unit, saved: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var books by remember { mutableStateOf(store.bookIds().map(store::info)) }
    var importing by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableFloatStateOf(0f) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf<BookInfo?>(null) }

    fun import(uri: Uri) {
        val name = PdfImport.displayName(context, uri)
        val id = name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(40) + "-" + Integer.toHexString(name.hashCode())
        if (store.has(id)) { open(id); return }
        importing = name; progress = 0f; error = null
        scope.launch {
            try {
                val book = withContext(Dispatchers.Default) {
                    // A book prepared on the PC (pipeline/book.py) is JSON; anything else is read as a PDF on the phone.
                    val head = context.contentResolver.openInputStream(uri)!!.use { it.read() }
                    if (head == '{'.code) Book.fromJson(id, org.json.JSONObject(context.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }))
                    else PdfImport.read(context, uri, id) { p, n -> progress = p.toFloat() / n }
                }
                if (book.size == 0) error("No text found. Is this a scanned PDF?")
                withContext(Dispatchers.IO) { store.save(book); store.saveProgress(book, ByteArray(book.size), 0) }
                importing = null
                open(id)
            } catch (e: Throwable) {
                importing = null
                error = "Couldn't read that PDF: ${e.message ?: e.javaClass.simpleName}"
            }
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) import(uri) }
    LaunchedEffect(Debug.pdf) { Debug.pdf?.let { Debug.pdf = null; import(it) } }

    val stats = remember { store.stats() }
    val savedCount = remember { store.saved().size }
    val sorted = remember(books) { books.sortedByDescending { store.lastOpened(it.id) } }
    val current = sorted.firstOrNull()

    Column(Modifier.fillMaxSize().background(P.bg)) {
        TopBar("Read Aloud", greeting(), back = null)
        LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { ScoreCard(stats) }

            if (current != null) item {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(P.card)
                        .combinedClickable(onClick = { open(current.id) }, onLongClick = { confirmDelete = current }).padding(18.dp)
                ) {
                    Text("CONTINUE READING", style = lx(11, 600, P.primary).copy(letterSpacing = 1.sp))
                    Spacer(Modifier.height(6.dp))
                    Text(current.title, style = lx(19, 600), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(14.dp))
                    BookProgress(current)
                    Spacer(Modifier.height(16.dp))
                    Pill("Continue", Ic.mic, filled = true) { open(current.id) }
                }
            }

            if (sorted.size > 1) {
                item { SectionTitle("Your books") }
                itemsIndexed(sorted.drop(1), key = { _, b -> b.id }) { _, b ->
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(P.card)
                            .combinedClickable(onClick = { open(b.id) }, onLongClick = { confirmDelete = b }).padding(16.dp)
                    ) {
                        Text(b.title, style = lx(16, 600), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(10.dp))
                        BookProgress(b)
                    }
                }
            }

            item {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(P.card).clickable(onClick = saved).padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(40.dp).clip(CircleShape).background(P.primarySoft), contentAlignment = Alignment.Center) {
                        Icon(Ic.star, null, tint = P.primary, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Saved words", style = lx(15, 600))
                        Text(if (savedCount == 0) "Look up any word" else "$savedCount words to review", style = lx(12, 400, P.sub))
                    }
                    Icon(Ic.chevron, null, tint = P.muted, modifier = Modifier.size(20.dp))
                }
            }

            item {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).border(1.5.dp, P.line, RoundedCornerShape(16.dp)).padding(16.dp)) {
                    Text(if (books.isEmpty()) "Add your first book" else "Add a book", style = lx(15, 600))
                    Text("Best: a .readaloud.json made on the PC (cleaner text). Or open a PDF here.", style = lx(12, 400, P.sub),
                        modifier = Modifier.padding(top = 2.dp, bottom = 12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Pill("Open a book", Ic.plus, filled = true) { picker.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) }
                        Pill("Open a PDF") { picker.launch(arrayOf("application/pdf")) }
                    }
                    importing?.let {
                        Spacer(Modifier.height(14.dp))
                        Text("Reading $it…", style = lx(14, 500))
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth(), color = P.primary, trackColor = P.primarySoft)
                        Text("Big books take a minute. This happens only once.", style = lx(12, 400, P.sub), modifier = Modifier.padding(top = 6.dp))
                    }
                    error?.let { Text(it, style = lx(13, 400, P.accent), modifier = Modifier.padding(top = 10.dp)) }
                }
            }
        }
    }

    confirmDelete?.let { b ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Remove book?", style = lx(18, 600)) },
            text = { Text("“${b.title}” and its reading progress will be removed from the app. Saved words stay.", style = lx(14)) },
            confirmButton = { TextButton({ store.delete(b.id); books = store.bookIds().map(store::info); confirmDelete = null }) { Text("Remove", color = P.accent) } },
            dismissButton = { TextButton({ confirmDelete = null }) { Text("Cancel") } },
        )
    }
}

private fun greeting(): String = when (java.time.LocalTime.now().hour) {
    in 5..11 -> "Good morning"
    in 12..18 -> "Good afternoon"
    else -> "Good evening"
}

@Composable
private fun SectionTitle(text: String) = Text(text, style = lx(15, 600, P.sub), modifier = Modifier.padding(top = 4.dp))

@Composable
private fun BookProgress(b: BookInfo) {
    val pct = if (b.words == 0) 0f else b.read.toFloat() / b.words
    LinearProgressIndicator(progress = { pct }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)), color = P.good, trackColor = P.line)
    Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Text("${"%,d".format(b.read)} of ${"%,d".format(b.words)} words", style = lx(12, 400, P.sub), modifier = Modifier.weight(1f))
        Text("${(pct * 100).toInt()}%", style = lx(12, 600, P.good))
    }
}

/** The global score: today's reading on top, all-time totals below. */
@Composable
private fun ScoreCard(s: Stats) {
    val brush = androidx.compose.ui.graphics.Brush.linearGradient(listOf(P.primary, P.primary.copy(alpha = 0.78f)))
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(brush).padding(20.dp)) {
        Text("TODAY", style = lx(11, 600, P.onPrimary.copy(alpha = 0.75f)).copy(letterSpacing = 1.sp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text("%,d".format(s.todayWords), style = lx(40, 700, P.onPrimary))
            Text("  words", style = lx(16, 500, P.onPrimary.copy(alpha = 0.85f)), modifier = Modifier.padding(bottom = 8.dp))
            Spacer(Modifier.weight(1f))
            Text(duration(s.todaySeconds), style = lx(16, 500, P.onPrimary.copy(alpha = 0.85f)), modifier = Modifier.padding(bottom = 8.dp))
        }
        Spacer(Modifier.height(14.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(P.onPrimary.copy(alpha = 0.2f)))
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth()) {
            Stat("%,d".format(s.words), "words read", Modifier.weight(1f))
            Stat(duration(s.seconds), "reading", Modifier.weight(1f))
            Stat("${s.streak}", if (s.streak == 1) "day streak" else "days streak", Modifier.weight(1f))
            Stat(s.accuracy?.let { "$it%" } ?: "–", "accuracy", Modifier.weight(1f))
        }
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier) = Column(modifier) {
    Text(value, style = lx(17, 600, P.onPrimary), maxLines = 1)
    Text(label, style = lx(11, 400, P.onPrimary.copy(alpha = 0.75f)), maxLines = 1)
}

// ---------------------------------------------------------------- reader

@Composable
fun ReaderScreen(store: Store, voice: Voice, id: String, back: () -> Unit, saved: () -> Unit) {
    var book by remember { mutableStateOf<Book?>(null) }
    LaunchedEffect(id) { book = withContext(Dispatchers.IO) { store.load(id) } }
    val b = book
    if (b == null) {
        Box(Modifier.fillMaxSize().background(P.bg), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = P.primary) }
        return
    }
    Reader(store, voice, b, back, saved)
}

@Composable
private fun Reader(store: Store, voice: Voice, book: Book, back: () -> Unit, saved: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tracker = remember(book.id) { Tracker(book, store.status(book.id, book.size), store.cursor(book.id)) }
    var rev by remember { mutableIntStateOf(0) }            // bumps whenever word colours change
    var listening by remember { mutableStateOf(false) }
    var heard by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var lookup by remember { mutableStateOf<String?>(null) }
    var showContents by remember { mutableStateOf(false) }
    val list = rememberLazyListState(initialFirstVisibleItemIndex = book.wPara.getOrElse(tracker.cursor) { 0 }.coerceAtLeast(0))
    val layouts = remember { HashMap<Int, TextLayoutResult>() }
    var unsaved by remember { mutableIntStateOf(0) }

    fun persist() {
        val status = tracker.status.copyOf(); val c = tracker.cursor
        scope.launch(Dispatchers.IO) { store.saveProgress(book, status, c) }
        unsaved = 0
    }
    fun changed() { rev++; unsaved++ }

    val listener = remember(book.id) {
        Listener(context.applicationContext,
            onWords = { if (tracker.hear(it)) changed() },
            eager = { tracker.isAwaited(it) },
            onHeard = { heard = it },
            onError = { message = it; listening = false })
    }
    // Session time counts only while the mic is on.
    var timeBefore by remember { mutableLongStateOf(0L) }       // ms from earlier mic-on stretches
    var since by remember { mutableLongStateOf(0L) }            // when the current stretch began (0 = mic off)
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(listening) { while (listening) { now = System.currentTimeMillis(); kotlinx.coroutines.delay(1000) } }
    fun sessionSeconds() = (timeBefore + if (since > 0) System.currentTimeMillis() - since else 0) / 1000

    fun setListening(on: Boolean) {
        if (on == listening && on == listener.active) return
        listening = on
        if (on) { message = null; since = System.currentTimeMillis(); listener.start() }
        else { if (since > 0) timeBefore += System.currentTimeMillis() - since; since = 0; listener.stop() }
    }

    var sessionId by remember { mutableLongStateOf(System.currentTimeMillis()) }
    fun saveSession() = store.saveSession(sessionId, book.id, tracker.sessionRead, tracker.sessionSkipped, sessionSeconds())

    /** Logs this session for the home stats, then starts a fresh one (called when leaving the reader or the app). */
    fun endSession() {
        saveSession()
        tracker.sessionRead = 0; tracker.sessionSkipped = 0; timeBefore = 0; if (since > 0) since = System.currentTimeMillis()
        sessionId = System.currentTimeMillis()
    }
    // Save once the highlight has been still for 2 s (the app may be killed without warning).
    LaunchedEffect(rev) { if (unsaved > 0) { kotlinx.coroutines.delay(2000); persist(); saveSession() } }
    // While the mic is on, keep the session log current every 15 s.
    LaunchedEffect(listening) { while (listening) { kotlinx.coroutines.delay(15_000); saveSession() } }

    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) setListening(true) else message = "The app needs the microphone to follow your reading."
    }
    fun toggleMic() {
        when {
            listening -> setListening(false)
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED -> setListening(true)
            else -> askMic.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // Stop listening and save when the app goes to the background or the reader closes.
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, book.id) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_STOP) { setListening(false); persist(); endSession() } }
        owner.lifecycle.addObserver(obs)
        store.touch(book.id)
        Debug.say = { text -> if (tracker.hear(tokensOf(text))) changed(); heard = text }
        onDispose { owner.lifecycle.removeObserver(obs); setListening(false); persist(); endSession(); Debug.say = null }
    }

    // The word popup uses the speaker, so the mic pauses while it is open.
    var resumeAfterLookup by remember { mutableStateOf(false) }
    fun openLookup(word: String) { resumeAfterLookup = listening; if (listening) setListening(false); lookup = word }
    fun closeLookup() { lookup = null; if (resumeAfterLookup) setListening(true); resumeAfterLookup = false }

    // Keep the highlighted word in view: scroll when it drops into the bottom part of the screen.
    LaunchedEffect(tracker.cursor, rev) {
        if (tracker.done) return@LaunchedEffect
        val para = book.wPara[tracker.cursor]
        val info = list.layoutInfo.visibleItemsInfo.firstOrNull { it.index == para }
        val layout = layouts[para]
        if (info == null || layout == null) { list.animateScrollToItem(para); return@LaunchedEffect }
        val y = info.offset + layout.getBoundingBox(book.wStart[tracker.cursor]).top
        val end = list.layoutInfo.viewportEndOffset
        if (y > end * 0.65f || y < 0) list.animateScrollBy(y - end * 0.3f)
    }

    val cursorPara = book.wPara.getOrElse(tracker.cursor) { book.paras.size - 1 }
    val page = book.paraPage.getOrElse(cursorPara) { 0 } + 1
    val readCount = remember(rev) { tracker.status.count { it == St.READ } }

    Column(Modifier.fillMaxSize().background(P.bg)) {
        TopBar(book.title, "Page $page · ${(100L * readCount / book.size.coerceAtLeast(1))}% read", back = back) {
            IconBtn(Ic.list, "Contents") { showContents = true }
            IconBtn(Ic.star, "Saved words", onClick = saved)
        }
        LazyColumn(Modifier.weight(1f), state = list, contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 8.dp, bottom = 120.dp)) {
            itemsIndexed(book.paras) { pi, _ ->
                Paragraph(book, pi, tracker, rev, layouts,
                    onTap = { w -> tracker.moveTo(w); changed() },
                    onLookup = { w -> openLookup(book.wordText(w)) })
            }
        }
        // Bottom bar: what was heard, skip, mic.
        Column(Modifier.fillMaxWidth().background(P.card).navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
            val status = message ?: when {
                tracker.done -> "You reached the end of the book."
                listening && heard.isNotBlank() -> "Heard: “$heard”"
                listening -> "Listening… read the highlighted word."
                else -> "Tap the mic and read aloud. Tap a word to start there; double-tap or hold for its meaning."
            }
            Text(status, style = lx(13, 400, if (message != null) P.accent else P.sub), maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pill("Skip", Ic.skip) { tracker.skip(); changed() }
                // Session counter: words said right and reading time, since the book was opened.
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center) {
                    // Reading rev / now subscribes this row to word changes and the 1-second tick.
                    val words = rev.let { tracker.sessionRead }
                    val secs = now.let { sessionSeconds() }
                    Counter("$words", "words")
                    Spacer(Modifier.width(22.dp))
                    Counter(clock(secs), "time")
                }
                Box(
                    Modifier.size(64.dp).clip(CircleShape).background(if (listening) P.accent else P.primary).clickable { toggleMic() },
                    contentAlignment = Alignment.Center,
                ) { Icon(if (listening) Ic.stop else Ic.mic, if (listening) "Stop" else "Read aloud", tint = P.onPrimary, modifier = Modifier.size(28.dp)) }
            }
        }
    }

    lookup?.let { WordSheet(it, store, voice, onDismiss = { closeLookup() }) }

    if (showContents) ContentsDialog(book, onDismiss = { showContents = false }) { para ->
        showContents = false
        tracker.moveTo(book.paraFirstWord[para]); changed()
        scope.launch { list.scrollToItem(para) }
    }
}

@Composable
private fun Counter(value: String, label: String) = Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Text(value, style = lx(18, 600))
    Text(label, style = lx(11, 400, P.sub))
}

@Composable
private fun Paragraph(book: Book, pi: Int, tracker: Tracker, rev: Int, layouts: HashMap<Int, TextLayoutResult>, onTap: (Int) -> Unit, onLookup: (Int) -> Unit) {
    val first = book.paraFirstWord[pi]; val last = book.paraFirstWord[pi + 1]
    val cursor = tracker.cursor
    val text: AnnotatedString = remember(pi, rev, cursor) {
        buildAnnotatedString {
            append(book.paras[pi])
            for (w in first until last) {
                val style = when {
                    w == cursor -> SpanStyle(color = P.current)
                    tracker.status[w] == St.READ -> SpanStyle(color = P.ink)
                    tracker.status[w] == St.SKIPPED -> SpanStyle(color = P.weak, textDecoration = TextDecoration.Underline)
                    else -> null
                }
                if (style != null) addStyle(style, book.wStart[w], book.wEnd[w])
            }
        }
    }
    // A short heading-like paragraph (no sentence end) is shown bolder.
    val para = book.paras[pi]
    val heading = para.length < 70 && !para.trimEnd().last().let { it in ".?!:,;\"”’)" }
    val tap by rememberUpdatedState(onTap); val look by rememberUpdatedState(onLookup)
    fun wordAt(pos: Offset): Int? {
        val layout = layouts[pi] ?: return null
        val off = layout.getOffsetForPosition(pos)
        for (w in first until last) if (off < book.wEnd[w]) return if (off >= book.wStart[w] - 1) w else null
        return null
    }
    Text(
        text,
        style = if (heading) lx(20, 600, P.unread).copy(lineHeight = 28.sp) else lx(19, 400, P.unread).copy(lineHeight = 31.sp),
        onTextLayout = { layouts[pi] = it },
        modifier = Modifier.fillMaxWidth().padding(vertical = if (heading) 14.dp else 8.dp).pointerInput(pi) {
            detectTapGestures(
                onTap = { p -> wordAt(p)?.let(tap) },
                onDoubleTap = { p -> wordAt(p)?.let(look) },
                onLongPress = { p -> wordAt(p)?.let(look) },
            )
        },
    )
}

@Composable
private fun ContentsDialog(book: Book, onDismiss: () -> Unit, go: (Int) -> Unit) {
    var pageText by remember { mutableStateOf("") }
    val pages = (book.paraPage.lastOrNull() ?: 0) + 1
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onDismiss) { Text("Close") } },
        title = { Text("Contents", style = lx(18, 600)) },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Go to page", style = lx(14))
                    BasicTextField(pageText, { pageText = it.filter(Char::isDigit).take(5) }, singleLine = true, textStyle = lx(15),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(72.dp).border(1.dp, P.line, RoundedCornerShape(8.dp)).padding(8.dp))
                    Text("of $pages", style = lx(13, 400, P.sub))
                    TextButton({
                        val p = pageText.toIntOrNull() ?: return@TextButton
                        val para = book.paraPage.indexOfFirst { it >= p - 1 }
                        if (para >= 0) go(para)
                    }) { Text("Go") }
                }
                if (book.toc.isEmpty()) Text("This PDF has no chapter list.", style = lx(13, 400, P.sub), modifier = Modifier.padding(top = 8.dp))
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    itemsIndexed(book.toc) { _, t ->
                        Text(t.title, style = lx(if (t.level == 0) 15 else 14, if (t.level == 0) 500 else 400, if (t.level == 0) P.ink else P.sub),
                            modifier = Modifier.fillMaxWidth().clickable { go(t.para) }.padding(start = (t.level * 16).dp, top = 10.dp, bottom = 10.dp))
                    }
                }
            }
        },
    )
}

// ---------------------------------------------------------------- word popup (the extension, on the phone)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordSheet(raw: String, store: Store, voice: Voice, onDismiss: () -> Unit) {
    val entry = remember(raw) { Dict.lookup(raw) }
    var savedList by remember { mutableStateOf(store.saved()) }
    val isSaved = savedList.any { it.word == entry.word }
    var symbol by remember { mutableStateOf<String?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = P.card) {
        Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 32.dp)) {
            Text(entry.word, style = lx(28, 600))
            Spacer(Modifier.height(6.dp))
            if (!Dict.ready) Text("Dictionary loading…", style = lx(14, 400, P.sub))
            else if (entry.ipa != null) IpaRow(entry.ipa, entry.approx) { symbol = it }
            else Text("Not in the pronunciation dictionary", style = lx(14, 400, P.sub))
            Spacer(Modifier.height(12.dp))
            if (entry.es.isNotEmpty()) {
                Text("Español" + if (entry.esApprox) " *" else "", style = lx(12, 500, P.sub))
                Text(entry.es.take(6).joinToString(", "), style = lx(17, 400))
            } else if (Dict.ready) Text("No Spanish translation found", style = lx(14, 400, P.sub))
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Pill("Pronounce", Ic.speaker, filled = true) { voice.speak(entry.word) }
                Pill("Slow") { voice.speak(entry.word, slow = true) }
                Pill(if (isSaved) "Saved" else "Save", Ic.star) {
                    savedList = if (isSaved) savedList.filter { it.word != entry.word }
                    else listOf(Saved(entry.word, entry.ipa, entry.es.take(3).joinToString(", "))) + savedList
                    store.setSaved(savedList)
                }
            }
        }
    }
    symbol?.let { SymbolDialog(it, voice) { symbol = null } }
}

/** /ipa/ with each symbol tappable, opening its guide card. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IpaRow(ipa: String, approx: Boolean, size: Int = 24, onSymbol: (String) -> Unit) {
    FlowRow(verticalArrangement = Arrangement.Center) {
        Text("/", style = ipaStyle(size))
        for (s in ipaSymbols(ipa)) {
            Text(s, style = ipaStyle(size, if (s == "ˈ" || s == "ˌ") P.accent else P.primary),
                modifier = Modifier.clip(RoundedCornerShape(4.dp)).clickable { onSymbol(s) }.padding(horizontal = 1.dp))
        }
        Text("/" + if (approx) " *" else "", style = ipaStyle(size))
    }
}

@Composable
fun SymbolDialog(sym: String, voice: Voice, onDismiss: () -> Unit) {
    val info = symbolInfo(sym)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onDismiss) { Text("Close") } },
        title = { Text(sym, style = ipaStyle(40, P.ink)) },
        text = {
            if (info == null) Text("No guide entry for this symbol.", style = lx(14))
            else Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(info.name, style = lx(16, 600))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (ex in info.examples) Pill(ex, Ic.speaker) { voice.speak(ex) }
                }
                Text(info.examples.mapNotNull { e -> Dict.lookup(e).ipa?.let { "$e /$it/" } }.joinToString("   "), style = ipaStyle(15))
                Text(info.tip, style = lx(14))
                Text("/${info.symbol}/ : ${info.technical}", style = lx(12, 400, P.sub))
            }
        },
    )
}

// ---------------------------------------------------------------- saved words + search

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SavedScreen(store: Store, voice: Voice, back: () -> Unit) {
    var saved by remember { mutableStateOf(store.saved()) }
    var query by remember { mutableStateOf("") }
    var lookup by remember { mutableStateOf<String?>(null) }
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    val exact = remember(query) { if (Dict.ready && query.isNotBlank()) Dict.lookup(query).takeIf { it.ipa != null || it.es.isNotEmpty() } else null }
    val near = remember(query) { if (Dict.ready) Dict.suggest(query) else emptyList() }

    Column(Modifier.fillMaxSize().background(P.bg)) {
        TopBar("Saved words", "${saved.size} words", back = back) {
            if (saved.isNotEmpty()) TextButton({
                clipboard.setText(AnnotatedString(saved.joinToString("\n") { s -> listOfNotNull(s.word, s.ipa?.let { "/$it/" }, s.es.ifEmpty { null }).joinToString("  ") }))
                copied = true
            }) { Text(if (copied) "Copied" else "Copy", style = lx(14, 500, P.primary)) }
        }
        Row(
            Modifier.padding(horizontal = 20.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(P.card).padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Ic.search, null, tint = P.sub, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) Text("Look up any word", style = lx(16, 400, P.muted))
                BasicTextField(query, { query = it }, singleLine = true, textStyle = lx(16), modifier = Modifier.fillMaxWidth())
            }
            if (query.isNotEmpty()) IconBtn(Ic.close, "Clear", P.sub) { query = "" }
        }
        LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (exact != null) item {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(P.primarySoft).clickable { lookup = exact.word }.padding(14.dp)) {
                    Text(exact.word, style = lx(17, 600))
                    exact.ipa?.let { Text("/$it/" + if (exact.approx) " *" else "", style = ipaStyle(16)) }
                    if (exact.es.isNotEmpty()) Text(exact.es.take(4).joinToString(", "), style = lx(14, 400, P.sub))
                }
            }
            if (query.isNotBlank() && near.isNotEmpty()) item {

                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (w in near) Pill(w) { query = w }
                }
            }
            if (query.isNotBlank() && exact == null && near.isEmpty() && Dict.ready) item { Text("No matches", style = lx(14, 400, P.sub)) }
            if (saved.isEmpty() && query.isBlank()) item {
                Text("No saved words yet. In a book, double-tap or hold a word, then tap Save.", style = lx(14, 400, P.sub))
            }
            if (query.isBlank()) itemsIndexed(saved, key = { _, s -> s.word }) { _, s ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(P.card).clickable { lookup = s.word }.padding(start = 14.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(s.word, style = lx(16, 600))
                            s.ipa?.let { Text("  /$it/", style = ipaStyle(15)) }
                        }
                        if (s.es.isNotEmpty()) Text(s.es, style = lx(13, 400, P.sub), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconBtn(Ic.speaker, "Pronounce", P.primary) { voice.speak(s.word) }
                    IconBtn(Ic.close, "Remove", P.muted) { saved = saved.filter { it.word != s.word }; store.setSaved(saved) }
                }
            }
        }
    }
    lookup?.let { WordSheet(it, store, voice, onDismiss = { lookup = null; saved = store.saved() }) }
}
