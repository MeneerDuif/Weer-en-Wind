package nl.weerenwind.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import nl.weerenwind.R
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class WeerColors(
    val id: String,
    val label: String,
    val bg: Color,
    val surface: Color,
    val border: Color,
    val text: Color,
    val muted: Color,
    val faint: Color,
    val rain: Color,
    val warm: Color,
    val cold: Color,
    val wind: Color,
    val warn: Color,
    val sun: Color,
    val isLight: Boolean,
)

object Themes {
    /** Standaard: puur zwart, gedempt wit, kleur alleen waar het iets betekent. */
    val amoled = WeerColors(
        id = "amoled", label = "AMOLED-zwart",
        bg = Color(0xFF000000), surface = Color(0xFF000000), border = Color(0xFF1C1C1C),
        text = Color(0xFFE8E8E8), muted = Color(0xFFA6A6A6), faint = Color(0xFF7C7C7C),
        rain = Color(0xFF5AB0FF), warm = Color(0xFFFFB454), cold = Color(0xFF8FB4FF),
        wind = Color(0xFF5FD8C0), warn = Color(0xFFFF6B6B), sun = Color(0xFFFFD166),
        isLight = false,
    )
    val grafiet = WeerColors(
        id = "grafiet", label = "Grafiet (donkergrijs)",
        bg = Color(0xFF121212), surface = Color(0xFF1B1B1B), border = Color(0xFF2A2A2A),
        text = Color(0xFFE6E6E6), muted = Color(0xFF9A9A9A), faint = Color(0xFF858585),
        rain = Color(0xFF5AB0FF), warm = Color(0xFFFFB454), cold = Color(0xFF8FB4FF),
        wind = Color(0xFF5FD8C0), warn = Color(0xFFFF6B6B), sun = Color(0xFFFFD166),
        isLight = false,
    )
    val nachtblauw = WeerColors(
        id = "nachtblauw", label = "Nachtblauw",
        bg = Color(0xFF0A0F1C), surface = Color(0xFF101829), border = Color(0xFF1E2A44),
        text = Color(0xFFDDE6F5), muted = Color(0xFF8394B3), faint = Color(0xFF7183A5),
        rain = Color(0xFF6CB6FF), warm = Color(0xFFFFB86B), cold = Color(0xFF9CC2FF),
        wind = Color(0xFF6EE0C8), warn = Color(0xFFFF7A7A), sun = Color(0xFFFFD27A),
        isLight = false,
    )
    val nachtrood = WeerColors(
        id = "nachtrood", label = "Nachtrood (zacht in het donker)",
        bg = Color(0xFF000000), surface = Color(0xFF000000), border = Color(0xFF2A0D0A),
        text = Color(0xFFE0675A), muted = Color(0xFFBF5950), faint = Color(0xFFC25449),
        rain = Color(0xFFD9826F), warm = Color(0xFFE0675A), cold = Color(0xFFC77367),
        wind = Color(0xFFB85C50), warn = Color(0xFFFF5040), sun = Color(0xFFE88A5A),
        isLight = false,
    )
    val contrast = WeerColors(
        id = "contrast", label = "Hoog contrast",
        bg = Color(0xFF000000), surface = Color(0xFF000000), border = Color(0xFFFFFFFF),
        text = Color(0xFFFFFFFF), muted = Color(0xFFD6D6D6), faint = Color(0xFF9E9E9E),
        rain = Color(0xFF33C3FF), warm = Color(0xFFFFD600), cold = Color(0xFF7FDBFF),
        wind = Color(0xFF00FFC8), warn = Color(0xFFFF4040), sun = Color(0xFFFFD600),
        isLight = false,
    )
    val licht = WeerColors(
        id = "licht", label = "Licht",
        bg = Color(0xFFF7F7F5), surface = Color(0xFFFFFFFF), border = Color(0xFFE2E2DE),
        text = Color(0xFF1A1A1A), muted = Color(0xFF6B6B6B), faint = Color(0xFF6E6E69),
        rain = Color(0xFF1F6FD1), warm = Color(0xFFA85A00), cold = Color(0xFF3A5FCC),
        wind = Color(0xFF0E7E6E), warn = Color(0xFFC62828), sun = Color(0xFF8F6700),
        isLight = true,
    )
    val retro = WeerColors(
        id = "retro", label = "Retro weerkaart",
        bg = Color(0xFFF1E9D2), surface = Color(0xFFF7F1E0), border = Color(0xFFCDBF9C),
        text = Color(0xFF2B2118), muted = Color(0xFF6E5F4A), faint = Color(0xFF72644A),
        rain = Color(0xFF2C5D8F), warm = Color(0xFFB1432A), cold = Color(0xFF4A6896),
        wind = Color(0xFF397156), warn = Color(0xFFA3261A), sun = Color(0xFF876115),
        isLight = true,
    )

