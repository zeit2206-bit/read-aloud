package com.zeit2206.readaloud

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Keeps the speech recognizer listening until [stop]: each session ends after a pause, so a new one starts
 * right away. Words reach [onWords] as soon as they are stable: within a session the last partial word can
 * still change ("thin" → "think"), so it is held back until the next word or the final result arrives,
 * unless [eager] says it is already the word being waited for.
 */
class Listener(
    private val context: Context,
    private val onWords: (List<String>) -> Unit,
    private val eager: (String) -> Boolean,
    private val onHeard: (String) -> Unit,
    private val onError: (String) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    var active = false; private set
    private var fed = 0   // words of the current session already passed on
    private val again = Runnable { session() }

    fun start() {
        if (active) return
        if (!SpeechRecognizer.isRecognitionAvailable(context)) { onError("No speech recognizer on this device"); return }
        active = true
        session()
    }

    fun stop() {
        active = false
        main.removeCallbacksAndMessages(null)
        unmute()
        recognizer?.let { it.cancel(); it.destroy() }
        recognizer = null
    }

    // Android 13+: one long "segmented" session instead of a restart after every pause. Turned off for good
    // if the recognizer service keeps failing with it.
    private var segmented = Build.VERSION.SDK_INT >= 33
    private var startedAt = 0L
    private var quickFails = 0

    // The recognizer plays a beep when a session starts on many phones: silence the sound streams for a moment.
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val beepStreams = intArrayOf(AudioManager.STREAM_MUSIC, AudioManager.STREAM_NOTIFICATION, AudioManager.STREAM_SYSTEM)
    private var muted = emptyList<Int>()

    private fun muteBeep() {
        if (muted.isNotEmpty()) return
        muted = beepStreams.filter { s ->
            try { if (audio.isStreamMute(s)) false else { audio.adjustStreamVolume(s, AudioManager.ADJUST_MUTE, 0); true } }
            catch (e: SecurityException) { false }   // e.g. Do Not Disturb blocks changing notification volume
        }
        main.postDelayed({ unmute() }, 700)
    }

    private fun unmute() {
        for (s in muted) try { audio.adjustStreamVolume(s, AudioManager.ADJUST_UNMUTE, 0) } catch (_: SecurityException) {}
        muted = emptyList()
    }

    private fun session() {
        if (!active) return
        recognizer?.destroy()
        fed = 0
        startedAt = System.currentTimeMillis()
        val r = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = r
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            // Long sessions mean fewer restarts (and fewer start beeps).
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 15_000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3_000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3_000L)
            if (segmented) putExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION, RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS)
        }
        r.setRecognitionListener(object : RecognitionListener {
            // Segmented session: each pause closes a segment; the session itself keeps listening.
            override fun onSegmentResults(b: Bundle) {
                val text = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                if (text.isNotBlank()) { onHeard(text); val words = tokensOf(text); feed(words, words.size) }
                fed = 0
                quickFails = 0
            }
            override fun onEndOfSegmentedSession() = restart(50)
            override fun onPartialResults(b: Bundle) {
                val text = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                if (text.isBlank()) return
                onHeard(text)
                val words = tokensOf(text)
                // Pass on everything but the last word; the last one too if it is exactly the awaited word.
                val stable = if (words.isNotEmpty() && eager(words.last())) words.size else words.size - 1
                feed(words, stable)
            }
            override fun onResults(b: Bundle) {
                val text = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                if (text.isNotBlank()) { onHeard(text); val words = tokensOf(text); feed(words, words.size) }
                restart(50)
            }
            override fun onError(error: Int) {
                // A segmented session that dies at once, again and again: this recognizer doesn't support it.
                if (segmented && System.currentTimeMillis() - startedAt < 1500 && ++quickFails >= 3) segmented = false
                handleError(error)
            }
            fun handleError(error: Int) = when (error) {
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> { stop(); onError("Microphone permission needed") }
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY, SpeechRecognizer.ERROR_CLIENT -> restart(400)
                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER -> restart(1500)
                else -> restart(50)   // no match / speech timeout: just keep listening
            }
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        muteBeep()
        r.startListening(intent)
    }

    private fun feed(words: List<String>, upTo: Int) {
        if (upTo > fed) { onWords(words.subList(fed, upTo)); fed = upTo }
    }

    private fun restart(delayMs: Long) {
        if (!active) return
        // Only replace a pending restart: the pending unmute must still run.
        main.removeCallbacks(again)
        main.postDelayed(again, delayMs)
    }
}

