/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.asset.shaderpack.PackShaderSets
import com.awakekt.awake.asset.shaders.AttachedContentFeature
import com.awakekt.awake.asset.shaders.ContentFeatureHost
import com.awakekt.awake.asset.shaders.ContentFeatureSource
import com.awakekt.awake.asset.shaders.RenderBackend
import com.awakekt.awake.asset.shaders.ShaderStage
import com.awakekt.awake.asset.terrain.clipmap.TerrainClipmapConfig
import com.awakekt.awake.asset.terrain.clipmap.TerrainClipmapGeometry
import com.awakekt.awake.compose.ui.platform.InputOwnership
import com.awakekt.awake.core.input.Input
import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.engine.platform.error.childScope
import com.awakekt.awake.kit.terrainlayers.TERRAIN_LAYERS_PROVIDER
import com.awakekt.awake.kit.terrainlayers.TerrainControlMap
import com.awakekt.awake.kit.terrainlayers.TerrainControlMapCodec
import com.awakekt.awake.kit.terrainlayers.TerrainLayer
import com.awakekt.awake.kit.terrainlayers.TerrainLayerPalette
import com.awakekt.awake.kit.terrainlayers.TerrainLayerPaletteCodec
import com.awakekt.awake.kit.terrainlayers.TerrainLayersFastShaders
import com.awakekt.awake.kit.terrainlayers.TerrainLayersSurfaceProvider
import com.awakekt.awake.kit.terrainlayers.TerrainQuality
import com.awakekt.awake.physics.PhysicsWorld
import com.awakekt.awake.physics.jolt.createJoltPhysicsWorld
import com.awakekt.awake.render.testing.NoopRenderer
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.scene.binding.instantiate
import com.awakekt.awake.scene.controls.GameplayInput
import com.awakekt.awake.scene.document.SceneDocument
import com.awakekt.awake.scene.document.SceneLoader
import com.awakekt.awake.scene.physics.PhysicsBody
import com.awakekt.awake.scene.rendering.terrain.TerrainComponent
import com.awakekt.awake.terrain.TerrainSurface
import com.awakekt.awake.terrain.TerrainSurfaceProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

/** A project's `terrain` draws when it is played on a renderer, with nothing wired by the host. */
class ProjectTerrainTest {
    private val input = Input()

    // Surfaces resolve off the test thread, as in a session, in a scope the test owns.
    private fun TestScope.services(
        renderer: HostRenderer,
        content: SceneContent,
        physics: PhysicsWorld? = null,
    ) = SceneHostServices(
        input = { GameplayInput(input.currentSnapshot, InputOwnership()) },
        physics = physics,
        renderer = renderer,
        content = content,
    ).withCoroutineScope(backgroundScope.childScope(Dispatchers.Default))

    @Test
    fun aPlainTerrainDrawsAndIsDetachedWhenTheSceneCloses() = runTest {
        val project = loadProject(files(PLAIN_TERRAIN))
        val renderer = HostRenderer()
        val systems = sceneSystemsFor(project.scene, services(renderer, project.content))
        val world = World().also { SceneLoader.instantiate(project.scene, it) }

        repeat(2) { systems.frame.forEach { system -> system.update(world, DELTA) } }
        assertEquals(1, renderer.attached, "drawn with the built-in terrain shading")

        systems.close()
        assertEquals(1, renderer.detached)
    }

    @Test
    fun aProjectsContentHoldsTheLayeredTerrainProvider() = runTest {
        val project = loadProject(files(PLAIN_TERRAIN))

        assertEquals(setOf(TERRAIN_LAYERS_PROVIDER), project.content[CoreSceneContent.TerrainSurfaces]?.keys)
    }

