package nl.weerenwind.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import nl.weerenwind.data.Repo
import nl.weerenwind.data.Warning
import nl.weerenwind.logic.Alerts
import androidx.compose.ui.unit.sp
import nl.weerenwind.AppViewModel
import nl.weerenwind.UiState
import nl.weerenwind.data.Forecast
import nl.weerenwind.data.Place
import nl.weerenwind.data.Settings
import nl.weerenwind.logic.Codes
import nl.weerenwind.logic.Logic
import nl.weerenwind.logic.Units
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Date

private val KICKER = DateTimeFormatter.ofPattern("EEEE d MMMM", Units.NL)

@Composable
fun TodayScreen(state: UiState, vm: AppViewModel, goPlaces: () -> Unit) {
    val c = LocalWeer.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp)) {
        ScreenTitle("Vandaag", kicker = LocalDate.now().format(KICKER)) {
            TextAction(if (state.loading) "Bezig…" else "↻ Ververs", enabled = !state.loading) { vm.refresh() }
        }
        StatusLine(state) { vm.refresh() }
        val warns = state.bundle?.warnings.orEmpty()
            .mapNotNull { w -> state.slotPlaces.filter { w.appliesTo(it) }.takeIf { it.isNotEmpty() }?.let { w to it } }
            .sortedByDescending { it.first.level }
        warns.forEach { (w, ps) -> WarningBanner(w, ps, Modifier.reveal(1)) }

        val a = state.placeA
        if (a == null) {
            WCard(Modifier.fillMaxWidth().reveal(1)) {
                Voice("Nog nergens.", size = 34.sp)
                Gap(8)
                Text("Zoek een plaats, wijk of postcode. Je locatie is niet nodig.", fontSize = 13.sp, color = c.muted)
                Gap(10)
                TextAction("Plek toevoegen →", onClick = goPlaces)
            }
            return@Column
        }
        val b = state.placeB
        val two = b != null

        // Twee plekken als twee klokken: naast elkaar, gescheiden door één haarlijn.
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).reveal(1)) {
            val stale = state.bundle?.isStale() == true
            Dial(a, state.bundle?.forecasts?.get(a.id), state.settings, two, stale, Modifier.weight(1f))
            if (b != null) {
                Box(Modifier.padding(horizontal = 14.dp).width(1.dp).fillMaxHeight().background(c.border))
                Dial(b, state.bundle?.forecasts?.get(b.id), state.settings, true, stale, Modifier.weight(1f))
            }
        }

        Gap(26)
        state.slotPlaces.forEachIndexed { i, p ->
            state.bundle?.forecasts?.get(p.id)?.let { NextHours(p, it, state.settings, Modifier.reveal(2 + i)) }
        }

        Gap(18)
        Text("Weerdata · Open-Meteo.com (CC BY 4.0) · o.a. KNMI HARMONIE", fontSize = 10.sp, color = c.faint,
            modifier = Modifier.reveal(4))
    }
}

/**
 * Eerlijke status: wanneer is de data van, is die verouderd, ben je offline, mislukte het verversen?
 * Bij een fout of offline blijft de laatste data gewoon zichtbaar.
 */
@Composable
fun StatusLine(state: UiState, onRetry: () -> Unit) {
    val c = LocalWeer.current
    val b = state.bundle
    val at = b?.let { SimpleDateFormat("HH:mm", Units.NL).format(Date(it.fetchedAt)) }
    val age = b?.let { ageText(it.ageMs()) }
    val stale = b?.isStale() == true
    val (txt, col, retry) = when {
        state.loading -> Triple("Verversen…", c.faint, false)
        state.offline && b != null -> Triple("Offline · data van $at ($age)", if (stale) c.warn else c.muted, false)
        state.offline -> Triple("Offline · nog geen data", c.warn, false)
        state.error != null && b != null -> Triple("${state.error} · data van $at ($age)", c.warn, true)
        state.error != null -> Triple(state.error ?: "", c.warn, true)
        stale -> Triple("Verouderd · data van $at ($age)", c.warn, true)
        b != null -> Triple("Bijgewerkt $at", c.faint, false)
        else -> return
    }
    Row(Modifier.fillMaxWidth().padding(bottom = 16.dp).semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically) {
        if (col == c.warn) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(c.warn))
            HGap(8)
        }
        Text(txt.uppercase(), fontSize = 10.sp, letterSpacing = 1.2.sp, color = col, modifier = Modifier.weight(1f))
        if (retry) TextAction("Opnieuw", onClick = onRetry)
    }
}

