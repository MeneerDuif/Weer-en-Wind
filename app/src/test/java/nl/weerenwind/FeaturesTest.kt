package nl.weerenwind

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import nl.weerenwind.data.Feeds
import nl.weerenwind.data.Place
import nl.weerenwind.data.RadarPoint
import nl.weerenwind.logic.Alerts
import nl.weerenwind.logic.RadarLogic
import nl.weerenwind.ui.Themes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.OffsetDateTime
import kotlin.math.max
import kotlin.math.min

class FeaturesTest {
    private val now = LocalDateTime.of(2026, 11, 24, 18, 32)

    private fun radar(vararg mmh: Double, start: LocalDateTime = LocalDateTime.of(2026, 11, 24, 18, 30)) =
        mmh.mapIndexed { i, v -> RadarPoint(start.plusMinutes(5L * i), v) }

    @Test fun parseRadarText() {
        val pts = Feeds.radar("000|18:30\n109|18:35\n141|18:40\n\nrommel\n000|18:45\n", now)
        assertEquals(4, pts.size)
        assertEquals(0.0, pts[0].mmh, 0.0)
        assertEquals(1.0, pts[1].mmh, 0.01)   // 10^0
        assertEquals(10.0, pts[2].mmh, 0.01)  // 10^1
        assertEquals(LocalDateTime.of(2026, 11, 24, 18, 40), pts[2].time)
    }

    @Test fun radarAcrossMidnight() {
        val late = LocalDateTime.of(2026, 11, 24, 23, 55)
        val pts = Feeds.radar("000|23:55\n000|00:00\n000|00:05", late)
        assertEquals(LocalDateTime.of(2026, 11, 25, 0, 5), pts[2].time)
    }

    @Test fun radarSummaryAndAlert() {
        val pts = radar(0.0, 0.0, 0.0, 0.8, 2.5, 0.6, 0.0, 0.0)
        assertEquals("Droog tot 18:45, daarna regen", RadarLogic.summary(pts, now))
        val a = Alerts.rain(pts, now, 30)
        assertNotNull(a)
        assertEquals(13, a!!.minutes)
        assertEquals("Regen over 13 min, droog om 19:00", a.text)
        assertNull("te ver weg", Alerts.rain(pts, now, 10))
        assertNull("regent al", Alerts.rain(radar(1.0, 1.0, 0.0), now, 30))
    }

    @Test fun parseWarnings() {
        val xml = """<?xml version="1.0" encoding="UTF-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom" xmlns:cap="urn:oasis:names:tc:emergency:cap:1.2">
              <entry><id>a1</id><title>Orange wind</title><cap:identifier>w-1</cap:identifier>
                <cap:areaDesc>Noord-Holland</cap:areaDesc><cap:event>Zware windstoten</cap:event>
                <cap:awareness_level>3; orange; Severe</cap:awareness_level>
                <cap:onset>2026-11-24T18:00:00+01:00</cap:onset><cap:expires>2099-01-01T00:00:00+01:00</cap:expires></entry>
              <entry><cap:identifier>w-2</cap:identifier><cap:areaDesc>Utrecht</cap:areaDesc>
                <cap:severity>Minor</cap:severity><cap:expires>2099-01-01T00:00:00+01:00</cap:expires></entry>
              <entry><cap:identifier>w-3</cap:identifier><cap:areaDesc>Zeeland</cap:areaDesc>
                <cap:severity>Moderate</cap:severity><cap:expires>2020-01-01T00:00:00+01:00</cap:expires></entry>
            </feed>"""
        val w = Feeds.warnings(xml, OffsetDateTime.parse("2026-11-24T18:30:00+01:00"))
        assertEquals(1, w.size)
        assertEquals(2, w[0].level)
        assertEquals("Zware windstoten", w[0].event)
        val home = Place("p", "Haarlem", "", 52.38, 4.64, "PDOK", "Noord-Holland")
        assertTrue(w[0].appliesTo(home))
        assertTrue(!w[0].appliesTo(home.copy(province = "")))
    }

    /** WCAG AA: alle tekstkleuren minstens 4,5:1 tegen achtergrond én oppervlak, in elk thema. */
    @Test fun themesMeetWcagAA() {
        fun ratio(a: Color, b: Color): Double {
            val la = a.luminance() + 0.05; val lb = b.luminance() + 0.05
            return (max(la, lb) / min(la, lb)).toDouble()
        }
        for (t in Themes.all) {
            val roles = mapOf("text" to t.text, "muted" to t.muted, "faint" to t.faint, "rain" to t.rain,
                "warm" to t.warm, "cold" to t.cold, "wind" to t.wind, "warn" to t.warn, "sun" to t.sun)
            for ((name, col) in roles) for (bg in listOf(t.bg, t.surface)) {
                val r = ratio(col, bg)
                assertTrue("${t.id}.$name = ${"%.2f".format(r)}:1", r >= 4.5)
            }
        }
    }
}
