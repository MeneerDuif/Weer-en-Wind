package nl.weerenwind.logic

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

object Units {
    val NL: Locale = Locale.forLanguageTag("nl-NL")
    private val HM = DateTimeFormatter.ofPattern("HH:mm")
    private val BFT = doubleArrayOf(1.0, 6.0, 12.0, 20.0, 29.0, 39.0, 50.0, 62.0, 75.0, 89.0, 103.0, 118.0)

    /** Beaufort uit km/u (KNMI-grenzen). */
    fun bft(kmh: Double): Int = BFT.count { kmh >= it }

    fun wind(kmh: Double, unit: String): String = when (unit) {
        "kmh" -> "${kmh.safeRound()} km/u"
        "ms" -> "${(kmh / 3.6).safeRound()} m/s"
        else -> "${bft(kmh)} Bft"
    }

    fun compass(deg: Int): String {
        val names = arrayOf("N", "NO", "O", "ZO", "Z", "ZW", "W", "NW")
        val d = ((deg % 360) + 360) % 360
        return names[((d + 22.5) / 45.0).toInt() % 8]
    }

    fun temp(t: Double): String = if (t.isNaN()) "–" else "${t.safeRound()}°"

    fun mm(v: Double): String = when {
        v < 0.05 -> "0"
        v < 10 -> String.format(NL, "%.1f", v)
        else -> v.safeRound().toString()
    }

    fun hm(t: LocalDateTime): String = t.format(HM)

    private fun Double.safeRound(): Int = if (isNaN()) 0 else roundToInt().let { if (it == 0) 0 else it }
}

object Codes {
    private const val TEXT = "︎" // forceer zwart-wit glyph in plaats van emoji

    fun text(code: Int): String = when (code) {
        0 -> "Helder"
        1 -> "Overwegend helder"
        2 -> "Half bewolkt"
        3 -> "Bewolkt"
        45, 48 -> "Mist"
        51, 53, 55 -> "Motregen"
        56, 57 -> "IJzel (motregen)"
        61 -> "Lichte regen"
        63 -> "Regen"
        65 -> "Zware regen"
        66, 67 -> "IJzel"
        71, 73, 75 -> "Sneeuw"
        77 -> "Korrelsneeuw"
        80, 81 -> "Buien"
        82 -> "Zware buien"
        85, 86 -> "Sneeuwbuien"
        95 -> "Onweer"
        96, 99 -> "Onweer met hagel"
        else -> "–"
    }

    fun glyph(code: Int, day: Boolean): String = when (code) {
        0, 1 -> if (day) "☀$TEXT" else "☾"
        2 -> "◐"
        3 -> "☁$TEXT"
        45, 48 -> "≡"
        71, 73, 75, 77, 85, 86 -> "❄$TEXT"
        95, 96, 99 -> "ϟ"
        in 51..67, in 80..82 -> "☂$TEXT"
        else -> "·"
    }

    fun isSnow(code: Int) = code in 71..77 || code == 85 || code == 86
    fun isThunder(code: Int) = code >= 95
    fun isShower(code: Int) = code in 80..82
    fun isFreezing(code: Int) = code == 56 || code == 57 || code == 66 || code == 67
}