fun ageText(ms: Long): String {
    val min = ms / 60000
    return when {
        min < 60 -> "$min min oud"
        min < 48 * 60 -> "${min / 60} uur oud"
        else -> "${min / (24 * 60)} dagen oud"
    }
}

/** KNMI-waarschuwing als banner met gekleurde streep. */
@Composable
fun WarningBanner(w: Warning, places: List<Place>, modifier: Modifier = Modifier) {
    val c = LocalWeer.current
    val col = when (w.level) { 3 -> c.warn; 2 -> c.warm; else -> c.sun }
    val hm = DateTimeFormatter.ofPattern("EEE HH:mm", Units.NL)
    val period = listOfNotNull(
        w.onset?.atZoneSameInstant(Repo.AMS)?.format(hm)?.let { "vanaf $it" },
        w.expires?.atZoneSameInstant(Repo.AMS)?.format(hm)?.let { "tot $it" },
    ).joinToString(" ")
    Row(
        modifier.fillMaxWidth().padding(bottom = 10.dp)
            .semantics(mergeDescendants = true) {}
            .drawBehind { drawRect(col, Offset.Zero, Size(2.dp.toPx(), size.height)) }
            .padding(start = 10.dp, top = 2.dp, bottom = 2.dp),
    ) {
        Column {
            Text("${Alerts.codeName(w.level)} · ${w.event}".uppercase(), fontSize = 11.sp, letterSpacing = 1.2.sp,
                color = col, fontWeight = FontWeight.Medium)
            Text("${w.area} (${places.joinToString { it.name }}) $period".trim(), fontSize = 11.sp, color = c.muted)
        }
    }
}

@Composable
private fun Dial(place: Place, f: Forecast?, s: Settings, compact: Boolean, stale: Boolean, modifier: Modifier) {
    val c = LocalWeer.current
    val spoken = f?.let {
        val n = it.nowEstimate(stale)
        "${place.name}: ${Units.temp(n.temp).dropLast(1)} graden, voelt als ${Units.temp(n.feels).dropLast(1)}, " +
            "${Codes.text(n.code).lowercase()}. ${Logic.summary(it, s)}. " +
            "Wind ${Units.wind(n.wind, s.windUnit)} uit ${Units.compassLong(n.dir)}. " +
            Logic.advice(it, s).joinToString(" ") { a -> "${a.title}: ${a.detail}." }
    } ?: "${place.name}: nog geen data"
    Column(modifier.clearAndSetSemantics { contentDescription = spoken }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(moodColor(f, c)))
            HGap(6)
            Label(place.name, color = c.text)
        }
        if (f == null) {
            Gap(10)
            Text("Nog geen data", color = c.muted, fontSize = 13.sp)
            return@Column
        }
        val cur = f.nowEstimate(stale)
        Gap(10)
        WeatherIcon(cur.code, cur.isDay, if (compact) 34.dp else 44.dp)
        BigNumber(Units.temp(cur.temp), if (compact) 76.sp else 104.sp, Modifier.padding(top = 2.dp))
        Text((if (stale) "VERWACHT · " else "") + "VOELT ${Units.temp(cur.feels)} · ${Codes.text(cur.code).uppercase()}", fontSize = 10.sp,
            letterSpacing = 1.sp, color = c.muted, lineHeight = 14.sp)
        Gap(14)
        Voice(Logic.summary(f, s) + ".", size = if (compact) 19.sp else 24.sp)
        Gap(12)
        Row(verticalAlignment = Alignment.CenterVertically) {
            WindArrow(cur.dir)
            HGap(6)
            Text("${Units.wind(cur.wind, s.windUnit)} ${Units.compass(cur.dir)}".uppercase(), fontSize = 11.sp,
                color = c.muted, letterSpacing = 0.8.sp)
        }
        val advice = Logic.advice(f, s)
        Gap(4)
        if (advice.isEmpty()) {
            Gap(6)
            Text("Geen bijzonderheden", fontSize = 11.sp, color = c.faint)
        } else advice.forEach { AdviceRow(it) }
    }
}

/**
 * Komende 12 uur als één lijn: temperatuur als witte curve met waarden, neerslag als blauwe staafjes
 * eronder. Lijn en staafjes tekenen zich bij binnenkomst van links naar rechts.
 */
