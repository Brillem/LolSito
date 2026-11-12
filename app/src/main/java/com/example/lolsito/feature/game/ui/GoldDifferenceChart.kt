package com.example.lolsito.feature.game.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.example.lolsito.feature.game.domain.GoldPoint
import kotlin.math.abs

/** Smallest gold range either side of the centre line, so an even game does not look like a swing. */
private const val MIN_GOLD_SCALE = 1000

/** Minute mark spacings to choose from, smallest first; the chart uses the first that gives at most [MAX_MINUTE_MARKS]. */
private val MINUTE_MARK_STEPS = listOf(5, 10, 15, 20, 30)
private const val MAX_MINUTE_MARKS = 6

/**
 * Gold difference over game time: above the centre line the blue side leads, below it the red side.
 * Minute marks run along the bottom. Draws nothing with fewer than two [points].
 */
@Composable
fun GoldDifferenceChart(points: List<GoldPoint>, blueColor: Color, redColor: Color, modifier: Modifier = Modifier) {
    if (points.size < 2) return
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val axisColor = MaterialTheme.colorScheme.outlineVariant
    val scale = maxOf(points.maxOf { abs(it.difference) }, MIN_GOLD_SCALE)
    val totalSeconds = points.last().gameTime.seconds.coerceAtLeast(1)
    val totalMinutes = (totalSeconds / 60).toInt()
    val markStep = MINUTE_MARK_STEPS.firstOrNull { totalMinutes / it <= MAX_MINUTE_MARKS } ?: MINUTE_MARK_STEPS.last()

    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            "Gold difference",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(140.dp)
                .padding(top = 4.dp)
                .semantics { contentDescription = "Gold difference over the game, blue side above, red side below" },
        ) {
            val minuteLabelHeight = textMeasurer.measure("0", labelStyle).size.height.toFloat()
            val chartHeight = size.height - minuteLabelHeight - 4.dp.toPx()
            val middle = chartHeight / 2
            fun x(seconds: Long) = size.width * seconds / totalSeconds
            fun y(difference: Int) = middle - middle * difference / scale

            val line = Path()
            val area = Path().apply { moveTo(x(points.first().gameTime.seconds), middle) }
            points.forEachIndexed { index, point ->
                val px = x(point.gameTime.seconds)
                val py = y(point.difference)
                if (index == 0) line.moveTo(px, py) else line.lineTo(px, py)
                area.lineTo(px, py)
            }
            area.lineTo(x(points.last().gameTime.seconds), middle)
            area.close()

            val stroke = Stroke(width = 2.dp.toPx(), join = StrokeJoin.Round)
            clipRect(bottom = middle) {
                drawPath(area, blueColor.copy(alpha = 0.25f))
                drawPath(line, blueColor, style = stroke)
            }
            clipRect(top = middle, bottom = chartHeight) {
                drawPath(area, redColor.copy(alpha = 0.25f))
                drawPath(line, redColor, style = stroke)
            }
            drawLine(axisColor, Offset(0f, middle), Offset(size.width, middle), strokeWidth = 1.dp.toPx())

            // Scale: the gold lead at the top (blue) and bottom (red) edges.
            val scaleLabel = textMeasurer.measure(formatGold(scale), labelStyle)
            drawText(scaleLabel, color = blueColor, topLeft = Offset(0f, 0f))
            drawText(scaleLabel, color = redColor, topLeft = Offset(0f, chartHeight - scaleLabel.size.height))

            for (minute in 0..totalMinutes step markStep) {
                val px = x(minute * 60L)
                drawLine(axisColor, Offset(px, middle - 3.dp.toPx()), Offset(px, middle + 3.dp.toPx()), strokeWidth = 1.dp.toPx())
                val label = textMeasurer.measure("${minute}m", labelStyle)
                val left = (px - label.size.width / 2f).coerceIn(0f, size.width - label.size.width)
                drawText(label, topLeft = Offset(left, size.height - label.size.height))
            }
        }
    }
}
