/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.scene.rendering

import io.github.awakelab.awake.asset.shaders.RenderBackend
import io.github.awakelab.awake.asset.shaders.shaderSet
import io.github.awakelab.awake.asset.terrain.Heightmap
import io.github.awakelab.awake.asset.terrain.clipmap.TerrainClipmapConfig
import io.github.awakelab.awake.core.geometry.VertexFormat
import io.github.awakelab.awake.core.math.Vec3f
import io.github.awakelab.awake.render.pipeline.ResourceKind
import io.github.awakelab.awake.scene.rendering.terrain.TerrainComponent
import io.github.awakelab.awake.scene.rendering.terrain.terrainContentFeature
import kotlin.test.Test
import kotlin.test.assertEquals
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

    private companion object {
        val SHADERS = shaderSet("terrain")
    }
}
