/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.asset.shaderpack

import io.github.awakelab.awake.asset.shaderdsl.AslShaderDefinition
import io.github.awakelab.awake.asset.shaderdsl.abs
import io.github.awakelab.awake.asset.shaderdsl.clamp
import io.github.awakelab.awake.asset.shaderdsl.div
import io.github.awakelab.awake.asset.shaderdsl.dot
import io.github.awakelab.awake.asset.shaderdsl.fieldsFrom
import io.github.awakelab.awake.asset.shaderdsl.floor
import io.github.awakelab.awake.asset.shaderdsl.inputsFrom
import io.github.awakelab.awake.asset.shaderdsl.lit
import io.github.awakelab.awake.asset.shaderdsl.max
import io.github.awakelab.awake.asset.shaderdsl.minus
import io.github.awakelab.awake.asset.shaderdsl.mix
import io.github.awakelab.awake.asset.shaderdsl.normalize
import io.github.awakelab.awake.asset.shaderdsl.plus
import io.github.awakelab.awake.asset.shaderdsl.sampler
import io.github.awakelab.awake.asset.shaderdsl.shader
import io.github.awakelab.awake.asset.shaderdsl.texture2d
import io.github.awakelab.awake.asset.shaderdsl.texture2dArray
import io.github.awakelab.awake.asset.shaderdsl.textureSample
import io.github.awakelab.awake.asset.shaderdsl.textureSampleArray
import io.github.awakelab.awake.asset.shaderdsl.textureSampleLevel
import io.github.awakelab.awake.asset.shaderdsl.times
import io.github.awakelab.awake.asset.shaderdsl.toU32
import io.github.awakelab.awake.asset.shaderdsl.unaryMinus
import io.github.awakelab.awake.asset.shaderdsl.vec2
import io.github.awakelab.awake.asset.shaderdsl.vec3
import io.github.awakelab.awake.asset.shaderdsl.vec4
import io.github.awakelab.awake.asset.shaderdsl.w
import io.github.awakelab.awake.asset.shaderdsl.x
import io.github.awakelab.awake.asset.shaderdsl.xyz
import io.github.awakelab.awake.asset.shaderdsl.y
import io.github.awakelab.awake.asset.shaderdsl.z
import io.github.awakelab.awake.core.geometry.GpuDataShape
import io.github.awakelab.awake.core.geometry.VertexFormat
import io.github.awakelab.awake.core.geometry.VertexSemantic
import io.github.awakelab.awake.render.pipeline.BindingLayout
import io.github.awakelab.awake.render.pipeline.BindingSemantic

/**
 * 4-layer texture-splatted clipmap terrain shader.
 *
 * Samples a 16-bit packed heightmap in the vertex stage for displacement and morphing, and blends
 * up to 4 diffuse layers from a 2D texture array using an RGBA splat weightmap in the fragment stage.
 */
