/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering

import com.awakekt.awake.asset.shaderpack.TERRAIN_SURFACE_FIRST_BINDING
import com.awakekt.awake.asset.shaders.AttachedContentFeature
import com.awakekt.awake.asset.shaders.ContentFeatureHost
import com.awakekt.awake.asset.shaders.ContentFeatureSource
import com.awakekt.awake.asset.shaders.RenderBackend
import com.awakekt.awake.asset.terrain.Heightmap
import com.awakekt.awake.asset.terrain.clipmap.TerrainClipmapConfig
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.render.passes.ContentFeature
import com.awakekt.awake.scene.rendering.terrain.TerrainComponent
import com.awakekt.awake.scene.rendering.terrain.TerrainContentSystem
import com.awakekt.awake.scene.rendering.terrain.TerrainSurface
import com.awakekt.awake.scene.rendering.terrain.TerrainSurfaceProvider
import com.awakekt.awake.scene.rendering.terrain.TerrainSurfaceReference
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class TerrainContentSystemTest {

    private val world = World()
    private val host = RecordingHost()
    private val scope = TestScope()
    private val system = TerrainContentSystem(
        host,
        scope,
        providers = mapOf(PROVIDER to TerrainSurfaceProvider { delay(RESOLVE_MILLIS); SURFACE }),
    )

    @Test
    fun aTerrainWithoutASurfaceAttachesOnceWithBaseShading() {
        spawnTerrain()

        repeat(3) { system.update(world, 0f) }

        assertEquals(1, host.attached.size)
        assertEquals(setOf(HEIGHTMAP_BINDING), host.attached.single().textures.keys)
        assertEquals(0, host.detached)
    }

    /** The provider resolves in the scope; nothing attaches until an update sees its result. */
    @Test
    fun aReferencedSurfaceAttachesWithTheProvidersShaderAndTextures() {
        spawnTerrain(TerrainSurfaceReference(PROVIDER, 1, JsonObject(emptyMap())))

        system.update(world, 0f)
        assertEquals(0, host.attached.size)
        scope.advanceUntilIdle()
        system.update(world, 0f)

        assertEquals(setOf(HEIGHTMAP_BINDING, TERRAIN_SURFACE_FIRST_BINDING), host.attached.single().textures.keys)
    }

    @Test
    fun anUnknownProviderDrawsBaseShadingAndKeepsTheReference() {
        val reference = TerrainSurfaceReference("not.installed", 3, JsonObject(emptyMap()))
        val entity = spawnTerrain(reference)

        system.update(world, 0f)

        assertEquals(setOf(HEIGHTMAP_BINDING), host.attached.single().textures.keys)
        assertEquals(reference, world.get<TerrainSurfaceReference>(entity))
    }

    @Test
    fun surfacedOnlyLeavesATerrainWithoutASurfaceToAnotherRenderer() {
        val surfacedOnly = TerrainContentSystem(host, scope, surfacedOnly = true)
        spawnTerrain()
        spawnTerrain(TerrainSurfaceReference("not.installed", 1, JsonObject(emptyMap())))

        repeat(2) { surfacedOnly.update(world, 0f) }

        assertEquals(1, host.attached.size)
    }

    @Test
    fun destroyingTheEntityDetachesItsTerrain() {
        val entity = spawnTerrain()
        repeat(2) { system.update(world, 0f) }

        world.destroy(entity)
        system.update(world, 0f)

        assertEquals(1, host.detached)
    }

    /** A new component instance is a different terrain: the old one is freed and the new one attached. */
    @Test
    fun replacingTheComponentReattaches() {
        val entity = spawnTerrain()
        repeat(2) { system.update(world, 0f) }

        world.add(entity, terrain())
        repeat(2) { system.update(world, 0f) }

        assertEquals(1, host.detached)
        assertEquals(2, host.attached.size)
    }

    /** An attach that finished for an entity destroyed before the next update is freed, not kept. */
    @Test
    fun aResultForADestroyedEntityIsDetached() {
        val entity = spawnTerrain()
        system.update(world, 0f)

        world.destroy(entity)
        system.update(world, 0f)
        system.detachAll()

        assertEquals(1, host.attached.size)
        assertEquals(1, host.detached)
    }

    @Test
    fun aRejectedAttachIsNotRetriedEveryFrame() {
        host.reject = true
        spawnTerrain()

        repeat(3) { system.update(world, 0f) }

        assertEquals(1, host.attempts)
    }

    @Test
    fun detachAllFreesEveryAttachedTerrain() {
        spawnTerrain()
        spawnTerrain()
        repeat(2) { system.update(world, 0f) }

        system.detachAll()

        assertEquals(2, host.attached.size)
        assertEquals(2, host.detached)
    }

    private fun spawnTerrain(reference: TerrainSurfaceReference? = null): Entity {
        val entity = world.create()
        world.add(entity, terrain())
        reference?.let { world.add(entity, it) }
        return entity
    }

    private fun terrain() = TerrainComponent(
        heightmap = Heightmap(FloatArray(4 * 4), width = 4, depth = 4, scale = Vec3f(1f, 1f, 1f)),
        clipmapConfig = TerrainClipmapConfig(ringCount = 2, ringResolution = 16, baseSpacing = 1f),
    )

    /** Accepts every attach unless told to reject, and counts what it was asked to do. */
    private class RecordingHost : ContentFeatureHost {
        val attached = mutableListOf<ContentFeature>()
        var attempts = 0
        var detached = 0
        var reject = false

        override suspend fun attachContentFeature(source: ContentFeatureSource): AttachedContentFeature {
            attempts++
            require(!reject) { "rejected by the test host" }
            attached += source.resolve(RenderBackend.Vulkan)
            return AttachedContentFeature { detached++ }
        }
    }

    private companion object {
        const val PROVIDER = "test.layers"
        const val RESOLVE_MILLIS = 10L
        const val HEIGHTMAP_BINDING = 1

        val SURFACE = TerrainSurface(
            PROBE_SURFACE_SHADERS,
            mapOf(TERRAIN_SURFACE_FIRST_BINDING to PROBE_LAYERS),
        )
    }
}
