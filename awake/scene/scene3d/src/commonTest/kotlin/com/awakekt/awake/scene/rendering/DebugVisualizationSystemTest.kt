/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.geometry.MeshGeometry
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.core.math.Aabb
import com.awakekt.awake.core.math.ClipSpace
import com.awakekt.awake.core.math.Frustum
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.core.text.font.UiFont
import com.awakekt.awake.ecs.World
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.mesh.Mesh
import com.awakekt.awake.render.renderer.LineSegment
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.render.texture.PbrTextureSet
import com.awakekt.awake.render.texture.RenderTarget
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.rendering.CONSERVATIVE_ASPECT
import com.awakekt.awake.scene.rendering.debug.DebugVisualizationSystem
import com.awakekt.awake.scene.rendering.debug.WorldDebugSettings
import com.awakekt.awake.scene.rendering.mesh.InstancedMeshRenderer
import com.awakekt.awake.scene.rendering.mesh.MeshBounds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DebugVisualizationSystemTest {
    private class RecordingRenderer : Renderer {
        var lastDebugLines: List<LineSegment>? = null
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

        override suspend fun readPixels(target: RenderTarget): TextureAsset =
            error("not needed for this test")

        override fun drawUi(primitives: List<UiDrawPrimitive>, font: UiFont?) = Unit
        override fun drawDebugLines(lines: List<LineSegment>) {
            lastDebugLines = lines
        }

        override fun destroy() = Unit
    }

    private fun worldWithPrimaryCamera(): World {
        val world = World()
        val cameraEntity = world.create()
        world.add(
            cameraEntity,
            com.awakekt.awake.scene.rendering.Camera(
                Lens(
                    eye = Vec3f(0f, 0f, 5f),
                    center = Vec3f(0f, 0f, 0f),
                    fovYRadians = 1f,
                    near = 0.1f,
                    far = 100f,
                ),
            ),
        )
        return world
    }

    private fun World.primaryCameraEntityId(): Int {
        var id = -1
        family<com.awakekt.awake.scene.rendering.Camera>().forEach { entity, _ ->
            id = entity.id
        }
        return id
    }

    @Test
    fun drawsNothingWhenNoWorldDebugSettingsExists() {
        val world = worldWithPrimaryCamera()
        val renderer = RecordingRenderer()

        DebugVisualizationSystem(renderer).update(world, 1f / 60f)

        assertEquals(null, renderer.lastDebugLines)
    }

    @Test
    fun drawsNothingWhenEveryToggleIsOff() {
        val world = worldWithPrimaryCamera()
        world.add(world.create(), WorldDebugSettings())
        val renderer = RecordingRenderer()

        DebugVisualizationSystem(renderer).update(world, 1f / 60f)

        assertEquals(null, renderer.lastDebugLines)
    }

    @Test
    fun showFrustumDrawsOneLinePerFrustumEdge() {
        val world = worldWithPrimaryCamera()
        world.add(
            world.create(),
            WorldDebugSettings(
                showFrustum = true,
                frustumTargetEntityId = world.primaryCameraEntityId(),
            ),
        )
        val renderer = RecordingRenderer()

        DebugVisualizationSystem(renderer).update(world, 1f / 60f)

        assertEquals(Frustum.EDGES.size, renderer.lastDebugLines?.size)
    }

    @Test
    fun showBoundsDrawsOneBoxPerMeshBoundsEntity() {
        val world = worldWithPrimaryCamera()
        world.add(world.create(), WorldDebugSettings(showBounds = true))
        val entity = world.create()
        world.add(entity, Transform())
        world.add(entity, MeshBounds(Aabb(Vec3f(-0.5f, -0.5f, -0.5f), Vec3f(0.5f, 0.5f, 0.5f))))
        val renderer = RecordingRenderer()

        DebugVisualizationSystem(renderer).update(world, 1f / 60f)

        assertEquals(Aabb.EDGES.size, renderer.lastDebugLines?.size)
    }

    @Test
    fun showInstanceBoundsDrawsOneBoxForEachSubmittedInstance() {
        val world = worldWithPrimaryCamera()
        world.add(world.create(), WorldDebugSettings(showInstanceBounds = true))
        val mesh = object : Mesh {
            override val format = com.awakekt.awake.core.geometry.VertexFormat.PositionNormalColor
            override val localBounds = Aabb(Vec3f(-0.5f, -0.5f, -0.5f), Vec3f(0.5f, 0.5f, 0.5f))
            override val sizeBytes: Long = 0
            override fun destroy() = Unit
        }
        val material = object : Material {
            override fun updateUniformBuffer(uniformFloats: FloatArray) = Unit
            override fun destroy() = Unit
        }
        val transforms = List(100) { index -> Mat4().apply { m03 = index.toFloat() } }
        world.add(world.create(), InstancedMeshRenderer(mesh, material, transforms))
        val renderer = RecordingRenderer()

        DebugVisualizationSystem(renderer).update(world, 1f / 60f)

        assertEquals(
            transforms.size * Aabb.EDGES.size,
            renderer.lastDebugLines?.size,
            "The diagnostic must use exactly the submitted instance transforms, with no implicit origin box.",
        )
    }

    @Test
    fun bothTogglesCombineTheirLines() {
        val world = worldWithPrimaryCamera()
        world.add(
            world.create(),
            WorldDebugSettings(
                showFrustum = true,
                showBounds = true,
                frustumTargetEntityId = world.primaryCameraEntityId(),
            ),
        )
        val entity = world.create()
        world.add(entity, Transform())
        world.add(entity, MeshBounds(Aabb(Vec3f(-0.5f, -0.5f, -0.5f), Vec3f(0.5f, 0.5f, 0.5f))))
        val renderer = RecordingRenderer()

        DebugVisualizationSystem(renderer).update(world, 1f / 60f)

        assertEquals(Frustum.EDGES.size + Aabb.EDGES.size, renderer.lastDebugLines?.size)
        assertTrue(
            CONSERVATIVE_ASPECT > 1f,
            "sanity: the shared conservative aspect constant is importable here",
        )
    }

    @Test
    fun showGridDrawsGridLines() {
        val world = worldWithPrimaryCamera()
        world.add(
            world.create(),
            WorldDebugSettings(
                showGrid = true,
                gridFadeDistance = 10f,
                gridScale = 1f,
            ),
        )
        val renderer = RecordingRenderer()
        DebugVisualizationSystem(renderer).update(world, 1f / 60f)

        val lines = renderer.lastDebugLines
        assertTrue(lines != null && lines.isNotEmpty(), "Grid lines should be drawn")
        assertEquals(42, lines.size)
    }

    @Test
    fun showAxisLinesDrawsThreeColoredAxes() {
        val world = worldWithPrimaryCamera()
        world.add(
            world.create(),
            WorldDebugSettings(
                showAxisLines = true,
                gridFadeDistance = 50f,
            ),
        )
        val renderer = RecordingRenderer()
        DebugVisualizationSystem(renderer).update(world, 1f / 60f)

        val lines = renderer.lastDebugLines
        assertTrue(lines != null && lines.size == 3, "Should draw X, Z, and Y axis lines")
    }
}
