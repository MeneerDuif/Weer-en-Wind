package nl.weerenwind.logic

import nl.weerenwind.data.Forecast
import nl.weerenwind.data.Hour
import nl.weerenwind.data.RadarPoint
import nl.weerenwind.data.Settings
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.max
import kotlin.math.roundToInt

enum class AdviceKind { RAIN, COLD, SUN, ICE, WIND }

data class Advice(val kind: AdviceKind, val title: String, val detail: String)

object Logic {
    /** Aantal uren waarover adviezen gaan ("de komende uren"). */
    const val ADVICE_HOURS = 6

    private fun wet(h: Hour, s: Settings) = h.precip >= s.rainMm || h.prob >= max(s.rainProb, 70)

    /** Begin van het interval waar een uurwaarde over gaat. */
    private fun start(h: Hour): LocalDateTime = h.time.minusHours(1)

    private fun kind(hours: List<Hour>): String = when {
        hours.any { Codes.isSnow(it.code) } -> "sneeuw"
        hours.any { Codes.isThunder(it.code) } -> "onweersbuien"
        hours.any { Codes.isShower(it.code) } -> "buien"
        else -> "regen"
    }

    /** Korte zin zoals "Droog tot 15:00, daarna buien". */
    fun summary(f: Forecast, s: Settings, now: LocalDateTime = f.localNow()): String {
        RadarLogic.summary(f.radarFor(now), now)?.let { return it }
        val hrs = f.upcoming(now, 12)
        if (hrs.isEmpty()) return "Geen verwachting beschikbaar"
        val nowWet = wet(hrs[0], s) || f.current.precip >= 0.1
        return if (!nowWet) {
            val i = hrs.indexOfFirst { wet(it, s) }
            if (i < 0) "Droog de komende 12 uur"
            else {
                val k = kind(hrs.drop(i).take(3))
                "Droog tot ${Units.hm(start(hrs[i]))}, daarna $k"
            }
        } else {
            val k = kind(hrs.take(3)).replaceFirstChar { it.uppercase() }
            val j = hrs.indexOfFirst { !wet(it, s) }
            when {
                j < 0 -> "$k de komende uren"
                j == 0 -> "$k, snel droog"
                else -> "$k tot ${Units.hm(start(hrs[j]))}, daarna droog"
            }
        }
    }

    fun advice(f: Forecast, s: Settings, now: LocalDateTime = f.localNow()): List<Advice> {
        val hrs = f.upcoming(now, ADVICE_HOURS)
        if (hrs.isEmpty()) return emptyList()
        val out = mutableListOf<Advice>()

        val radar = f.radarFor(now)
        val rainNow = if (radar.isNotEmpty()) RadarLogic.at(radar, now) >= RadarLogic.WET else f.current.precip >= 0.1
        val radarWet = RadarLogic.firstWet(radar, now)
        val firstWet = hrs.firstOrNull { it.precip >= s.rainMm || it.prob >= s.rainProb }
        if (rainNow) {
            out += Advice(AdviceKind.RAIN, "Regenjas aan", "Het regent nu")
        } else if (radarWet != null) {
            out += Advice(AdviceKind.RAIN, "Regenjas mee", "Radar: ${RadarLogic.intensity(radarWet.mmh)} vanaf ${Units.hm(radarWet.time)}")
        } else if (firstWet != null) {
            out += Advice(AdviceKind.RAIN, "Regenjas mee", "Kans ${firstWet.prob}% vanaf ${Units.hm(maxOf(start(firstWet), now))}")
        }

        val minFeels = minOf(f.current.feels, hrs.minOf { it.feels })
        if (minFeels <= s.coldFeels) {
            out += Advice(AdviceKind.COLD, "Warme jas", "Voelt als ${Units.temp(minFeels)}")
        }

        val ice = hrs.firstOrNull { it.temp <= 1.0 && (it.precip >= 0.1 || Codes.isFreezing(it.code) || Codes.isSnow(it.code)) }
        if (ice != null) {
            out += Advice(AdviceKind.ICE, "Kans op gladheid", "Rond ${Units.hm(start(ice))}, ${Units.temp(ice.temp)} met neerslag")
        }

        val maxWind = max(f.current.wind, hrs.maxOf { it.wind })
        val maxGust = max(f.current.gust, hrs.maxOf { it.gust })
        if (Units.bft(maxWind) >= s.windBft || maxGust >= 60) {
            val dirH = hrs.maxBy { it.wind }
            out += Advice(AdviceKind.WIND, "Harde wind",
                "${Units.bft(maxWind)} Bft uit ${Units.compass(dirH.dir)}, stoten ${maxGust.roundToInt()} km/u")
        }

        val maxUv = hrs.maxOf { it.uv }
        if (maxUv >= s.uvMin) {
            out += Advice(AdviceKind.SUN, "Zonnebrand", "UV-index tot ${maxUv.roundToInt()}")
        }
        return out
    }
}

/** Radar (Buienradar, 5-minutenvakken, 2 uur vooruit). Nauwkeuriger dan het model voor de komende 2 uur. */
object RadarLogic {
    /** Vanaf 0,2 mm/u telt het als nat. */
    const val WET = 0.2

    fun at(pts: List<RadarPoint>, t: LocalDateTime): Double =
        pts.firstOrNull { !t.isBefore(it.time) && t.isBefore(it.time.plusMinutes(5)) }?.mmh ?: 0.0

    fun firstWet(pts: List<RadarPoint>, now: LocalDateTime): RadarPoint? =
        pts.firstOrNull { it.time.plusMinutes(5).isAfter(now) && it.mmh >= WET }

    fun intensity(mmh: Double): String = when {
        mmh < 1.0 -> "lichte regen"
        mmh < 4.0 -> "regen"
        mmh < 10.0 -> "flinke regen"
        else -> "zware buien"
    }

