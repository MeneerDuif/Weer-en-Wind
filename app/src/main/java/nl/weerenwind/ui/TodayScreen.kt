package nl.weerenwind.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
import java.util.Date

@Composable
fun TodayScreen(state: UiState, vm: AppViewModel, goPlaces: () -> Unit) {
    val c = LocalWeer.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        ScreenTitle("Vandaag") {
            TextButton(onClick = { vm.refresh() }, enabled = !state.loading) {
                Text(if (state.loading) "Bezig…" else "Ververs", color = c.muted, fontSize = 13.sp)
            }
        }
        StatusLine(state)

        val a = state.placeA
        if (a == null) {
            WCard(Modifier.fillMaxWidth()) {
                Text("Nog geen plekken", fontSize = 17.sp, color = c.text)
                Spacer(Modifier.height(6.dp))
                Text("Zoek een plaats, wijk of postcode. Je locatie is niet nodig.", fontSize = 14.sp, color = c.muted)
                TextButton(onClick = goPlaces) { Text("Plek toevoegen →", color = c.text) }
            }
            return@Column
        }
        val b = state.placeB
        Row(
            Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PlaceCard(a, state.bundle?.forecasts?.get(a.id), state.settings, Modifier.weight(1f))
            if (b != null) PlaceCard(b, state.bundle?.forecasts?.get(b.id), state.settings, Modifier.weight(1f))
        }

        Spacer(Modifier.height(14.dp))
        state.slotPlaces.forEach { p ->
            state.bundle?.forecasts?.get(p.id)?.let { NextHours(p, it, state.settings) }
        }

        Spacer(Modifier.height(16.dp))
        Text("Weerdata: Open-Meteo.com (CC BY 4.0), o.a. KNMI HARMONIE", fontSize = 11.sp, color = c.faint)
    }
}

@Composable
fun StatusLine(state: UiState) {
    val c = LocalWeer.current
    val b = state.bundle
    val txt = when {
        state.error != null -> state.error
        b != null -> "Bijgewerkt " + SimpleDateFormat("HH:mm", Units.NL).format(Date(b.fetchedAt))
        else -> null
    }
    if (txt != null) {
        Text(txt, fontSize = 12.sp, color = if (state.error != null) c.warn else c.faint,
            modifier = Modifier.padding(bottom = 10.dp))
    }
}

@Composable
private fun PlaceCard(place: Place, f: Forecast?, s: Settings, modifier: Modifier) {
    val c = LocalWeer.current
    WCard(modifier.fillMaxHeight()) {
        Label(place.name)
        if (f == null) {
            Spacer(Modifier.height(8.dp))
            Text("Nog geen data", color = c.muted, fontSize = 14.sp)
            return@WCard
        }
        val cur = f.current
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(Codes.glyph(cur.code, cur.isDay), fontSize = 26.sp, color = c.text)
            Spacer(Modifier.width(8.dp))
            Text(Units.temp(cur.temp), fontSize = 44.sp, fontWeight = FontWeight.Light, color = c.text, style = Tnum)
        }
        Text("Voelt als ${Units.temp(cur.feels)}", fontSize = 13.sp, color = c.muted)
        Text(Codes.text(cur.code), fontSize = 13.sp, color = c.muted)
        Spacer(Modifier.height(10.dp))
        Text(Logic.summary(f, s), fontSize = 14.sp, color = c.text)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            WindArrow(cur.dir)
            Text(" ${Units.wind(cur.wind, s.windUnit)} ${Units.compass(cur.dir)}", fontSize = 13.sp, color = c.muted)
        }
        val advice = Logic.advice(f, s)
        if (advice.isEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("Geen bijzonderheden", fontSize = 12.sp, color = c.faint)
        } else advice.forEach { AdviceRow(it) }
    }
}

@Composable
private fun NextHours(place: Place, f: Forecast, s: Settings) {
    val c = LocalWeer.current
    val hrs = f.upcoming(f.localNow(), 6)
    WCard(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
        Label("${place.name} · komende uren")
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            hrs.forEach { h ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("%02d".format(h.time.hour), fontSize = 12.sp, color = c.faint, style = Tnum)
                    Text(Codes.glyph(h.code, h.isDay), fontSize = 16.sp, color = c.text)
                    Text(Units.temp(h.temp), fontSize = 14.sp, color = c.text, style = Tnum)
                    Text(if (h.precip >= 0.05) Units.mm(h.precip) else "–", fontSize = 11.sp,
                        color = if (h.precip >= s.rainMm) c.rain else c.faint, style = Tnum)
                }
            }
        }
    }
}
