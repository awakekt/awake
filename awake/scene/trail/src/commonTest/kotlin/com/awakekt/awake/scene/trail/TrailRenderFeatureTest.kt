/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.trail

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.World
import com.awakekt.awake.render.mesh.DynamicMesh
import com.awakekt.awake.render.mesh.GpuDynamicMeshes
import com.awakekt.awake.render.pipeline.GpuCapability
import com.awakekt.awake.render.pipeline.GpuCapabilityKind
import com.awakekt.awake.render.testing.NoopRenderer
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.render.texture.asGpuImageSource
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.rendering.Camera
import com.awakekt.awake.scene.rendering.RenderFeatureContext3D
import com.awakekt.awake.trail.TrailRenderer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** [TrailRenderFeature]: a scene's trails drawn through their transforms, and released with their entities. */
class TrailRenderFeatureTest {
    private class DynamicRenderer(private val supported: Boolean) : NoopRenderer() {
        var created = 0
        var destroyed = 0
        private val dynamic = object : GpuDynamicMeshes {
            override fun createDynamicMesh(format: VertexFormat, vertexCapacity: Int, indices: IntArray): DynamicMesh {
                created++
                return object : DynamicMesh {
                    override val format = format
                    override val sizeBytes: Long = 0
                    override val vertexCapacity = vertexCapacity
                    override val indexCapacity = indices.size
                    override var drawIndexCount = 0
                    override fun writeVertices(vertices: FloatArray, firstVertex: Int, vertexCount: Int) = Unit
                    override fun destroy() {
                        destroyed++
                    }
                }
            }
        }

        @Suppress("UNCHECKED_CAST")
        override fun <T : GpuCapability> capability(kind: GpuCapabilityKind<T>): T? =
            if (supported && kind == GpuDynamicMeshes) dynamic as T else null
    }

    private val context = RenderFeatureContext3D(
        Camera(Lens(eye = Vec3f(0f, 0f, 5f), center = Vec3f(0f, 0f, 0f), fovYRadians = 1f, near = 0.1f, far = 20f)),
        0f,
        1f,
    )

    private fun World.withTrail(textureName: String? = null): Pair<World, TrailRenderer> {
        val trail = TrailRenderer(4).apply {
            for (i in 0 until 4) set(i, Vec3f(i * 0.2f, 0f, 0f), Vec3f(i * 0.2f, 0.1f, 0f), Color.White, Color.White)
            visible = 4
        }
        val entity = create()
        add(entity, Transform())
        add(entity, trail)
        textureName?.let { add(entity, TrailTextureReference(it)) }
        return this to trail
    }

    @Test
    fun aTrailIsDrawnAndReleasedWhenItsEntityGoes() {
        val renderer = DynamicRenderer(supported = true)
        val feature = TrailRenderFeature(renderer)
        val (world, trail) = World().withTrail()

        val draws = feature.collect(world, context).draws
        assertEquals(1, draws.size)
        assertTrue(draws.single().transparent)
        assertSame(trail, world.queryFirstTrail())

        world.destroyAll()
        assertEquals(0, feature.collect(world, context).draws.size)
        assertEquals(1, renderer.created)
        assertEquals(1, renderer.destroyed)
        feature.close()
    }

    @Test
    fun aRendererThatCannotDrawTrailsCreatesNothing() {
        val renderer = DynamicRenderer(supported = false)
        val feature = TrailRenderFeature(renderer)
        val (world, _) = World().withTrail()

        assertEquals(0, feature.collect(world, context).draws.size)
        assertEquals(0, renderer.created)
        feature.close()
    }

    @Test
    fun anAuthoredTextureWaitsForItsImageAndResolvesAgainAfterInvalidation() {
        val renderer = DynamicRenderer(supported = true)
        val name = "textures/ribbon.png"
        val (world, _) = World().withTrail(name)
        val image = TextureAsset(byteArrayOf(-1, 0, 0, -1), 1, 1).asGpuImageSource()
        var ready = false
        var requests = 0
        TrailRenderFeature(renderer) { requested ->
            assertEquals(name, requested)
            requests++
            image.takeIf { ready }
        }.use { feature ->
            assertEquals(0, feature.collect(world, context).draws.size)
            assertEquals(0, renderer.created)
            ready = true
            assertEquals(1, feature.collect(world, context).draws.size)
            assertEquals(1, feature.collect(world, context).draws.size)
            assertEquals(2, requests, "the uploaded material is reused")
            feature.forgetTexture(name)
            assertEquals(1, feature.collect(world, context).draws.size)
            assertEquals(3, requests)
            assertEquals(1, renderer.created, "reloading the texture reuses the mesh")
        }
    }

    private fun World.queryFirstTrail(): TrailRenderer? {
        var found: TrailRenderer? = null
        queryEach<Transform, TrailRenderer> { _, _, trail -> if (found == null) found = trail }
        return found
    }

    private fun World.destroyAll() {
        val entities = ArrayList<com.awakekt.awake.ecs.Entity>()
        queryEach<Transform, TrailRenderer> { entity, _, _ -> entities += entity }
        entities.forEach { destroy(it) }
    }
}
