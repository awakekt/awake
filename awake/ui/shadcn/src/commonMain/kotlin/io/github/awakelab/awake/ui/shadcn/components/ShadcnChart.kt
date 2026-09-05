/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package io.github.awakelab.awake.ui.shadcn.components

import io.github.awakelab.awake.compose.foundation.Canvas
import io.github.awakelab.awake.compose.foundation.layout.Arrangement
import io.github.awakelab.awake.compose.foundation.layout.Box
import io.github.awakelab.awake.compose.foundation.layout.Column
import io.github.awakelab.awake.compose.foundation.layout.Row
import io.github.awakelab.awake.compose.foundation.layout.fillMaxWidth
import io.github.awakelab.awake.compose.foundation.layout.height
import io.github.awakelab.awake.compose.foundation.layout.size
import io.github.awakelab.awake.compose.runtime.Composer
import io.github.awakelab.awake.compose.ui.Alignment
import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.compose.ui.unit.Dp
import io.github.awakelab.awake.compose.ui.unit.dp
import io.github.awakelab.awake.core.color.Color
import io.github.awakelab.awake.core.graphics2d.DrawStroke
import io.github.awakelab.awake.core.graphics2d.StrokeCap
import io.github.awakelab.awake.core.graphics2d.StrokeJoin
import io.github.awakelab.awake.core.graphics2d.drawPath
import io.github.awakelab.awake.tailwind.Tw
import io.github.awakelab.awake.ui.shadcn.theme.shadcnTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Data series for bar, line, and area charts.
 *
 * @param name Series label name (e.g. "Desktop", "Mobile").
 * @param color Series theme color.
 * @param values Y-axis numerical values per category.
 */
data class ShadcnChartSeries(
    val name: String,
    val color: Color,
    val values: List<Float>,
)

/**
 * Data slice for pie and donut charts.
 *
 * @param label Slice label (e.g. "Chrome", "Safari").
 * @param value Slice numerical value.
 * @param color Slice theme color.
 */
data class ShadcnPieSlice(
    val label: String,
    val value: Float,
    val color: Color,
)

/**
 * Card container wrapping a chart with title and description headers.
 *
 * @param title Chart card title.
 * @param description Optional muted subtitle description.
 * @param modifier Custom layout modifier.
 * @param content Slot for chart primitives (`ShadcnBarChart`, `ShadcnLineChart`, `ShadcnPieChart`).
 *
 * Keywords: chart container, chart card, chart wrapper.
 */
context(_: Composer)
fun ShadcnChartContainer(
    title: String,
    description: String? = null,
    modifier: Modifier = Modifier,
    content: (
        context(Composer)
        () -> Unit
    )? = null,
) {
    ShadcnCard(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(Tw.Spacing.s2)) {
            ShadcnText(title, variant = ShadcnTextVariant.H3)
            if (description != null) {
                shadcnMuted(description)
            }
            content?.let { it() }
        }
    }
}

/**
 * Legend row displaying series color indicator dots and names.
 *
 * @param series List of [ShadcnChartSeries] to render in the legend.
 * @param modifier Custom layout modifier.
 *
 * Keywords: chart legend, chart series labels.
 */
context(_: Composer)
fun ShadcnChartLegend(
    series: List<ShadcnChartSeries>,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedByHorizontal(Tw.Spacing.s4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        series.forEach { s ->
            Row(
                horizontalArrangement = Arrangement.spacedByHorizontal(Tw.Spacing.s2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.size(8.dp))
                ShadcnBadge(label = s.name)
            }
        }
    }
}

/**
 * `ShadcnBarChart`: A vertical bar chart with rounded tops and theme grid lines.
 *
 * @param categories List of X-axis category labels (e.g. `["Jan", "Feb", "Mar"]`).
 * @param series List of [ShadcnChartSeries] values.
 * @param modifier Custom layout modifier.
 * @param height Chart canvas height (`200.dp` by default).
 *
 * Keywords: bar chart, histogram, column chart.
 */
context(_: Composer)
fun ShadcnBarChart(
    categories: List<String>,
    series: List<ShadcnChartSeries>,
    modifier: Modifier = Modifier,
    height: Dp = 200.dp,
) {
    val theme = shadcnTheme
    val maxValue = series.flatMap { it.values }.maxOrNull()?.coerceAtLeast(1f) ?: 1f

    Canvas(modifier.fillMaxWidth().height(height)) {
        val w = this.width.toFloat()
        val h = this.height.toFloat()
        val gridColor = theme.palette.border.withAlpha(0.3f)

        // Draw horizontal grid lines
        for (i in 0..3) {
            val y = h * (i / 3f)
            drawRect(x = 0f, y = y, width = w, height = 1f, color = gridColor)
        }

        if (categories.isEmpty() || series.isEmpty()) return@Canvas

        val numCategories = categories.size
        val numSeries = series.size
        val categoryWidth = w / numCategories
        val barGroupWidth = categoryWidth * 0.7f
        val barWidth = (barGroupWidth / numSeries).coerceAtLeast(4f)

        for (cIndex in categories.indices) {
            val groupStartX = cIndex * categoryWidth + (categoryWidth - barGroupWidth) / 2f
            for (sIndex in series.indices) {
                val s = series[sIndex]
                val valY = s.values.getOrNull(cIndex) ?: 0f
                val barHeight = (valY / maxValue) * (h - 20f)
                val x = groupStartX + sIndex * barWidth
                val y = h - barHeight

                drawRoundedRect(
                    x = x,
                    y = y,
                    width = barWidth * 0.85f,
                    height = barHeight.coerceAtLeast(2f),
                    color = s.color,
                    radius = 4f,
                )
            }
        }
    }
}

