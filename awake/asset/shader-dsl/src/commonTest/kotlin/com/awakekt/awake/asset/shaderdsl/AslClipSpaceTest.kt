/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaderdsl

import com.awakekt.awake.core.geometry.GpuDataShape
import com.awakekt.awake.core.math.ClipSpace
import kotlin.test.Test
import kotlin.test.assertTrue

class AslClipSpaceTest {

    @Test
    fun ndcToUvFlipsYOnlyForVulkan() {
        val ndc = AslRef("ndc", GpuDataShape.Vec2)
        val vulkanUv = ndcToUv(ndc, ClipSpace.Vulkan)
        val webGpuUv = ndcToUv(ndc, ClipSpace.WebGpu)

        val vulkanWgsl = render(vulkanUv, AslStage.Fragment)
        val webGpuWgsl = render(webGpuUv, AslStage.Fragment)

        assertTrue(vulkanWgsl.contains("(ndc.y + 1.0) * 0.5"))
        assertTrue(webGpuWgsl.contains("(1.0 - ndc.y) * 0.5"))
    }

    @Test
    fun unprojectFarRayEmitsPerspectiveDivideAndEyeSubtraction() {
        val s = shader("test_unproject") {
            val u = uniformBlock("Uniforms", group = 0, binding = 0)
            val ivp by u.field(GpuDataShape.Mat4)
            val eye by u.field(GpuDataShape.Vec4)

            val varyings = varyings("Varyings")
            val ndc by varyings.varying(GpuDataShape.Vec2, location = 0)

            vertex {
                val corner = fullScreenTriangleCorner()
                ndc set corner
                varyings.position set vec4(corner, 1f.lit, 1f.lit)
            }

            fragment {
                val ray = let("rayDir", unprojectFarRay(ivp, eye, ndc))
                colorOutput(vec4(ray, 1f.lit))
            }
        }

        val wgsl = s.emitWgsl()
        assertTrue(wgsl.contains("vec4f(ndc, 1.0, 1.0)"))
        assertTrue(wgsl.contains("normalize"))
        assertTrue(wgsl.contains(".w"))
    }

    @Test
    fun unprojectClipToWorldEmitsPerspectiveDivide() {
        val s = shader("test_clip_to_world") {
            val u = uniformBlock("Uniforms", group = 0, binding = 0)
            val ivp by u.field(GpuDataShape.Mat4)

            val varyings = varyings("Varyings")
            val ndc by varyings.varying(GpuDataShape.Vec2, location = 0)

            vertex {
                val corner = fullScreenTriangleCorner()
                ndc set corner
                varyings.position set vec4(corner, 1f.lit, 1f.lit)
            }

            fragment {
                val worldPos = let("worldPos", unprojectClipToWorld(ivp, ndc, 1f.lit))
                colorOutput(vec4(worldPos, 1f.lit))
            }
        }

        val wgsl = s.emitWgsl()
        assertTrue(wgsl.contains("vec4f(ndc, 1.0, 1.0)"))
        assertTrue(wgsl.contains(".w"))
    }
}