    val all = listOf(amoled, grafiet, nachtblauw, nachtrood, contrast, licht, retro)

    /** "systeem" volgt het toestel: AMOLED-zwart in donker, Licht overdag. */
    fun byId(id: String, systemDark: Boolean): WeerColors =
        if (id == "systeem") (if (systemDark) amoled else licht)
        else all.firstOrNull { it.id == id } ?: amoled
}

val LocalWeer = staticCompositionLocalOf { Themes.amoled }

/**
 * Typografie: een redactionele serif (Instrument Serif) voor grote getallen en zinnen,
 * en een technische mono (IBM Plex Mono) voor data, labels en tabellen — als een nachtelijk meetinstrument.
 */
object Type {
    val serif = FontFamily(
        Font(R.font.instrument_serif, FontWeight.Normal, FontStyle.Normal),
        Font(R.font.instrument_serif_italic, FontWeight.Normal, FontStyle.Italic),
    )
    val mono = FontFamily(
        Font(R.font.plex_mono_light, FontWeight.Light),
        Font(R.font.plex_mono, FontWeight.Normal),
        Font(R.font.plex_mono_medium, FontWeight.Medium),
    )

    fun typography(): Typography {
        val base = Typography()
        fun m(t: TextStyle) = t.copy(fontFamily = mono)
        fun s(t: TextStyle) = t.copy(fontFamily = serif, fontWeight = FontWeight.Normal)
        return Typography(
            displayLarge = s(base.displayLarge), displayMedium = s(base.displayMedium), displaySmall = s(base.displaySmall),
            headlineLarge = s(base.headlineLarge), headlineMedium = s(base.headlineMedium), headlineSmall = s(base.headlineSmall),
            titleLarge = s(base.titleLarge), titleMedium = m(base.titleMedium), titleSmall = m(base.titleSmall),
            bodyLarge = m(base.bodyLarge).copy(fontSize = 14.sp), bodyMedium = m(base.bodyMedium), bodySmall = m(base.bodySmall),
            labelLarge = m(base.labelLarge), labelMedium = m(base.labelMedium), labelSmall = m(base.labelSmall),
        )
    }
}

@Composable
fun WeerTheme(c: WeerColors, content: @Composable () -> Unit) {
    val scheme = if (c.isLight) {
        lightColorScheme(
            primary = c.text, onPrimary = c.bg, background = c.bg, onBackground = c.text,
            surface = c.bg, onSurface = c.text, surfaceVariant = c.surface, onSurfaceVariant = c.muted,
            outline = c.border, secondaryContainer = c.border, onSecondaryContainer = c.text,
        )
    } else {
        darkColorScheme(
            primary = c.text, onPrimary = c.bg, background = c.bg, onBackground = c.text,
            surface = c.bg, onSurface = c.text, surfaceVariant = c.surface, onSurfaceVariant = c.muted,
            outline = c.border, secondaryContainer = c.border, onSecondaryContainer = c.text,
        )
    }
    CompositionLocalProvider(LocalWeer provides c) {
        MaterialTheme(colorScheme = scheme, typography = Type.typography(), content = content)
    }
}
