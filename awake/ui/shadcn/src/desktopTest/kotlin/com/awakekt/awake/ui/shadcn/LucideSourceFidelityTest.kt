/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.shadcn

import com.awakekt.awake.compose.ui.graphics.vector.fitTo
import com.awakekt.awake.core.graphics2d.PathCommand
import com.awakekt.awake.core.graphics2d.StrokeCap
import com.awakekt.awake.core.graphics2d.StrokeJoin
import com.awakekt.awake.core.math2d.Rectangle
import com.awakekt.awake.ui.shadcn.components.LucideIcons
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LucideSourceFidelityTest {
    @Test
    fun pinnedIconsKeepTheirSourceStroke() {
        val icons = listOf(
            LucideIcons.camera,
            LucideIcons.check,
            LucideIcons.chevronDown,
            LucideIcons.chevronRight,
            LucideIcons.chevronUp,
            LucideIcons.chevronsUpDown,
            LucideIcons.galleryVerticalEnd,
            LucideIcons.save,
            LucideIcons.x,
        )
        icons.forEach { icon ->
            assertEquals(24f, icon.viewportWidth)
            assertEquals(24f, icon.viewportHeight)
            assertTrue(icon.paths.isNotEmpty())
            icon.paths.forEach { path ->
                assertEquals(null, path.fill)
                val stroke = requireNotNull(path.stroke)
                assertEquals(2f, stroke.width.value)
                assertEquals(StrokeCap.Round, stroke.cap)
                assertEquals(StrokeJoin.Round, stroke.join)
            }
        }
    }

    @Test
    fun sourceCurvesSurviveAtSmallAndLargeSlots() {
        listOf(LucideIcons.camera, LucideIcons.galleryVerticalEnd, LucideIcons.save).forEach { icon ->
            assertTrue(icon.paths.any { path -> path.path.commands.any { it is PathCommand.CubicTo } })
            assertEquals(4f / 3f, icon.fitTo(Rectangle(0f, 0f, 16f, 16f))[0].stroke!!.width.value)
            assertEquals(4f, icon.fitTo(Rectangle(0f, 0f, 48f, 48f))[0].stroke!!.width.value)
        }
    }
}
