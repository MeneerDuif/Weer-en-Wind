package nl.weerenwind.data

import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.pow

/** Parsers voor de radar- en waarschuwingsfeeds. Alleen JDK, zodat ze ook in unit tests draaien. */
object Feeds {

    /**
     * Buienradar "raintext": regels als `077|18:35`, elke 5 minuten, 2 uur vooruit.
     * Waarde 0–255 → mm/u = 10^((waarde − 109) / 32). Tijden zijn Nederlandse lokale tijd;
     * de datum kiezen we zo dat het tijdstip het dichtst bij [now] ligt (rond middernacht).
     */
    fun radar(text: String, now: LocalDateTime): List<RadarPoint> =
        text.lineSequence().mapNotNull { line ->
            val parts = line.trim().split('|')
            if (parts.size < 2) return@mapNotNull null
            val v = parts[0].trim().toIntOrNull() ?: return@mapNotNull null
            val t = runCatching { LocalTime.parse(parts[1].trim()) }.getOrNull() ?: return@mapNotNull null
            var dt = now.toLocalDate().atTime(t)
            if (dt.isBefore(now.minusHours(12))) dt = dt.plusDays(1)
            if (dt.isAfter(now.plusHours(12))) dt = dt.minusDays(1)
            val mmh = if (v <= 0) 0.0 else 10.0.pow((v - 109) / 32.0)
            RadarPoint(dt, if (mmh < 0.05) 0.0 else mmh)
        }.toList()

    /**
     * MeteoAlarm Atom-feed (KNMI-waarschuwingen per provincie). We lezen per `entry`:
     * areaDesc, event, awareness_level/severity, onset, expires en identifier.
     * Groen (geen waarschuwing) en verlopen meldingen vallen weg.
     */
    fun warnings(xml: String, now: OffsetDateTime = OffsetDateTime.now()): List<Warning> {
        val f = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            isExpandEntityReferences = false
        }
        val doc = f.newDocumentBuilder().parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))
        val entries = doc.getElementsByTagName("entry")
        val out = mutableListOf<Warning>()
        for (i in 0 until entries.length) {
            val e = entries.item(i) as? Element ?: continue
            val m = mutableMapOf<String, String>()
            val kids = e.childNodes
            for (j in 0 until kids.length) {
                val k = kids.item(j) as? Element ?: continue
                val key = k.nodeName.substringAfter(':')
                if (key !in m) m[key] = k.textContent.trim()
            }
            val level = level(m["awareness_level"], m["severity"])
            if (level < 1) continue
            val expires = time(m["expires"])
            if (expires != null && expires.isBefore(now)) continue
            val area = m["areaDesc"] ?: continue
            val event = (m["event"] ?: m["title"] ?: "Waarschuwing").ifBlank { "Waarschuwing" }
            val id = m["identifier"] ?: m["id"] ?: "$area|$event|${m["onset"]}"
            out += Warning(id, area, event, level, time(m["onset"]), expires)
        }
        return out.distinctBy { it.id + it.area }
    }

    /** "2; yellow; Moderate" → 1, "3; orange; Severe" → 2, "4; red; Extreme" → 3. */
    private fun level(awareness: String?, severity: String?): Int {
        awareness?.trim()?.takeWhile { it.isDigit() }?.toIntOrNull()?.let { return (it - 1).coerceIn(0, 3) }
        return when (severity?.trim()?.lowercase()) {
            "moderate" -> 1
            "severe" -> 2
            "extreme" -> 3
            else -> 0
        }
    }

    private fun time(s: String?): OffsetDateTime? = s?.let { runCatching { OffsetDateTime.parse(it.trim()) }.getOrNull() }
}