/**
 * Moves the highlight through the book as words are heard. A heard word that matches the highlighted word
 * (exactly or by a close spelling) marks it read. A heard word that instead matches one of the next few words
 * means the reader went on: the words in between are marked skipped. Anything else is ignored, so a
 * mispronounced word stays highlighted until it is said right.
 */
class Tracker(private val book: Book, val status: ByteArray, start: Int) {
    var cursor = book.speakable(start); private set
    private var sub = 0          // which spoken token of the current word is next ("1879" has three)
    private var pending: String? = null   // unmatched heard word, for compounds split by the recognizer

    val done get() = cursor >= book.size
    private fun expected(i: Int = cursor, k: Int = sub) = book.keys.getOrNull(i)?.getOrNull(k)

    fun moveTo(i: Int) { cursor = book.speakable(i.coerceIn(0, book.size)); sub = 0; pending = null }

    /** Marks the highlighted word skipped and moves on. */
    fun skip() { if (!done) { status[cursor] = St.SKIPPED; sessionSkipped++; advance() } }

    /** Words said right / passed over since the reader was opened (for the session counter and accuracy). */
    var sessionRead = 0
    var sessionSkipped = 0

    fun isAwaited(heard: String) = expected()?.let { same(heard, it) } ?: false

    /** Feeds heard words; returns true when the highlight moved. */
    fun hear(words: List<String>): Boolean {
        val before = cursor
        for (w in words) hearOne(w)
        return cursor != before
    }

    private fun advance() { cursor = book.speakable(cursor + 1); sub = 0 }

    private fun consume() {
        sub++
        // Said right now: read, even if it was skipped before (tap it and say it again to clear the yellow).
        if (sub >= book.keys[cursor].size) {
            val wasSkipped = status[cursor] == St.SKIPPED
            status[cursor] = St.READ; sessionRead++; advance()
            // A skipped word was just fixed: jump over the words after it that are already read.
            if (wasSkipped) while (!done && status[cursor] == St.READ) advance()
        }
    }

    private fun hearOne(w: String) {
        if (done) return
        val exp = expected() ?: return
        val joined = pending?.plus(w)
        pending = null
        when {
            same(w, exp) -> consume()
            joined != null && joined == exp -> consume()                       // "some" + "thing" → "something"
            exp.length < w.length && w.startsWith(exp) && w.removePrefix(exp) == nextToken() -> { consume(); consume() }  // "every one" heard as "everyone"
            else -> {
                // Did the reader move on? Look a few words ahead for an exact, meaningful match.
                if (w.length >= 3) for (ahead in 1..3) {
                    var j = cursor; repeat(ahead) { j = book.speakable(j + 1) }
                    if (j >= book.size) break
                    if (book.keys[j].first() == w) {
                        var k = cursor
                        while (k < j) { if (book.keys[k].isNotEmpty()) { status[k] = St.SKIPPED; sessionSkipped++ }; k++ }
                        cursor = j; sub = 0; consume(); return
                    }
                }
                pending = w
            }
        }
    }

    private fun nextToken(): String? {
        val keys = book.keys[cursor]
        return if (sub + 1 < keys.size) keys[sub + 1] else book.keys.getOrNull(book.speakable(cursor + 1))?.firstOrNull()
    }

    private fun same(heard: String, exp: String): Boolean {
        if (heard == exp) return true
        // Short near-misses are usually a different word ("sink" for "think"): only long words get one letter of slack.
        return exp.length >= 7 && editDistance(heard, exp) <= 1
    }
}

/** The device's offline US voice, slowed like the extension's 0.8×. */
class Voice(context: Context) {
    private var ready = false
    private lateinit var tts: TextToSpeech

    init {
        tts = TextToSpeech(context.applicationContext) { st ->
            ready = st == TextToSpeech.SUCCESS
            if (ready) {
                tts.language = Locale.US
                tts.voices?.filter { v -> v.locale == Locale.US && !v.isNetworkConnectionRequired }?.maxByOrNull { v -> v.quality }?.let { v -> tts.voice = v }
            }
        }
    }

    fun speak(text: String, slow: Boolean = false) {
        if (!ready) return
        tts.setSpeechRate(if (slow) 0.6f else 0.8f)
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "say")
    }

    fun release() = tts.shutdown()
}
