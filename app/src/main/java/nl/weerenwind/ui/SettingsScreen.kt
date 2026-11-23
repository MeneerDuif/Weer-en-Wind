package nl.weerenwind.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nl.weerenwind.AppViewModel
import nl.weerenwind.UiState
import nl.weerenwind.data.LogEntry
import nl.weerenwind.logic.Units
import java.text.SimpleDateFormat
import java.util.Date

@Composable
fun SettingsScreen(state: UiState, vm: AppViewModel) {
    val c = LocalWeer.current
    val s = state.settings
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        ScreenTitle("Opties")

        Section("Thema") {
            ThemeRow("systeem", "Volgt het systeem (AMOLED / Licht)", Themes.amoled, s.theme == "systeem") {
                vm.updateSettings { it.copy(theme = "systeem") }
            }
            Themes.all.forEach { t ->
                ThemeRow(t.id, t.label + if (t.id == "amoled") " (standaard)" else "", t, s.theme == t.id) {
                    vm.updateSettings { it.copy(theme = t.id) }
                }
            }
        }

        Section("Startpagina") {
            Row(Modifier.fillMaxWidth()) {
                TABS.take(3).forEachIndexed { i, (label, _) ->
                    Chip(label, s.startTab == i) { vm.updateSettings { it.copy(startTab = i) } }
                }
            }
        }

        Section("Wind") {
            Row {
                Chip("Beaufort", s.windUnit == "bft") { vm.updateSettings { it.copy(windUnit = "bft") } }
                Chip("km/u", s.windUnit == "kmh") { vm.updateSettings { it.copy(windUnit = "kmh") } }
                Chip("m/s", s.windUnit == "ms") { vm.updateSettings { it.copy(windUnit = "ms") } }
            }
        }

        Section("Adviezen en drempels") {
            Stepper("Koud vanaf gevoelstemperatuur", "${s.coldFeels} °C", { vm.updateSettings { it.copy(coldFeels = (it.coldFeels - 1).coerceAtLeast(-10)) } }) {
                vm.updateSettings { it.copy(coldFeels = (it.coldFeels + 1).coerceAtMost(20)) }
            }
            Stepper("Regenjas vanaf kans", "${s.rainProb}%", { vm.updateSettings { it.copy(rainProb = (it.rainProb - 10).coerceAtLeast(10)) } }) {
                vm.updateSettings { it.copy(rainProb = (it.rainProb + 10).coerceAtMost(90)) }
            }
            Stepper("Harde wind vanaf", "${s.windBft} Bft", { vm.updateSettings { it.copy(windBft = (it.windBft - 1).coerceAtLeast(3)) } }) {
                vm.updateSettings { it.copy(windBft = (it.windBft + 1).coerceAtMost(10)) }
            }
            Stepper("Zonnebrand vanaf UV-index", "${s.uvMin}", { vm.updateSettings { it.copy(uvMin = (it.uvMin - 1).coerceAtLeast(1)) } }) {
                vm.updateSettings { it.copy(uvMin = (it.uvMin + 1).coerceAtMost(11)) }
            }
            Stepper("Duur van je rit (Hoe nat word ik?)", "${s.rideMinutes} min", { vm.updateSettings { it.copy(rideMinutes = (it.rideMinutes - 5).coerceAtLeast(5)) } }) {
                vm.updateSettings { it.copy(rideMinutes = (it.rideMinutes + 5).coerceAtMost(120)) }
            }
        }

        Section("Scherm") {
            Toggle("Statusbalk tonen", s.showStatusBar) { v -> vm.updateSettings { it.copy(showStatusBar = v) } }
            Toggle("Navigatieknoppen tonen", s.showNavBar) { v -> vm.updateSettings { it.copy(showNavBar = v) } }
        }

        PrivacyLog(vm)

        Section("Over") {
            Text(
                "Weer en Wind · geen account, geen advertenties, geen analytics, geen Google-diensten, geen locatie.\n\n" +
                    "Weerdata: Open-Meteo.com (CC BY 4.0), voor Nederland o.a. KNMI HARMONIE. " +
                    "Zoeken: PDOK Locatieserver (Kadaster/BZK) en Open-Meteo Geocoding. " +
                    "De gratis Open-Meteo-API is alleen voor niet-commercieel gebruik.",
                fontSize = 13.sp, color = c.muted,
            )
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Spacer(Modifier.height(18.dp))
    Label(title)
    Spacer(Modifier.height(8.dp))
    content()
}

@Composable
private fun ThemeRow(id: String, label: String, t: WeerColors, selected: Boolean, onClick: () -> Unit) {
    val c = LocalWeer.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Voorbeeld: achtergrond, tekst en twee accentkleuren
        Box(
            Modifier.size(width = 54.dp, height = 30.dp).clip(RoundedCornerShape(8.dp)).background(t.bg)
                .border(1.dp, if (id == "systeem") c.border else t.border, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Aa", fontSize = 11.sp, color = t.text)
                Spacer(Modifier.width(4.dp))
                Box(Modifier.size(6.dp).clip(CircleShape).background(t.rain))
                Spacer(Modifier.width(2.dp))
                Box(Modifier.size(6.dp).clip(CircleShape).background(t.warm))
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(label, fontSize = 14.sp, color = if (selected) c.text else c.muted, modifier = Modifier.weight(1f))
        if (selected) Text("●", fontSize = 12.sp, color = c.text)
    }
}

@Composable
private fun Stepper(label: String, value: String, minus: () -> Unit, plus: () -> Unit) {
    val c = LocalWeer.current
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 14.sp, color = c.text, modifier = Modifier.weight(1f))
        TextButton(onClick = minus) { Text("−", fontSize = 18.sp, color = c.muted) }
        Text(value, fontSize = 14.sp, color = c.text, style = Tnum, modifier = Modifier.width(64.dp))
        TextButton(onClick = plus) { Text("+", fontSize = 18.sp, color = c.muted) }
    }
}

