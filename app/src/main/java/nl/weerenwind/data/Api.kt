package nl.weerenwind.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Locale

object Api {
    private const val HOURLY =
        "temperature_2m,apparent_temperature,precipitation,precipitation_probability,weather_code," +
            "wind_speed_10m,wind_direction_10m,wind_gusts_10m,uv_index,is_day"
    private const val CURRENT =
        "temperature_2m,apparent_temperature,precipitation,weather_code," +
            "wind_speed_10m,wind_direction_10m,wind_gusts_10m,is_day"

    private const val DAILY =
        "weather_code,temperature_2m_max,temperature_2m_min,precipitation_sum,precipitation_probability_max," +
            "wind_speed_10m_max,wind_gusts_10m_max,wind_direction_10m_dominant,uv_index_max,sunrise,sunset"

    /** Alle plekken in één verzoek. Alleen coördinaten gaan mee, geen namen. */
    fun forecast(ctx: Context, places: List<Place>): String {
        val lat = places.joinToString(",") { String.format(Locale.US, "%.3f", it.lat) }
        val lon = places.joinToString(",") { String.format(Locale.US, "%.3f", it.lon) }
        val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
            "&current=$CURRENT&hourly=$HOURLY&minutely_15=precipitation&daily=$DAILY" +
            "&past_hours=2&forecast_hours=50&forecast_minutely_15=16&forecast_days=7" +
            "&timezone=auto&wind_speed_unit=kmh"
        return Net.get(ctx, url, "weerverwachting (${places.size} plek${if (places.size == 1) "" else "ken"})")
    }

    private fun enc(q: String) = URLEncoder.encode(q.trim(), "UTF-8")

    /** Zoeken in Nederland via PDOK Locatieserver (overheid). */
    fun searchNL(ctx: Context, q: String): List<Place> {
        val fq = enc("type:(woonplaats OR gemeente OR wijk OR buurt OR postcode)")
        val url = "https://api.pdok.nl/bzk/locatieserver/search/v3_1/free?q=${enc(q)}&fq=$fq" +
            "&fl=id,weergavenaam,type,centroide_ll&rows=8"
        val docs = JSONObject(Net.get(ctx, url, "plek zoeken (NL)"))
            .optJSONObject("response")?.optJSONArray("docs") ?: return emptyList()
        val point = Regex("""POINT\(([-\d.]+) ([-\d.]+)\)""")
        return (0 until docs.length()).mapNotNull { i ->
            val d = docs.getJSONObject(i)
            val m = point.find(d.optString("centroide_ll")) ?: return@mapNotNull null
            val full = d.optString("weergavenaam")
            val type = d.optString("type")
            Place(
                id = "pdok:" + d.optString("id"),
                name = full.substringBefore(","),
                detail = (typeLabel(type) + " · " + full.substringAfter(",", "").trim()).trimEnd(' ', '·'),
                lat = m.groupValues[2].toDouble(),
                lon = m.groupValues[1].toDouble(),
                source = "PDOK",
            )
        }
    }

    private fun typeLabel(t: String) = when (t) {
        "woonplaats" -> "Plaats"
        "gemeente" -> "Gemeente"
        "wijk" -> "Wijk"
        "buurt" -> "Buurt"
        "postcode" -> "Postcode"
        else -> t
    }

    /** Zoeken in het buitenland via de Open-Meteo-geocoder. */
    fun searchWorld(ctx: Context, q: String): List<Place> {
        val url = "https://geocoding-api.open-meteo.com/v1/search?name=${enc(q)}&count=8&language=nl&format=json"
        val res = JSONObject(Net.get(ctx, url, "plek zoeken (buitenland)")).optJSONArray("results")
            ?: return emptyList()
        return (0 until res.length()).map { i ->
            val r = res.getJSONObject(i)
            Place(
                id = "om:" + r.optLong("id"),
                name = r.optString("name"),
                detail = listOf(r.optString("admin1"), r.optString("country"))
                    .filter { it.isNotBlank() }.joinToString(", "),
                lat = r.optDouble("latitude"),
                lon = r.optDouble("longitude"),
                source = r.optString("country_code"),
            )
        }
    }
}

object Parse {
    fun forecasts(raw: String, ids: List<String>): Map<String, Forecast> {
        val t = raw.trim()
        val objs = if (t.startsWith("[")) {
            val a = JSONArray(t)
            (0 until a.length()).map { a.getJSONObject(it) }
        } else listOf(JSONObject(t))
        return objs.zip(ids).associate { (o, id) -> id to one(o, id) }
    }

    private fun one(o: JSONObject, id: String): Forecast {
        val c = o.getJSONObject("current")
        val now = Now(
            time = time(c.getString("time")),
            temp = c.num("temperature_2m"),
            feels = c.num("apparent_temperature"),
            precip = c.num("precipitation"),
            code = c.optInt("weather_code"),
            wind = c.num("wind_speed_10m"),
            gust = c.num("wind_gusts_10m"),
            dir = c.optInt("wind_direction_10m"),
            isDay = c.optInt("is_day", 1) == 1,
        )
        val h = o.getJSONObject("hourly")
        val ht = h.getJSONArray("time")
        val hours = (0 until ht.length()).map { i ->
            Hour(
                time = time(ht.getString(i)),
                temp = h.at("temperature_2m", i),
                feels = h.at("apparent_temperature", i),
                precip = h.at("precipitation", i),
                prob = h.at("precipitation_probability", i).toInt(),
                code = h.at("weather_code", i).toInt(),
                wind = h.at("wind_speed_10m", i),
                gust = h.at("wind_gusts_10m", i),
                dir = h.at("wind_direction_10m", i).toInt(),
                uv = h.at("uv_index", i),
                isDay = h.at("is_day", i) >= 0.5,
            )
        }
        val m = o.optJSONObject("minutely_15")
        val quarters = if (m == null) emptyList() else {
            val mt = m.getJSONArray("time")
            (0 until mt.length()).map { i -> Quarter(time(mt.getString(i)), m.at("precipitation", i)) }
        }
        val d = o.optJSONObject("daily")
        val days = if (d == null) emptyList() else {
            val dt = d.getJSONArray("time")
            (0 until dt.length()).map { i ->
                Day(
                    date = LocalDate.parse(dt.getString(i)),
                    code = d.at("weather_code", i).toInt(),
                    tMax = d.at("temperature_2m_max", i),
                    tMin = d.at("temperature_2m_min", i),
                    precip = d.at("precipitation_sum", i),
                    probMax = d.at("precipitation_probability_max", i).toInt(),
                    windMax = d.at("wind_speed_10m_max", i),
                    gustMax = d.at("wind_gusts_10m_max", i),
                    dir = d.at("wind_direction_10m_dominant", i).toInt(),
                    uvMax = d.at("uv_index_max", i),
                    sunrise = d.optJSONArray("sunrise")?.optString(i)?.takeIf { it.length >= 16 }?.let { time(it) },
                    sunset = d.optJSONArray("sunset")?.optString(i)?.takeIf { it.length >= 16 }?.let { time(it) },
                )
            }
        }
        return Forecast(id, o.optInt("utc_offset_seconds"), now, hours, quarters, days)
    }

    private fun time(s: String) = LocalDateTime.parse(s)

    private fun JSONObject.num(k: String): Double = optDouble(k).let { if (it.isNaN()) 0.0 else it }

    private fun JSONObject.at(k: String, i: Int): Double {
        val a = optJSONArray(k) ?: return 0.0
        val v = a.optDouble(i)
        return if (v.isNaN()) 0.0 else v
    }
}
