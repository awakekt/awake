/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaderpack

import com.awakekt.awake.asset.shaders.ContentFeatureSource
import com.awakekt.awake.asset.shaders.ShaderSet
import com.awakekt.awake.asset.shaders.aslShaderSet
import com.awakekt.awake.asset.shaders.spec
import com.awakekt.awake.asset.shaders.stagesFor
import com.awakekt.awake.asset.shaders.withSolidOpaque
import com.awakekt.awake.asset.terrain.clipmap.TerrainClipmapConfig
import com.awakekt.awake.asset.terrain.clipmap.TerrainClipmapGeometry
import com.awakekt.awake.asset.terrain.clipmap.TerrainClipmapTracker
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.render.passes.ContentFeature
import com.awakekt.awake.render.passes.ContentPaint
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.terrain.PagedTerrain

/** One clipmap draw over a bounded resident set. Resources update before every depth/lit pass. */
fun pagedTerrainContentFeature(
    terrain: PagedTerrain,
    config: TerrainClipmapConfig = TerrainClipmapConfig(baseSpacing = terrain.layout.sampleSpacing),
    shaders: ShaderSet = aslShaderSet(::pagedTerrainShader).withSolidOpaque(aslShaderSet(::pagedTerrainCoreShader)),
    sharedTextures: Map<Int, TextureAsset> = emptyMap(),
    samplerTextures: Map<Int, Int> = emptyMap(),
    isVisible: () -> Boolean = { true },
): ContentFeatureSource {
    require(config.ringCount <= MAX_CLIPMAP_RINGS)
    require(sharedTextures.keys.intersect(PagedTerrainUploads(terrain).bindings).isEmpty())
    val layout = terrain.layout
    val sampling = floatArrayOf(layout.cellCountX * layout.cellSize, layout.cellCountZ * layout.cellSize, 1f / terrain.heights.fallback.width, 1f / terrain.heights.fallback.depth)
    val depth = aslShaderSet(PagedTerrainShadowDepthShader)
    val coreIndices = TerrainClipmapGeometry.coreIndexCount(config)
    return ContentFeatureSource { backend ->
        // Every resolve uploads new images, so each starts from the initial contents with its own uploads.
        val uploads = PagedTerrainUploads(terrain)
        val solidCoreSpec = shaders.solidOpaque?.stagesFor(backend)?.spec(
            vertexFormat = VertexFormat.PositionNormalColorUv,
            uniforms = PagedTerrainUniformLayout.Draw,
        )
        ContentFeature(
            name = "paged-terrain",
            spec = shaders.stagesFor(backend).spec(vertexFormat = VertexFormat.PositionNormalColorUv, uniforms = PagedTerrainUniformLayout.Draw),
            depth = depth.stagesFor(backend).spec(vertexFormat = VertexFormat.PositionNormalColorUv, uniforms = PagedTerrainUniformLayout.Draw),
            textures = uploads.initialImages() + sharedTextures,
            geometry = TerrainClipmapGeometry.buildMergedClipmapMesh(config),
            paint = ContentPaint.OpaqueGeometry,
            textureUpdates = uploads,
            samplerTextures = mapOf(2 to 1) + samplerTextures,
            images = emptyMap(),
            build = { pipeline, uniforms, geometry ->
                TerrainRenderFeature(
                    pipeline, uniforms, requireNotNull(geometry), TerrainClipmapTracker(config),
                    (layout.maxElevation - layout.minElevation) * layout.heightScale,
                    layout.minElevation * layout.heightScale, sampling, isVisible, terrain,
                )
            },
            alternateSpec = solidCoreSpec,
            buildWithAlternate = { pipeline, corePipeline, uniforms, geometry ->
                TerrainRenderFeature(
                    pipeline = pipeline,
                    uniforms = uniforms,
                    geometry = requireNotNull(geometry),
                    tracker = TerrainClipmapTracker(config),
                    heightScale = (layout.maxElevation - layout.minElevation) * layout.heightScale,
                    heightBias = layout.minElevation * layout.heightScale,
                    sampling = sampling,
                    isVisible = isVisible,
                    pagedTerrain = terrain,
                    corePipeline = corePipeline,
                    coreIndexCount = coreIndices,
                )
            },
        )
    }
}
