/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.sample.uishowcase.ui.pages.status

import com.awakekt.awake.compose.foundation.layout.Arrangement
import com.awakekt.awake.compose.foundation.layout.Column
import com.awakekt.awake.compose.foundation.layout.Row
import com.awakekt.awake.compose.runtime.remember
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.sample.uishowcase.ui.ShowcaseCategory
import com.awakekt.awake.sample.uishowcase.ui.ShowcasePage
import com.awakekt.awake.tailwind.Tw
import com.awakekt.awake.ui.shadcn.components.ShadcnBarChart
import com.awakekt.awake.ui.shadcn.components.ShadcnChartContainer
import com.awakekt.awake.ui.shadcn.components.ShadcnChartLegend
import com.awakekt.awake.ui.shadcn.components.ShadcnChartSeries
import com.awakekt.awake.ui.shadcn.components.ShadcnLineChart
import com.awakekt.awake.ui.shadcn.components.ShadcnPieChart
import com.awakekt.awake.ui.shadcn.components.ShadcnPieSlice

internal val ChartPage = ShowcasePage(
    id = "chart",
    title = "Chart",
    category = ShowcaseCategory.Status,
    description = "Beautiful, accessible bar, line, area, and donut charts styled with OKLCH theme tokens.",
    usageCode = """
ShadcnChartContainer(title = "Monthly Active Users") {
    ShadcnBarChart(categories = months, series = userSeries)
}
    """.trimIndent(),
    referenceExample = "registry/new-york-v4/examples/chart-demo.tsx",
    previewHeight = 650,
    hero = {
        val months = remember { listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun") }

        val barSeries = remember {
            listOf(
                ShadcnChartSeries("Desktop", Color.fromHex(0x2563EB), listOf(186f, 305f, 237f, 73f, 209f, 214f)),
                ShadcnChartSeries("Mobile", Color.fromHex(0x60A5FA), listOf(80f, 200f, 120f, 190f, 130f, 140f)),
            )
        }

        val pieSlices = remember {
            listOf(
                ShadcnPieSlice("Chrome", 275f, Color.fromHex(0x2563EB)),
                ShadcnPieSlice("Safari", 200f, Color.fromHex(0x38BDF8)),
                ShadcnPieSlice("Firefox", 187f, Color.fromHex(0x818CF8)),
                ShadcnPieSlice("Edge", 173f, Color.fromHex(0xC084FC)),
                ShadcnPieSlice("Other", 90f, Color.fromHex(0xE879F9)),
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(Tw.Spacing.s4)) {
            ShadcnChartContainer(
                title = "Bar Chart - Multiple Series",
                description = "Showing total desktop and mobile visitors for the last 6 months",
            ) {
                ShadcnChartLegend(series = barSeries)
                ShadcnBarChart(categories = months, series = barSeries)
            }

            ShadcnChartContainer(
                title = "Area Line Chart - Interactive",
                description = "Smooth curve line visualization with area fill",
            ) {
                ShadcnLineChart(categories = months, series = barSeries, showAreaFill = true)
            }

            ShadcnChartContainer(
                title = "Donut Chart - Browser Share",
                description = "January - June 2026",
            ) {
                Row(horizontalArrangement = Arrangement.CenterHorizontally) {
                    ShadcnPieChart(
                        slices = pieSlices,
                        isDonut = true,
                        centerValue = "925",
                        centerLabel = "Visitors",
                    )
                }
            }
        }
    },
)