    /** A host running the scene's systems itself supplies a provider of its own as content. */
    @Test
    fun aSurfaceIsDrawnByTheProviderTheHostSupplies() = runTest {
        installProjectComponents()
        val scene = SceneLoader.decode(SURFACED_TERRAIN)
        var resolved = 0
        val provider = TerrainSurfaceProvider {
            resolved++
            TerrainSurface(PackShaderSets.Terrain)
        }
        val renderer = HostRenderer()
        val content = SceneContent.build { this[CoreSceneContent.TerrainSurfaces] = mapOf("test.surface" to provider) }
        val systems = sceneSystemsFor(scene, services(renderer, content))
        val world = World().also { SceneLoader.instantiate(scene, it) }

        repeat(ATTEMPTS) {
            systems.frame.forEach { system -> system.update(world, DELTA) }
            if (renderer.attached == 1) {
                assertEquals(1, resolved)
                systems.close()
                return@runTest
            }
            withContext(Dispatchers.Default) { delay(WAIT_MILLIS) }
        }
        fail("The surfaced terrain was not drawn")
    }

    @Test
    fun aHeadlessHostDrawsNoTerrain() {
        installProjectComponents()

        val systems = sceneSystemsFor(
            SceneLoader.decode(PLAIN_TERRAIN),
            SceneHostServices.headless(input = { GameplayInput(input.currentSnapshot, InputOwnership()) }),
        )

        assertFalse(systems.frame.any { it is TerrainDrawingSystem })
    }

    @Test
    fun anInvisibleColliderTerrainUploadsNothingUntilShown() = runTest {
        val project = loadProject(files(INVISIBLE_COLLIDER_TERRAIN), physicsWorld = ::createJoltPhysicsWorld)
        val physics = createJoltPhysicsWorld()
        try {
            val renderer = HostRenderer()
            val systems = sceneSystemsFor(project.scene, services(renderer, project.content, physics = physics))
            val world = World().also { SceneLoader.instantiate(project.scene, it) }

            repeat(3) {
                systems.fixed.forEach { system -> system.update(world, DELTA) }
                systems.frame.forEach { system -> system.update(world, DELTA) }
            }
            var entity: Entity? = null
            var terrain: TerrainComponent? = null
            world.queryEach<TerrainComponent> { found, component ->
                entity = found
                terrain = component
            }
            assertTrue(
                world.has<PhysicsBody>(requireNotNull(entity)),
                "invisible collider terrain must still build its physics heightfield body",
            )
            assertEquals(0, renderer.attached, "invisible collider-only terrains must not upload GPU content features")

            requireNotNull(terrain).isVisible = true

            repeat(2) { systems.frame.forEach { system -> system.update(world, DELTA) } }
            assertEquals(1, renderer.attached, "flipping isVisible=true must lazily attach the terrain")

            systems.close()
            assertEquals(1, renderer.detached)
        } finally {
            physics.destroy()
        }
    }

    @Test
    fun aGameFeatureCanOptIntoFastTerrainQualityWithoutCollidingWithTerrainSurfaces() = runTest {
        val fastQualityFeature = object : SceneFeature {
            override val id = "com.example.fast-terrain"

            override suspend fun load(scene: SceneDocument, files: AssetSource, content: SceneContent.Builder) {
                if (content.hasRenderer) content[CoreSceneContent.TerrainQuality] = TerrainQuality.Fast
            }

            override fun plan(scene: SceneDocument, plan: SceneSystemPlan) = Unit
        }
        val project = loadProject(files(PLAIN_TERRAIN), capabilities = listOf(fastQualityFeature))
        assertEquals(TerrainQuality.Fast, project.content[CoreSceneContent.TerrainQuality])
        assertEquals(setOf(TERRAIN_LAYERS_PROVIDER), project.content[CoreSceneContent.TerrainSurfaces]?.keys)

        val renderer = HostRenderer()
        val systems = sceneSystemsFor(project.scene, services(renderer, project.content), capabilities = listOf(fastQualityFeature))
        val world = World().also { SceneLoader.instantiate(project.scene, it) }

        repeat(2) { systems.frame.forEach { system -> system.update(world, DELTA) } }
        assertEquals(1, renderer.attached)
        val feature = renderer.sources.single().resolve(RenderBackend.Vulkan)
        assertEquals(PackShaderSets.TerrainFast.vulkan[ShaderStage.FRAGMENT], feature.spec.fragmentShader)

        systems.close()
        assertEquals(1, renderer.detached)
    }

