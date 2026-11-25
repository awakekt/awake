/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering

import com.awakekt.awake.asset.shaderpack.TERRAIN_SURFACE_FIRST_BINDING
import com.awakekt.awake.asset.shaders.AttachedContentFeature
import com.awakekt.awake.asset.shaders.ContentFeatureAttacher
import com.awakekt.awake.asset.shaders.ContentFeatureGpu
import com.awakekt.awake.asset.shaders.ContentFeatureHost
import com.awakekt.awake.asset.shaders.ContentFeatureSource
import com.awakekt.awake.asset.shaders.ContentGroupUpload
import com.awakekt.awake.asset.shaders.ContentUpload
import com.awakekt.awake.asset.shaders.RenderBackend
import com.awakekt.awake.asset.terrain.Heightmap
import com.awakekt.awake.asset.terrain.clipmap.TerrainClipmapConfig
import com.awakekt.awake.core.geometry.MeshGeometry
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.render.command.BufferHandle
import com.awakekt.awake.render.command.MaterialBinding
import com.awakekt.awake.render.command.PipelineHandle
import com.awakekt.awake.render.command.UniformBlock
import com.awakekt.awake.render.command.UniformBlockOwner
import com.awakekt.awake.render.passes.ContentDepthSource
import com.awakekt.awake.render.passes.ContentFeature
import com.awakekt.awake.render.passes.ContentGeometry
import com.awakekt.awake.render.pipeline.PipelineFactory
import com.awakekt.awake.render.pipeline.PipelineRegistry
import com.awakekt.awake.render.pipeline.PipelineSpec
import com.awakekt.awake.render.renderer.UniformWriter
import com.awakekt.awake.scene.rendering.terrain.TerrainComponent
import com.awakekt.awake.scene.rendering.terrain.TerrainContentSystem
import com.awakekt.awake.terrain.TerrainSurface
import com.awakekt.awake.terrain.TerrainSurfaceProvider
import com.awakekt.awake.terrain.TerrainSurfaceReference
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

@OptIn(ExperimentalCoroutinesApi::class)
class TerrainContentSystemTest {

