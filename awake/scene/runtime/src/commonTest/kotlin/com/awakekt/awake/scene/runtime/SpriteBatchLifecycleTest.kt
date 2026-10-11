/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime

import com.awakekt.awake.core.geometry.MeshGeometry
import com.awakekt.awake.ecs.World
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.mesh.Mesh
import com.awakekt.awake.render.passes.sprites.SpriteRenderBatch
import com.awakekt.awake.render.passes.uniforms.SpriteUniformLayout
import com.awakekt.awake.render.testing.NoopRenderer
import com.awakekt.awake.render.texture.PbrTextureSet
import com.awakekt.awake.render.texture.RenderTarget
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.render.texture.TextureFiltering
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.scene2d.SceneSprite
import com.awakekt.awake.scene.scene2d.Sprite
import com.awakekt.awake.scene.scene2d.collectSpriteDraws
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertSame

class SpriteBatchLifecycleTest {
    @Test
    fun liveFramesReuseTheirSheetAndDisposeOnlyOwnedBuffersOnce() {
        val renderer = CountingRenderer()
        var resolves = 0
        val batch = SpriteRenderBatch(renderer) {
            resolves++
            TextureAsset(ByteArray(16), 4, 1)
        }
        val world = World()
        val sprite = Sprite(SceneSprite("sheet", columns = 4))
        world.create().also {
            world.add(it, Transform())
            world.add(it, sprite)
        }
        val first = batch.collect(world.collectSpriteDraws()).single()
        sprite.frame = 3
        sprite.flipX = true
        sprite.sortOrder = 7
        val next = batch.collect(world.collectSpriteDraws()).single()
        assertSame(first.mesh, next.mesh)
        assertSame(first.material, next.material)
        assertNotEquals(first.extraUniformFloats.toList(), next.extraUniformFloats.toList())
        assertEquals(7, next.sortOrder)
        assertEquals(1, resolves)
        assertEquals(1, renderer.meshes)
        assertEquals(1, renderer.materials)
        batch.destroy()
        batch.destroy()
        assertEquals(1, renderer.destroyedMeshes)
        assertEquals(1, renderer.destroyedMaterials)
    }

    private class CountingRenderer : NoopRenderer() {
        var meshes = 0
        var materials = 0
        var destroyedMeshes = 0
        var destroyedMaterials = 0

        override fun createMesh(geometry: MeshGeometry): Mesh {
            meshes++
            return object : Mesh by super.createMesh(geometry) {
                override fun destroy() { destroyedMeshes++ }
            }
        }

        override fun createMaterial(texture: TextureAsset?, renderTarget: RenderTarget?, uniformFloatCount: Int, pbrTextures: PbrTextureSet?): Material {
            materials++
            assertEquals(TextureFiltering.Nearest, texture?.filtering)
            assertEquals(SpriteUniformLayout.total, uniformFloatCount)
            return object : Material by super.createMaterial(texture, renderTarget, uniformFloatCount, pbrTextures) {
                override fun destroy() { destroyedMaterials++ }
            }
        }
    }
}
