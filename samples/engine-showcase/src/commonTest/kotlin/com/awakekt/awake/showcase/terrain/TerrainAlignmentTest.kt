/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.showcase.terrain

import com.awakekt.awake.core.math.ClipSpace
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Quat
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.physics.MotionType
import com.awakekt.awake.physics.jolt.createJoltPhysicsWorld
import com.awakekt.awake.scene.document.SceneLoader
import com.awakekt.awake.scene.rendering.camera.SceneCamera
import com.awakekt.awake.scene.runtime.DefaultSceneComponentResolvers
import kotlinx.coroutines.test.runTest
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * That the terrain you can see is the terrain you land on, and that the camera actually frames it.
 *
 * [HeightfieldSceneFramingTest] already checks the camera *aims* near the middle of the terrain,
 * which is the failure that shipped once. Two things it cannot see, both checked here:
 *
 * - **Aiming at the middle is not the same as fitting in the frame.** A field of view and an eye
 *   distance decide how much of the terrain is on screen, and nothing asserted either. The terrain
 *   can sit dead centre and still run off all four edges, or shrink to a speck.
 * - **The mesh and the collider are two separate objects built from the same samples.** They agree
 *   only because both default to a centred origin and the backend offsets the heightfield to match.
 *   Change either convention and boxes land on a surface that is not the one being drawn -- which
 *   reads as physics being broken rather than as an origin mismatch.
 */
class TerrainAlignmentTest {
    init {
        DefaultSceneComponentResolvers.install()
    }

    private val bounds = assertNotNull(TerrainExampleAsset.geometry.bounds, "the terrain has no bounds")

    private val geometry = TerrainExampleAsset.geometry
    private val stride = geometry.format.strideFloats

    private fun vertex(index: Int) = Vec3f(
        geometry.vertices[index * stride],
        geometry.vertices[index * stride + 1],
        geometry.vertices[index * stride + 2],
    )

    /**
     * The height the mesh's own triangles draw at a world point, or null where none covers it.
     *
     * The triangles, not `Heightmap.heightAtWorld`. That helper interpolates a quad bilinearly and
     * the mesh draws it as two triangles, so the two differ everywhere a quad is not flat -- by
     * more, on this terrain, than the thing being measured. Comparing against it reports a
     * disagreement that is really the difference between two ways of asking.
     */
    private fun drawnHeightAt(x: Float, z: Float): Float? {
        val indices = geometry.indices
        var triangle = 0
        while (triangle < indices.size) {
            val a = vertex(indices[triangle])
            val b = vertex(indices[triangle + 1])
            val c = vertex(indices[triangle + 2])
            val area = (b.z - c.z) * (a.x - c.x) + (c.x - b.x) * (a.z - c.z)
            if (abs(area) > 1e-9f) {
                val wa = ((b.z - c.z) * (x - c.x) + (c.x - b.x) * (z - c.z)) / area
                val wb = ((c.z - a.z) * (x - c.x) + (a.x - c.x) * (z - c.z)) / area
                val wc = 1f - wa - wb
                if (wa >= -EDGE_SLACK && wb >= -EDGE_SLACK && wc >= -EDGE_SLACK) {
                    return wa * a.y + wb * b.y + wc * c.y
                }
            }
            triangle += 3
        }
        return null
    }

    @Test
    fun whatYouLandOnIsWhatYouCanSee() = runTest {
        val world = createJoltPhysicsWorld()
        try {
            world.createBody(
                TerrainExampleAsset.collisionShape,
                Vec3f(0f, 0f, 0f),
                Quat.IDENTITY,
                MotionType.STATIC,
            )

            // Probed away from the rim, because a ray at the very edge can miss by rounding and
            // that would be a flaky test rather than a real disagreement.
            var probes = 0
            for (step in 0 until PROBES_PER_AXIS) {
                for (stepZ in 0 until PROBES_PER_AXIS) {
                    val x = lerp(bounds.min.x, bounds.max.x, (step + 1f) / (PROBES_PER_AXIS + 1f))
                    val z = lerp(bounds.min.z, bounds.max.z, (stepZ + 1f) / (PROBES_PER_AXIS + 1f))
                    val hit = world.raycast(Vec3f(x, PROBE_HEIGHT, z), Vec3f(0f, -1f, 0f), PROBE_HEIGHT * 2f)
                    assertNotNull(hit, "a ray straight down at ($x, $z) hit no terrain at all")
                    val drawn = assertNotNull(drawnHeightAt(x, z), "no triangle covers ($x, $z)")
                    assertTrue(
                        abs(hit.point.y - drawn) < SURFACE_SLACK,
                        "at ($x, $z) the collider is at ${hit.point.y} but the mesh draws $drawn",
                    )
                    probes++
                }
            }
            assertTrue(probes > 0, "no probes ran, so this asserts nothing")
        } finally {
            world.destroy()
        }
    }

