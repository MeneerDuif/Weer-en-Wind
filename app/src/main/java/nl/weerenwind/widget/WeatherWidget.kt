package nl.weerenwind.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Bundle
import android.text.TextPaint
import android.text.TextUtils
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.res.ResourcesCompat
import nl.weerenwind.MainActivity
import nl.weerenwind.R
import nl.weerenwind.data.Forecast
import nl.weerenwind.data.Place
import nl.weerenwind.data.Repo
import nl.weerenwind.data.Settings
import nl.weerenwind.data.Warning
import nl.weerenwind.logic.Alerts
import nl.weerenwind.logic.Codes
import nl.weerenwind.logic.Logic
import nl.weerenwind.logic.Units
import nl.weerenwind.ui.Themes
import nl.weerenwind.ui.WeerColors
import nl.weerenwind.ui.moodColor
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.concurrent.thread
import kotlin.math.max
import kotlin.math.min

/** Klein (2×1): twee plekken, of nu → over 3 uur. */
class WeatherWidget : BaseWeatherWidget() {
    companion object {
        /** Ververst alle widgets van beide maten. */
        fun updateAll(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            listOf(WeatherWidget::class.java, WeatherWidgetLarge::class.java).forEach { cls ->
                mgr.getAppWidgetIds(ComponentName(ctx, cls)).forEach { WidgetRenderer.push(ctx, mgr, it) }
            }
        }
    }
}

/** Groot (4×2): plekken bovenaan, temperatuurcurve en regen voor 12 uur, waarschuwing als die er is. */
class WeatherWidgetLarge : BaseWeatherWidget()

open class BaseWeatherWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        ids.forEach { WidgetRenderer.push(context, mgr, it) }
        val b = Repo.loadBundle(context)
        val stale = b == null || System.currentTimeMillis() - b.fetchedAt > 2 * 3600 * 1000
        if (stale && Repo.loadPlaces(context).isNotEmpty()) {
            val pending = goAsync()
            thread {
                try {
                    Repo.fetch(context)
                    ids.forEach { WidgetRenderer.push(context, mgr, it) }
                } catch (_: Exception) {
                } finally {
                    pending.finish()
                }
            }
        }
    }

    override fun onAppWidgetOptionsChanged(context: Context, mgr: AppWidgetManager, id: Int, newOptions: Bundle) {
        WidgetRenderer.push(context, mgr, id)
    }
}

