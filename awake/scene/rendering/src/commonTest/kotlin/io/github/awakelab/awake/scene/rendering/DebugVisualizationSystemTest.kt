/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.scene.rendering

import io.github.awakelab.awake.core.color.Color
import io.github.awakelab.awake.core.geometry.MeshGeometry
import io.github.awakelab.awake.core.graphics2d.UiDrawPrimitive
import io.github.awakelab.awake.core.math.Aabb
import io.github.awakelab.awake.core.math.ClipSpace
import io.github.awakelab.awake.core.math.Frustum
import io.github.awakelab.awake.core.math.Lens
import io.github.awakelab.awake.core.math.Vec3f
import io.github.awakelab.awake.core.text.font.UiFont
import io.github.awakelab.awake.ecs.World
import io.github.awakelab.awake.render.material.Material
import io.github.awakelab.awake.render.mesh.Mesh
import io.github.awakelab.awake.render.renderer.DrawCall
import io.github.awakelab.awake.render.renderer.LineSegment
import io.github.awakelab.awake.render.renderer.Renderer
import io.github.awakelab.awake.render.renderer.SceneLight
import io.github.awakelab.awake.render.texture.PbrTextureSet
import io.github.awakelab.awake.render.texture.RenderTarget
import io.github.awakelab.awake.render.texture.TextureAsset
import io.github.awakelab.awake.scene.core.transform.Transform
import io.github.awakelab.awake.scene.rendering.CONSERVATIVE_ASPECT
import io.github.awakelab.awake.scene.rendering.debug.DebugVisualizationSystem
import io.github.awakelab.awake.scene.rendering.debug.WorldDebugSettings
import io.github.awakelab.awake.scene.rendering.mesh.MeshBounds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DebugVisualizationSystemTest {
    private class RecordingRenderer : Renderer {
        var lastDebugLines: List<LineSegment>? = null
        override val clipSpace: ClipSpace = ClipSpace.WebGpu
        override var clearColor: Color = Color.Black
        override var wireframe: Boolean = false
        override var shadowsEnabled: Boolean = true
        override fun createMesh(geometry: MeshGeometry): Mesh = error("not needed for this test")
        override fun createMaterial(
            texture: TextureAsset?,
            renderTarget: RenderTarget?,
            uniformFloatCount: Int,
            pbrTextures: PbrTextureSet?,
        ): Material = error("not needed for this test")

        override fun createRenderTarget(width: Int, height: Int): RenderTarget =
            error("not needed for this test")

        override fun draw(camera: Lens, drawCalls: List<DrawCall>, light: SceneLight) = Unit
        override fun renderToTexture(
            target: RenderTarget,
            camera: Lens,
            drawCalls: List<DrawCall>,
            light: SceneLight,
        ) = Unit

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
            io.github.awakelab.awake.scene.rendering.Camera(
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
        family<io.github.awakelab.awake.scene.rendering.Camera>().forEach { entity, _ ->
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