    @Test
    fun theTerrainFitsTheFrameWithoutRunningOffIt() = runTest {
        val camera = sceneCamera()
        val lens = Lens.perspective(
            eye = Vec3f(camera.eye.x, camera.eye.y, camera.eye.z),
            center = Vec3f(camera.center.x, camera.center.y, camera.center.z),
            up = Vec3f(camera.up.x, camera.up.y, camera.up.z),
            fovYDegrees = camera.fovYDegrees,
            near = camera.near,
            far = camera.far,
        )
        val viewProjection = lens.viewProjectionMatrix(ASPECT, ClipSpace.Vulkan)

        var minNdcX = Float.MAX_VALUE
        var maxNdcX = -Float.MAX_VALUE
        var minNdcY = Float.MAX_VALUE
        var maxNdcY = -Float.MAX_VALUE
        var onScreen = 0
        var total = 0
        for (index in 0 until geometry.vertices.size / stride) {
            val ndc = project(viewProjection, vertex(index)) ?: continue
            total++
            if (abs(ndc.x) <= 1f && abs(ndc.y) <= 1f) onScreen++
            minNdcX = min(minNdcX, ndc.x)
            maxNdcX = max(maxNdcX, ndc.x)
            minNdcY = min(minNdcY, ndc.y)
            maxNdcY = max(maxNdcY, ndc.y)
        }
        assertTrue(total > 0, "no terrain vertex projected in front of the camera at all")

        // The point the camera aims at has to be on screen. Not "every vertex on screen": ground
        // running past the near edge of the frame is what ground does, and demanding otherwise
        // encodes a preference rather than a requirement.
        val lookedAt = assertNotNull(
            project(viewProjection, Vec3f(camera.center.x, camera.center.y, camera.center.z)),
            "the camera's own look-at point is behind it",
        )
        assertTrue(
            abs(lookedAt.x) <= 1f && abs(lookedAt.y) <= 1f,
            "the camera looks at a point off its own screen: $lookedAt",
        )

        // And most of the terrain is actually in frame. Below this it is scenery at the edge of a
        // shot of something else, which for the terrain showcase is the failure worth catching.
        val visible = onScreen.toFloat() / total
        assertTrue(
            visible > MINIMUM_VISIBLE_FRACTION,
            "only ${(visible * 100).toInt()}% of the terrain is on screen " +
                "(x $minNdcX..$maxNdcX, y $minNdcY..$maxNdcY)",
        )
    }

    private suspend fun sceneCamera(): SceneCamera {
        val document = SceneLoader.loadFromResource("assets/examples/heightfield-terrain.scene.json")
        return assertNotNull(
            document.nodes.firstNotNullOfOrNull { node ->
                node.components.filterIsInstance<SceneCamera>().firstOrNull()
            },
            "the scene has no camera",
        )
    }

    /** [point] in normalised device coordinates, or null when it sits behind the camera. */
    private fun project(viewProjection: Mat4, point: Vec3f): Vec3f? {
        val m = viewProjection
        val x = m.m00 * point.x + m.m01 * point.y + m.m02 * point.z + m.m03
        val y = m.m10 * point.x + m.m11 * point.y + m.m12 * point.z + m.m13
        val w = m.m30 * point.x + m.m31 * point.y + m.m32 * point.z + m.m33
        return if (w <= 0f) null else Vec3f(x / w, y / w, 0f)
    }

    private fun lerp(from: Float, to: Float, t: Float) = from + (to - from) * t

    private companion object {
        const val PROBES_PER_AXIS = 7
        const val PROBE_HEIGHT = 20f

        /** A ray reports where it hit a triangle; the mesh interpolates the same triangle. */
        const val SURFACE_SLACK = 0.05f

        /** 16:9, the shape a showcase window actually has. */
        const val ASPECT = 16f / 9f

        /** Normalised device coordinates span -1..1 on both axes. */
        const val NDC_AREA = 4f

        /** Most of it in frame; the near edge running past the bottom is normal for ground. */
        const val MINIMUM_VISIBLE_FRACTION = 0.6f

        /** Barycentric slack, so a probe exactly on a shared edge belongs to one of its triangles. */
        const val EDGE_SLACK = 1e-4f
    }
}
