package nl.weerenwind.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nl.weerenwind.AppViewModel
import nl.weerenwind.UiState
import nl.weerenwind.data.Forecast
import nl.weerenwind.logic.Codes

val TABS = listOf(
    "Vandaag" to "01",
    "Uren" to "02",
    "Week" to "03",
    "Regen" to "04",
    "Plekken" to "05",
    "Opties" to "06",
)

/** De "stemming" van het weer bij plek A bepaalt de gloed achter het scherm. */
fun moodColor(f: Forecast?, c: WeerColors): Color {
    val cur = f?.current ?: return c.muted
    return when {
        Codes.isThunder(cur.code) -> c.warm
        Codes.isSnow(cur.code) -> c.cold
        cur.precip >= 0.1 || cur.code in 51..67 || Codes.isShower(cur.code) -> c.rain
        cur.temp >= 20 -> c.warm
        cur.temp <= 3 -> c.cold
        cur.isDay && cur.code <= 1 -> c.sun
        !cur.isDay -> c.cold
        else -> c.muted
    }
}

/**
 * Achtergrond met diepte maar zonder licht te verspillen: puur zwart, een zwakke gloed in de
 * stemmingskleur linksboven en een fijn stippenraster, zoals millimeterpapier van een weerkaart.
 */
@Composable
fun Atmosphere(mood: Color) {
    val c = LocalWeer.current
    val glow by animateColorAsState(mood, tween(1400), label = "mood")
    val strength = if (c.isLight) 0.10f else 0.16f
    Canvas(Modifier.fillMaxSize().background(c.bg)) {
        drawRect(
            Brush.radialGradient(
                listOf(glow.copy(alpha = strength), glow.copy(alpha = strength * 0.35f), Color.Transparent),
                center = Offset(size.width * 0.1f, size.height * 0.02f),
                radius = size.maxDimension * 0.75f,
            )
        )
        val step = 22.dp.toPx()
        val r = 0.7.dp.toPx()
        val dot = c.faint.copy(alpha = if (c.isLight) 0.35f else 0.22f)
        var y = step / 2
        while (y < size.height) {
            var x = step / 2
            while (x < size.width) {
                drawCircle(dot, r, Offset(x, y))
                x += step
            }
            y += step
        }
    }
}

@Composable
fun AppRoot(vm: AppViewModel, state: UiState) {
    val c = LocalWeer.current
    var tab by rememberSaveable { mutableIntStateOf(state.settings.startTab.coerceIn(0, TABS.size - 1)) }
    val mood = moodColor(state.placeA?.let { state.bundle?.forecasts?.get(it.id) }, c)

    Box(Modifier.fillMaxSize()) {
        Atmosphere(mood)
        Scaffold(
            containerColor = Color.Transparent,
            contentColor = c.text,
            bottomBar = { TabBar(tab) { tab = it } },
        ) { pad ->
            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    (fadeIn(tween(320, delayMillis = 60)) + slideInVertically(tween(420, easing = EaseOut)) { it / 30 })
                        .togetherWith(fadeOut(tween(140)))
                },
                modifier = Modifier.padding(pad).fillMaxSize(),
                label = "tabs",
            ) { t ->
                Box(Modifier.fillMaxSize()) {
                    when (t) {
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
    }
}

/** Tabbalk als schaalverdeling: nummer + naam in mono, de actieve tab krijgt een witte streep. */
@Composable
private fun TabBar(selected: Int, onSelect: (Int) -> Unit) {
    val c = LocalWeer.current
    Column(Modifier.background(c.bg).navigationBarsPadding()) {
        HorizontalDivider(thickness = 1.dp, color = c.border)
        Row(Modifier.fillMaxWidth().height(58.dp)) {
            TABS.forEachIndexed { i, (label, num) ->
                val active = i == selected
                val col by animateColorAsState(if (active) c.text else c.faint, tween(250), label = "tab$i")
                val bar by animateDpAsState(if (active) 22.dp else 0.dp, tween(300, easing = EaseOut), label = "bar$i")
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clickable(remember { MutableInteractionSource() }, indication = null) { onSelect(i) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.width(bar).height(2.dp).background(c.text))
                    Text(num, fontFamily = Type.mono, fontSize = 9.sp, color = if (active) c.muted else c.faint,
                        modifier = Modifier.padding(top = 9.dp))
                    Text(label.uppercase(), fontFamily = Type.mono, fontSize = 9.sp, letterSpacing = 0.8.sp,
                        color = col, fontWeight = if (active) FontWeight.Medium else FontWeight.Normal, maxLines = 1)
                }
            }
        }
    }
}
