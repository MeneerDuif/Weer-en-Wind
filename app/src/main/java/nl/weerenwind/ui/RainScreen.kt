package nl.weerenwind.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nl.weerenwind.UiState
import nl.weerenwind.data.Forecast
import nl.weerenwind.data.Place
import nl.weerenwind.data.Settings
import nl.weerenwind.logic.Codes
import nl.weerenwind.logic.Rain
import nl.weerenwind.logic.RadarLogic
import nl.weerenwind.logic.Units

private val OFFSETS = listOf(-1, 0, 1, 3, 8, 12, 24)

@Composable
fun RainScreen(state: UiState) {
    val c = LocalWeer.current
    var offset by rememberSaveable { mutableIntStateOf(0) }
    val s = state.settings
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp)) {
        ScreenTitle("Regen", kicker = "Buien · ritten · droge vensters")
        if (state.slotPlaces.isEmpty()) {
            Text("Voeg eerst een plek toe.", color = c.muted)
            return@Column
        }
        Label("Vooruitkijken")
        Spacer(Modifier.height(6.dp))
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            OFFSETS.forEach { o ->
                Chip(when { o == 0 -> "Nu"; o > 0 -> "+$o u"; else -> "$o u" }, offset == o) { offset = o }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            for (p in state.slotPlaces) {
                val f = state.bundle?.forecasts?.get(p.id)
                if (f != null) LookCard(p, f, offset, s, Modifier.weight(1f))
            }
        }
        for (p in state.slotPlaces) {
            val f = state.bundle?.forecasts?.get(p.id) ?: continue
            RainCard(p, f, s)
        }
    }
}

@Composable
private fun LookCard(p: Place, f: Forecast, offset: Int, s: Settings, modifier: Modifier) {
    val c = LocalWeer.current
    val t = f.localNow().plusHours(offset.toLong())
    val h = f.hourAt(t)
    WCard(modifier) {
        Label(p.name)
        if (h == null) {
            Text("Buiten bereik", color = c.muted, fontSize = 13.sp)
            return@WCard
        }
        Text("rond ${Units.hm(h.time)}", fontSize = 12.sp, color = c.faint)
        Row(verticalAlignment = Alignment.CenterVertically) {
            WeatherIcon(h.code, h.isDay, 26.dp)
            Spacer(Modifier.width(6.dp))
            BigNumber(Units.temp(h.temp), 40.sp)
        }
        Text("${Units.mm(h.precip)} mm · ${h.prob}%", fontSize = 13.sp,
            color = if (h.prob >= s.rainProb || h.precip >= s.rainMm) c.rain else c.muted)
        Row(verticalAlignment = Alignment.CenterVertically) {
            WindArrow(h.dir, size = 11.dp)
            Text(" ${Units.wind(h.wind, s.windUnit)}", fontSize = 13.sp, color = c.muted)
        }
    }
}

@Composable
private fun RainCard(p: Place, f: Forecast, s: Settings) {
    val c = LocalWeer.current
    val now = f.localNow()
    WCard(Modifier.fillMaxWidth().padding(top = 22.dp).reveal(2)) {
        Label(p.name, color = c.text, size = 12.sp)

        val radar = f.radarFor(now).filter { it.time.plusMinutes(5).isAfter(now) }
        val qs = f.quarters.filter { it.time.isAfter(now) }.take(12)
        if (radar.size >= 3) {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Label("Radar · komende 2 uur", color = c.rain)
                HGap(8)
                Label("per 5 min", color = c.faint)
            }
            Spacer(Modifier.height(6.dp))
            Voice((RadarLogic.summary(radar, now) ?: "Droog volgens de radar") + ".", size = 20.sp)
            Spacer(Modifier.height(8.dp))
            val wet = radar.filter { it.mmh >= RadarLogic.WET }
            Bars(
                radar.map { it.mmh }, null, 4.0,
                description = if (wet.isEmpty()) "Radar: droog de komende 2 uur."
                else "Radar: neerslag van ${Units.hm(wet.first().time)} tot ${Units.hm(wet.last().time.plusMinutes(5))}, " +
                    "maximaal ${Units.mm(wet.maxOf { it.mmh })} millimeter per uur.",
            )
            BarLabels(listOf(Units.hm(radar.first().time), Units.hm(radar[radar.size / 2].time), Units.hm(radar.last().time.plusMinutes(5))))
            Text("Bron: Buienradar", fontSize = 10.sp, color = c.faint, modifier = Modifier.padding(top = 4.dp))
        } else if (qs.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text("Komende 3 uur, per kwartier (model" + (if (p.inRadarArea) "; radar niet beschikbaar)" else ")"), fontSize = 13.sp, color = c.muted)
            Spacer(Modifier.height(6.dp))
            Bars(qs.map { it.precip * 4 }, null, 4.0, description = "Model, komende 3 uur: " +
                (qs.filter { it.precip >= 0.05 }.takeIf { it.isNotEmpty() }?.let { "neerslag rond ${Units.hm(it.first().time.minusMinutes(15))}." } ?: "droog.")) // mm/u
            BarLabels(listOf(Units.hm(qs.first().time.minusMinutes(15)), Units.hm(qs.last().time)))
        }

        val hrs = f.upcoming(now, 24)
        if (hrs.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Text("Buienbalk 24 uur (hoogte = mm, kleur = kans)", fontSize = 13.sp, color = c.muted)
            Spacer(Modifier.height(6.dp))
            Bars(hrs.map { it.precip }, hrs.map { it.prob }, 4.0, description = "Komende 24 uur: totaal " +
                "${Units.mm(hrs.sumOf { it.precip })} millimeter, hoogste kans ${hrs.maxOf { it.prob }} procent.")
            BarLabels(listOf(
                Units.hm(hrs.first().time.minusHours(1)),
                Units.hm(hrs[hrs.size / 2].time),
                Units.hm(hrs.last().time),
            ))
        }

        Spacer(Modifier.height(14.dp))
        Voice("Hoe nat word ik?", size = 24.sp)
        val dur = s.rideMinutes
        val mmNow = Rain.ride(f, now, dur)
        Text("Rit van $dur min, nu vertrekken: ${Rain.label(mmNow)} (${Units.mm(mmNow)} mm)",
            fontSize = 14.sp, color = if (mmNow >= 0.1) c.rain else c.text)
        val (bestT, bestMm) = Rain.best(f, now, dur)
        Text(
            if (bestT == now) "Nu vertrekken is het beste moment in de komende 3 uur."
            else "Beter: vertrek om ${Units.hm(bestT)} — ${Rain.label(bestMm)} (${Units.mm(bestMm)} mm)",
            fontSize = 13.sp, color = c.muted,
        )

        Spacer(Modifier.height(14.dp))
        Voice("Droge vensters", size = 24.sp)
        Label("minstens 2 uur, komende 24 uur", color = c.faint)
        val windows = Rain.dryWindows(f, now, s)
        if (windows.isEmpty()) {
            Text("Geen in de komende 24 uur", fontSize = 13.sp, color = c.muted)
        } else windows.forEach { (a, b) ->
            val sameDay = a.toLocalDate() == now.toLocalDate()
            Text("${if (sameDay) "" else "morgen "}${Units.hm(a)} – ${Units.hm(b)}", fontSize = 14.sp, color = c.text, style = Tnum)
        }
    }
}