@Composable
private fun NextHours(place: Place, f: Forecast, s: Settings, modifier: Modifier) {
    val c = LocalWeer.current
    val hrs = f.upcoming(f.localNow(), 12)
    if (hrs.size < 2) return
    val grow = growIn(delayMs = 350, durationMs = 1100)
    WCard(modifier.fillMaxWidth().padding(bottom = 18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Label(place.name, color = c.text)
            HGap(8)
            Label("· komende 12 uur", color = c.faint)
        }
        Gap(14)
        val tMin = hrs.minOf { it.temp }
        val tMax = maxOf(hrs.maxOf { it.temp }, tMin + 2)
        val text = c.text
        val rain = c.rain
        val faint = c.faint
        val measurer = rememberTextMeasurer()
        val tempStyle = TextStyle(fontFamily = Type.mono, fontSize = 10.sp, color = c.text)
        val hourStyle = TextStyle(fontFamily = Type.mono, fontSize = 9.sp, color = c.faint)
        val spoken = "Komende 12 uur: temperatuur van ${Units.temp(tMin)} tot ${Units.temp(hrs.maxOf { it.temp })}. " +
            hrs.filter { it.precip >= s.rainMm }.let { w ->
                if (w.isEmpty()) "Geen neerslag van betekenis." else "Neerslag rond ${w.joinToString { Units.hm(it.time.minusHours(1)) }}."
            }
        Canvas(Modifier.fillMaxWidth().height(118.dp).semantics { contentDescription = spoken }) {
            val n = hrs.size
            val padX = 10.dp.toPx()
            val stepX = (size.width - 2 * padX) / (n - 1)
            val top = 18.dp.toPx()
            val curveH = 40.dp.toPx()
            val base = size.height - 16.dp.toPx()
            val maxH = 26.dp.toPx()
            fun x(i: Int) = padX + i * stepX
            fun y(t: Double) = top + curveH - ((t - tMin) / (tMax - tMin)).toFloat() * curveH
            val path = Path()
            hrs.forEachIndexed { i, h ->
                if (i == 0) path.moveTo(x(0), y(h.temp)) else {
                    val px = x(i - 1); val py = y(hrs[i - 1].temp)
                    val mx = (px + x(i)) / 2
                    path.cubicTo(mx, py, mx, y(h.temp), x(i), y(h.temp))
                }
            }
            // uurlabels staan altijd; lijn, punten en staafjes tekenen zich van links naar rechts
            hrs.forEachIndexed { i, h ->
                if (i % 2 == 0) {
                    val l = measurer.measure("%02d".format(h.time.minusHours(1).hour), hourStyle)
                    drawText(l, topLeft = Offset(x(i) - l.size.width / 2f, size.height - l.size.height))
                }
            }
            clipRect(right = size.width * grow + 1f) {
                drawPath(path, text, style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round))
                hrs.forEachIndexed { i, h ->
                    val pt = Offset(x(i), y(h.temp))
                    drawCircle(text, if (i % 2 == 0) 2.2.dp.toPx() else 1.2.dp.toPx(), pt)
                    if (i % 2 == 0) {
                        val l = measurer.measure(Units.temp(h.temp), tempStyle)
                        drawText(l, topLeft = Offset(pt.x - l.size.width / 2f, pt.y - l.size.height - 3.dp.toPx()))
                    }
                }
                val bw = stepX * 0.42f
                hrs.forEachIndexed { i, h ->
                    drawRect(faint, Offset(x(i) - bw / 2, base - 1.dp.toPx()), Size(bw, 1.dp.toPx()))
                    if (h.precip >= 0.05) {
                        val hh = (h.precip / 3.0).coerceIn(0.1, 1.0).toFloat() * maxH
                        val a = (0.35f + 0.65f * h.prob / 100f).coerceIn(0.35f, 1f)
                        drawRect(rain.copy(alpha = a), Offset(x(i) - bw / 2, base - hh), Size(bw, hh))
                    }
                }
            }
        }
        val wet = hrs.filter { it.precip >= s.rainMm }
        Gap(8)
        Text(
            if (wet.isEmpty()) "Geen neerslag van betekenis"
            else "Neerslag ${Units.mm(wet.sumOf { it.precip })} mm in ${wet.size} uur",
            fontSize = 11.sp, color = if (wet.isEmpty()) c.faint else c.rain, fontWeight = FontWeight.Normal,
        )
    }
}
