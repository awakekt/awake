/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaderdsl

import com.awakekt.awake.core.geometry.GpuDataShape
import com.awakekt.awake.render.pipeline.ResourceKind
import com.awakekt.awake.render.pipeline.ShaderStage
import com.awakekt.awake.render.pipeline.TextureSampleType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CubemapShaderDslTest {

    @Test
    fun cubemapShaderEmitsExpectedWgslAndGroupBindings() {
        val cubemapShader = shader("skybox_cubemap_test") {
            val envMap by textureCube(group = 0, binding = 1)
            val envSampler by sampler(group = 0, binding = 2)

            val varyings = varyings("Varyings")
            val rayDir by varyings.varying(GpuDataShape.Vec3, location = 0)

            vertex {
                val corner = fullScreenTriangleCorner()
                rayDir set vec3(corner, 1f.lit)
                varyings.position set vec4(corner, 1f.lit, 1f.lit)
            }

            fragment {
                val color = textureSampleCube(envMap, envSampler, rayDir)
                colorOutput(color)
            }
        }

        val wgsl = cubemapShader.emitWgsl()
        assertTrue(wgsl.contains("var envMap : texture_cube<f32>;"))
        assertTrue(wgsl.contains("var envSampler : sampler;"))
        assertTrue(wgsl.contains("textureSample(envMap, envSampler, rayDir)"))

        val group0 = cubemapShader.bindingsForGroup(0)
        assertNotNull(group0)
        val textureBinding = group0.at(1)
        assertNotNull(textureBinding)
        assertEquals(ResourceKind.SampledTexture, textureBinding.kind)
        assertTrue(textureBinding.cubemap)
        assertEquals(false, textureBinding.arrayed)
        assertEquals(TextureSampleType.Float, textureBinding.textureSampleType)
        assertEquals(setOf(ShaderStage.Fragment), textureBinding.stages)
    }

    @Test
    fun textureSampleCubeRejectsWrongTextureType() {
        assertFailsWith<AslDefinitionException> {
            shader("bad_cubemap") {
                val texture2d by texture2d(group = 0, binding = 1)
                val samp by sampler(group = 0, binding = 2)
                vertex {
                    val corner = fullScreenTriangleCorner()
                    returnPosition(vec4(corner, 0f.lit, 1f.lit))
                }
                fragment {
                    textureSampleCube(texture2d, samp, vec3(1f.lit))
                }
            }
        }
    }

    @Test
    fun textureSampleCubeRejectsNonVec3Direction() {
        assertFailsWith<AslDefinitionException> {
            shader("bad_cubemap_dir") {
                val cube by textureCube(group = 0, binding = 1)
                val samp by sampler(group = 0, binding = 2)
                vertex {
                    val corner = fullScreenTriangleCorner()
                    returnPosition(vec4(corner, 0f.lit, 1f.lit))
                }
                fragment {
                    textureSampleCube(cube, samp, vec2(1f.lit))
                }
            }
        }
    }
}
