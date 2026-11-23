package nl.weerenwind.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Alle opslag staat in de privémap van de app. Geen cloudback-up (allowBackup=false). */
object Repo {
    private const val CACHE = "forecast.json"
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
            .apply()
    }

    fun loadPlaces(ctx: Context): List<Place> = try {
        val a = JSONArray(prefs(ctx).getString("places", "[]"))
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            Place(o.getString("id"), o.getString("name"), o.optString("detail"),
                o.getDouble("lat"), o.getDouble("lon"), o.optString("source"))
        }
    } catch (_: Exception) {
        emptyList()
    }

    fun savePlaces(ctx: Context, places: List<Place>) {
        val a = JSONArray()
        places.forEach {
            a.put(JSONObject().put("id", it.id).put("name", it.name).put("detail", it.detail)
                .put("lat", it.lat).put("lon", it.lon).put("source", it.source))
        }
        prefs(ctx).edit().putString("places", a.toString()).apply()
    }

    fun slot(ctx: Context, key: String): String? = prefs(ctx).getString("slot$key", null)

    fun setSlot(ctx: Context, key: String, id: String?) {
        prefs(ctx).edit().putString("slot$key", id).apply()
    }

    fun loadBundle(ctx: Context): Bundle? = try {
        val f = File(ctx.filesDir, CACHE)
        if (!f.exists()) null else {
            val o = JSONObject(f.readText())
            val idsJson = o.getJSONArray("ids")
            val ids = (0 until idsJson.length()).map { idsJson.getString(it) }
            Bundle(o.getLong("fetchedAt"), Parse.forecasts(o.getString("raw"), ids))
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
        val forecasts = Parse.forecasts(raw, ids)
        val now = System.currentTimeMillis()
        File(ctx.filesDir, CACHE).writeText(
            JSONObject().put("fetchedAt", now).put("ids", JSONArray(ids)).put("raw", raw).toString()
        )
        return Bundle(now, forecasts)
    }
}
