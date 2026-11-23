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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nl.weerenwind.AppViewModel
import nl.weerenwind.UiState
import nl.weerenwind.data.Place

@Composable
fun PlacesScreen(state: UiState, vm: AppViewModel) {
    val c = LocalWeer.current
    val focus = LocalFocusManager.current
    var q by rememberSaveable { mutableStateOf("") }
    val doSearch = { focus.clearFocus(); vm.search(q) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        ScreenTitle("Plekken")
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = q,
                onValueChange = { q = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text("Plaats, wijk of postcode", color = c.faint) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { doSearch() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = c.text,
                    unfocusedBorderColor = c.border,
                    cursorColor = c.text,
                    focusedTextColor = c.text,
                    unfocusedTextColor = c.text,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                ),
            )
            Spacer(Modifier.width(6.dp))
            TextButton(onClick = { doSearch() }) { Text("Zoek", color = c.text) }
        }

        if (state.searching) Text("Zoeken…", color = c.muted, modifier = Modifier.padding(top = 8.dp))
        state.searchError?.let { Text(it, color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp)) }
        if (state.results.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            WCard(Modifier.fillMaxWidth()) {
                Label("Resultaten · tik om toe te voegen")
                state.results.forEach { r ->
                    Column(Modifier.fillMaxWidth().clickable { vm.addPlace(r); q = "" }.padding(vertical = 9.dp)) {
                        Text(r.name, fontSize = 15.sp, color = c.text)
                        Text(r.detail, fontSize = 12.sp, color = c.muted)
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        Label("Jouw plekken · A en B staan naast elkaar")
        Spacer(Modifier.height(6.dp))
        if (state.places.isEmpty()) {
            Text("Nog geen plekken opgeslagen.", color = c.muted, fontSize = 14.sp)
        }
        state.places.forEach { p ->
            PlaceRow(p, p.id == state.slotA, p.id == state.slotB,
                onA = { vm.toggleSlot("A", p.id) },
                onB = { vm.toggleSlot("B", p.id) },
                onRemove = { vm.removePlace(p.id) })
            HorizontalDivider(thickness = 1.dp, color = c.border)
        }

        Spacer(Modifier.height(20.dp))
        Text(
            "Zoeken gaat via PDOK (Nederlandse overheid) en de Open-Meteo-geocoder (buitenland). " +
                "Je plekken blijven op dit toestel; voor de verwachting gaan alleen coördinaten (afgerond op ~100 m) naar Open-Meteo.",
            fontSize = 12.sp, color = c.faint,
        )
    }
}

@Composable
private fun PlaceRow(p: Place, isA: Boolean, isB: Boolean, onA: () -> Unit, onB: () -> Unit, onRemove: () -> Unit) {
    val c = LocalWeer.current
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(p.name, fontSize = 15.sp, color = c.text)
            Text(p.detail, fontSize = 12.sp, color = c.muted)
        }
        SlotButton("A", isA, onA)
        Spacer(Modifier.width(6.dp))
        SlotButton("B", isB, onB)
        TextButton(onClick = onRemove) { Text("✕", color = c.faint) }
    }
}

@Composable
private fun SlotButton(label: String, selected: Boolean, onClick: () -> Unit) {
    val c = LocalWeer.current
    Box(
        Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(if (selected) c.text else Color.Transparent)
            .border(1.dp, if (selected) c.text else c.border, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 13.sp, color = if (selected) c.bg else c.muted)
    }
}
