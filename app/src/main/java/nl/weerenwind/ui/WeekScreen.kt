package nl.weerenwind.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nl.weerenwind.UiState
import nl.weerenwind.data.Day
import nl.weerenwind.data.Place
import nl.weerenwind.data.Settings
import nl.weerenwind.logic.Codes
import nl.weerenwind.logic.Units
import nl.weerenwind.logic.Week
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private val DAYNAME = DateTimeFormatter.ofPattern("EEE d", Units.NL)

@Composable
fun WeekScreen(state: UiState) {
    val c = LocalWeer.current
    val s = state.settings
    val withDays = state.slotPlaces.mapNotNull { p ->
        state.bundle?.forecasts?.get(p.id)?.let { f -> p to f.days.filter { !it.date.isBefore(f.localNow().toLocalDate()) }.take(7) }
    }.filter { it.second.isNotEmpty() }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        ScreenTitle("Week")
        when {
            state.slotPlaces.isEmpty() -> { Text("Voeg eerst een plek toe.", color = c.muted); return@Column }
            withDays.isEmpty() -> {
                Text(if (state.loading) "Weekverwachting ophalen…" else "Nog geen weekdata. Tik op Ververs op Vandaag.", color = c.muted)
                return@Column
            }
        }
        // Eén temperatuurschaal voor alle plekken, zodat de balkjes vergelijkbaar zijn.
        val all = withDays.flatMap { it.second }
        val lo = all.minOf { it.tMin }
        val hi = max(all.maxOf { it.tMax }, lo + 1)

        Text(
            "Vanaf dag ${Week.RELIABLE_DAYS + 1} is de verwachting onzekerder; die dagen staan gedimd. Tik op een dag voor details.",
            fontSize = 12.sp, color = c.faint, modifier = Modifier.padding(bottom = 4.dp),
        )
        withDays.forEach { (p, days) -> WeekCard(p, days, lo, hi, s) }
        Spacer(Modifier.height(12.dp))
        Text("Weerdata: Open-Meteo.com (CC BY 4.0)", fontSize = 11.sp, color = c.faint)
    }
}

@Composable
private fun WeekCard(p: Place, days: List<Day>, lo: Double, hi: Double, s: Settings) {
    val c = LocalWeer.current
    var open by rememberSaveable(p.id) { mutableStateOf<String?>(null) }
    val today = days.first().date
    WCard(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Label(p.name, color = c.text, size = 12.sp)
        Spacer(Modifier.height(4.dp))
        days.forEachIndexed { i, d ->
            if (i > 0) HorizontalDivider(thickness = 1.dp, color = c.border)
            val isOpen = open == d.date.toString()
            Column(
                Modifier
                    .fillMaxWidth()
                    .alpha(if (i > Week.RELIABLE_DAYS) 0.62f else 1f)
                    .clickable { open = if (isOpen) null else d.date.toString() }
                    .padding(vertical = 9.dp),
            ) {
                DayRow(d, dayLabel(d.date, today), lo, hi)
                Row(Modifier.padding(start = 74.dp, top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    val wet = d.precip >= 1.0 || d.probMax >= max(s.rainProb, 60)
                    Text(Week.line(d, s), fontSize = 12.sp, color = if (wet) c.rain else c.muted,
                        modifier = Modifier.weight(1f))
                    WindArrow(d.dir, size = 12.sp,
                        color = if (Units.bft(d.windMax) >= s.windBft) c.wind else c.faint)
                    Text(" ${Units.wind(d.windMax, s.windUnit)}", fontSize = 12.sp,
                        color = if (Units.bft(d.windMax) >= s.windBft) c.wind else c.muted, style = Tnum)
                }
                if (isOpen) DayDetails(d, s)
            }
        }
    }
}

private fun dayLabel(d: LocalDate, today: LocalDate): String = when (d) {
    today -> "Vandaag"
    today.plusDays(1) -> "Morgen"
    else -> d.format(DAYNAME).replaceFirstChar { it.uppercase() }
}

@Composable
private fun DayRow(d: Day, label: String, lo: Double, hi: Double) {
    val c = LocalWeer.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.width(74.dp), fontSize = 14.sp, color = c.text)
        Text(Codes.glyph(d.code, true), Modifier.width(26.dp), fontSize = 16.sp, color = c.text)
        Text(Units.temp(d.tMin), Modifier.width(38.dp), fontSize = 14.sp, color = c.muted,
            style = Tnum, textAlign = TextAlign.End)
        val cold = c.cold
        val warm = c.warm
        val track = c.border
        Canvas(Modifier.weight(1f).height(6.dp).padding(horizontal = 10.dp)) {
            val r = CornerRadius(size.height / 2, size.height / 2)
            drawRoundRect(track, Offset.Zero, size, r)
            val x0 = (((d.tMin - lo) / (hi - lo)) * size.width).toFloat()
            val x1 = (((d.tMax - lo) / (hi - lo)) * size.width).toFloat()
            val start = min(x0, x1).coerceIn(0f, size.width)
            val w = max(max(x0, x1) - start, size.height)
            drawRoundRect(
                Brush.horizontalGradient(listOf(cold, warm), startX = 0f, endX = size.width),
                Offset(start, 0f), Size(w, size.height), r,
            )
        }
        Text(Units.temp(d.tMax), Modifier.width(38.dp), fontSize = 14.sp, color = c.text,
            style = Tnum, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun DayDetails(d: Day, s: Settings) {
    val c = LocalWeer.current
    Column(Modifier.padding(start = 74.dp, top = 8.dp)) {
        Text(Codes.text(d.code), fontSize = 13.sp, color = c.text)
        Detail("Neerslag", "${Units.mm(d.precip)} mm · kans tot ${d.probMax}%")
        Detail("Wind", "${Units.wind(d.windMax, s.windUnit)} uit ${Units.compass(d.dir)}, stoten ${d.gustMax.roundToInt()} km/u")
        Detail("UV-index", "tot ${d.uvMax.roundToInt()}")
        if (d.sunrise != null && d.sunset != null) {
            Detail("Zon", "op ${Units.hm(d.sunrise)} · onder ${Units.hm(d.sunset)}")
        }
    }
}

@Composable
private fun Detail(k: String, v: String) {
    val c = LocalWeer.current
    Row(Modifier.padding(top = 3.dp)) {
        Text(k, Modifier.width(76.dp), fontSize = 12.sp, color = c.faint)
        Text(v, fontSize = 12.sp, color = c.muted)
    }
}
