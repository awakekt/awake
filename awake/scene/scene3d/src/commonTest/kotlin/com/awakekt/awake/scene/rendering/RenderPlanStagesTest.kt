/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering

import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.World
import com.awakekt.awake.render.command.GpuDrawPreparationContext
import com.awakekt.awake.render.command.GpuDrawPreparer
import com.awakekt.awake.render.command.GpuDrawRequest
import com.awakekt.awake.render.command.GpuResolvedDraw
import com.awakekt.awake.render.renderer.RenderDrawStage
import com.awakekt.awake.render.renderer.RenderDrawStages
import com.awakekt.awake.render.testing.NoopRenderer
import com.awakekt.awake.scene.rendering.camera.Camera
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The frame [RenderSystem3D] plans tells its collect and compile stages, in order, so a profile can
 * split the planning that runs before the renderer draws (#1621). A capture tells nothing: it is
 * planned inside another phase's mark, and telling it there would put one mark inside another.
 */
class RenderPlanStagesTest {
    private val world = World()
    private val camera = Camera(Lens(eye = Vec3f(0f, 5f, 10f), center = Vec3f.ZERO, fovYRadians = 1f, near = 0.1f, far = 100f))
    private val system = RenderSystem3D(NoopRenderer(), drawPreparer = NothingToPrepare)
    private val told = mutableListOf<String>()

    init {
        world.add(world.create(), camera)
        system.drawStages = object : RenderDrawStages {
            override fun begin(stage: RenderDrawStage) {
                told += "begin $stage"
            }
            override fun end(stage: RenderDrawStage) {
                told += "end $stage"
            }
        }
    }

    @Test
    fun aPlannedFrameTellsItsCollectAndCompileStages() {
        system.update(world, DELTA)

        assertEquals(listOf("begin Collect", "end Collect", "begin Compile", "end Compile"), told)
    }

    @Test
    fun aCaptureTellsNothing() {
        system.planCapture(world, camera, WIDTH, HEIGHT)

        assertEquals(emptyList(), told)
    }

    @Test
    fun withNoneSetAFrameStillPlans() {
        system.drawStages = null

        system.update(world, DELTA)

        assertEquals(emptyList(), told)
        assertEquals(true, system.lastFramePlanned)
    }

    /** The frame has no renderables, so nothing is ever prepared. */
    private object NothingToPrepare : GpuDrawPreparer {
        override fun prepare(request: GpuDrawRequest, sourceIndex: Int, context: GpuDrawPreparationContext): GpuResolvedDraw? = null
        override fun canInstance(format: VertexFormat): Boolean = false
    }

    private companion object {
        const val DELTA = 1f / 60f
        const val WIDTH = 64
        const val HEIGHT = 48
    }
}
