package nl.weerenwind.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId

/** Alle opslag staat in de privémap van de app. Geen cloudback-up (allowBackup=false). */
object Repo {
    private const val CACHE = "forecast.json"
    private const val EXTRAS = "extras.json"
    const val MAX_PLACES = 10

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("weer", Context.MODE_PRIVATE)

    fun loadSettings(ctx: Context): Settings {
        val p = prefs(ctx)
        val d = Settings()
        return Settings(
            theme = p.getString("theme", d.theme) ?: d.theme,
            startTab = p.getInt("startTab", d.startTab),
            showStatusBar = p.getBoolean("showStatusBar", d.showStatusBar),
            showNavBar = p.getBoolean("showNavBar", d.showNavBar),
            windUnit = p.getString("windUnit", d.windUnit) ?: d.windUnit,
            coldFeels = p.getInt("coldFeels", d.coldFeels),
            rainProb = p.getInt("rainProb", d.rainProb),
            rainMm = p.getFloat("rainMm", d.rainMm.toFloat()).toDouble(),
            windBft = p.getInt("windBft", d.windBft),
            uvMin = p.getInt("uvMin", d.uvMin),
            rideMinutes = p.getInt("rideMinutes", d.rideMinutes),
            hours48 = p.getBoolean("hours48", d.hours48),
            rainAlerts = p.getBoolean("rainAlerts", d.rainAlerts),
            warnAlerts = p.getBoolean("warnAlerts", d.warnAlerts),
            alertLeadMin = p.getInt("alertLeadMin", d.alertLeadMin),
        )
    }

    fun saveSettings(ctx: Context, s: Settings) {
        prefs(ctx).edit()
            .putString("theme", s.theme)
            .putInt("startTab", s.startTab)
            .putBoolean("showStatusBar", s.showStatusBar)
            .putBoolean("showNavBar", s.showNavBar)
            .putString("windUnit", s.windUnit)
            .putInt("coldFeels", s.coldFeels)
            .putInt("rainProb", s.rainProb)
            .putFloat("rainMm", s.rainMm.toFloat())
            .putInt("windBft", s.windBft)
            .putInt("uvMin", s.uvMin)
            .putInt("rideMinutes", s.rideMinutes)
            .putBoolean("hours48", s.hours48)
            .putBoolean("rainAlerts", s.rainAlerts)
            .putBoolean("warnAlerts", s.warnAlerts)
            .putInt("alertLeadMin", s.alertLeadMin)
            .apply()
    }

