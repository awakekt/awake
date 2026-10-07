/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.geometry.MeshGeometry
import com.awakekt.awake.core.math.Frustum
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.core.math.planes
import com.awakekt.awake.ecs.World
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.mesh.Mesh
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.render.passes.sprites.TilemapRenderBatch
import com.awakekt.awake.render.testing.NoopRenderer
import com.awakekt.awake.render.texture.PbrTextureSet
import com.awakekt.awake.render.texture.RenderTarget
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.rendering.Camera
import com.awakekt.awake.scene.rendering.RenderFeatureContext3D
import com.awakekt.awake.scene.scene2d.SceneTilemap
import com.awakekt.awake.scene.scene2d.Tilemap
import com.awakekt.awake.tilemap.TilemapGrid
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class TilemapBatchLifecycleTest {
    @Test
    fun hiddenChunksAreUploadedOnlyWhenTheyEnterTheView() {
        val renderer = CountingRenderer()
        val grid = TilemapGrid(4, 2, IntArray(8), chunkSize = 2)
        val batch = TilemapRenderBatch(renderer, grid, 1, 1, 1f) { TextureAsset(ByteArray(4), 1, 1) }
        val lens = Lens(eye = Vec3f(1f, -1f, 5f), center = Vec3f(1f, -1f, 0f), fovYRadians = 1f, near = 0.1f, far = 20f).apply {
            projection = Lens.Projection.Orthographic
            orthoHalfHeight = 0.9f
        }
        val draws = ArrayList<RenderDrawCommand>()
        batch.collect(Mat4(), Color.White, 0, Frustum.planes(lens, 1f), draws)
        assertEquals(1, draws.size)
        assertEquals(1, renderer.meshes)
        lens.eye.x = 3f
        lens.center.x = 3f
        draws.clear()
        batch.collect(Mat4(), Color.White, 0, Frustum.planes(lens, 1f), draws)
        assertEquals(1, draws.size)
        assertEquals(2, renderer.meshes)
        batch.destroy()
        assertEquals(2, renderer.destroyedMeshes)
    }

    @Test
    fun unchangedChunksReuseRequestsAndAnEditRebuildsOnlyOne() {
        val renderer = CountingRenderer()
        val grid = TilemapGrid(4, 2, IntArray(8), chunkSize = 2)
        val batch = TilemapRenderBatch(renderer, grid, 1, 1, 1f) { TextureAsset(ByteArray(4), 1, 1) }
        val out = ArrayList<RenderDrawCommand>()
        val model = Mat4()
        batch.collect(model, Color.White, 0, emptyList(), out)
        assertEquals(2, out.size)
        val first = out.toList()
        out.clear()
        batch.collect(model, Color.White, 0, emptyList(), out)
        assertSame(first[0], out[0])
        assertSame(first[1], out[1])
        assertEquals(2, renderer.meshes)
        grid[0, 0] = -1
        out.clear()
        batch.collect(model, Color.White, 9, emptyList(), out)
        assertEquals(3, renderer.meshes)
        assertEquals(1, renderer.destroyedMeshes)
        assertSame(first[1], out[1])
        assertEquals(9, out[0].sortOrder)
        batch.destroy()
        batch.destroy()
        assertEquals(3, renderer.destroyedMeshes)
        assertEquals(1, renderer.destroyedMaterials)
    }

    @Test
    fun removingTheComponentReleasesItsGpuResources() {
        val renderer = CountingRenderer()
        val feature = SceneTilemapRenderFeature(renderer) { TextureAsset(ByteArray(4), 1, 1) }
        val world = World()
        val entity = world.create()
        world.add(entity, Transform())
        world.add(entity, Tilemap(SceneTilemap("tiles", 2, 2, List(4) { 0 })))
        val lens = Lens(eye = Vec3f(1f, -1f, 5f), center = Vec3f(1f, -1f, 0f), fovYRadians = 1f, near = 0.1f, far = 20f)
        val context = RenderFeatureContext3D(Camera(lens), 0f, 1f)
        feature.collect(world, context)
        world.remove<Tilemap>(entity)
        assertEquals(0, feature.collect(world, context).draws.size)
        assertEquals(1, renderer.destroyedMaterials)
        feature.destroy()
        assertEquals(renderer.meshes, renderer.destroyedMeshes)
    }

    private class CountingRenderer : NoopRenderer() {
        var meshes = 0
        var destroyedMeshes = 0
        var destroyedMaterials = 0
        override fun createMesh(geometry: MeshGeometry): Mesh {
            meshes++
            return object : Mesh by super.createMesh(geometry) {
                override fun destroy() {
                    destroyedMeshes++
                }
            }
        }
        override fun createMaterial(texture: TextureAsset?, renderTarget: RenderTarget?, uniformFloatCount: Int, pbrTextures: PbrTextureSet?): Material =
            object : Material by super.createMaterial(texture, renderTarget, uniformFloatCount, pbrTextures) {
                override fun destroy() {
                    destroyedMaterials++
                }
            }
    }
}
