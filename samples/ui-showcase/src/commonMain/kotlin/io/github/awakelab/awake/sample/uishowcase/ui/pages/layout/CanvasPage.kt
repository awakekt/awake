/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.sample.uishowcase.ui.pages.layout

import io.github.awakelab.awake.compose.foundation.Canvas
import io.github.awakelab.awake.compose.foundation.layout.height
import io.github.awakelab.awake.compose.foundation.layout.width
import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.compose.ui.unit.dp
import io.github.awakelab.awake.sample.uishowcase.ui.ShowcaseCategory
import io.github.awakelab.awake.sample.uishowcase.ui.ShowcasePage
import io.github.awakelab.awake.ui.shadcn.components.shadcnMuted
import io.github.awakelab.awake.ui.shadcn.components.shadcnSurface
import io.github.awakelab.awake.ui.shadcn.theme.shadcnTheme

internal val CanvasPage = ShowcasePage(
    id = "canvas",
    title = "Canvas",
    category = ShowcaseCategory.Layout,
    description = "Escape hatch for custom drawing: rects, rounded rects, and a clipped region.",
    usageCode = """Canvas(Modifier.width(360.dp).height(190.dp)) { drawRect(color = ...) }""",
    previewWidth = 520,
    previewHeight = 400,
    notes = listOf("Custom drawing stays an explicit advanced API; ordinary components stay on shadcn recipes."),
    hero = {
        val theme = shadcnTheme
        shadcnMuted("Canvas is a raw draw surface -- coordinates are node-local, origin at the top-left.")
        shadcnSurface(modifier = Modifier.width(420.dp).height(220.dp)) {
            Canvas(Modifier.width(360.dp).height(190.dp)) {
                drawRect(x = 0f, y = 0f, width = width.toFloat(), height = 56f, color = theme.palette.primary)
                clipped(inset = 4f) {
                    drawRect(x = 20f, y = 18f, width = 48f, height = 48f, color = theme.palette.accent)
                }
                drawRoundedRect(
                    x = 188f,
                    y = 82f,
                    width = 92f,
                    height = 92f,
                    color = theme.palette.muted,
                    radius = theme.radii.md.value,
                )
            }
        }
    },
)