    fun loadPlaces(ctx: Context): List<Place> = try {
        val a = JSONArray(prefs(ctx).getString("places", "[]"))
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            Place(o.getString("id"), o.getString("name"), o.optString("detail"),
                o.getDouble("lat"), o.getDouble("lon"), o.optString("source"), o.optString("province"))
        }
    } catch (_: Exception) {
        emptyList()
    }

    fun savePlaces(ctx: Context, places: List<Place>) {
        val a = JSONArray()
        places.forEach {
            a.put(JSONObject().put("id", it.id).put("name", it.name).put("detail", it.detail)
                .put("lat", it.lat).put("lon", it.lon).put("source", it.source).put("province", it.province))
        }
        prefs(ctx).edit().putString("places", a.toString()).apply()
    }

    fun slot(ctx: Context, key: String): String? = prefs(ctx).getString("slot$key", null)

    fun setSlot(ctx: Context, key: String, id: String?) {
        prefs(ctx).edit().putString("slot$key", id).apply()
    }

    val AMS: ZoneId = ZoneId.of("Europe/Amsterdam")

    fun slotPlaces(ctx: Context): List<Place> {
        val places = loadPlaces(ctx)
        return listOfNotNull(slot(ctx, "A"), slot(ctx, "B")).mapNotNull { id -> places.firstOrNull { it.id == id } }
    }

    fun loadBundle(ctx: Context): Bundle? = try {
        val f = File(ctx.filesDir, CACHE)
        if (!f.exists()) null else {
            val o = JSONObject(f.readText())
            val idsJson = o.getJSONArray("ids")
            val ids = (0 until idsJson.length()).map { idsJson.getString(it) }
            val x = loadExtras(ctx)
            val forecasts = Parse.forecasts(o.getString("raw"), ids)
                .mapValues { (id, fc) -> fc.copy(radar = x.radar[id].orEmpty()) }
            Bundle(o.getLong("fetchedAt"), forecasts, x.warnings, x.radarAt)
        }
    } catch (_: Exception) {
        null
    }

    /** Haalt de verwachting voor alle opgeslagen plekken op in één verzoek en bewaart die lokaal. */
    fun fetch(ctx: Context): Bundle? {
        val places = loadPlaces(ctx).take(MAX_PLACES)
        if (places.isEmpty()) return null
        val raw = Api.forecast(ctx, places)
        val ids = places.map { it.id }
        Parse.forecasts(raw, ids) // valideert vóór het overschrijven van de cache
        File(ctx.filesDir, CACHE).writeText(
            JSONObject().put("fetchedAt", System.currentTimeMillis()).put("ids", JSONArray(ids)).put("raw", raw).toString()
        )
        refreshExtras(ctx)
        return loadBundle(ctx)
    }

    // --- Radar en waarschuwingen ------------------------------------------------------------

    data class Extras(val radarAt: Long, val radar: Map<String, List<RadarPoint>>, val warnings: List<Warning>)

    private fun loadExtras(ctx: Context): Extras = try {
        val o = JSONObject(File(ctx.filesDir, EXTRAS).readText())
        val ro = o.optJSONObject("radar") ?: JSONObject()
        val radar = ro.keys().asSequence().associateWith { id ->
            val a = ro.getJSONArray(id)
            (0 until a.length()).map { i ->
                val p = a.getJSONArray(i)
                RadarPoint(LocalDateTime.parse(p.getString(0)), p.getDouble(1))
            }
        }
        val wa = o.optJSONArray("warnings") ?: JSONArray()
        val warnings = (0 until wa.length()).map { i ->
            val w = wa.getJSONObject(i)
            Warning(w.getString("id"), w.getString("area"), w.getString("event"), w.getInt("level"),
                w.optString("onset").takeIf { it.isNotBlank() }?.let { OffsetDateTime.parse(it) },
                w.optString("expires").takeIf { it.isNotBlank() }?.let { OffsetDateTime.parse(it) })
        }.filter { it.expires == null || it.expires.isAfter(OffsetDateTime.now()) }
        Extras(o.optLong("radarAt"), radar, warnings)
    } catch (_: Exception) {
        Extras(0, emptyMap(), emptyList())
    }

    private fun saveExtras(ctx: Context, x: Extras) {
        val ro = JSONObject()
        x.radar.forEach { (id, pts) ->
            ro.put(id, JSONArray().apply { pts.forEach { put(JSONArray().put(it.time.toString()).put(it.mmh)) } })
        }
        val wa = JSONArray()
        x.warnings.forEach {
            wa.put(JSONObject().put("id", it.id).put("area", it.area).put("event", it.event).put("level", it.level)
                .put("onset", it.onset?.toString() ?: "").put("expires", it.expires?.toString() ?: ""))
        }
        File(ctx.filesDir, EXTRAS).writeText(
            JSONObject().put("radarAt", x.radarAt).put("radar", ro).put("warnings", wa).toString()
        )
    }

    /**
     * Ververst radar (alleen plek A en B, binnen het radargebied) en waarschuwingen.
     * Elk onderdeel mag apart mislukken; dan blijft de vorige waarde staan.
     */
    fun refreshExtras(ctx: Context, radar: Boolean = true, warnings: Boolean = true): Extras {
        backfillProvinces(ctx)
        val old = loadExtras(ctx)
        val now = LocalDateTime.now(AMS)
        var radarMap = old.radar.filterValues { it.isNotEmpty() && it.last().time.isAfter(now) }
        var radarAt = old.radarAt
        if (radar) {
            val fresh = slotPlaces(ctx).filter { it.inRadarArea }.mapNotNull { p ->
                runCatching { p.id to Api.radar(ctx, p, now) }.getOrNull()
            }
            if (fresh.isNotEmpty()) {
                radarMap = radarMap + fresh
                radarAt = System.currentTimeMillis()
            }
        }
        var warns = old.warnings
        if (warnings && loadPlaces(ctx).any { it.province.isNotBlank() }) {
            runCatching { Api.warnings(ctx) }.onSuccess { warns = it }
        }
        val x = Extras(radarAt, radarMap, warns)
        saveExtras(ctx, x)
        return x
    }

    /** Plekken uit eerdere versies hebben nog geen provincie: eenmalig opzoeken via PDOK. */
    private fun backfillProvinces(ctx: Context) {
        val places = loadPlaces(ctx)
        val prefs = prefs(ctx)
        val todo = places.filter {
            it.province.isBlank() && it.id.startsWith("pdok:") && !prefs.getBoolean("prov_tried_${it.id}", false)
        }
        if (todo.isEmpty()) return
        val updated = places.map { p ->
            if (p !in todo) p else {
                prefs.edit().putBoolean("prov_tried_${p.id}", true).apply()
                runCatching { Api.province(ctx, p.lat, p.lon) }.getOrNull()
                    ?.takeIf { it.isNotBlank() }?.let { p.copy(province = it) } ?: p
            }
        }
        savePlaces(ctx, updated)
    }

    // --- Bijhouden welke meldingen al zijn verstuurd --------------------------------------------

    fun lastRainAlert(ctx: Context, id: String): Long = prefs(ctx).getLong("rainAlert_$id", 0)
    fun setLastRainAlert(ctx: Context, id: String, t: Long) = prefs(ctx).edit().putLong("rainAlert_$id", t).apply()
    fun notifiedWarnings(ctx: Context): Set<String> = prefs(ctx).getStringSet("warnNotified", emptySet()) ?: emptySet()
    fun setNotifiedWarnings(ctx: Context, ids: Set<String>) =
        prefs(ctx).edit().putStringSet("warnNotified", ids.toList().takeLast(200).toSet()).apply()
}
