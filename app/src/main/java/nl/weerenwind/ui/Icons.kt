package nl.weerenwind.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * Eigen lijn-iconen: wit voor de vorm, één accentkleur voor wat ertoe doet
 * (zon, druppels, sneeuw, bliksem). Zelfde lijndikte overal.
 */
@Composable
fun WeatherIcon(code: Int, isDay: Boolean, size: Dp = 28.dp, modifier: Modifier = Modifier) {
    val c = LocalWeer.current
    Canvas(modifier.size(size)) {
        val sw = this.size.minDimension * 0.065f
        val stroke = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        when (code) {
            0, 1 -> if (isDay) sun(Offset(0.5f, 0.5f), 1f, c.sun, stroke) else moon(Offset(0.5f, 0.5f), 1f, c.text, stroke)
            2 -> {
                if (isDay) sun(Offset(0.36f, 0.36f), 0.7f, c.sun, stroke) else moon(Offset(0.36f, 0.36f), 0.7f, c.muted, stroke)
                cloud(Offset(0.58f, 0.62f), 0.8f, c.text, c.bg, stroke)
            }
            3 -> cloud(Offset(0.5f, 0.52f), 1f, c.text, c.bg, stroke)
            45, 48 -> fog(c.text, stroke)
            51, 53, 55, 56, 57 -> { cloud(Offset(0.5f, 0.4f), 0.9f, c.text, c.bg, stroke); drops(c.rain, sw, short = true) }
            in 61..67, in 80..82 -> { cloud(Offset(0.5f, 0.4f), 0.9f, c.text, c.bg, stroke); drops(c.rain, sw, short = false) }
            in 71..77, 85, 86 -> { cloud(Offset(0.5f, 0.4f), 0.9f, c.text, c.bg, stroke); flakes(c.cold, sw) }
            95, 96, 99 -> { cloud(Offset(0.5f, 0.4f), 0.9f, c.text, c.bg, stroke); bolt(c.warm, stroke) }
            else -> cloud(Offset(0.5f, 0.52f), 1f, c.muted, c.bg, stroke)
        }
    }
}

private fun DrawScope.p(x: Float, y: Float) = Offset(x * size.width, y * size.height)

private fun DrawScope.sun(center: Offset, scale: Float, color: Color, stroke: Stroke) {
    val ctr = p(center.x, center.y)
    val r = size.minDimension * 0.17f * scale
    drawCircle(color, r, ctr, style = stroke)
    for (i in 0 until 8) {
        val a = Math.toRadians(i * 45.0)
        val d = Offset(cos(a).toFloat(), sin(a).toFloat())
        drawLine(color, ctr + d * (r * 1.55f), ctr + d * (r * 2.15f), stroke.width, StrokeCap.Round)
    }
}

private fun DrawScope.moon(center: Offset, scale: Float, color: Color, stroke: Stroke) {
    val ctr = p(center.x, center.y)
    val r = size.minDimension * 0.27f * scale
    val a = Path().apply { addOval(Rect(ctr, r)) }
    val b = Path().apply { addOval(Rect(ctr + Offset(r * 0.55f, -r * 0.4f), r * 0.85f)) }
    drawPath(Path.combine(PathOperation.Difference, a, b), color, style = stroke)
}

private fun DrawScope.cloud(center: Offset, scale: Float, color: Color, fill: Color, stroke: Stroke) {
    val ctr = p(center.x, center.y)
    val u = size.minDimension * 0.1f * scale
    val path = listOf(
        Path().apply { addOval(Rect(ctr + Offset(-1.6f * u, 0.4f * u), 1.25f * u)) },
        Path().apply { addOval(Rect(ctr + Offset(0f, -0.5f * u), 1.9f * u)) },
        Path().apply { addOval(Rect(ctr + Offset(1.75f * u, 0.5f * u), 1.15f * u)) },
        Path().apply { addRect(Rect(ctr.x - 1.6f * u, ctr.y + 0.4f * u, ctr.x + 1.75f * u, ctr.y + 1.65f * u)) },
    ).reduce { acc, next -> Path.combine(PathOperation.Union, acc, next) }
    drawPath(path, fill, style = Fill)
    drawPath(path, color, style = stroke)
}

private fun DrawScope.drops(color: Color, sw: Float, short: Boolean) {
    val len = if (short) 0.07f else 0.16f
    listOf(0.33f, 0.5f, 0.67f).forEachIndexed { i, x ->
        val y = if (i == 1) 0.72f else 0.68f
        drawLine(color, p(x + 0.03f, y), p(x - 0.03f, y + len), sw, StrokeCap.Round)
    }
}

private fun DrawScope.flakes(color: Color, sw: Float) {
    listOf(0.34f to 0.74f, 0.5f to 0.84f, 0.66f to 0.74f).forEach { (x, y) ->
        val ctr = p(x, y)
        val r = size.minDimension * 0.05f
        for (i in 0 until 3) {
            val a = Math.toRadians(i * 60.0)
            val d = Offset(cos(a).toFloat(), sin(a).toFloat()) * r
            drawLine(color, ctr - d, ctr + d, sw * 0.8f, StrokeCap.Round)
        }
    }
}

private fun DrawScope.bolt(color: Color, stroke: Stroke) {
    val path = Path().apply {
        moveTo(p(0.55f, 0.6f).x, p(0.55f, 0.6f).y)
        lineTo(p(0.44f, 0.78f).x, p(0.44f, 0.78f).y)
        lineTo(p(0.56f, 0.78f).x, p(0.56f, 0.78f).y)
        lineTo(p(0.46f, 0.95f).x, p(0.46f, 0.95f).y)
    }
    drawPath(path, color, style = stroke)
}

private fun DrawScope.fog(color: Color, stroke: Stroke) {
    listOf(Triple(0.18f, 0.82f, 0.36f), Triple(0.26f, 0.74f, 0.52f), Triple(0.18f, 0.66f, 0.68f)).forEach { (a, b, y) ->
        drawLine(color, p(a, y), p(b, y), stroke.width, StrokeCap.Round)
    }
}

/** Windpijl: wijst waar de wind naartoe waait (wind uit het noorden → pijl naar beneden). */
@Composable
fun WindArrow(dir: Int, color: Color = LocalWeer.current.wind, size: Dp = 12.dp) {
    Canvas(Modifier.size(size)) {
        rotate(dir.toFloat()) {
            val sw = this.size.minDimension * 0.12f
            val top = Offset(center.x, this.size.height * 0.1f)
            val bottom = Offset(center.x, this.size.height * 0.9f)
            drawLine(color, top, bottom, sw, StrokeCap.Round)
            drawLine(color, bottom, Offset(center.x - this.size.width * 0.28f, this.size.height * 0.62f), sw, StrokeCap.Round)
            drawLine(color, bottom, Offset(center.x + this.size.width * 0.28f, this.size.height * 0.62f), sw, StrokeCap.Round)
        }
    }
}