    @Test
    fun fastTerrainQualitySwitchesLayeredTerrainSurfaceProviderToFastShadersAndPreservesKeys() = runTest {
        installProjectComponents()
        val palette = TerrainLayerPalette(layers = listOf(TerrainLayer(id = "grass", albedo = "grass.png")))
        val control = TerrainControlMap(1, 1, ByteArray(4), byteArrayOf(-1, 0, 0, 0))
        val assets = AssetSource { path ->
            runCatching {
                when (path.value) {
                    "terrain/harbor.terrainpalette.json" -> TerrainLayerPaletteCodec.encode(palette)
                    "terrain/harbor.terrainctl" -> TerrainControlMapCodec.encode(control)
                    "terrain/grass.png" -> ByteArray(4) { 1 }
                    else -> error("unexpected: ${path.value}")
                }
            }
        }
        val provider = TerrainLayersSurfaceProvider(assets, decodeImage = { TextureAsset(ByteArray(2 * 2 * 4) { -1 }, 2, 2) })
        val scene = SceneLoader.decode(LAYERED_TERRAIN)
        val content = SceneContent.build {
            this[CoreSceneContent.TerrainSurfaces] = mapOf(TERRAIN_LAYERS_PROVIDER to provider)
            this[CoreSceneContent.TerrainQuality] = TerrainQuality.Fast
        }
        val renderer = HostRenderer()
        val systems = sceneSystemsFor(scene, services(renderer, content))
        val world = World().also { SceneLoader.instantiate(scene, it) }

        repeat(ATTEMPTS) {
            systems.frame.forEach { system -> system.update(world, DELTA) }
            if (renderer.attached == 1) {
                val attachedFeature = renderer.sources.single().resolve(RenderBackend.Vulkan)
                assertEquals(TerrainLayersFastShaders.vulkan[ShaderStage.FRAGMENT], attachedFeature.spec.fragmentShader)
                assertTrue(attachedFeature.textureKeys.isNotEmpty(), "Fast layered terrain must preserve shared textureKeys")
                systems.close()
                return@runTest
            }
            withContext(Dispatchers.Default) { delay(WAIT_MILLIS) }
        }
        fail("The fast layered terrain was not drawn")
    }

