package nl.weerenwind.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nl.weerenwind.AppViewModel
import nl.weerenwind.UiState

val TABS = listOf(
    "Vandaag" to "◐",
    "Uren" to "≣",
    "Week" to "▦",
    "Regen" to "☂︎",
    "Plekken" to "◎",
    "Opties" to "⚙︎",
)

@Composable
fun AppRoot(vm: AppViewModel, state: UiState) {
    val c = LocalWeer.current
    var tab by rememberSaveable { mutableIntStateOf(state.settings.startTab.coerceIn(0, TABS.size - 1)) }
    Scaffold(
        containerColor = c.bg,
        contentColor = c.text,
        bottomBar = {
            Column {
                HorizontalDivider(thickness = 1.dp, color = c.border)
                NavigationBar(containerColor = c.bg, tonalElevation = 0.dp) {
                    TABS.forEachIndexed { i, (label, glyph) ->
                        NavigationBarItem(
                            selected = tab == i,
                            onClick = { tab = i },
                            icon = { Text(glyph, fontSize = 18.sp) },
                            label = { Text(label, fontSize = 11.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = c.text,
                                selectedTextColor = c.text,
                                unselectedIconColor = c.faint,
                                unselectedTextColor = c.muted,
                                indicatorColor = c.border,
                            ),
                        )
                    }
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                0 -> TodayScreen(state, vm) { tab = 4 }
                1 -> HourlyScreen(state, vm)
                2 -> WeekScreen(state)
                3 -> RainScreen(state)
                4 -> PlacesScreen(state, vm)
                else -> SettingsScreen(state, vm)
            }
        }
    }
}
