/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.diagnostics.DebugLines
import com.awakekt.awake.core.diagnostics.DebugRows
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.Aabb
import com.awakekt.awake.core.math.ClipSpace
import com.awakekt.awake.core.math.Frustum
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.World
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.mesh.Mesh
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.rendering.debug.CullResult
import com.awakekt.awake.scene.rendering.debug.CullingDebugSource
import com.awakekt.awake.scene.rendering.debug.CullingProbe
import com.awakekt.awake.scene.rendering.mesh.MeshBounds
import com.awakekt.awake.scene.rendering.mesh.MeshRenderer
import com.awakekt.awake.scene.rendering.mesh.SceneDrawCollector
import com.awakekt.awake.scene.rendering.spatial.Occluder
import com.awakekt.awake.scene.rendering.spatial.SceneCullingCompiler
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CullingDebugSourceTest {
    @Test
    fun eachMeshIsRecordedWithWhatCullingDecided() {
        val world = World()
        meshAt(world, 0f, 0f, 0f)
        meshAt(world, 0f, 0f, 20f)
        val probe = CullingProbe()

        val draws = plan(world, camera(), probe)

        assertEquals(1, draws)
        assertEquals(1, probe.drawn)
        assertEquals(1, probe.outsideView)
        assertEquals(0, probe.occluded)
        assertEquals(2, probe.boxCount)
        assertEquals(listOf(CullResult.Drawn, CullResult.OutsideView), resultsOf(probe))
        assertEquals(Vec3f(-0.5f, -0.5f, 19.5f), probe.writeMin(1, Vec3f(0f, 0f, 0f)), "the box is in world space")
        assertEquals(Vec3f(0.5f, 0.5f, 20.5f), probe.writeMax(1, Vec3f(0f, 0f, 0f)))
    }

    @Test
    fun aMeshBehindAnOccluderIsRecordedAsOccluded() {
        val world = World()
        val occluder = world.create()
        world.add(occluder, Transform())
        world.add(occluder, Occluder(Aabb(Vec3f(-10f, -10f, 1.9f), Vec3f(10f, 10f, 2.1f))))
        meshAt(world, 0f, 0f, 0f)
        val probe = CullingProbe()

        plan(world, camera(), probe)

        assertEquals(1, probe.occluded)
        assertEquals(listOf(CullResult.Occluded), resultsOf(probe))
    }

    @Test
    fun aFrozenProbeKeepsCullingWithTheCameraItHadWhenItFroze() {
        val world = World()
        meshAt(world, 0f, 0f, 0f)
        val probe = CullingProbe()
        val camera = camera()
        probe.frozen = true
        plan(world, camera, probe)

        // Turn the live camera away: the mesh is now behind it.
        camera.lens.center = Vec3f(0f, 0f, 10f)
        val draws = plan(world, camera, probe)

        assertEquals(1, draws, "the frozen view still has the mesh in front of it")
        assertEquals(listOf(CullResult.Drawn), resultsOf(probe))
        assertTrue(probe.hasFrozenView)

        probe.frozen = false
        assertEquals(0, plan(world, camera, probe), "thawed, culling follows the live camera again")
        assertEquals(listOf(CullResult.OutsideView), resultsOf(probe))
        assertFalse(probe.hasFrozenView)
    }

    @Test
    fun boxesPastTheLimitAreCountedButNotKept() {
        val world = World()
        meshAt(world, 0f, 0f, 0f)
        meshAt(world, 1f, 0f, 0f)
        meshAt(world, 0f, 0f, 20f)
        val probe = CullingProbe(maxBoxes = 2)

        plan(world, camera(), probe)

        assertEquals(2, probe.boxCount)
        assertEquals(1, probe.boxesNotKept)
        assertEquals(2, probe.drawn, "the counts cover every mesh")
        assertEquals(1, probe.outsideView)
    }

    @Test
    fun theSourceDrawsEachBoxInItsResultsColourAndTheFrozenView() {
        val world = World()
        meshAt(world, 0f, 0f, 0f)
        meshAt(world, 0f, 0f, 20f)
        val probe = CullingProbe()
        val source = CullingDebugSource(probe)
        plan(world, camera(), probe)
        val lines = RecordedLines()

        source.lines(lines)

        assertEquals(24, lines.colors.size, "12 edges a box")
        assertEquals(List(12) { CullingDebugSource.DRAWN } + List(12) { CullingDebugSource.OUTSIDE_VIEW }, lines.colors)

        probe.frozen = true
        plan(world, camera(), probe)
        lines.colors.clear()
        source.lines(lines)

        assertEquals(24 + Frustum.EDGES.size, lines.colors.size)
        assertEquals(CullingDebugSource.FROZEN_VIEW, lines.colors.last())
    }

    @Test
    fun theSourcesRowsAreTheCounts() {
        val world = World()
        meshAt(world, 0f, 0f, 0f)
        meshAt(world, 0f, 0f, 20f)
        val probe = CullingProbe()
        plan(world, camera(), probe)
        val rows = RecordedRows()

        CullingDebugSource(probe).rows(rows)

        assertEquals(
            mapOf("drawn" to "1", "outside view" to "1", "occluded" to "0", "shadow only" to "0", "frozen (F9)" to "no"),
            rows.values,
        )
    }

    private fun plan(world: World, camera: Camera, probe: CullingProbe): Int {
        val compiler = compilers.getOrPut(world) { SceneCullingCompiler(ClipSpace.WebGpu) }
        compiler.probe = probe
        return SceneDrawCollector(compiler)
            .collectBeforeParticles(world, compiler.prepare(world, camera), elapsedTimeSeconds = 0f)
            .size
    }

    private val compilers = HashMap<World, SceneCullingCompiler>()

    private fun resultsOf(probe: CullingProbe): List<CullResult> = List(probe.boxCount) { probe.result(it) }

    private fun camera() =
        Camera(Lens(eye = Vec3f(0f, 0f, 5f), center = Vec3f(0f, 0f, 0f), fovYRadians = 1f, near = 0.1f, far = 100f))

    private fun meshAt(world: World, x: Float, y: Float, z: Float) {
        val entity = world.create()
        world.add(entity, Transform(worldMatrix = Mat4().translate(x, y, z)))
        world.add(entity, MeshRenderer(fakeMesh(), fakeMaterial()))
        world.add(entity, MeshBounds(Aabb(Vec3f(-0.5f, -0.5f, -0.5f), Vec3f(0.5f, 0.5f, 0.5f))))
    }

    private class RecordedLines : DebugLines {
        val colors = ArrayList<Color>()

        override fun line(a: Vec3f, b: Vec3f, color: Color) {
            colors += color
        }
    }

    private class RecordedRows : DebugRows {
        val values = LinkedHashMap<String, String>()

        override fun row(label: String, value: Int, warn: Boolean) {
            values[label] = value.toString()
        }

        override fun row(label: String, value: Float, decimals: Int, warn: Boolean) {
            values[label] = value.toString()
        }

        override fun row(label: String, value: String, warn: Boolean) {
            values[label] = value
        }
    }

    private fun fakeMesh(): Mesh = object : Mesh {
        override val format = VertexFormat.PositionNormalColorUv
        override val sizeBytes = 0L
        override fun destroy() = Unit
    }

    private fun fakeMaterial(): Material = object : Material {
        override fun updateUniformBuffer(uniformFloats: FloatArray) = Unit
        override fun destroy() = Unit
    }
}
