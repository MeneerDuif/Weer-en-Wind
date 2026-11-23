package nl.weerenwind.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import nl.weerenwind.logic.Advice
import nl.weerenwind.logic.AdviceKind

/** Cijfers met vaste breedte, zodat kolommen netjes uitlijnen. */
val Tnum = TextStyle(fontFeatureSettings = "tnum")

/** Zachte "expo-out" — snel binnen, rustig landen. */
val EaseOut = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

/**
 * Gestaffelde binnenkomst: elk element komt [index] × 70 ms later omhoog en in beeld.
 * Eén keer per scherm, zodat het een moment blijft en geen ruis.
 */
@Composable
fun Modifier.reveal(index: Int): Modifier {
    val p = remember { Animatable(0f) }
    val dy = with(LocalDensity.current) { 18.dp.toPx() }
    LaunchedEffect(Unit) {
        delay(index * 70L)
        p.animateTo(1f, tween(620, easing = EaseOut))
    }
    return this.graphicsLayer {
        alpha = p.value
        translationY = (1f - p.value) * dy
    }
}

/** Animatie 0 → 1 bij eerste weergave, voor balkjes en lijnen. */
@Composable
fun growIn(delayMs: Int = 120, durationMs: Int = 900): Float {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(delayMs.toLong())
        p.animateTo(1f, tween(durationMs, easing = EaseOut))
    }
    return p.value
}

/**
 * Sectie in plaats van een kaart: een haarlijn met een korte witte "tick" aan het begin,
 * zoals de schaalverdeling op een instrument.
 */
@Composable
fun WCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val c = LocalWeer.current
    Column(
        modifier
            .drawBehind {
                val h = 1.dp.toPx()
                drawRect(c.border, Offset.Zero, Size(size.width, h))
                drawRect(c.text, Offset.Zero, Size(18.dp.toPx(), h))
            }
            .padding(top = 14.dp, bottom = 10.dp),
        content = content,
    )
}

@Composable
fun Label(text: String, modifier: Modifier = Modifier, color: Color = LocalWeer.current.muted, size: TextUnit = 10.sp) {
    Text(
        text.uppercase(), modifier = modifier, color = color, fontSize = size, letterSpacing = 1.6.sp,
        maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium, fontFamily = Type.mono,
    )
}

/** Serif-zin, cursief: de "stem" van de app. */
@Composable
fun Voice(text: String, modifier: Modifier = Modifier, size: TextUnit = 20.sp, color: Color = LocalWeer.current.text) {
    Text(text, modifier = modifier, fontFamily = Type.serif, fontStyle = FontStyle.Italic,
        fontSize = size, lineHeight = size * 1.15f, color = color)
}

/** Groot getal in de serif. */
@Composable
fun BigNumber(text: String, size: TextUnit, modifier: Modifier = Modifier, color: Color = LocalWeer.current.text) {
    Text(text, modifier = modifier, fontFamily = Type.serif, fontSize = size, lineHeight = size,
        color = color, style = TextStyle(fontFeatureSettings = "lnum"))
}

@Composable
fun ScreenTitle(title: String, kicker: String? = null, trailing: @Composable () -> Unit = {}) {
    val c = LocalWeer.current
    Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 18.dp).reveal(0)) {
        if (kicker != null) Label(kicker, color = c.faint)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text(title, fontSize = 44.sp, lineHeight = 46.sp, fontFamily = Type.serif, color = c.text,
                modifier = Modifier.weight(1f))
            trailing()
        }
    }
}

/** Kleine knop in mono-kapitalen, zonder vulling. */
@Composable
fun TextAction(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    val c = LocalWeer.current
    Text(
        text.uppercase(), fontFamily = Type.mono, fontSize = 11.sp, letterSpacing = 1.4.sp,
        color = if (enabled) c.text else c.faint,
        modifier = Modifier.clip(RoundedCornerShape(2.dp)).clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 8.dp),
    )
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    val c = LocalWeer.current
    val shape = RoundedCornerShape(2.dp)
    Box(
        Modifier
            .padding(end = 6.dp, bottom = 6.dp)
            .clip(shape)
            .background(if (selected) c.text else Color.Transparent)
            .border(1.dp, if (selected) c.text else c.border, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(text.uppercase(), fontSize = 11.sp, letterSpacing = 1.2.sp, fontFamily = Type.mono,
            color = if (selected) c.bg else c.muted)
    }
}

@Composable
fun adviceColor(k: AdviceKind): Color {
    val c = LocalWeer.current
    return when (k) {
        AdviceKind.RAIN -> c.rain
        AdviceKind.COLD -> c.cold
        AdviceKind.SUN -> c.sun
        AdviceKind.ICE -> c.warn
        AdviceKind.WIND -> c.wind
    }
}

/** Advies als label met een gekleurde verticale streep. */
@Composable
fun AdviceRow(a: Advice) {
    val c = LocalWeer.current
    val col = adviceColor(a.kind)
    Row(
        Modifier.padding(top = 8.dp).drawBehind {
            drawRect(col, Offset.Zero, Size(2.dp.toPx(), size.height))
        }.padding(start = 9.dp),
    ) {
        Column {
            Text(a.title.uppercase(), fontSize = 11.sp, letterSpacing = 1.2.sp, color = col, fontWeight = FontWeight.Medium)
            Text(a.detail, fontSize = 11.sp, color = c.muted, lineHeight = 14.sp)
        }
    }
}

/**
 * Staafjes voor neerslag die bij binnenkomst omhoog groeien.
 * Hoogte = hoeveelheid (tot [maxMm]); doorzichtigheid = kans, als die bekend is.
 */
@Composable
fun Bars(values: List<Double>, probs: List<Int>?, maxMm: Double, modifier: Modifier = Modifier) {
    val c = LocalWeer.current
    val grow = growIn()
    Canvas(modifier.fillMaxWidth().height(64.dp)) {
        if (values.isEmpty()) return@Canvas
        val n = values.size
        val gap = 3.dp.toPx()
        val w = (size.width - gap * (n - 1)) / n
        // drie fijne referentielijnen: 1, 2 en 4 mm/u
        listOf(1.0, 2.0, 4.0).forEach { mm ->
            val y = size.height - (mm / maxMm).toFloat().coerceAtMost(1f) * size.height
            drawRect(c.border.copy(alpha = 0.5f), Offset(0f, y), Size(size.width, 0.5.dp.toPx()))
        }
        values.forEachIndexed { i, v ->
            val x = i * (w + gap)
            drawRect(c.faint, Offset(x, size.height - 1.dp.toPx()), Size(w, 1.dp.toPx()))
            if (v >= 0.05) {
                val frac = (v / maxMm).coerceIn(0.06, 1.0).toFloat()
                val alpha = probs?.getOrNull(i)?.let { (0.35f + 0.65f * it / 100f).coerceIn(0.35f, 1f) } ?: 1f
                val stagger = (grow * 1.4f - i.toFloat() / n * 0.4f).coerceIn(0f, 1f)
                val h = size.height * frac * stagger
                drawRect(c.rain.copy(alpha = alpha), Offset(x, size.height - h), Size(w, h))
            }
        }
    }
}

@Composable
fun BarLabels(labels: List<String>) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        labels.forEach { Text(it, fontSize = 10.sp, color = LocalWeer.current.faint, style = Tnum) }
    }
}

@Composable
fun Gap(h: Int) = Spacer(Modifier.height(h.dp))

@Composable
fun HGap(w: Int) = Spacer(Modifier.width(w.dp))
