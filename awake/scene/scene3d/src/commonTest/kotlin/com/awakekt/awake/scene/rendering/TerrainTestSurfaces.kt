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
import com.awakekt.awake.asset.shaderpack.TERRAIN_SURFACE_FIRST_BINDING
import com.awakekt.awake.asset.shaderpack.terrainClipmapDiscardUnderFinerRing
import com.awakekt.awake.asset.shaderpack.terrainClipmapVertexStage
import com.awakekt.awake.asset.shaders.aslShaderSet
import com.awakekt.awake.render.pipeline.BindingLayout
import com.awakekt.awake.render.pipeline.BindingSemantic
import com.awakekt.awake.render.texture.TextureAsset

/** Three layers of 2x2 texels, for a surface's arrayed binding. */
internal val PROBE_LAYERS = TextureAsset(ByteArray(2 * 2 * 4 * 3), width = 2, height = 2, layerCount = 3)

/** A surface on the shared clipmap stage that samples an array at the terrain's world position. */
internal val PROBE_SURFACE_SHADERS = aslShaderSet(
    shader("terrain_surface_probe") {
        val terrain = terrainClipmapVertexStage()
        val group = BindingLayout.Standard.slot(BindingSemantic.Material)
        val layers by texture2dArray(group = group, binding = TERRAIN_SURFACE_FIRST_BINDING)
        val layerSampler by sampler(group = group, binding = TERRAIN_SURFACE_FIRST_BINDING + 1)
        fragment {
            terrainClipmapDiscardUnderFinerRing(terrain)
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