@Composable
private fun Toggle(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    val c = LocalWeer.current
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 14.sp, color = c.text, modifier = Modifier.weight(1f))
        Switch(
            checked = value, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = c.bg, checkedTrackColor = c.text,
                uncheckedThumbColor = c.muted, uncheckedTrackColor = c.bg, uncheckedBorderColor = c.border,
            ),
        )
    }
}

@Composable
private fun PrivacyLog(vm: AppViewModel) {
    val c = LocalWeer.current
    var open by remember { mutableStateOf(false) }
    var entries by remember { mutableStateOf<List<LogEntry>>(emptyList()) }
    Section("Privacy-logboek") {
        Text("Elk netwerkverzoek van de afgelopen 7 dagen. Er is geen ander verkeer.", fontSize = 13.sp, color = c.muted)
        TextButton(onClick = { open = !open; if (open) entries = vm.log() }) {
            Text(if (open) "Verberg logboek" else "Toon logboek", color = c.text)
        }
        if (open) {
            if (entries.isEmpty()) Text("Nog geen verzoeken.", fontSize = 13.sp, color = c.faint)
            val fmt = SimpleDateFormat("EEE d MMM HH:mm", Units.NL)
            Text("${entries.size} verzoeken", fontSize = 12.sp, color = c.faint)
            entries.take(200).forEach { e ->
                Column(Modifier.padding(vertical = 5.dp)) {
                    Text("${fmt.format(Date(e.time))} · ${e.host}", fontSize = 12.sp, color = c.text, style = Tnum)
                    Text("${e.purpose} · ${if (e.code in 200..299) "ok" else "fout ${e.code}"} · ${e.bytes / 1024} kB",
                        fontSize = 11.sp, color = if (e.code in 200..299) c.muted else c.warn)
                }
            }
        }
    }
}
