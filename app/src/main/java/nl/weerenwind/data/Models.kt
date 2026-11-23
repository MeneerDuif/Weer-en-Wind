package nl.weerenwind.data

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

data class Place(
    val id: String,
    val name: String,
    val detail: String,
    val lat: Double,
    val lon: Double,
    val source: String,
)

/** Eén uur uit de verwachting. Neerslag, kans en weercode gelden voor het uur vóór [time] (Open-Meteo-conventie). */
data class Hour(
    val time: LocalDateTime,
    val temp: Double,
    val feels: Double,
    val precip: Double,
    val prob: Int,
    val code: Int,
    val wind: Double,
    val gust: Double,
    val dir: Int,
    val uv: Double,
    val isDay: Boolean,
)

data class Now(
    val time: LocalDateTime,
    val temp: Double,
    val feels: Double,
    val precip: Double,
    val code: Int,
    val wind: Double,
    val gust: Double,
    val dir: Int,
    val isDay: Boolean,
)

/** Neerslag (mm) in het kwartier vóór [time]. */
data class Quarter(val time: LocalDateTime, val precip: Double)

/** Eén dag uit de weekverwachting. */
data class Day(
    val date: LocalDate,
    val code: Int,
    val tMax: Double,
    val tMin: Double,
    val precip: Double,
    val probMax: Int,
    val windMax: Double,
    val gustMax: Double,
    val dir: Int,
    val uvMax: Double,
    val sunrise: LocalDateTime?,
    val sunset: LocalDateTime?,
)

data class Forecast(
    val placeId: String,
    val utcOffsetSec: Int,
    val current: Now,
    val hours: List<Hour>,
    val quarters: List<Quarter>,
    val days: List<Day> = emptyList(),
) {
    fun localNow(): LocalDateTime = LocalDateTime.now(ZoneOffset.ofTotalSeconds(utcOffsetSec))

    /** Uren waarvan het interval nog niet voorbij is. */
    fun upcoming(now: LocalDateTime, n: Int): List<Hour> = hours.filter { it.time.isAfter(now) }.take(n)

    /** Het uur dat het dichtst bij [t] ligt. */
    fun hourAt(t: LocalDateTime): Hour? {
        val rounded = t.truncatedTo(ChronoUnit.HOURS).let { if (t.minute >= 30) it.plusHours(1) else it }
        return hours.firstOrNull { it.time == rounded }
    }

    /** Neerslag in mm per minuut op moment [t]: kwartierdata als die er is, anders uurdata. */
    fun precipPerMinute(t: LocalDateTime): Double {
        val q = quarters.firstOrNull { !t.isBefore(it.time.minusMinutes(15)) && t.isBefore(it.time) }
        if (q != null) return q.precip / 15.0
        val h = hours.firstOrNull { !t.isBefore(it.time.minusHours(1)) && t.isBefore(it.time) }
        return (h?.precip ?: 0.0) / 60.0
    }
}

data class Bundle(val fetchedAt: Long, val forecasts: Map<String, Forecast>)

data class Settings(
    val theme: String = "amoled",
    val startTab: Int = 0,
    val showStatusBar: Boolean = true,
    val showNavBar: Boolean = true,
    val windUnit: String = "bft",
    val coldFeels: Int = 8,
    val rainProb: Int = 40,
    val rainMm: Double = 0.2,
    val windBft: Int = 6,
    val uvMin: Int = 3,
    val rideMinutes: Int = 20,
    val hours48: Boolean = false,
)

data class LogEntry(val time: Long, val host: String, val purpose: String, val code: Int, val bytes: Int)
