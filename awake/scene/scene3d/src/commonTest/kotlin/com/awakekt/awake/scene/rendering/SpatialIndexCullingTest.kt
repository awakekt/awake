/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.geometry.MeshGeometry
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.core.math.Aabb
import com.awakekt.awake.core.math.ClipSpace
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.core.text.font.UiFont
import com.awakekt.awake.ecs.World
import com.awakekt.awake.render.command.GpuDrawPreparationSource
import com.awakekt.awake.render.command.GpuDrawPreparer
import com.awakekt.awake.render.command.GpuPassInput
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.mesh.Mesh
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.render.renderer.LineSegment
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.render.texture.PbrTextureSet
import com.awakekt.awake.render.texture.RenderTarget
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.rendering.Camera
import com.awakekt.awake.scene.rendering.RenderSystem3D
import com.awakekt.awake.scene.rendering.mesh.MeshBounds
import com.awakekt.awake.scene.rendering.mesh.MeshRenderer
import com.awakekt.awake.scene.rendering.spatial.SpatialIndexSystem
import com.awakekt.awake.scene.rendering.spatial.findSpatialIndex
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The index changes how culling is computed and must not change what it decides.
 *
 * So every test here is the same scene rendered twice -- once with `SpatialIndexSystem`
 * installed, once without -- asserting the two produce the same draw calls. A faster culler that
 * quietly drops one prop is worse than no culler at all, and the difference is invisible in a
 * frame time.
 */
class SpatialIndexCullingTest {

    @Test
    fun theIndexDrawsExactlyWhatPerEntityCullingDraws() {
        val random = Random(seed = 4_242)
        val layout = scatter(random, count = 300)

        val withoutIndex = render(layout, useIndex = false)
        val withIndex = render(layout, useIndex = true)

        assertTrue(withoutIndex.isNotEmpty(), "The scene must draw something, or this proves nothing.")
        assertTrue(
            withoutIndex.size < layout.size,
            "Most of this scene is behind the camera; if everything drew, culling is not running " +
                "and the comparison below is vacuous.",
        )
        assertEquals(
            withoutIndex.toSet(),
            withIndex.toSet(),
            "The index must select the same entities the per-entity test selects.",
        )
    }

    @Test
    fun anEntityThatMovesIntoViewIsDrawnOnTheFrameItArrives() {
        val world = worldWithCamera()
        val entity = world.spawnProp(x = 0f, z = FAR_BEHIND)
        val renderer = RecordingRenderer()
        val index = SpatialIndexSystem()
        val render = RenderSystem3D(renderer)
        index.update(world, DELTA)
        render.update(world, DELTA)
        assertEquals(0, renderer.lastDrawCalls.size, "It starts behind the camera.")

        // Straight in front of the camera, which looks down -Z from z = 20.
        val transform = requireNotNull(world.get<Transform>(entity))
        transform.worldMatrix = transform.worldMatrix.translate(0f, 0f, -FAR_BEHIND)
        index.update(world, DELTA)
        render.update(world, DELTA)

        assertEquals(
            1,
            renderer.lastDrawCalls.size,
            "A moved entity has to be re-indexed in the same frame it moved, or it pops in a " +
                "frame late -- the whole failure mode a stale index produces.",
        )
    }

    @Test
    fun aDestroyedEntityLeavesTheIndex() {
        val world = worldWithCamera()
        val entity = world.spawnProp(x = 0f, z = 0f)
        val index = SpatialIndexSystem()
        index.update(world, DELTA)
        assertEquals(1, requireNotNull(world.findSpatialIndex()).grid.size)

        world.destroy(entity)
        index.update(world, DELTA)

        assertEquals(
            0,
            requireNotNull(world.findSpatialIndex()).grid.size,
            "Nothing reports a destroyed entity, so the index reconciles against the live set -- " +
                "without that, every query keeps returning an entity that no longer exists.",
        )
    }

