package nl.weerenwind

import nl.weerenwind.data.Forecast
import nl.weerenwind.data.Hour
import nl.weerenwind.data.Now
import nl.weerenwind.data.Settings
import nl.weerenwind.logic.AdviceKind
import nl.weerenwind.logic.Logic
import nl.weerenwind.logic.Rain
import nl.weerenwind.logic.Units
import nl.weerenwind.logic.Week
import nl.weerenwind.data.Day
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class LogicTest {
    private val t0 = LocalDateTime.of(2026, 11, 23, 12, 10)

    private fun forecast(precip: List<Double>, temp: Double = 10.0, code: Int = 3, wind: Double = 10.0): Forecast {
        val hours = precip.mapIndexed { i, p ->
            Hour(t0.withMinute(0).plusHours(i + 1L), temp, temp - 2, p, if (p > 0) 80 else 5,
                if (p > 0) 80 else code, wind, wind * 1.5, 225, 0.0, true)
        }
        val now = Now(t0, temp, temp - 2, 0.0, code, wind, wind * 1.5, 225, true)
        return Forecast("x", 3600, now, hours, emptyList())
    }

    @Test fun beaufort() {
        assertEquals(0, Units.bft(0.5))
        assertEquals(1, Units.bft(3.0))
        assertEquals(4, Units.bft(25.0))
        assertEquals(6, Units.bft(45.0))
        assertEquals(12, Units.bft(130.0))
    }

    @Test fun compass() {
        assertEquals("N", Units.compass(0))
        assertEquals("ZW", Units.compass(225))
        assertEquals("N", Units.compass(350))
        assertEquals("O", Units.compass(90))
    }

    @Test fun summaryDryThenShowers() {
        val f = forecast(listOf(0.0, 0.0, 0.0, 1.2, 2.0) + List(10) { 0.0 })
        // vierde uur (eindigt 16:00) is nat → regen begint om 15:00
        assertEquals("Droog tot 15:00, daarna buien", Logic.summary(f, Settings(), t0))
    }

    @Test fun summaryAllDry() {
        assertEquals("Droog de komende 12 uur", Logic.summary(forecast(List(14) { 0.0 }), Settings(), t0))
    }

    @Test fun adviceRainColdWind() {
        val f = forecast(listOf(0.0, 0.5) + List(10) { 0.0 }, temp = 4.0, wind = 45.0)
        val kinds = Logic.advice(f, Settings(), t0).map { it.kind }
        assertTrue(AdviceKind.RAIN in kinds)
        assertTrue(AdviceKind.COLD in kinds)
        assertTrue(AdviceKind.WIND in kinds)
    }

    @Test fun rideAndBestDeparture() {
        // 6 mm in het uur 12:00-13:00, daarna droog
        val f = forecast(listOf(6.0) + List(10) { 0.0 })
        val now = Rain.ride(f, t0, 20)
        assertEquals(2.0, now, 0.01)
        val (best, mm) = Rain.best(f, t0, 20)
        assertEquals(0.0, mm, 0.01)
        assertEquals(LocalDateTime.of(2026, 11, 23, 13, 0), best)
    }

    @Test fun dryWindows() {
        val f = forecast(listOf(1.0, 0.0, 0.0, 0.0, 1.0, 0.0) + List(20) { 1.0 })
        val w = Rain.dryWindows(f, t0, Settings())
        assertEquals(1, w.size)
        assertEquals(LocalDateTime.of(2026, 11, 23, 13, 0), w[0].first)
        assertEquals(LocalDateTime.of(2026, 11, 23, 16, 0), w[0].second)
    }

    @Test fun weekLine() {
        fun day(p: Double, prob: Int, code: Int) = Day(LocalDate.of(2026, 11, 24), code, 9.0, 3.0, p, prob, 20.0, 35.0, 240, 1.0, null, null)
        assertEquals("Droog", Week.line(day(0.0, 5, 3), Settings()))
        assertEquals("Grotendeels droog", Week.line(day(0.3, 20, 3), Settings()))
        assertEquals("Buien, 4,2 mm (80%)", Week.line(day(4.2, 80, 81), Settings()))
    }
}
