package nl.weerenwind.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nl.weerenwind.AppViewModel
import nl.weerenwind.UiState
import nl.weerenwind.data.Hour
import nl.weerenwind.data.Settings
import nl.weerenwind.logic.Codes
import nl.weerenwind.logic.Units
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val DAY = DateTimeFormatter.ofPattern("EEEE d MMMM", Units.NL)

@Composable
fun HourlyScreen(state: UiState, vm: AppViewModel) {
    val c = LocalWeer.current
    val s = state.settings
    val n = if (s.hours48) 48 else 24
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        item { Column {
            ScreenTitle("Uurrooster") {
                Row {
                    Chip("24 u", !s.hours48) { vm.updateSettings { it.copy(hours48 = false) } }
                    Chip("48 u", s.hours48) { vm.updateSettings { it.copy(hours48 = true) } }
                }
            }
            if (state.slotPlaces.isEmpty()) Text("Voeg eerst een plek toe.", color = c.muted)
        } }
        for (p in state.slotPlaces) {
            val f = state.bundle?.forecasts?.get(p.id) ?: continue
            val now = f.localNow()
            val hrs = f.upcoming(now, n)
            val today = now.toLocalDate()
            item(key = "head-${p.id}") {
                Column {
                    Label(p.name, Modifier.padding(top = 4.dp, bottom = 6.dp), color = c.text, size = 12.sp)
                    HeaderRow()
                }
            }
            items(count = hrs.size, key = { "${p.id}-$it" }) { i ->
                val h = hrs[i]
                val d = h.time.minusMinutes(1).toLocalDate()
                val prev = if (i == 0) null else hrs[i - 1].time.minusMinutes(1).toLocalDate()
                Column {
                    if (prev != null && d != prev) DayHeader(d, today)
                    HourRow(h, s)
                }
            }
            item(key = "gap-${p.id}") { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun HeaderRow() {
    val c = LocalWeer.current
    Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        Text("UUR", Modifier.width(44.dp), fontSize = 10.sp, color = c.faint)
        Text("", Modifier.width(30.dp))
        Text("TEMP", Modifier.width(50.dp), fontSize = 10.sp, color = c.faint)
        Text("MM", Modifier.width(48.dp), fontSize = 10.sp, color = c.faint)
        Text("KANS", Modifier.width(48.dp), fontSize = 10.sp, color = c.faint)
        Text("WIND", Modifier.weight(1f), fontSize = 10.sp, color = c.faint)
    }
    HorizontalDivider(thickness = 1.dp, color = c.border)
}

@Composable
private fun DayHeader(d: LocalDate, today: LocalDate) {
    val c = LocalWeer.current
    val prefix = when (d) {
        today.plusDays(1) -> "Morgen · "
        today.plusDays(2) -> "Overmorgen · "
        else -> ""
    }
    Text(prefix + d.format(DAY), fontSize = 12.sp, color = c.muted,
        modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))
    HorizontalDivider(thickness = 1.dp, color = c.border)
}

@Composable
private fun HourRow(h: Hour, s: Settings) {
    val c = LocalWeer.current
    // Neerslag geldt voor het uur dat eindigt op h.time; we tonen het beginuur van dat interval.
    val label = "%02d".format(h.time.minusHours(1).hour)
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.width(44.dp), fontSize = 14.sp, color = c.muted, style = Tnum)
        Text(Codes.glyph(h.code, h.isDay), Modifier.width(30.dp), fontSize = 16.sp, color = c.text)
        Text(Units.temp(h.temp), Modifier.width(50.dp), fontSize = 15.sp, color = c.text, style = Tnum)
        Text(if (h.precip >= 0.05) Units.mm(h.precip) else "–", Modifier.width(48.dp), fontSize = 14.sp,
            color = if (h.precip >= 0.1) c.rain else c.faint, style = Tnum)
        Text("${h.prob}%", Modifier.width(48.dp), fontSize = 14.sp,
            color = if (h.prob >= s.rainProb) c.rain else c.faint, style = Tnum)
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            WindArrow(h.dir, size = 13.sp)
            Text(" ${Units.wind(h.wind, s.windUnit)}", fontSize = 13.sp,
                color = if (Units.bft(h.wind) >= s.windBft) c.wind else c.muted, style = Tnum)
        }
    }
}
