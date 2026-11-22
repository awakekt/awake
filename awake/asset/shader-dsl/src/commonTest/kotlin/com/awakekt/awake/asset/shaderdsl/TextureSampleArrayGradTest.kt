/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaderdsl

import com.awakekt.awake.core.geometry.GpuDataShape
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TextureSampleArrayGradTest {

    @Test
    fun emitsSixArgumentWgslTextureSampleGradForTextureArray() {
        val def = shader("array_grad_test") {
            val layers by texture2dArray(group = 0, binding = 0)
            val linear by sampler(group = 0, binding = 1)
            val out = varyings("Out")
            val uv by out.varying(GpuDataShape.Vec2, location = 0)

            vertex {
                val corner = fullScreenTriangleCorner()
                uv set corner
                out.position set vec4(corner, 0f.lit, 1f.lit)
            }

            fragment {
                val ddx = let("ddx", dpdx(uv))
                val ddy = let("ddy", dpdy(uv))
                colorOutput(textureSampleArrayGrad(layers, linear, uv, 0u.lit, ddx, ddy))
            }
        }

        val wgsl = def.emitWgsl()
        assertTrue(wgsl.contains("textureSampleGrad(layers, linear, uv, 0u, ddx, ddy)"), wgsl)
    }

    @Test
    fun rejectsNonArrayTextureOrFloatLayer() {
        assertFailsWith<AslDefinitionException> {
            shader("bad_array_grad_tex") {
                val flat by texture2d(group = 0, binding = 0)
                val linear by sampler(group = 0, binding = 1)
                val out = varyings("Out")
                val uv by out.varying(GpuDataShape.Vec2, location = 0)
                vertex {
                    val corner = fullScreenTriangleCorner()
                    uv set corner
                    out.position set vec4(corner, 0f.lit, 1f.lit)
                }
                fragment {
                    colorOutput(textureSampleArrayGrad(flat, linear, uv, 0u.lit, dpdx(uv), dpdy(uv)))
                }
            }
        }
        assertFailsWith<AslDefinitionException> {
            shader("bad_array_grad_layer") {
                val layers by texture2dArray(group = 0, binding = 0)
                val linear by sampler(group = 0, binding = 1)
                val out = varyings("Out")
                val uv by out.varying(GpuDataShape.Vec2, location = 0)
                vertex {
                    val corner = fullScreenTriangleCorner()
                    uv set corner
                    out.position set vec4(corner, 0f.lit, 1f.lit)
                }
                fragment {
                    colorOutput(textureSampleArrayGrad(layers, linear, uv, 0f.lit, dpdx(uv), dpdy(uv)))
                }
            }
        }
    }
}