object WidgetRenderer {
    fun push(ctx: Context, mgr: AppWidgetManager, id: Int) {
        val o = mgr.getAppWidgetOptions(id)
        val wDp = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0).takeIf { it > 0 } ?: 180
        val hDp = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0).takeIf { it > 0 } ?: 70
        val (bmp, desc) = try {
            render(ctx, wDp, hDp)
        } catch (_: Exception) {
            return
        }
        val v = RemoteViews(ctx.packageName, R.layout.widget_image)
        v.setImageViewBitmap(R.id.img, bmp)
        v.setContentDescription(R.id.img, desc)
        val intent = Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pi = PendingIntent.getActivity(ctx, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        v.setOnClickPendingIntent(R.id.root, pi)
        mgr.updateAppWidget(id, v)
    }

    private class Fonts(ctx: Context) {
        val serif: Typeface = ResourcesCompat.getFont(ctx, R.font.instrument_serif) ?: Typeface.SERIF
        val italic: Typeface = ResourcesCompat.getFont(ctx, R.font.instrument_serif_italic) ?: Typeface.SERIF
        val mono: Typeface = ResourcesCompat.getFont(ctx, R.font.plex_mono) ?: Typeface.MONOSPACE
        val monoMed: Typeface = ResourcesCompat.getFont(ctx, R.font.plex_mono_medium) ?: Typeface.MONOSPACE
    }

    /** Tekent de widget als afbeelding en geeft ook een beschrijving voor schermlezers terug. */
    fun render(ctx: Context, wDp: Int, hDp: Int): Pair<Bitmap, String> {
        val d = ctx.resources.displayMetrics.density
        val u = min(d, min(1400f / wDp, 900f / hDp)) // 1 dp in pixels, begrensd i.v.m. widgetgeheugen
        val w = (wDp * u).toInt().coerceAtLeast(10)
        val h = (hDp * u).toInt().coerceAtLeast(10)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)

        val s = Repo.loadSettings(ctx)
        val dark = (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val c = Themes.byId(s.theme, dark)
        val f = Fonts(ctx)
        val places = Repo.slotPlaces(ctx)
        val bundle = Repo.loadBundle(ctx)
        val a = places.getOrNull(0)
        val b = places.getOrNull(1)
        val fa = a?.let { bundle?.forecasts?.get(it.id) }
        val fb = b?.let { bundle?.forecasts?.get(it.id) }
        val stale = bundle?.isStale() ?: false

        background(cv, w, h, u, c, moodColor(fa, c).toArgb())
        val pad = 14 * u
        val desc = StringBuilder("artyweather. ")

        if (a == null) {
            text(cv, "ARTYWEATHER", pad, pad + 10 * u, mono(f.monoMed, 9 * u, c.muted.toArgb()))
            text(cv, "Voeg een plek toe.", pad, h / 2f + 10 * u, paint(f.italic, 20 * u, c.text.toArgb()))
            return bmp to "artyweather: nog geen plekken. Tik om de app te openen."
        }

        val large = hDp >= 100 && wDp >= 200
        val colW = if (b != null || !large) (w - 2 * pad - 20 * u) / 2 else w - 2 * pad
        val tempSize = if (large) 46 * u else min(34 * u, h * 0.42f)
        val top = pad + 8 * u

        column(cv, f, c, a, fa, s, stale, pad, top, colW, tempSize, u, desc)
        val x2 = pad + colW + 20 * u
        if (b != null) {
            line(cv, x2 - 10 * u, top - 4 * u, x2 - 10 * u, top + tempSize + 22 * u, c.border.toArgb(), u)
            column(cv, f, c, b, fb, s, stale, x2, top, colW, tempSize, u, desc)
        } else if (!large && fa != null) {
            // Eén plek in de kleine widget: nu → over 3 uur
            val later = fa.hourAt(fa.localNow().plusHours(3))
            text(cv, "OVER 3 UUR", x2, top, mono(f.monoMed, 9 * u, c.muted.toArgb()))
            if (later != null) {
                text(cv, Units.temp(later.temp), x2, top + tempSize * 0.95f, paint(f.serif, tempSize, c.text.toArgb()))
                text(cv, "${Units.mm(later.precip)} mm · ${later.prob}%", x2, top + tempSize + 12 * u,
                    mono(f.mono, 9 * u, (if (later.prob >= s.rainProb) c.rain else c.muted).toArgb()))
                desc.append("Over 3 uur ${Units.temp(later.temp)}, ${later.prob} procent kans op neerslag. ")
            }
        }

        if (large && fa != null) {
            val voiceY = top + tempSize + 34 * u
            if (b == null) {
                val p = paint(f.italic, 17 * u, c.text.toArgb())
                text(cv, ellipsize(Logic.summary(fa, s) + ".", p, w - 2 * pad), pad, voiceY, p)
            }
            val warn = bundle?.warnings?.filter { it.appliesTo(a) || (b != null && it.appliesTo(b)) }?.maxByOrNull { it.level }
            val curveTop = voiceY + (if (b == null) 10 * u else -8 * u)
            val bottom = h - pad - (if (warn != null) 16 * u else 4 * u)
            if (bottom - curveTop > 40 * u) curve(cv, f, c, fa, pad, curveTop, w - 2 * pad, bottom - curveTop, u)
            if (warn != null) {
                warningStrip(cv, f, c, warn, pad, h - pad, w - 2 * pad, u)
                desc.append("${Alerts.codeName(warn.level)}: ${warn.event}, ${warn.area}. ")
            }
        }

        bundle?.let {
            val age = SimpleDateFormat("HH:mm", Units.NL).format(Date(it.fetchedAt))
            val label = if (stale) "VEROUDERD · $age" else age
            val p = mono(f.mono, 8 * u, (if (stale) c.warn else c.faint).toArgb())
            val tw = p.measureText(label)
            if (large || stale) text(cv, label, w - pad - tw, if (large) pad + 6 * u else h - 6 * u, p)
            if (stale) desc.append("Data is verouderd, van $age. ")
        }
        return bmp to desc.toString().trim()
    }

    private fun column(
        cv: Canvas, f: Fonts, c: WeerColors, p: Place, fc: Forecast?, s: Settings, stale: Boolean,
        x: Float, top: Float, w: Float, tempSize: Float, u: Float, desc: StringBuilder,
    ) {
        val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = moodColor(fc, c).toArgb() }
        cv.drawCircle(x + 3 * u, top - 3 * u, 3 * u, dot)
        val np = mono(f.monoMed, 9 * u, c.text.toArgb())
        text(cv, ellipsize(p.name.uppercase(), np, w - 12 * u), x + 11 * u, top, np)
        if (fc == null) {
            text(cv, "–", x, top + tempSize, paint(f.serif, tempSize, c.muted.toArgb()))
            return
        }
        val now = fc.nowEstimate(stale)
        text(cv, Units.temp(now.temp), x, top + tempSize * 0.98f, paint(f.serif, tempSize, c.text.toArgb()))
        val sp = paint(f.italic, max(12 * u, tempSize * 0.3f), c.muted.toArgb())
        text(cv, ellipsize(Logic.summary(fc, s), sp, w), x, top + tempSize + sp.textSize * 1.05f, sp)
        desc.append("${p.name}: ${Units.temp(now.temp)}, ${Codes.text(now.code).lowercase()}. ${Logic.summary(fc, s)}. ")
    }

    private fun background(cv: Canvas, w: Int, h: Int, u: Float, c: WeerColors, mood: Int) {
        val r = 20 * u
        val rect = RectF(0f, 0f, w.toFloat(), h.toFloat())
        cv.save()
        cv.clipPath(Path().apply { addRoundRect(rect, r, r, Path.Direction.CW) })
        cv.drawColor(c.bg.toArgb())
        val glowA = if (c.isLight) 0.10f else 0.18f
        cv.drawRect(rect, Paint().apply {
            shader = RadialGradient(w * 0.08f, 0f, max(w, h) * 0.9f,
                intArrayOf(alpha(mood, glowA), alpha(mood, glowA * 0.3f), 0), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
        })
        val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = alpha(c.faint.toArgb(), if (c.isLight) 0.30f else 0.20f) }
        val step = 18 * u
        var y = step / 2
        while (y < h) {
            var x = step / 2
            while (x < w) { cv.drawCircle(x, y, 0.7f * u, dot); x += step }
            y += step
        }
        cv.restore()
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = u; color = c.border.toArgb() }
        cv.drawRoundRect(RectF(u / 2, u / 2, w - u / 2, h - u / 2), r, r, border)
    }

    private fun curve(cv: Canvas, f: Fonts, c: WeerColors, fc: Forecast, x: Float, y: Float, w: Float, h: Float, u: Float) {
        val hrs = fc.upcoming(fc.localNow(), 12)
        if (hrs.size < 2) return
        cv.drawRect(x, y, x + w, y + u, Paint().apply { color = c.border.toArgb() })
        cv.drawRect(x, y, x + 18 * u, y + u, Paint().apply { color = c.text.toArgb() })
        val lbl = mono(f.mono, 8 * u, c.faint.toArgb())
        val tlbl = mono(f.mono, 8.5f * u, c.text.toArgb())
        val top = y + 18 * u
        val base = y + h - 12 * u
        val curveH = (base - top) * 0.5f
        val tMin = hrs.minOf { it.temp }
        val tMax = max(hrs.maxOf { it.temp }, tMin + 2)
        val padX = 8 * u
        val step = (w - 2 * padX) / (hrs.size - 1)
        fun px(i: Int) = x + padX + i * step
        fun py(t: Double) = top + curveH - ((t - tMin) / (tMax - tMin)).toFloat() * curveH
        val path = Path()
        hrs.forEachIndexed { i, hr ->
            if (i == 0) path.moveTo(px(0), py(hr.temp)) else {
                val mx = (px(i - 1) + px(i)) / 2
                path.cubicTo(mx, py(hrs[i - 1].temp), mx, py(hr.temp), px(i), py(hr.temp))
            }
        }
        cv.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 1.4f * u; color = c.text.toArgb(); strokeCap = Paint.Cap.ROUND
        })
        val bw = step * 0.42f
        val maxBar = (base - top) * 0.38f
        val dotP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = c.text.toArgb() }
        hrs.forEachIndexed { i, hr ->
            val cx = px(i)
            cv.drawCircle(cx, py(hr.temp), if (i % 3 == 0) 2f * u else 1f * u, dotP)
            if (i % 3 == 0) {
                val t = Units.temp(hr.temp)
                text(cv, t, cx - tlbl.measureText(t) / 2, py(hr.temp) - 5 * u, tlbl)
                val hh = "%02d".format(hr.time.minusHours(1).hour)
                text(cv, hh, cx - lbl.measureText(hh) / 2, y + h, lbl)
            }
            cv.drawRect(cx - bw / 2, base - u, cx + bw / 2, base, Paint().apply { color = c.faint.toArgb() })
            if (hr.precip >= 0.05) {
                val bh = (hr.precip / 3.0).coerceIn(0.1, 1.0).toFloat() * maxBar
                val al = (0.35f + 0.65f * hr.prob / 100f).coerceIn(0.35f, 1f)
                cv.drawRect(cx - bw / 2, base - bh, cx + bw / 2, base, Paint().apply { color = alpha(c.rain.toArgb(), al) })
            }
        }
    }

    private fun warningStrip(cv: Canvas, f: Fonts, c: WeerColors, wn: Warning, x: Float, yBottom: Float, w: Float, u: Float) {
        val col = when (wn.level) { 3 -> c.warn; 2 -> c.warm; else -> c.sun }.toArgb()
        cv.drawRect(x, yBottom - 10 * u, x + 2 * u, yBottom, Paint().apply { color = col })
        val p = mono(f.monoMed, 8.5f * u, col)
        text(cv, ellipsize("${Alerts.codeName(wn.level).uppercase()} · ${wn.event.uppercase()} · ${wn.area}", p, w - 8 * u),
            x + 7 * u, yBottom - 1 * u, p)
    }

    private fun paint(tf: Typeface, size: Float, color: Int) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = tf; textSize = size; this.color = color
    }

    private fun mono(tf: Typeface, size: Float, color: Int) = paint(tf, size, color).apply { letterSpacing = 0.12f }

    private fun text(cv: Canvas, s: String, x: Float, baseline: Float, p: Paint) = cv.drawText(s, x, baseline, p)

    private fun line(cv: Canvas, x0: Float, y0: Float, x1: Float, y1: Float, color: Int, u: Float) =
        cv.drawLine(x0, y0, x1, y1, Paint().apply { this.color = color; strokeWidth = u })

    private fun ellipsize(s: String, p: TextPaint, w: Float): String =
        TextUtils.ellipsize(s, p, max(w, 1f), TextUtils.TruncateAt.END).toString()

    private fun alpha(color: Int, a: Float): Int = (color and 0x00FFFFFF) or ((a.coerceIn(0f, 1f) * 255).toInt() shl 24)
}
