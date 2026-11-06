package com.zeit2206.readaloud

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File

sealed interface Screen {
    data object Library : Screen
    data class Reader(val id: String) : Screen
    data object Saved : Screen
}

class MainActivity : ComponentActivity() {
    private var screen by mutableStateOf<Screen>(Screen.Library)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Thread { Dict.load(applicationContext) }.start()
        P.dark = getSharedPreferences("store", MODE_PRIVATE).getBoolean("dark", false)
        val store = Store(this)
        val voice = Voice(this)
        handleDebug(intent)
        setContent {
            AppTheme {
                when (val s = screen) {
                    Screen.Library -> LibraryScreen(store, open = { screen = Screen.Reader(it) }, saved = { screen = Screen.Saved })
                    is Screen.Reader -> {
                        BackHandler { screen = Screen.Library }
                        ReaderScreen(store, voice, s.id, back = { screen = Screen.Library }, saved = { screen = Screen.Saved })
                    }
                    Screen.Saved -> {
                        BackHandler { screen = Screen.Library }
                        SavedScreen(store, voice, back = { screen = Screen.Library })
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDebug(intent)
    }

    /**
     * Emulator test hooks (debug builds only):
     *   adb shell am start -n com.zeit2206.readaloud/.MainActivity --es pdf book.pdf      (imports files/<name> from the app's external files dir)
     *   adb shell am start -n com.zeit2206.readaloud/.MainActivity --es say "the words heard"   (as if the mic heard them)
     */
    private fun handleDebug(intent: Intent?) {
        if (!BuildConfig.DEBUG || intent == null) return
        intent.getStringExtra("pdf")?.let { Debug.pdf = Uri.fromFile(File(getExternalFilesDir(null), it)) }
        intent.getStringExtra("say")?.let { text -> Debug.say?.invoke(text) }
    }
}

object Debug {
    var pdf by mutableStateOf<Uri?>(null)
    var say: ((String) -> Unit)? = null
}