/**
 * `ShadcnLineChart`: A smooth line chart with data point dots and optional area fill.
 *
 * @param categories List of X-axis category labels.
 * @param series List of [ShadcnChartSeries] values.
 * @param modifier Custom layout modifier.
 * @param height Chart canvas height (`200.dp` by default).
 * @param showAreaFill Whether to draw a semi-transparent gradient fill beneath the line.
 *
 * Keywords: line chart, area chart, trend chart, curve chart.
 */
context(_: Composer)
fun ShadcnLineChart(
    categories: List<String>,
    series: List<ShadcnChartSeries>,
    modifier: Modifier = Modifier,
    height: Dp = 200.dp,
    showAreaFill: Boolean = false,
) {
    val theme = shadcnTheme
    val maxValue = series.flatMap { it.values }.maxOrNull()?.coerceAtLeast(1f) ?: 1f

    Canvas(modifier.fillMaxWidth().height(height)) {
        val w = this.width.toFloat()
        val h = this.height.toFloat()
        val gridColor = theme.palette.border.withAlpha(0.3f)

        // Draw horizontal grid lines
        for (i in 0..3) {
            val y = h * (i / 3f)
            drawRect(x = 0f, y = y, width = w, height = 1f, color = gridColor)
        }

        if (categories.size < 2 || series.isEmpty()) return@Canvas

        val stepX = w / (categories.size - 1)

        series.forEach { s ->
            val points = s.values.mapIndexed { idx, valY ->
                val x = idx * stepX
                val y = h - (valY / maxValue) * (h - 20f)
                x to y
            }

            // Draw area fill if requested
            if (showAreaFill && points.isNotEmpty()) {
                val fillPath = drawPath {
                    moveTo(points.first().first, h)
                    points.forEach { (px, py) -> lineTo(px, py) }
                    lineTo(points.last().first, h)
                    close()
                }
                drawPath(fillPath, s.color.withAlpha(0.15f))
            }

            // Draw line path
            if (points.isNotEmpty()) {
                val linePath = drawPath {
                    moveTo(points.first().first, points.first().second)
                    for (i in 1 until points.size) {
                        lineTo(points[i].first, points[i].second)
                    }
                }
                drawStrokedPath(
                    linePath,
                    DrawStroke(width = 2.5f.dp, cap = StrokeCap.Round, join = StrokeJoin.Round),
                    s.color,
                )
            }

            // Draw point dots
            points.forEach { (px, py) ->
                drawRoundedRect(
                    x = px - 3f,
                    y = py - 3f,
                    width = 6f,
                    height = 6f,
                    color = s.color,
                    radius = 3f,
                )
            }
        }
    }
}

/**
 * `ShadcnPieChart`: A pie or donut chart with colored slices and centered summary label.
 *
 * @param slices List of [ShadcnPieSlice] entries.
 * @param modifier Custom layout modifier.
 * @param size Chart square diameter (`180.dp` by default).
 * @param isDonut Whether to render a donut cutout hole in the center.
 * @param centerLabel Optional secondary label text in donut center.
 * @param centerValue Optional primary numeric value in donut center.
 *
 * Keywords: pie chart, donut chart, arc chart, percentage breakdown.
 */
context(_: Composer)
fun ShadcnPieChart(
    slices: List<ShadcnPieSlice>,
    modifier: Modifier = Modifier,
    size: Dp = 180.dp,
    isDonut: Boolean = true,
    centerLabel: String? = null,
    centerValue: String? = null,
) {
    val total = slices.sumOf { it.value.toDouble() }.toFloat().coerceAtLeast(1f)

    Box(
        modifier = modifier.size(size),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val cx = width / 2f
            val cy = height / 2f
            val outerRadius = (width.coerceAtMost(height) / 2f) - 4f
            val innerRadius = if (isDonut) outerRadius * 0.6f else 0f

            var startAngle = -PI.toFloat() / 2f

            slices.forEach { slice ->
                val sweepAngle = (slice.value / total) * 2f * PI.toFloat()
                val segments = 32
                val arcPath = drawPath {
                    moveTo(cx + cos(startAngle) * outerRadius, cy + sin(startAngle) * outerRadius)
                    for (i in 1..segments) {
                        val a = startAngle + (i.toFloat() / segments) * sweepAngle
                        lineTo(cx + cos(a) * outerRadius, cy + sin(a) * outerRadius)
                    }
                    if (isDonut) {
                        for (i in segments downTo 0) {
                            val a = startAngle + (i.toFloat() / segments) * sweepAngle
                            lineTo(cx + cos(a) * innerRadius, cy + sin(a) * innerRadius)
                        }
                    } else {
                        lineTo(cx, cy)
                    }
                    close()
                }
                drawPath(arcPath, slice.color)
                startAngle += sweepAngle
            }
        }

        if (isDonut && (centerLabel != null || centerValue != null)) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Tw.Spacing.s1),
            ) {
                if (centerValue != null) {
                    ShadcnText(centerValue, variant = ShadcnTextVariant.H3)
                }
                if (centerLabel != null) {
                    shadcnMuted(centerLabel)
                }
            }
        }
    }
}