    @Test
    fun terrainClipmapConfigHookReducesClipmapGeometryAndPreservesAuthoredBaseSpacing() = runTest {
        installProjectComponents()
        val authoredScene = """
{ "version": 1, "name": "ground", "nodes": [
  { "name": "Ground", "components": [
    { "component": "terrain", "width": 2, "depth": 2, "samples": [0.0, 0.0, 0.0, 0.0],
      "clipmapRings": 6, "clipmapRingResolution": 64, "clipmapBaseSpacing": 4.0 }
  ] }
] }
"""
        val compactFeature = object : SceneFeature {
            override val id = "test.compact-clipmap"

            override suspend fun load(scene: SceneDocument, files: AssetSource, content: SceneContent.Builder) {
                if (content.hasRenderer) content[CoreSceneContent.TerrainClipmapConfig] = TerrainClipmapConfig.Compact
            }

            override fun plan(scene: SceneDocument, plan: SceneSystemPlan) = Unit
        }
        val defaultProject = loadProject(files(authoredScene))
        val compactProject = loadProject(files(authoredScene), capabilities = listOf(compactFeature))
        assertEquals(TerrainClipmapConfig.Compact, compactProject.content[CoreSceneContent.TerrainClipmapConfig])

        val defaultRenderer = HostRenderer()
        val defaultSystems = sceneSystemsFor(defaultProject.scene, services(defaultRenderer, defaultProject.content))
        val defaultWorld = World().also { SceneLoader.instantiate(defaultProject.scene, it) }
        repeat(2) { defaultSystems.frame.forEach { system -> system.update(defaultWorld, DELTA) } }
        val defaultMesh = defaultRenderer.sources.single().resolve(RenderBackend.Vulkan).geometry!!
        defaultSystems.close()

        val compactRenderer = HostRenderer()
        val compactSystems = sceneSystemsFor(
            compactProject.scene,
            services(compactRenderer, compactProject.content),
            capabilities = listOf(compactFeature),
        )
        val compactWorld = World().also { SceneLoader.instantiate(compactProject.scene, it) }
        repeat(2) { compactSystems.frame.forEach { system -> system.update(compactWorld, DELTA) } }
        val compactFeatureResolved = compactRenderer.sources.single().resolve(RenderBackend.Vulkan)
        val compactMesh = compactFeatureResolved.geometry!!
        val expectedCompact = TerrainClipmapGeometry.buildMergedClipmapMesh(
            TerrainClipmapConfig.Compact.copy(baseSpacing = 4.0f),
        )
        assertEquals(expectedCompact.indices.size, compactMesh.indices.size)
        assertEquals(expectedCompact.vertices.size, compactMesh.vertices.size)
        assertTrue(compactMesh.indices.size * 4 < defaultMesh.indices.size, "Compact clipmap should cut triangles >4x")
        // Verify level-0 authored baseSpacing (4.0m) was preserved rather than overwritten by Compact's 2.0m default.
        assertEquals(expectedCompact.vertices.toList(), compactMesh.vertices.toList())
        compactSystems.close()
    }

    private class HostRenderer :
        NoopRenderer(),
        ContentFeatureHost {
        var attached = 0
        var detached = 0
        val sources = mutableListOf<ContentFeatureSource>()

        override suspend fun attachContentFeature(source: ContentFeatureSource): AttachedContentFeature {
            attached++
            sources += source
            return AttachedContentFeature { detached++ }
        }
    }

    private fun files(scene: String) = AssetSource { path ->
        runCatching { mapOf(MANIFEST_PATH to MANIFEST, "scenes/main.scene.json" to scene).getValue(path.value).encodeToByteArray() }
    }

    private companion object {
        const val DELTA = 1f / 60f
        const val ATTEMPTS = 100
        const val WAIT_MILLIS = 10L
        const val MANIFEST_PATH = "awake.project.json"
        const val MANIFEST = """{"formatVersion":1,"id":"com.example.harbor-town","name":"Harbor Town","version":"1.0.0","entryScene":"scenes/main.scene.json"}"""

        const val PLAIN_TERRAIN = """
{ "version": 1, "name": "ground", "nodes": [
  { "name": "Ground", "components": [
    { "component": "terrain", "width": 2, "depth": 2, "samples": [0.0, 0.0, 0.0, 0.0] }
  ] }
] }
"""

        const val SURFACED_TERRAIN = """
{ "version": 1, "name": "ground", "nodes": [
  { "name": "Ground", "components": [
    { "component": "terrain", "width": 2, "depth": 2, "samples": [0.0, 0.0, 0.0, 0.0],
      "surface": { "provider": "test.surface" } }
  ] }
] }
"""

        const val LAYERED_TERRAIN = """
{ "version": 1, "name": "ground", "nodes": [
  { "name": "Ground", "components": [
    { "component": "terrain", "width": 2, "depth": 2, "samples": [0.0, 0.0, 0.0, 0.0],
      "surface": { "provider": "awake.terrain.layers", "version": 1,
        "payload": { "palette": "terrain/harbor.terrainpalette.json", "control": "terrain/harbor.terrainctl" } } }
  ] }
] }
"""

        const val INVISIBLE_COLLIDER_TERRAIN = """
{ "version": 1, "name": "ground", "nodes": [
  { "name": "ColliderGround", "components": [
    { "component": "terrain", "width": 4, "depth": 4,
      "samples": [0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0],
      "isVisible": false, "collider": true }
  ] }
] }
"""
    }
}