    /**
     * The models drawn, as their float contents.
     *
     * By content because `Mat4` has no structural equality, and the two runs build separate
     * worlds -- comparing the matrices themselves compares object identity and can only ever
     * fail.
     */
    private fun render(layout: List<Pair<Float, Float>>, useIndex: Boolean): List<List<Float>> {
        val world = worldWithCamera()
        layout.forEach { (x, z) -> world.spawnProp(x, z) }
        val renderer = RecordingRenderer()
        if (useIndex) SpatialIndexSystem().update(world, DELTA)
        RenderSystem3D(renderer).update(world, DELTA)
        assertNotNull(renderer.lastDrawCalls)
        return renderer.lastDrawCalls.map { it.model.data.toList() }
    }

    private fun scatter(random: Random, count: Int): List<Pair<Float, Float>> =
        List(count) {
            (random.nextFloat() - HALF) * SPREAD to (random.nextFloat() - HALF) * SPREAD
        }

    private fun worldWithCamera(): World {
        val world = World()
        world.add(
            world.create(),
            Camera(
                Lens(
                    eye = Vec3f(0f, 0f, 20f),
                    center = Vec3f(0f, 0f, 0f),
                    fovYRadians = 1f,
                    near = 0.5f,
                    far = 200f,
                ),
            ),
        )
        return world
    }

    private fun World.spawnProp(x: Float, z: Float) = create().also { entity ->
        add(entity, Transform(worldMatrix = Mat4().translate(x, 0f, z)))
        add(entity, MeshRenderer(fakeMesh(), fakeMaterial()))
        add(entity, MeshBounds(Aabb(Vec3f(-1f, -1f, -1f), Vec3f(1f, 1f, 1f))))
    }

    private fun fakeMesh(): Mesh = object : Mesh {
        override val format = VertexFormat.PositionNormalColor
        override val sizeBytes: Long = 0
        override fun destroy() = Unit
    }

    private fun fakeMaterial(): Material = object : Material {
        override fun updateUniformBuffer(uniformFloats: FloatArray) = Unit
        override fun destroy() = Unit
    }

    private class RecordingRenderer :
        Renderer,
        GpuDrawPreparationSource {
        var lastDrawCalls: List<RenderDrawCommand> = emptyList()
        override val gpuDrawPreparer = GpuDrawPreparer { command, sourceIndex, _ ->
            if (sourceIndex == 0) lastDrawCalls = emptyList()
            lastDrawCalls += RenderDrawCommand(
                mesh = command.mesh,
                material = command.material,
                model = command.model,
                extraUniformFloats = command.extraUniformFloats,
                vertexAnimation = command.vertexAnimation,
                timeSeconds = command.timeSeconds,
                instanceModels = command.instanceModels,
                instanceJointPalettes = command.instanceJointPalettes,
                instanceColors = command.instanceColors,
                instanceFrames = command.instanceFrames,
                cullMode = command.cullMode,
                transparent = command.transparent,
            )
            null
        }
        override val clipSpace: ClipSpace = ClipSpace.WebGpu
        override var clearColor: Color = Color.Black
        override var wireframe: Boolean = false
        override fun createMesh(geometry: MeshGeometry): Mesh = error("not needed for this test")
        override fun createMaterial(
            texture: TextureAsset?,
            renderTarget: RenderTarget?,
            uniformFloatCount: Int,
            pbrTextures: PbrTextureSet?,
        ): Material = error("not needed for this test")

        override fun createRenderTarget(width: Int, height: Int): RenderTarget =
            error("not needed for this test")

        override fun draw(input: GpuPassInput) {
        }

        override suspend fun readPixels(target: RenderTarget): TextureAsset =
            error("not needed for this test")

        override fun drawUi(primitives: List<UiDrawPrimitive>, font: UiFont?) = Unit
        override fun drawDebugLines(lines: List<LineSegment>) = Unit
        override fun destroy() = Unit
    }

    private companion object {
        const val DELTA = 1f / 60f
        const val SPREAD = 300f
        const val HALF = 0.5f
        const val FAR_BEHIND = 100f
    }
}
