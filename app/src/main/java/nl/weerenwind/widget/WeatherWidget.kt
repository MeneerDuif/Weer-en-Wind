package nl.weerenwind.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import nl.weerenwind.MainActivity
import nl.weerenwind.R
import nl.weerenwind.data.Forecast
import nl.weerenwind.data.Repo
import nl.weerenwind.logic.Codes
import nl.weerenwind.logic.Logic
import nl.weerenwind.logic.Units
import kotlin.concurrent.thread

/** 2×1-widget: twee plekken naast elkaar, of (met één plek) nu → over 3 uur. Ververst hooguit elke 2 uur. */
class WeatherWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        render(context, mgr, ids)
        val b = Repo.loadBundle(context)
        val stale = b == null || System.currentTimeMillis() - b.fetchedAt > 2 * 3600 * 1000
        if (stale && Repo.loadPlaces(context).isNotEmpty()) {
            val pending = goAsync()
            thread {
                try {
                    Repo.fetch(context)
                    render(context, mgr, ids)
                } catch (_: Exception) {
                } finally {
                    pending.finish()
                }
            }
        }
    }

    companion object {
        fun updateAll(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, WeatherWidget::class.java))
            if (ids.isNotEmpty()) render(ctx, mgr, ids)
        }

        fun render(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
            val v = RemoteViews(ctx.packageName, R.layout.widget_two)
            val places = Repo.loadPlaces(ctx)
            val bundle = Repo.loadBundle(ctx)
            val s = Repo.loadSettings(ctx)
            val a = places.firstOrNull { it.id == Repo.slot(ctx, "A") }
            val b = places.firstOrNull { it.id == Repo.slot(ctx, "B") }
            val fa = a?.let { bundle?.forecasts?.get(it.id) }
            val fb = b?.let { bundle?.forecasts?.get(it.id) }

            if (a == null) {
                v.setTextViewText(R.id.a_name, "ARTYWEATHER")
                v.setTextViewText(R.id.a_temp, "–")
                v.setTextViewText(R.id.a_line, "Voeg een plek toe")
                v.setTextViewText(R.id.b_name, "")
                v.setTextViewText(R.id.b_temp, "")
                v.setTextViewText(R.id.b_line, "")
            } else {
                fill(v, R.id.a_name, R.id.a_temp, R.id.a_line, a.name, fa, s)
                if (b != null) {
                    fill(v, R.id.b_name, R.id.b_temp, R.id.b_line, b.name, fb, s)
                } else {
                    // Eén plek: nu → straks (+3 uur)
                    val later = fa?.hourAt(fa.localNow().plusHours(3))
                    v.setTextViewText(R.id.b_name, "OVER 3 UUR")
                    v.setTextViewText(R.id.b_temp, later?.let { "${Codes.glyph(it.code, it.isDay)} ${Units.temp(it.temp)}" } ?: "–")
                    v.setTextViewText(R.id.b_line, later?.let { "${Units.mm(it.precip)} mm · ${it.prob}%" } ?: "")
                }
            }

            val intent = Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val pi = PendingIntent.getActivity(ctx, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            v.setOnClickPendingIntent(R.id.root, pi)
            mgr.updateAppWidget(ids, v)
        }

        private fun fill(v: RemoteViews, nameId: Int, tempId: Int, lineId: Int, name: String, f: Forecast?, s: nl.weerenwind.data.Settings) {
            v.setTextViewText(nameId, name.uppercase())
            if (f == null) {
                v.setTextViewText(tempId, "–")
                v.setTextViewText(lineId, "")
                return
            }
            v.setTextViewText(tempId, "${Codes.glyph(f.current.code, f.current.isDay)} ${Units.temp(f.current.temp)}")
            v.setTextViewText(lineId, Logic.summary(f, s))
        }
    }
}
