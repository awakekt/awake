/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.kit.terrainlayers

import com.awakekt.awake.asset.shaders.AttachedContentFeature
import com.awakekt.awake.asset.shaders.ContentFeatureHost
import com.awakekt.awake.asset.shaders.ContentFeatureSource
import com.awakekt.awake.asset.shaders.RenderBackend
import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.render.passes.ContentFeature
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.scene.binding.SceneComponentRegistry
import com.awakekt.awake.scene.binding.SceneResolutionContext
import com.awakekt.awake.scene.document.SceneLoader
import com.awakekt.awake.scene.rendering.terrain.SceneTerrain
import com.awakekt.awake.scene.rendering.terrain.TerrainBinding
import com.awakekt.awake.scene.rendering.terrain.TerrainContentSystem
import com.awakekt.awake.scene.rendering.terrain.TerrainSurfaceReference
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TerrainLayersSurfaceProviderTest {

    private val files = mutableMapOf(
        "terrain/region.terrainpalette.json" to TerrainLayerPaletteCodec.encode(PALETTE),
        "terrain/textures/grass.png" to byteArrayOf(10),
        "terrain/textures/rock.png" to byteArrayOf(20),
        "terrain/textures/rock_height.png" to byteArrayOf(99),
        "terrain/region.terrainctl" to TerrainControlMapCodec.encode(CONTROL),
    )
    private val assets = AssetSource { path ->
        files[path.value]?.let { Result.success(it) } ?: Result.failure(NoSuchElementException(path.value))
    }

    /** Test images are one byte: that grey, 2x2. */
    private val provider = TerrainLayersSurfaceProvider(assets) { bytes ->
        TextureAsset(ByteArray(2 * 2 * 4) { bytes[0] }, 2, 2)
    }

    @Test
    fun aPayloadResolvesToTheLayeredSurfaceWithImagesRelativeToThePalette() = runTest {
        val surface = provider.resolve(reference())

        assertEquals(TerrainLayersShaders, surface.shaders)
        val layers = surface.textures.getValue(LAYER_ALBEDO_BINDING)
        assertEquals(2, layers.layerCount)
        assertEquals(20, layers.data[16].toInt() and 0xFF, "rock's albedo is the second layer")
        assertEquals(99, layers.data[16 + 3].toInt() and 0xFF, "rock's height lands in alpha")
        assertEquals(CONTROL.width, surface.textures.getValue(CONTROL_INDICES_BINDING).width)
    }

    @Test
    fun aMissingFileNamesThePath() = runTest {
        files.remove("terrain/textures/rock.png")

        val failure = assertFailsWith<IllegalStateException> { provider.resolve(reference()) }
        assertTrue("terrain/textures/rock.png" in failure.message.orEmpty(), failure.message)
    }

    @Test
    fun anotherProvidersReferenceIsRejected() = runTest {
        assertFailsWith<IllegalArgumentException> { provider.resolve(reference().copy(provider = "other")) }
    }

    /** Scene JSON to a host: the binding stores the surface, the system resolves it and attaches. */
    @Test
    fun aSceneTerrainNamingThisProviderAttachesTheLayeredSurface() {
        SceneComponentRegistry.registerGlobal(TerrainBinding)
        val terrain = SceneLoader.decode(
            """
            {"version": 1, "nodes": [{"components": [{"component": "terrain", "width": 4, "depth": 4,
             "samples": [${List(16) { 0 }.joinToString()}],
             "surface": {"provider": "$TERRAIN_LAYERS_PROVIDER", "payload": ${reference().payload}}}]}]}
            """,
        ).nodes.single().components.single() as SceneTerrain
        val world = World()
        TerrainBinding.attachTyped(world, world.create(), terrain, NoLinks(world))
        val host = RecordingHost()
        val scope = TestScope()
        val system = TerrainContentSystem(host, scope, mapOf(TERRAIN_LAYERS_PROVIDER to provider))

        system.update(world, 0f)
        scope.advanceUntilIdle()
        system.update(world, 0f)

        assertEquals(
            setOf(1, LAYER_ALBEDO_BINDING, LAYER_TABLE_BINDING, CONTROL_INDICES_BINDING, CONTROL_WEIGHTS_BINDING),
            host.attached.single().textures.keys,
        )
    }

    private fun reference() = TerrainSurfaceReference(
        TERRAIN_LAYERS_PROVIDER,
        1,
        buildJsonObject {
            put("palette", "terrain/region.terrainpalette.json")
            put("control", "terrain/region.terrainctl")
        },
    )

    private class RecordingHost : ContentFeatureHost {
        val attached = mutableListOf<ContentFeature>()

        override suspend fun attachContentFeature(source: ContentFeatureSource): AttachedContentFeature {
            attached += source.resolve(RenderBackend.Vulkan)
            return AttachedContentFeature { }
        }
    }

    private class NoLinks(override val world: World) : SceneResolutionContext {
        override fun deferNodeLink(targetNodeName: String, onResolved: (target: Entity) -> Unit) = Unit

        override fun recordRequest(request: Any) = Unit
    }

    private companion object {
        val PALETTE = TerrainLayerPalette(
            layers = listOf(
                TerrainLayer(id = "grass", albedo = "textures/grass.png"),
                TerrainLayer(id = "rock", albedo = "textures/rock.png", height = "textures/rock_height.png"),
            ),
        )

        val CONTROL = TerrainControlMap.reduce(4, 4, layerCount = 2) { layer, x, _ -> if ((x < 2) == (layer == 0)) 1f else 0f }.controlMap
    }
}
