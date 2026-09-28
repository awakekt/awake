/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.terrain

import com.awakekt.awake.asset.terrain.Heightmap
import com.awakekt.awake.core.geometry.MeshGeometry
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.World
import com.awakekt.awake.render.mesh.Mesh
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.render.testing.NoopRenderer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TerrainShadowCastersTest {

    private class CountingRenderer : NoopRenderer() {
        var created = 0
        var destroyed = 0

        override fun createMesh(geometry: MeshGeometry): Mesh {
            created++
            return object : Mesh {
                override val format = geometry.format
                override val sizeBytes = 0L
                override fun destroy() {
                    destroyed++
                }
            }
        }
    }

    @Test
    fun aTerrainCastsAShadowOnlyMeshThatIsBuiltOnceAndRebuiltOnEdit() {
        val renderer = CountingRenderer()
        val casters = TerrainShadowCasters(renderer)
        val world = World()
        val terrain = TerrainComponent(Heightmap(FloatArray(9), 3, 3, Vec3f(1f, 1f, 1f)))
        val entity = world.create().also { world.add(it, terrain) }

        val first = casters.draws(world).single()
        assertTrue(first.shadowsOnly)
        assertEquals(Mat4().data.toList(), first.model.data.toList(), "placed by the heightmap itself, as the clipmap is")
        assertSame(first.mesh, casters.draws(world).single().mesh, "an unchanged terrain keeps its mesh")

        terrain.revision++
        assertNotSame(first.mesh, casters.draws(world).single().mesh, "an edit rebuilds it")
        assertEquals(2 to 1, renderer.created to renderer.destroyed)

        terrain.isVisible = false
        assertTrue(casters.draws(world).isEmpty(), "a hidden terrain casts nothing")

        world.remove<TerrainComponent>(entity)
        casters.draws(world)
        assertEquals(2, renderer.destroyed, "a removed terrain's mesh is freed")
    }

    @Test
    fun aLargeHeightmapCastsFromEveryNthSample() {
        val large = Heightmap(FloatArray(1025 * 1025), 1025, 1025, Vec3f(1f, 2f, 1f))

        val caster = large.casterResolution()

        assertEquals(MAX_CASTER_SAMPLES to MAX_CASTER_SAMPLES, caster.width to caster.depth)
        assertEquals(Vec3f(2f, 2f, 2f), caster.scale)
        assertEquals(large.minX to large.minZ, caster.minX to caster.minZ, "the caster covers the same ground")
        val small = Heightmap(FloatArray(4), 2, 2, Vec3f(1f, 1f, 1f))
        assertSame(small, small.casterResolution(), "a heightmap within the cap is used as is")
    }

    private fun TerrainShadowCasters.draws(world: World): List<RenderDrawCommand> = ArrayList<RenderDrawCommand>().also { collect(world, it) }
}