    private val world = World()
    private val host = RecordingHost()
    private val scope = TestScope()
    private var providerResolves = 0
    private val system = TerrainContentSystem(
        host,
        scope,
        providers = mapOf(
            PROVIDER to TerrainSurfaceProvider {
                providerResolves++
                delay(RESOLVE_MILLIS)
                SURFACE
            },
        ),
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

    @Test
    fun surfacedOnlyAttachesWhenReferenceArrivesAfterTerrainWasAlreadyInWorld() {
        val surfacedOnly = TerrainContentSystem(host, scope, surfacedOnly = true)
        val entity = spawnTerrain()
        repeat(3) { surfacedOnly.update(world, 0f) }
        assertEquals(0, host.attached.size)

        world.add(entity, TerrainSurfaceReference("not.installed", 1, JsonObject(emptyMap())))
        repeat(2) { surfacedOnly.update(world, 0f) }

        assertEquals(1, host.attached.size)
        surfacedOnly.detachAll()
        assertEquals(1, host.detached)
    }

    @Test
    fun manyTerrainsAttachOnceAndDetachCleanly() {
        val entities = List(24) { spawnTerrain() }
        repeat(4) { system.update(world, 0f) }
        assertEquals(24, host.attached.size)
        assertEquals(0, host.detached)

        world.destroy(entities[10])
        repeat(2) { system.update(world, 0f) }
        assertEquals(1, host.detached)

        system.detachAll()
        assertEquals(24, host.detached)
    }

    @Test
    fun invisibleColliderTerrainsUploadZeroHeightmapsAndZeroClipmapMeshes() {
        val entities = List(31) {
            spawnTerrain(
                reference = TerrainSurfaceReference(PROVIDER, 1, JsonObject(emptyMap())),
                isVisible = false,
                collider = true,
            )
        }

        repeat(4) { system.update(world, 0f) }
        scope.advanceUntilIdle()
        repeat(2) { system.update(world, 0f) }

        assertEquals(0, host.attempts, "invisible terrains must never attempt GPU feature attachment")
        assertEquals(0, host.attached.size, "invisible terrains must upload 0 heightmaps and 0 clipmap meshes")
        assertEquals(0, providerResolves, "invisible terrains must not resolve surface providers")

        entities.forEach(world::destroy)
        system.update(world, 0f)
        system.detachAll()
        assertEquals(0, host.detached)
    }

    @Test
    fun flippingIsVisibleFromFalseToTrueLazilyAttachesOnThatFrame() {
        val entity = spawnTerrain(isVisible = false, collider = true)
        val component = requireNotNull(world.get<TerrainComponent>(entity))

        repeat(3) { system.update(world, 0f) }
        assertEquals(0, host.attached.size)
        assertEquals(0, host.attempts)

        component.isVisible = true
        system.update(world, 0f)

        assertEquals(1, host.attached.size, "flipping isVisible=true must lazily attach on that frame")
        assertEquals(setOf(HEIGHTMAP_BINDING), host.attached.single().textures.keys)
        assertEquals(0, host.detached)

        system.detachAll()
        assertEquals(1, host.detached)
    }

    @Test
    fun invisibleReferencedTerrainDefersSurfaceResolutionUntilShown() {
        val entity = spawnTerrain(
            reference = TerrainSurfaceReference(PROVIDER, 1, JsonObject(emptyMap())),
            isVisible = false,
        )
        val component = requireNotNull(world.get<TerrainComponent>(entity))

        repeat(2) { system.update(world, 0f) }
        scope.advanceUntilIdle()
        system.update(world, 0f)
        assertEquals(0, providerResolves)
        assertEquals(0, host.attached.size)

        component.isVisible = true
        system.update(world, 0f)
        scope.advanceUntilIdle()
        system.update(world, 0f)

        assertEquals(1, providerResolves)
        assertEquals(1, host.attached.size)
        assertEquals(setOf(HEIGHTMAP_BINDING, TERRAIN_SURFACE_FIRST_BINDING), host.attached.single().textures.keys)
    }

    @Test
    fun hidingVisibleTerrainDetachesAndReattachesCleanlyWhenShownAgain() {
        val entity = spawnTerrain(isVisible = true)
        val component = requireNotNull(world.get<TerrainComponent>(entity))

        repeat(2) { system.update(world, 0f) }
        assertEquals(1, host.attached.size)
        assertEquals(0, host.detached)

        component.isVisible = false
        system.update(world, 0f)
        assertEquals(1, host.detached, "hiding a visible terrain must detach its GPU feature")

        component.isVisible = true
        repeat(2) { system.update(world, 0f) }
        assertEquals(2, host.attached.size)
        assertEquals(1, host.detached)

        system.detachAll()
        assertEquals(2, host.detached)
    }

    @Test
    fun hidingDuringAsyncSurfaceResolutionCancelsAttachWithoutLeaking() {
        val entity = spawnTerrain(
            reference = TerrainSurfaceReference(PROVIDER, 1, JsonObject(emptyMap())),
            isVisible = true,
        )
        val component = requireNotNull(world.get<TerrainComponent>(entity))

        system.update(world, 0f)
        component.isVisible = false
        system.update(world, 0f)
        scope.advanceUntilIdle()
        repeat(2) { system.update(world, 0f) }

        assertEquals(0, host.attached.size)
        assertEquals(0, host.detached)
    }

    @Test
    fun multipleVisibleTerrainsWithIdenticalClipmapConfigShareOneGpuClipmapMesh() {
        val gpu = CountingClipmapGpu()
        val attacher = ContentFeatureAttacher(gpu)
        val sharedSystem = TerrainContentSystem(attacher, scope)
        val sharedConfig = TerrainClipmapConfig(ringCount = 3, ringResolution = 16, baseSpacing = 2f)

        val visibleA = spawnTerrain(isVisible = true, config = sharedConfig)
        val visibleB = spawnTerrain(isVisible = true, config = sharedConfig)
        val hiddenCollider = spawnTerrain(isVisible = false, collider = true, config = sharedConfig)
        spawnTerrain(isVisible = false, collider = true, config = sharedConfig)

        repeat(2) { sharedSystem.update(world, 0f) }
        assertEquals(2, gpu.heightmapUploads, "only the 2 visible terrains upload heightmaps")
        assertEquals(1, gpu.geometryUploads, "terrains sharing ClipmapConfig upload 1 shared clipmap GPU mesh")
        assertEquals(0, gpu.inlineGeometryUploads, "shared clipmap mesh is never uploaded inline per terrain")
        assertEquals(0, gpu.geometryReleases)

        // Flipping an invisible collider terrain to visible uploads its heightmap and reuses the shared clipmap mesh.
        requireNotNull(world.get<TerrainComponent>(hiddenCollider)).isVisible = true
        repeat(2) { sharedSystem.update(world, 0f) }
        assertEquals(3, gpu.heightmapUploads)
        assertEquals(1, gpu.geometryUploads, "lazily visible terrain must reuse the live shared clipmap mesh")
        assertEquals(0, gpu.geometryReleases)

        // Disposing one visible terrain keeps the shared clipmap mesh alive for the remaining terrains.
        world.destroy(visibleA)
        sharedSystem.update(world, 0f)
        assertEquals(1, gpu.heightmapReleases)
        assertEquals(0, gpu.geometryReleases)

        // Hiding another keeps it alive for the last remaining visible terrain.
        requireNotNull(world.get<TerrainComponent>(visibleB)).isVisible = false
        sharedSystem.update(world, 0f)
        assertEquals(2, gpu.heightmapReleases)
        assertEquals(0, gpu.geometryReleases)

        // Disposing the last visible terrain releases the shared clipmap GPU mesh cleanly.
        val firstUploadedGeometry = gpu.uploadedGeometries.single()
        sharedSystem.detachAll()
        assertEquals(3, gpu.heightmapReleases)
        assertEquals(1, gpu.geometryReleases)
        assertSame(firstUploadedGeometry, gpu.releasedGeometries.single())
    }

    private fun spawnTerrain(
        reference: TerrainSurfaceReference? = null,
        isVisible: Boolean = true,
        collider: Boolean = false,
        config: TerrainClipmapConfig = TerrainClipmapConfig(ringCount = 2, ringResolution = 16, baseSpacing = 1f),
    ): Entity {
        val entity = world.create()
        world.add(entity, terrain(isVisible = isVisible, collider = collider, config = config))
        reference?.let { world.add(entity, it) }
        return entity
    }

    private fun terrain(
        isVisible: Boolean = true,
        collider: Boolean = false,
        config: TerrainClipmapConfig = TerrainClipmapConfig(ringCount = 2, ringResolution = 16, baseSpacing = 1f),
    ) = TerrainComponent(
        heightmap = Heightmap(FloatArray(4 * 4), width = 4, depth = 4, scale = Vec3f(1f, 1f, 1f)),
        clipmapConfig = config,
        isVisible = isVisible,
        collider = collider,
    )

    private class FakePipeline : UniformBlockOwner {
        override val uniformBlock: UniformBlock = object : UniformBlock {
            override fun binding(frameIndex: Int): MaterialBinding = error("not recorded")

            override fun write(frameIndex: Int, fill: UniformWriter.() -> Unit) = Unit
        }
    }

    private class CountingClipmapGpu : ContentFeatureGpu<FakePipeline> {
        override val backend = RenderBackend.Vulkan
        override val registry = PipelineRegistry(PipelineFactory { _, _ -> FakePipeline() })

        var heightmapUploads = 0
        var heightmapReleases = 0
        var geometryUploads = 0
        var geometryReleases = 0
        var inlineGeometryUploads = 0
        val uploadedGeometries = mutableListOf<ContentGeometry>()
        val releasedGeometries = mutableListOf<ContentGeometry>()

        override fun handle(pipeline: FakePipeline): PipelineHandle = object : PipelineHandle {}

        override fun upload(pipeline: FakePipeline, feature: ContentFeature): ContentUpload =
            upload(pipeline, feature, includeGeometry = true)

        override fun upload(pipeline: FakePipeline, feature: ContentFeature, includeGeometry: Boolean): ContentUpload {
            if (HEIGHTMAP_BINDING in feature.textures) heightmapUploads++
            val geometry = if (includeGeometry && feature.geometry != null) {
                inlineGeometryUploads++
                ContentGeometry(object : BufferHandle {}, object : BufferHandle {}, feature.geometry!!.indices.size)
            } else {
                null
            }
            return ContentUpload(geometry) {
                if (HEIGHTMAP_BINDING in feature.textures) heightmapReleases++
            }
        }

        override fun uploadGroup(pipeline: FakePipeline, feature: ContentFeature): ContentGroupUpload =
            uploadGroup(pipeline, feature, includeGeometry = true)

        override fun uploadGroup(
            pipeline: FakePipeline,
            feature: ContentFeature,
            includeGeometry: Boolean,
        ): ContentGroupUpload {
            val upload = upload(pipeline, feature, includeGeometry)
            val block = object : UniformBlock {
                override fun binding(frameIndex: Int): MaterialBinding = error("not recorded")

                override fun write(frameIndex: Int, fill: UniformWriter.() -> Unit) = Unit
            }
            return ContentGroupUpload(block, upload)
        }

        override fun uploadGeometry(geometry: MeshGeometry): ContentUpload {
            geometryUploads++
            val uploaded = ContentGeometry(object : BufferHandle {}, object : BufferHandle {}, geometry.indices.size)
            uploadedGeometries += uploaded
            return ContentUpload(uploaded) {
                geometryReleases++
                releasedGeometries += uploaded
            }
        }

        override suspend fun addDepthCaster(
            pipeline: FakePipeline,
            depth: PipelineSpec,
            source: ContentDepthSource,
        ): ContentUpload? = null

        override fun destroyPipeline(pipeline: FakePipeline) = Unit

        override fun awaitIdle() = Unit
    }

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
