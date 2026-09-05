/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.asset.shaderpack

import io.github.awakelab.awake.asset.shaderdsl.AslShaderDefinition
import io.github.awakelab.awake.asset.shaderdsl.a
import io.github.awakelab.awake.asset.shaderdsl.abs
import io.github.awakelab.awake.asset.shaderdsl.div
import io.github.awakelab.awake.asset.shaderdsl.fieldsFrom
import io.github.awakelab.awake.asset.shaderdsl.fract
import io.github.awakelab.awake.asset.shaderdsl.fullScreenTriangleCorner
import io.github.awakelab.awake.asset.shaderdsl.fwidth
import io.github.awakelab.awake.asset.shaderdsl.length
import io.github.awakelab.awake.asset.shaderdsl.lit
import io.github.awakelab.awake.asset.shaderdsl.max
import io.github.awakelab.awake.asset.shaderdsl.minus
import io.github.awakelab.awake.asset.shaderdsl.mix
import io.github.awakelab.awake.asset.shaderdsl.plus
import io.github.awakelab.awake.asset.shaderdsl.rgb
import io.github.awakelab.awake.asset.shaderdsl.saturate
import io.github.awakelab.awake.asset.shaderdsl.shader
import io.github.awakelab.awake.asset.shaderdsl.smoothstep
import io.github.awakelab.awake.asset.shaderdsl.step
import io.github.awakelab.awake.asset.shaderdsl.times
import io.github.awakelab.awake.asset.shaderdsl.unaryMinus
import io.github.awakelab.awake.asset.shaderdsl.vec2
import io.github.awakelab.awake.asset.shaderdsl.vec4
import io.github.awakelab.awake.asset.shaderdsl.w
import io.github.awakelab.awake.asset.shaderdsl.x
import io.github.awakelab.awake.asset.shaderdsl.xyz
import io.github.awakelab.awake.asset.shaderdsl.y
import io.github.awakelab.awake.asset.shaderdsl.z
import io.github.awakelab.awake.core.geometry.GpuDataShape
import io.github.awakelab.awake.render.pipeline.BindingLayout
import io.github.awakelab.awake.render.pipeline.BindingSemantic
import io.github.awakelab.awake.render.renderer.InfiniteGridUniformLayout

/**
 * Procedural infinite grid rendered over an unprojected full-screen triangle.
 *
 * Calculates ground plane intersections (Y = 0), primary & secondary anti-aliased
 * grid lines via screen derivatives (fwidth), infinite X (red) and Z (blue) axis lines,
 * and distance-based horizon fog fading.
 */
val InfiniteGridShader: AslShaderDefinition = shader("infinite_grid") {
    val u = uniformBlock(
        "Uniforms",
        group = BindingLayout.Standard.slot(BindingSemantic.Material),
        binding = 0,
    )
    val handles = u.fieldsFrom(InfiniteGridUniformLayout)
    val inverseViewProjection = handles.value("inverseViewProjection")
    val cameraEye = handles.value("cameraEye")
    val gridParams = handles.value("gridParams")
    val gridColor = handles.value("gridColor")
    val subGridColor = handles.value("subGridColor")
    val axisColorX = handles.value("axisColorX")
    val axisColorZ = handles.value("axisColorZ")

    val out = varyings("VertexOutput")
    val ndc by out.varying(GpuDataShape.Vec2, location = 0)

    vertex {
        val corner = fullScreenTriangleCorner()
        out.position set vec4(corner, 1f.lit, 1f.lit)
        ndc set corner
    }

    fragment {
        val far = let("far", inverseViewProjection * vec4(ndc, 1f.lit, 1f.lit))
        val farWorld = let("farWorld", far.xyz / far.w)
        val rayDir = let("rayDir", farWorld - cameraEye.xyz)

        // Ground plane intersection at y = 0
        // cameraEye.y + t * rayDir.y = 0 => t = -cameraEye.y / rayDir.y
        val t = let("t", -cameraEye.y / rayDir.y)
        val hit = let("hit", step(0.001f.lit, t))
        val pos = let("pos", cameraEye.xyz + rayDir * t)
        val coord = let("coord", vec2(pos.x, pos.z))

        // Distance fog / horizon fade
        val dist = let("dist", length(pos - cameraEye.xyz))
        val fadeDistance = let("fadeDistance", gridParams.z)
        val fade = let("fade", saturate(1f.lit - dist / fadeDistance))

        // Primary grid (step = gridParams.x)
        val pCoord = let("pCoord", coord / gridParams.x)
        val pGrid = let("pGrid", abs(fract(pCoord - 0.5f.lit) - 0.5f.lit))
        val pDeriv = let("pDeriv", fwidth(pCoord))
        val pLine = let("pLine", smoothstep(pDeriv * 1.5f.lit, pDeriv * 0.5f.lit, pGrid))
        val pAlpha = let("pAlpha", max(pLine.x, pLine.y))

        // Sub grid (step = gridParams.y)
        val sCoord = let("sCoord", coord / gridParams.y)
        val sGrid = let("sGrid", abs(fract(sCoord - 0.5f.lit) - 0.5f.lit))
        val sDeriv = let("sDeriv", fwidth(sCoord))
        val sLine = let("sLine", smoothstep(sDeriv * 1.5f.lit, sDeriv * 0.5f.lit, sGrid))
        val sAlpha = let("sAlpha", max(sLine.x, sLine.y))

        // X axis line (z == 0)
        val distZ = let("distZ", abs(pos.z))
        val derivZ = let("derivZ", fwidth(pos.z))
        val axisX = let("axisX", smoothstep(derivZ * 2.0f.lit, derivZ * 0.5f.lit, distZ))

        // Z axis line (x == 0)
        val distX = let("distX", abs(pos.x))
        val derivX = let("derivX", fwidth(pos.x))
        val axisZ = let("axisZ", smoothstep(derivX * 2.0f.lit, derivX * 0.5f.lit, distX))

        // Composite grid color: subgrid -> primary grid -> axis lines
        val baseColor = let("baseColor", subGridColor.rgb)
        val baseAlpha = let("baseAlpha", sAlpha * subGridColor.a)

        val withPrimary = let("withPrimary", mix(baseColor, gridColor.rgb, pAlpha))
        val withPrimaryAlpha = let("withPrimaryAlpha", max(baseAlpha, pAlpha * gridColor.a))

        val withAxisZ = let("withAxisZ", mix(withPrimary, axisColorZ.rgb, axisZ))
        val withAxisZAlpha = let("withAxisZAlpha", max(withPrimaryAlpha, axisZ * axisColorZ.a))

        val finalRgb = let("finalRgb", mix(withAxisZ, axisColorX.rgb, axisX))
        val finalAxisAlpha = let("finalAxisAlpha", max(withAxisZAlpha, axisX * axisColorX.a))

        val totalAlpha = let("totalAlpha", finalAxisAlpha * fade * hit)
        colorOutput(vec4(finalRgb, totalAlpha))
    }
}
