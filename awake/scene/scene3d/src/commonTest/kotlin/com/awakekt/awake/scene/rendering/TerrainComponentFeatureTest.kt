/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering

import com.awakekt.awake.asset.shaderdsl.dot
import com.awakekt.awake.asset.shaderdsl.lit
import com.awakekt.awake.asset.shaderdsl.max
import com.awakekt.awake.asset.shaderdsl.normalize
import com.awakekt.awake.asset.shaderdsl.sampler
import com.awakekt.awake.asset.shaderdsl.shader
import com.awakekt.awake.asset.shaderdsl.texture2dArray
import com.awakekt.awake.asset.shaderdsl.textureSampleArray
import com.awakekt.awake.asset.shaderdsl.times
import com.awakekt.awake.asset.shaderdsl.vec2
import com.awakekt.awake.asset.shaderdsl.vec4
import com.awakekt.awake.asset.shaderdsl.x
import com.awakekt.awake.asset.shaderdsl.xyz
import com.awakekt.awake.asset.shaderdsl.z
import com.awakekt.awake.asset.shaderpack.PackShaderSets
import com.awakekt.awake.asset.shaderpack.TERRAIN_SURFACE_FIRST_BINDING
import com.awakekt.awake.asset.shaderpack.terrainClipmapVertexStage
import com.awakekt.awake.asset.shaders.RenderBackend
import com.awakekt.awake.asset.shaders.aslShaderSet
import com.awakekt.awake.asset.terrain.Heightmap
import com.awakekt.awake.asset.terrain.clipmap.TerrainClipmapConfig
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.pipeline.BindingLayout
import com.awakekt.awake.render.pipeline.BindingSemantic
import com.awakekt.awake.render.pipeline.ResourceKind
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.scene.rendering.terrain.TerrainComponent
import com.awakekt.awake.scene.rendering.terrain.terrainContentFeature
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

/**
 * The component is meant to be the single source: the same instance goes into the `World` and
 * into the `RenderPlan`, so what the ECS holds and what the pipeline was built for cannot drift.
 * These assert the resolved feature actually reflects the component it was given.
 */
class TerrainComponentFeatureTest {

    private val heightmap = Heightmap(
        samples = FloatArray(8 * 8) { it.toFloat() },
        width = 8,
        depth = 8,
        scale = Vec3f(2f, 3f, 2f),
    )

    private fun component(visible: Boolean = true) = TerrainComponent(
        heightmap = heightmap,
        clipmapConfig = TerrainClipmapConfig(ringCount = 2, ringResolution = 16, baseSpacing = 1f),
        isVisible = visible,
    )

    @Test
    fun theResolvedFeatureCarriesTheComponentsHeightmapAndGeometry() {
        val feature = terrainContentFeature(SHADERS, component()).resolve(RenderBackend.Vulkan)

        assertEquals("terrain", feature.name)
        assertEquals(VertexFormat.PositionNormalColorUv, feature.spec.vertexFormat)
        // The heightmap became this feature's own sampled texture, sized from the component's.
        val uploaded = assertNotNull(feature.textures[1])
        assertEquals(heightmap.width, uploaded.width)
        assertEquals(heightmap.depth, uploaded.height)
        assertNotNull(feature.geometry, "The clipmap mesh is built from the component's config.")
    }

    /** The declaration and the supplied texture have to agree, which `ContentFeature` enforces --
     * this pins that the wiring satisfies it rather than relying on the constructor throwing. */
    @Test
    fun theDeclaredBindingsMatchWhatTheFeatureSupplies() {
        val feature = terrainContentFeature(SHADERS, component()).resolve(RenderBackend.Vulkan)
        val bindings = assertNotNull(feature.spec.materialBindings)

        assertEquals(
            feature.textures.keys,
            bindings.entries.filter { it.kind == ResourceKind.SampledTexture }.map { it.binding }.toSet(),
        )
    }

    /** Resolving per backend is `ContentFeatureSource`'s whole purpose; terrain must not have
     * baked one in. */
    @Test
    fun theSameComponentResolvesForEitherBackend() {
        val source = terrainContentFeature(SHADERS, component())

        assertEquals("terrain", source.resolve(RenderBackend.Vulkan).name)
        assertEquals("terrain", source.resolve(RenderBackend.WebGpu).name)
    }

    /** A surface shader's bindings come from its own set, so its textures reach the pipeline. */
    @Test
    fun surfaceTexturesJoinTheHeightmapAtTheSurfaceShadersBindings() {
        val feature = terrainContentFeature(
            SURFACE_SHADERS,
            component(),
            mapOf(TERRAIN_SURFACE_FIRST_BINDING to LAYERS),
        ).resolve(RenderBackend.WebGpu)

        assertEquals(setOf(1, TERRAIN_SURFACE_FIRST_BINDING), feature.textures.keys)
        assertEquals(LAYERS.layerCount, feature.textures.getValue(TERRAIN_SURFACE_FIRST_BINDING).layerCount)
    }

    @Test
    fun aSurfaceTextureAtAClipmapBindingIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            terrainContentFeature(SHADERS, component(), mapOf(1 to LAYERS))
        }
    }

    /** `ContentFeature` catches a declared surface binding left without pixel data. */
    @Test
    fun aSurfaceShaderWithoutItsTexturesFailsToResolve() {
        assertFailsWith<IllegalArgumentException> {
            terrainContentFeature(SURFACE_SHADERS, component()).resolve(RenderBackend.Vulkan)
        }
    }

    private companion object {
        val SHADERS = PackShaderSets.Terrain

        val LAYERS = TextureAsset(ByteArray(2 * 2 * 4 * 3), width = 2, height = 2, layerCount = 3)

        val SURFACE_SHADERS = aslShaderSet(
            shader("terrain_surface_probe") {
                val terrain = terrainClipmapVertexStage()
                val group = BindingLayout.Standard.slot(BindingSemantic.Material)
                val layers by texture2dArray(group = group, binding = TERRAIN_SURFACE_FIRST_BINDING)
                val layerSampler by sampler(group = group, binding = TERRAIN_SURFACE_FIRST_BINDING + 1)
                fragment {
                    val uv = let("uv", vec2(terrain.worldPosition.x, terrain.worldPosition.z))
                    val albedo = let("albedo", textureSampleArray(layers, layerSampler, uv, 0.lit))
                    val light = let(
                        "light",
                        max(dot(normalize(terrain.worldNormal), normalize(terrain.sunDirection.xyz)), 0f.lit),
                    )
                    colorOutput(vec4(albedo.xyz * light, 1f.lit))
                }
            },
        )
    }
}
