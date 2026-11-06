package com.zeit2206.readaloud

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Palette (light from Pronunciation Coach, plus a dark variant). Reading [dark] in a getter makes Compose recompose on switch. */
object P {
    var dark by mutableStateOf(false)
    private fun c(light: Long, dark: Long) = if (this.dark) Color(dark) else Color(light)

    val bg get() = c(0xFFF6F5F1, 0xFF14171D)
    val card get() = c(0xFFFFFFFF, 0xFF1E222A)
    val ink get() = c(0xFF1C2230, 0xFFE9EAEE)
    val sub get() = c(0xFF5E6573, 0xFFA3A9B5)
    val muted get() = c(0xFF9AA1AE, 0xFF6E7582)
    val unread get() = c(0xFFADB2BC, 0xFF5D6370)
    val line get() = c(0xFFE6E3DC, 0xFF2E333D)
    val primary get() = c(0xFF3448C5, 0xFF7D8CF0)
    val primarySoft get() = c(0xFFE8EBFB, 0xFF262C47)
    val onPrimary get() = c(0xFFFFFFFF, 0xFF10131C)
    val accent get() = c(0xFFE4572E, 0xFFFF7A52)
    /** The word to read next: a strong colour of its own, no background. */
    val current get() = c(0xFFE4572E, 0xFFFF8A5C)
    val good get() = c(0xFF2E9E6A, 0xFF4CC28A)
    val weak get() = c(0xFFC98A00, 0xFFE0A82E)
}

@OptIn(ExperimentalTextApi::class)
private fun lexend(w: Int) = Font(R.font.lexend, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))

val Lexend = FontFamily(lexend(400), lexend(500), lexend(600), lexend(700))
val Charis = FontFamily(Font(R.font.charis_regular, FontWeight.Normal), Font(R.font.charis_bold, FontWeight.Bold))

fun lx(size: Int, weight: Int = 400, color: Color = P.ink) = TextStyle(fontFamily = Lexend, fontSize = size.sp, fontWeight = FontWeight(weight), color = color)
fun ipaStyle(size: Int, color: Color = P.sub) = TextStyle(fontFamily = Charis, fontSize = size.sp, color = color)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    // Status and navigation bars follow the theme.
    val view = androidx.compose.ui.platform.LocalView.current
    val dark = P.dark
    androidx.compose.runtime.SideEffect {
        val window = (view.context as android.app.Activity).window
        window.statusBarColor = P.bg.toArgb(); window.navigationBarColor = P.card.toArgb()
        androidx.core.view.WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !dark; isAppearanceLightNavigationBars = !dark
        }
    }
    Themed(content)
}

@Composable
private fun Themed(content: @Composable () -> Unit) = MaterialTheme(
    colorScheme = if (P.dark) darkColorScheme(primary = P.primary, background = P.bg, surface = P.card, onSurface = P.ink, surfaceContainerHigh = P.card)
        else lightColorScheme(primary = P.primary, background = P.bg, surface = P.card, onSurface = P.ink),
    content = content,
)

/** Stroke icons on a 24×24 grid. */
object Ic {
    private fun icon(name: String, vararg paths: String) = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        for (d in paths) addPath(addPathNodes(d), stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
    }.build()

    val back = icon("back", "M15 5l-7 7 7 7")
    val speaker = icon("speaker", "M4 9h4l5-4v14l-5-4H4z", "M16.5 8.5a5 5 0 0 1 0 7M19 6a8.5 8.5 0 0 1 0 12")
    val mic = icon("mic", "M9 6a3 3 0 0 1 6 0v5a3 3 0 0 1 -6 0z", "M5.5 11a6.5 6.5 0 0 0 13 0M12 17.5V21")
    val stop = icon("stop", "M8 8h8v8H8z")
    val list = icon("list", "M8 6h12M8 12h12M8 18h12", "M4 6h0.01M4 12h0.01M4 18h0.01")
    val star = icon("star", "M12 3.5l2.6 5.3 5.9.9-4.3 4.1 1 5.8L12 16.9l-5.2 2.7 1-5.8L3.5 9.7l5.9-.9z")
    val skip = icon("skip", "M5 6l8 6-8 6z", "M17 6v12")
    val plus = icon("plus", "M12 5v14M5 12h14")
    val close = icon("close", "M6 6l12 12M18 6L6 18")
    val copy = icon("copy", "M9 9h10v11H9z", "M5 15V4h10")
    val chevron = icon("chevron", "M9 5l7 7-7 7")
    val moon = icon("moon", "M20 14.5A8 8 0 0 1 9.5 4a8 8 0 1 0 10.5 10.5z")
    val sun = icon("sun", "M12 16a4 4 0 1 0 0 -8a4 4 0 0 0 0 8z", "M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4")
    val search = icon("search", "M10.5 17a6.5 6.5 0 1 0 0 -13a6.5 6.5 0 0 0 0 13z", "M15.5 15.5L20 20")
}