val TerrainSplatShader: AslShaderDefinition = shader("terrain_splat") {
    val group = BindingLayout.Standard.slot(BindingSemantic.Material)
    val u = uniformBlock("Uniforms", group = group, binding = 0)
    val handles = u.fieldsFrom(TerrainUniformLayout.Layout)
    val viewProjection = handles.value("viewProjection")
    val sunDirection = handles.value("sunDirection")
    val terrainParams = handles.value("terrainParams")
    val terrainSampling = handles.value("terrainSampling")
    val ringParams = handles.array("ringParams")

    val heightmap by texture2d(group = group, binding = 1)
    val heightmapSampler by sampler(group = group, binding = 2)
    val splatMap by texture2d(group = group, binding = 3)
    val splatSampler by sampler(group = group, binding = 4)
    val layers by texture2dArray(group = group, binding = 5)
    val layerSampler by sampler(group = group, binding = 6)

    val out = varyings("VertexOutput")
    val worldNormal by out.varying(GpuDataShape.Vec3, location = 0)
    val shade by out.varying(GpuDataShape.Vec3, location = 1)
    val splatUv by out.varying(GpuDataShape.Vec2, location = 2)
    val detailUv by out.varying(GpuDataShape.Vec2, location = 3)

    vertex {
        val ins = inputsFrom(VertexFormat.PositionNormalColorUv)
        val localPosition = ins.input(VertexSemantic.Position)
        val ringTag = ins.input(VertexSemantic.Color)

        val ring = let("ring", ringParams[toU32(ringTag.x)])
        val morphWidth = let("morphWidth", clamp(terrainParams.y, 0.05f.lit, 0.5f.lit))

        val dx = let("dx", abs(localPosition.x) / ring.w)
        val dz = let("dz", abs(localPosition.z) / ring.w)
        val edgeDistance = let("edgeDistance", max(dx, dz))
        val morphStart = let("morphStart", 1f.lit - morphWidth)
        val alpha = let(
            "alpha",
            clamp((edgeDistance - morphStart) / max(1f.lit - morphStart, 0.0001f.lit), 0f.lit, 1f.lit),
        )

        val coarseSpacing = let("coarseSpacing", ring.z * 2f.lit)
        val coarseX = let("coarseX", floor(localPosition.x / coarseSpacing + 0.5f.lit) * coarseSpacing)
        val coarseZ = let("coarseZ", floor(localPosition.z / coarseSpacing + 0.5f.lit) * coarseSpacing)
        val morphedX = let("morphedX", mix(localPosition.x, coarseX, alpha))
        val morphedZ = let("morphedZ", mix(localPosition.z, coarseZ, alpha))

        val worldX = let("worldX", morphedX + ring.x)
        val worldZ = let("worldZ", morphedZ + ring.y)
        val uv = let(
            "uv",
            vec2(worldX / terrainSampling.x + 0.5f.lit, worldZ / terrainSampling.y + 0.5f.lit),
        )

        val height = let(
            "height",
            decodeHeight(textureSampleLevel(heightmap, heightmapSampler, uv, 0f.lit)),
        )
        val worldPosition = let(
            "worldPosition",
            vec3(worldX, height * terrainParams.x + terrainParams.w, worldZ),
        )

        val stepU = let("stepU", terrainSampling.z)
        val stepV = let("stepV", terrainSampling.w)
        val heightLeft = let(
            "heightLeft",
            decodeHeight(textureSampleLevel(heightmap, heightmapSampler, vec2(uv.x - stepU, uv.y), 0f.lit)),
        )
        val heightRight = let(
            "heightRight",
            decodeHeight(textureSampleLevel(heightmap, heightmapSampler, vec2(uv.x + stepU, uv.y), 0f.lit)),
        )
        val heightDown = let(
            "heightDown",
            decodeHeight(textureSampleLevel(heightmap, heightmapSampler, vec2(uv.x, uv.y - stepV), 0f.lit)),
        )
        val heightUp = let(
            "heightUp",
            decodeHeight(textureSampleLevel(heightmap, heightmapSampler, vec2(uv.x, uv.y + stepV), 0f.lit)),
        )
        val spanX = let("spanX", 2f.lit * stepU * terrainSampling.x)
        val spanZ = let("spanZ", 2f.lit * stepV * terrainSampling.y)
        val slopeX = let("slopeX", (heightRight - heightLeft) * terrainParams.x / spanX)
        val slopeZ = let("slopeZ", (heightUp - heightDown) * terrainParams.x / spanZ)

        out.position set viewProjection * vec4(worldPosition, 1f.lit)
        worldNormal set normalize(vec3(-slopeX, 1f.lit, -slopeZ))
        shade set vec3(terrainParams.z, terrainParams.z, terrainParams.z)
        splatUv set uv
        detailUv set vec2(worldX * 0.25f.lit, worldZ * 0.25f.lit)
    }

    fragment {
        val normal = let("normal", normalize(worldNormal))
        val toLight = let("toLight", normalize(sunDirection.xyz))
        val ambient = let("ambient", sunDirection.w)
        val diffuse = let("diffuse", max(dot(normal, toLight), 0f.lit))
        val lighting = let("lighting", ambient + (1f.lit - ambient) * diffuse)

        val weights = let("weights", textureSample(splatMap, splatSampler, splatUv))
        val layer0 = let("layer0", textureSampleArray(layers, layerSampler, detailUv, 0.lit))
        val layer1 = let("layer1", textureSampleArray(layers, layerSampler, detailUv, 1.lit))
        val layer2 = let("layer2", textureSampleArray(layers, layerSampler, detailUv, 2.lit))
        val layer3 = let("layer3", textureSampleArray(layers, layerSampler, detailUv, 3.lit))

        val blendedDiffuse = let(
            "blendedDiffuse",
            layer0.xyz * weights.x +
                layer1.xyz * weights.y +
                layer2.xyz * weights.z +
                layer3.xyz * weights.w,
        )

        colorOutput(vec4(blendedDiffuse * shade * lighting, 1f.lit))
    }
}
