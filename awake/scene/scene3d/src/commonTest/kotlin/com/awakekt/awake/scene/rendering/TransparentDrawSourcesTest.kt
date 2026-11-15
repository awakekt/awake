/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering

import com.awakekt.awake.core.geometry.MeshGeometry
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.Aabb
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.World
import com.awakekt.awake.render.command.BufferHandle
import com.awakekt.awake.render.command.GpuDrawPreparationContext
import com.awakekt.awake.render.command.GpuDrawPreparer
import com.awakekt.awake.render.command.GpuDrawRequest
import com.awakekt.awake.render.command.GpuPassInput
import com.awakekt.awake.render.command.GpuResolvedDraw
import com.awakekt.awake.render.command.MaterialBinding
import com.awakekt.awake.render.command.PipelineHandle
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.render.renderer.createMaterial
import com.awakekt.awake.render.testing.NoopRenderer
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.core.transform.TransformSystem
import com.awakekt.awake.scene.rendering.camera.Camera
import com.awakekt.awake.scene.rendering.debug.RenderDiagnostics
import com.awakekt.awake.scene.rendering.mesh.MeshBounds
import com.awakekt.awake.scene.rendering.mesh.MeshRenderer
import com.awakekt.awake.scene.rendering.spatial.SpatialIndexSystem
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Each view's blended draws are counted by source, the main view apart from each capture (#1621),
 * so a portrait drawing more transparent geometry than expected says which source to look at.
 */
class TransparentDrawSourcesTest {
    private val renderer = NoopRenderer()
    private val world = World()
    private val handle = object : PipelineHandle, MaterialBinding, BufferHandle {}
    private val preparer = object : GpuDrawPreparer {
        override fun prepare(request: GpuDrawRequest, sourceIndex: Int, context: GpuDrawPreparationContext) =
            GpuResolvedDraw(pipeline = handle, materialBinding = handle, vertexBuffer = handle, indexBuffer = null, elementCount = 3)

        override fun canInstance(format: VertexFormat) = true

        override val redrawsPreparedFrames get() = false
    }
    private val mesh = renderer.createMesh(MeshGeometry(FloatArray(0), IntArray(0), VertexFormat.PositionNormalColor))
    private val material = renderer.createMaterial()

    /** A feature drawing [GLOWS] blended quads wherever the camera is, as a feature that does not cull does. */
    private class Glow(private val draws: List<RenderDrawCommand>) : RenderFeature3D {
        override fun collect(world: World, context: RenderFeatureContext3D) = RenderContribution(draws)
    }

    private val glow = Glow(List(GLOWS) { RenderDrawCommand(mesh, material, model = Mat4(), transparent = true) })
    private val system = RenderSystem3D(renderer, drawPreparer = preparer, features = listOf(glow))
    private val portrait = Camera(
        Lens(eye = Vec3f(0f, HIGH, 5f), center = Vec3f(0f, HIGH, 0f), fovYRadians = 0.6f, near = 0.1f, far = 20f),
        isPrimary = false,
    )

    @AfterTest
    fun off() {
        RenderDiagnostics.drawStatsEnabled = false
    }

    @Test
    fun eachViewCountsItsBlendedDrawsBySource() {
        RenderDiagnostics.drawStatsEnabled = true
        populate()

        frame()
        system.planCapture(world, portrait, SIZE, SIZE)

        val main = checkNotNull(RenderDiagnostics.transparentSources)
        val capture = RenderDiagnostics.captureTransparentSources.single()
        println("Main view $main; portrait $capture")
        assertEquals(listOf("Glow"), main.featureNames)
        assertEquals(PROPS, main.meshRenderers, "the main view sees every blended prop")
        assertEquals(GLOWS, main.featureDraws("Glow"))
        assertEquals(0, capture.meshRenderers, "the portrait's culling drops every prop")
        assertEquals(GLOWS, capture.featureDraws("Glow"), "and names the feature that draws there anyway")
        assertEquals(GLOWS, capture.total)
    }

    @Test
    fun aPortraitFarFromTheTownDrawsNoneOfItsBillboardGlows() {
        RenderDiagnostics.drawStatsEnabled = true
        populate(billboards = true)
        // In front of the portrait, so culling is not simply dropping every billboard.
        val near = world.create()
        world.add(near, Transform(position = Vec3f(-0.4f, 19_999.8f, -0.3f)))
        world.add(near, MeshRenderer(mesh, material, transparent = true, additive = true, billboard = true))
        world.add(near, MeshBounds(Aabb(Vec3f(-0.5f, -0.5f, 0f), Vec3f(0.5f, 0.5f, 0f))))
        // The consumer's portrait lens on 0.51.0: orthographic, turned to face the sun, 19,900 above the town.
        val portrait = Camera(
            Lens(
                eye = Vec3f(-2.163f, 19_997.465f, -1.280f),
                center = Vec3f(1.384f, 20_002.220f, 1.467f),
                up = Vec3f(-0.783f, 0.081f, 0.616f),
                fovYRadians = 1f,
                near = 0.1f,
                far = 20f,
            ).apply {
                projection = Lens.Projection.Orthographic
                orthoHalfHeight = 1.207f
            },
            isPrimary = false,
        )
        for (indexed in listOf(false, true)) {
            if (indexed) SpatialIndexSystem().update(world, 1f / 60f)
            frame()
            system.planCapture(world, portrait, SIZE, SIZE)
            assertEquals(1, RenderDiagnostics.captureTransparentSources.single().meshRenderers, "indexed=$indexed: the portrait only the one before it")
            assertEquals(PROPS, checkNotNull(RenderDiagnostics.transparentSources).meshRenderers, "indexed=$indexed: the main view sees every glow in the town")
        }
    }

    @Test
    fun aNewFrameStartsItsCapturesAfresh() {
        RenderDiagnostics.drawStatsEnabled = true
        populate()
        frame()
        system.planCapture(world, portrait, SIZE, SIZE)

        frame()

        assertEquals(emptyList(), RenderDiagnostics.captureTransparentSources, "last frame's portrait is not this frame's")
    }

    @Test
    fun offNothingIsCounted() {
        populate()

        frame()
        system.planCapture(world, portrait, SIZE, SIZE)

        assertNull(RenderDiagnostics.transparentSources)
        assertEquals(emptyList(), RenderDiagnostics.captureTransparentSources)
    }

    private fun populate(billboards: Boolean = false) {
        repeat(PROPS) { index ->
            val prop = world.create()
            world.add(prop, Transform(position = Vec3f((index % ROW - ROW / 2) * 4f, 0f, (index / ROW) * 4f)))
            world.add(prop, MeshRenderer(mesh, material, transparent = true, additive = true, billboard = billboards))
            world.add(prop, MeshBounds(Aabb(Vec3f(-1f, -1f, -1f), Vec3f(1f, 1f, 1f))))
        }
        world.add(world.create(), Camera(Lens(eye = Vec3f(0f, 30f, 60f), center = Vec3f.ZERO, fovYRadians = 1f, near = 0.1f, far = 400f)))
    }

    private fun frame() {
        TransformSystem().update(world, 1f / 60f)
        system.update(world, 1f / 60f)
    }

    private companion object {
        const val PROPS = 30
        const val ROW = 10
        const val GLOWS = 5
        const val HIGH = 20_000f
        const val SIZE = 64
    }
}
