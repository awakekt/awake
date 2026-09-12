/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.showcase.render

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.graphics2d.DrawCommand
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.render.passes2d.DrawRunCoalescer
import com.awakekt.awake.render.passes2d.StagedDrawRun

/**
 * Minimal 2D showcase coordinator.
 *
 * This intentionally stops at backend-neutral [StagedDrawRun]s. The sample proves that authored
 * 2D content can depend on [com.awakekt.awake.render.passes2d] without importing `scene3d`, a
 * backend renderer, or the RHI. A reusable ECS-driven 2D system belongs in a future `scene2d`
 * module after sprite/tilemap components have a real engine-wide owner.
 */
internal class RenderSystem2D {
    fun demoCommands(): List<UiDrawPrimitive> = listOf(
        DrawCommand.Quad(
            x = 24f,
            y = 24f,
            w = 96f,
            h = 64f,
            color = Color.White,
            tokenId = "engine-showcase-2d-proof",
        ),
    )

    fun stageDemoFrame(): List<StagedDrawRun> = DrawRunCoalescer.coalesce(
        demoCommands(),
    )
}