    /**
     * Zin op basis van de radar, of null als de radar niets bijzonders ziet (dan beslist het model).
     * Voorbeelden: "Droog tot 18:40, daarna lichte regen" · "Regen tot 19:05, daarna droog".
     */
    fun summary(pts: List<RadarPoint>, now: LocalDateTime): String? {
        val ahead = pts.filter { it.time.plusMinutes(5).isAfter(now) }
        if (ahead.isEmpty()) return null
        val wetNow = at(pts, now) >= WET
        return if (!wetNow) {
            val w = ahead.firstOrNull { it.mmh >= WET } ?: return null
            "Droog tot ${Units.hm(w.time)}, daarna ${intensity(ahead.filter { it.mmh >= WET }.take(6).maxOf { it.mmh })}"
        } else {
            val peak = ahead.takeWhile { it.mmh >= WET }.maxOfOrNull { it.mmh } ?: at(pts, now)
            val dry = ahead.firstOrNull { it.mmh < WET }
            val k = intensity(peak).replaceFirstChar { it.uppercase() }
            if (dry == null) "$k de komende 2 uur" else "$k tot ${Units.hm(dry.time)}, daarna droog"
        }
    }
}

data class RainAlert(val start: LocalDateTime, val minutes: Long, val text: String, val until: LocalDateTime?)

object Alerts {
    /**
     * Regenalarm: alleen als het nu droog is en de radar binnen [leadMin] minuten regen ziet.
     * Zo krijg je één melding per bui, niet één per kwartier.
     */
    fun rain(pts: List<RadarPoint>, now: LocalDateTime, leadMin: Int): RainAlert? {
        if (pts.isEmpty() || RadarLogic.at(pts, now) >= RadarLogic.WET) return null
        val w = RadarLogic.firstWet(pts, now) ?: return null
        val mins = ChronoUnit.MINUTES.between(now, w.time).coerceAtLeast(0)
        if (mins > leadMin) return null
        val spell = pts.filter { !it.time.isBefore(w.time) }.takeWhile { it.mmh >= RadarLogic.WET }
        val until = pts.firstOrNull { it.time.isAfter(w.time) && it.mmh < RadarLogic.WET }?.time
        val label = RadarLogic.intensity(spell.maxOf { it.mmh })
        val text = (if (mins < 5) "Nu " + label else label.replaceFirstChar { it.uppercase() } + " over $mins min") +
            (until?.let { ", droog om ${Units.hm(it)}" } ?: "")
        return RainAlert(w.time, mins, text, until)
    }

    /** Geen meldingen 's nachts (22:00–07:00). */
    fun quiet(now: LocalDateTime): Boolean = now.hour >= 22 || now.hour < 7

    fun codeName(level: Int) = when (level) { 3 -> "Code rood"; 2 -> "Code oranje"; else -> "Code geel" }
}

object Week {
    /** Na ongeveer 2 dagen worden verwachtingen merkbaar onzekerder. */
    const val RELIABLE_DAYS = 2

    /** Korte dagregel, zoals "Droog" of "Buien, 4,2 mm (80%)". */
    fun line(d: nl.weerenwind.data.Day, s: Settings): String {
        val wet = d.precip >= 1.0 || d.probMax >= max(s.rainProb, 60)
        if (!wet) return if (d.precip >= 0.1) "Grotendeels droog" else "Droog"
        val k = when {
            Codes.isSnow(d.code) -> "Sneeuw"
            Codes.isThunder(d.code) -> "Onweer"
            Codes.isShower(d.code) -> "Buien"
            else -> "Regen"
        }
        return "$k, ${Units.mm(d.precip)} mm (${d.probMax}%)"
    }
}

object Rain {
    /** Totale neerslag (mm) tijdens een rit van [minutes] minuten vanaf [start]. */
    fun ride(f: Forecast, start: LocalDateTime, minutes: Int): Double =
        (0 until minutes).sumOf { f.precipPerMinute(start.plusMinutes(it.toLong())) }

    fun label(mm: Double): String = when {
        mm < 0.1 -> "droog"
        mm < 0.5 -> "een paar druppels"
        mm < 2.0 -> "nat"
        else -> "doorweekt"
    }

    /** Beste vertrekmoment binnen [rangeMin] minuten: nu, of op een heel kwartier. */
    fun best(f: Forecast, now: LocalDateTime, minutes: Int, rangeMin: Int = 180): Pair<LocalDateTime, Double> {
        val firstQ = now.truncatedTo(ChronoUnit.HOURS).plusMinutes(((now.minute / 15) + 1) * 15L)
        val candidates = listOf(now) + generateSequence(firstQ) { it.plusMinutes(15) }
            .takeWhile { !it.isAfter(now.plusMinutes(rangeMin.toLong())) }
        var best = now to ride(f, now, minutes)
        for (c in candidates.drop(1)) {
            val mm = ride(f, c, minutes)
            if (mm < best.second - 0.05) best = c to mm
        }
        return best
    }

    /** Aaneengesloten droge periodes van minstens 2 uur in de komende 24 uur. */
    fun dryWindows(f: Forecast, now: LocalDateTime, s: Settings): List<Pair<LocalDateTime, LocalDateTime>> {
        val hrs = f.upcoming(now, 24)
        val out = mutableListOf<Pair<LocalDateTime, LocalDateTime>>()
        var run = mutableListOf<Hour>()
        fun flush() {
            if (run.size >= 2) out += maxOf(run.first().time.minusHours(1), now) to run.last().time
            run = mutableListOf()
        }
        for (h in hrs) {
            if (h.precip < 0.1 && h.prob < s.rainProb) run.add(h) else flush()
        }
        flush()
        return out
    }
}
