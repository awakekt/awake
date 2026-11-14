/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.planning

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.geometry.MeshGeometry
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.Aabb
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.render.command.BufferHandle
import com.awakekt.awake.render.command.GpuDrawPreparationContext
import com.awakekt.awake.render.command.GpuDrawPreparer
import com.awakekt.awake.render.command.GpuDrawRequest
import com.awakekt.awake.render.command.GpuResolvedDraw
import com.awakekt.awake.render.command.MaterialBinding
import com.awakekt.awake.render.command.PipelineHandle
import com.awakekt.awake.render.passes.SceneDrawStats
import com.awakekt.awake.render.pipeline.CullMode
import com.awakekt.awake.render.testing.NoopRenderer
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.rendering.RenderSystem3D
import com.awakekt.awake.scene.rendering.camera.Camera
import com.awakekt.awake.scene.rendering.debug.RenderDiagnostics
import com.awakekt.awake.scene.rendering.light.Light
import com.awakekt.awake.scene.rendering.mesh.DitherFade
import com.awakekt.awake.scene.rendering.mesh.MeshBounds
import com.awakekt.awake.scene.rendering.mesh.MeshRenderer
import com.awakekt.awake.scene.rendering.mesh.PbrMaterial
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** N renderers sharing M meshes submit on the order of M draws a pass, under a shadowing sun. */
class SharedMeshDrawCountTest {
    @AfterTest
    fun stopCounting() {
        RenderDiagnostics.drawStatsEnabled = false
    }

    @Test
    fun aTownOfSharedMeshesDrawsAboutOneDrawPerMeshInEveryPass() {
        val stats = plan(CullMode.Back) { _, _, _ -> }

        assertEquals(MESHES, stats.opaqueDraws, "3,000 renderers, 190 meshes: $stats")
        assertShadowPassesDrawAtMostOnePerMesh(stats)
    }

    @Test
    fun tintedAndFadingCopiesCastAsOneDrawPerMesh() {
        val stats = plan(CullMode.None, tints = false, decorate = ::tintAndFade)

        assertTrue(stats.opaqueDraws > MESHES, "a pipeline that reads no tint keeps tints apart, or this proves nothing: $stats")
        assertShadowPassesDrawAtMostOnePerMesh(stats)
    }

    @Test
    fun tintedAndFadingCopiesDrawAsOneDrawPerMeshWhereThePipelineTintsInstances() {
        val stats = plan(CullMode.None, tints = true, decorate = ::tintAndFade)

        assertEquals(MESHES, stats.opaqueDraws, "seven tints and five fades over 190 meshes: $stats")
        assertShadowPassesDrawAtMostOnePerMesh(stats)
    }

    private fun tintAndFade(world: World, entity: Entity, index: Int) {
        world.add(entity, PbrMaterial(baseColorFactor = Color(1f, (index % 7) / 7f, 1f)))
        world.add(entity, DitherFade(opacity = 0.5f + (index % 5) / 10f))
    }

    private fun assertShadowPassesDrawAtMostOnePerMesh(stats: SceneDrawStats) {
        assertTrue(stats.shadowDraws > 0, "the sun casts: $stats")
        for (pass in 0 until stats.shadowPasses) {
            assertTrue(stats.shadowPassDraws(pass) <= MESHES, "shadow pass $pass drew ${stats.shadowPassDraws(pass)}: $stats")
        }
    }

    private fun plan(cullMode: CullMode, tints: Boolean = false, decorate: (World, Entity, Int) -> Unit): SceneDrawStats {
        val renderer = NoopRenderer()
        val world = World()
        val meshes = List(MESHES) { renderer.createMesh(MeshGeometry(FloatArray(0), IntArray(0), VertexFormat.PositionNormalColorUv)) }
        val materials = List(MESHES) { renderer.createMaterial() }
        repeat(RENDERERS) { index ->
            val entity = world.create()
            world.add(entity, Transform(position = Vec3f((index % SIDE - SIDE / 2) * 4f, 0f, (index / SIDE - SIDE / 2) * 4f)))
            world.add(entity, MeshRenderer(meshes[index % MESHES], materials[index % MESHES], cullMode = cullMode))
            world.add(entity, MeshBounds(Aabb(Vec3f(-1f, -1f, -1f), Vec3f(1f, 1f, 1f))))
            decorate(world, entity, index)
        }
        world.add(world.create(), Camera(Lens(eye = Vec3f(0f, 30f, 60f), center = Vec3f.ZERO, fovYRadians = 1f, near = 0.1f, far = 400f)))
        world.add(world.create(), Light(direction = Vec3f(0.4f, 1f, 0.3f)))
        RenderDiagnostics.drawStatsEnabled = true
        RenderSystem3D(renderer, drawPreparer = InstancingPreparer(tints)).update(world, 1f / 60f)
        return checkNotNull(RenderDiagnostics.drawStats)
    }

    /** A backend with instanced pipelines for every format and for back culling, tinting copies where [tints]. */
    private class InstancingPreparer(private val tints: Boolean) : GpuDrawPreparer {
        private val handle = object : PipelineHandle, MaterialBinding, BufferHandle {}

        override fun prepare(request: GpuDrawRequest, sourceIndex: Int, context: GpuDrawPreparationContext) = GpuResolvedDraw(
            pipeline = handle,
            materialBinding = handle,
            vertexBuffer = handle,
            indexBuffer = null,
            elementCount = 36,
            instances = request.instanceModels?.size ?: 1,
        )

        override fun canInstance(format: VertexFormat): Boolean = true

        override fun canInstance(format: VertexFormat, cullMode: CullMode): Boolean = cullMode != CullMode.Front

        override fun canTintInstances(format: VertexFormat, cullMode: CullMode): Boolean = tints && canInstance(format, cullMode)
    }

    private companion object {
        const val RENDERERS = 3_000
        const val MESHES = 190
        const val SIDE = 55
    }
}
