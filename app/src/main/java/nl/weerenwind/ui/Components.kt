package nl.weerenwind.ui

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nl.weerenwind.logic.Advice
import nl.weerenwind.logic.AdviceKind

/** Cijfers met vaste breedte, zodat kolommen netjes uitlijnen. */
val Tnum = TextStyle(fontFeatureSettings = "tnum")

@Composable
fun WCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val c = LocalWeer.current
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .clip(shape)
            .background(c.surface)
            .border(1.dp, c.border, shape)
            .padding(14.dp),
        content = content,
    )
}

@Composable
fun Label(text: String, modifier: Modifier = Modifier, color: Color = LocalWeer.current.muted, size: TextUnit = 11.sp) {
    Text(
        text.uppercase(), modifier = modifier, color = color, fontSize = size, letterSpacing = 1.2.sp,
        maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium,
    )
}

@Composable
fun ScreenTitle(title: String, trailing: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, fontSize = 26.sp, fontWeight = FontWeight.Light, color = LocalWeer.current.text,
            modifier = Modifier.weight(1f))
        trailing()
    }
}

@Composable
fun WindArrow(dir: Int, color: Color = LocalWeer.current.wind, size: TextUnit = 14.sp) {
    // Pijl wijst waar de wind naartoe waait (wind uit het noorden → pijl naar beneden).
    Text("↓", color = color, fontSize = size, modifier = Modifier.rotate(dir.toFloat()))
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    val c = LocalWeer.current
    val shape = RoundedCornerShape(50)
    Box(
        Modifier
            .padding(end = 6.dp, bottom = 6.dp)
            .clip(shape)
            .background(if (selected) c.text else Color.Transparent)
            .border(1.dp, if (selected) c.text else c.border, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(text, fontSize = 13.sp, color = if (selected) c.bg else c.muted)
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

@Composable
fun AdviceRow(a: Advice) {
    val c = LocalWeer.current
    Row(Modifier.padding(top = 8.dp)) {
        Box(Modifier.padding(top = 5.dp).size(7.dp).clip(CircleShape).background(adviceColor(a.kind)))
        Spacer(Modifier.width(8.dp))
        Column {
            Text(a.title, fontSize = 13.sp, color = c.text, fontWeight = FontWeight.Medium)
            Text(a.detail, fontSize = 12.sp, color = c.muted)
        }
    }
}

/**
 * Staafjes voor neerslag. Hoogte = hoeveelheid (tot [maxMm]); doorzichtigheid = kans, als die bekend is.
 */
@Composable
fun Bars(values: List<Double>, probs: List<Int>?, maxMm: Double, modifier: Modifier = Modifier) {
    val c = LocalWeer.current
    Canvas(modifier.fillMaxWidth().height(56.dp)) {
        if (values.isEmpty()) return@Canvas
        val n = values.size
        val gap = 2.dp.toPx()
        val w = (size.width - gap * (n - 1)) / n
        values.forEachIndexed { i, v ->
            val x = i * (w + gap)
            drawRect(c.border, Offset(x, size.height - 2.dp.toPx()), Size(w, 2.dp.toPx()))
            if (v >= 0.05) {
                val frac = (v / maxMm).coerceIn(0.08, 1.0).toFloat()
                val alpha = probs?.getOrNull(i)?.let { (0.35f + 0.65f * it / 100f).coerceIn(0.35f, 1f) } ?: 1f
                val h = size.height * frac
                drawRect(c.rain.copy(alpha = alpha), Offset(x, size.height - h), Size(w, h))
            }
        }
    }
}

@Composable
fun BarLabels(labels: List<String>) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        labels.forEach { Text(it, fontSize = 11.sp, color = LocalWeer.current.faint, style = Tnum) }
    }
}
