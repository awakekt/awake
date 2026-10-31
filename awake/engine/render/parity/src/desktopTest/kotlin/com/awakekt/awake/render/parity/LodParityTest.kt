/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.parity

import com.awakekt.awake.asset.meshlod.AttributeMeshSimplifier
import com.awakekt.awake.asset.meshlod.MeshAttribute
import com.awakekt.awake.asset.meshlod.SimplifyOptions
import com.awakekt.awake.asset.shaderpack.LitShadowUniformLayout
import com.awakekt.awake.core.geometry.MeshGeometry
import com.awakekt.awake.core.geometry.generate.generate
import com.awakekt.awake.core.lod.LodQuality
import com.awakekt.awake.core.math.Aabb
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.World
import com.awakekt.awake.render.command.GpuPassInput
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.render.renderer.createMaterial
import com.awakekt.awake.render.testing.HeadlessRenderSession
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.rendering.RenderSystem3D
import com.awakekt.awake.scene.rendering.camera.Camera
import com.awakekt.awake.scene.rendering.light.Light
import com.awakekt.awake.scene.rendering.mesh.LodGroup
import com.awakekt.awake.scene.rendering.mesh.LodLevel
import com.awakekt.awake.scene.rendering.mesh.MeshBounds
import com.awakekt.awake.scene.rendering.mesh.MeshRenderer
import com.awakekt.awake.scene.rendering.mesh.SceneLod
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import kotlin.math.abs
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Copies of a lit, shadowed sphere at increasing distances, through the scene path a played project
 * draws with, on both backends. With levels of detail, each draws the level its size on screen calls
 * for, every level sharing the full mesh's vertex buffer; the backend is handed far fewer triangles,
 * and the picture stays the full-detail one but for a few edge pixels.
 */
class LodParityTest {
    @AfterTest
    fun resetQuality() {
        SceneLod.quality = LodQuality.Default
    }

    @Test
    fun distantCopiesDrawCoarserLevelsAndFewerTrianglesAndLookTheSame() {
        BACKEND_ORDER.forEach { backend ->
            val renderer = session(backend).renderer
            val (lod, lodTriangles) = renderer.renderSpheres()
            SceneLod.quality = LodQuality(bias = -LEVELS)
            val (full, fullTriangles) = renderer.renderSpheres()
            SceneLod.quality = LodQuality.Default
            val differing = lod.differingChannels(full)
            println("LOD on $backend: $lodTriangles of $fullTriangles triangles, $differing channels differ")

            assertTrue(lodTriangles * 2 < fullTriangles, "$backend: levels must cut the frame's triangles: $lodTriangles of $fullTriangles")
            assertTrue(differing < full.size * MAX_DIFFERING_SHARE, "$backend: levels must look like the full model: $differing channels differ")
            assertTrue(full.differingChannels(renderer.renderSpheres(copies = 0).first) > MIN_SPHERE_CHANNELS, "$backend: no sphere drew")
        }
    }

    /** The scene's pixels, and the triangles its resolved draws hand the backend, instances included. */
    private fun Renderer.renderSpheres(copies: Int = DISTANCES.size): Pair<ByteArray, Long> {
        val target = createRenderTarget(SCENE_SIZE, SCENE_SIZE)
        val sphere = generate { sphere(radius = 1f, rings = RINGS, sectors = RINGS) }
        val meshes = listOf(createMesh(sphere)).let { full -> full + LEVEL_RATIOS.map { createMeshSharingVertices(full[0], sphere.simplified(it)) } }
        val material = createMaterial(LitShadowUniformLayout)
        return try {
            val world = World()
            val camera = Camera(Lens(eye = Vec3f(0f, 1f, 2f), center = Vec3f(0f, 0f, -20f), fovYRadians = 1f, near = 0.1f, far = 200f))
            world.add(world.create(), camera)
            world.add(world.create(), Light(type = Light.Type.Directional, direction = Vec3f(0.4f, 1f, 0.3f), shadowsEnabled = true))
            DISTANCES.take(copies).forEachIndexed { index, distance ->
                val entity = world.create()
                world.add(entity, Transform(worldMatrix = Mat4().translate((index % 2) * 3f - 1.5f, 0f, -distance)))
                world.add(entity, MeshRenderer(meshes[0], material))
                world.add(entity, MeshBounds(Aabb(Vec3f(-1f, -1f, -1f), Vec3f(1f, 1f, 1f))))
                world.add(entity, LodGroup(meshes.map { LodLevel(it, material, maxDistance = Float.MAX_VALUE) }).also { it.minScreenCoverage = COVERAGE })
            }
            val input = RenderSystem3D(this).planCapture(world, camera, SCENE_SIZE, SCENE_SIZE)
            renderToTexture(target, input)
            runBlocking { readPixels(target) }.data to input.triangles()
        } finally {
            meshes.forEach { it.destroy() }
            material.destroy()
            target.destroy()
        }
    }

    private fun GpuPassInput.triangles(): Long = resolvedOpaqueDraws.sumOf { it.elementCount.toLong() / 3 * it.instances }

    /** This mesh with [ratio] of its triangles, over its own vertices, as the cooker simplifies a level. */
    private fun MeshGeometry.simplified(ratio: Float): MeshGeometry {
        val stride = format.strideFloats
        val count = vertices.size / stride
        val positions = FloatArray(count * 3) { vertices[it / 3 * stride + it % 3] }
        val normals = FloatArray(count * 3) { vertices[it / 3 * stride + 3 + it % 3] }
        val result = AttributeMeshSimplifier.simplify(positions, indices, SimplifyOptions(ratio), listOf(MeshAttribute(normals, 3, 0.5f)))
        return MeshGeometry(vertices, result.indices, format)
    }

    private fun ByteArray.differingChannels(other: ByteArray): Int =
        indices.count { abs((this[it].toInt() and 0xFF) - (other[it].toInt() and 0xFF)) > CHANNEL_TOLERANCE }

    private companion object {
        /** Vulkan first, for the loader reason [UiBackendParityTest] documents. */
        val BACKEND_ORDER = listOf(HeadlessUiBackend.Vulkan, HeadlessUiBackend.WebGpu)
        private val sessions = mutableMapOf<HeadlessUiBackend, HeadlessRenderSession>()

        fun session(backend: HeadlessUiBackend): HeadlessRenderSession = sessions.getOrPut(backend) { openHeadlessScene(backend) }

        @AfterClass
        @JvmStatic
        fun tearDown() {
            sessions.remove(HeadlessUiBackend.Vulkan)?.close()
        }

        val DISTANCES = listOf(4f, 8f, 16f, 30f, 45f, 60f, 80f, 100f)
        val LEVEL_RATIOS = listOf(0.5f, 0.25f, 0.1f)
        val COVERAGE = floatArrayOf(0.25f, 0.12f, 0.05f, 0f)
        const val LEVELS = 4
        const val RINGS = 48
        const val CHANNEL_TOLERANCE = 8

        /** Edge pixels of the far, coarse spheres; a missing or misplaced sphere differs in far more. */
        const val MAX_DIFFERING_SHARE = 0.02

        /** The spheres over the empty scene: far more channels than any rounding. */
        const val MIN_SPHERE_CHANNELS = 300
    }
}
