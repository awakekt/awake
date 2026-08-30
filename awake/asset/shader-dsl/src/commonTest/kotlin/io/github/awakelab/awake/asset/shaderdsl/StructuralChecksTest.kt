/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.asset.shaderdsl

import io.github.awakelab.awake.core.geometry.GpuDataShape
import io.github.awakelab.awake.core.geometry.VertexFormat
import io.github.awakelab.awake.core.geometry.VertexSemantic
import kotlin.test.Test
import kotlin.test.assertFailsWith

/** Every structural mistake ASL promises to catch before naga, one test each. */
class StructuralChecksTest {

    @Test
    fun duplicateVaryingLocationThrows() {
        assertFailsWith<AslDefinitionException> {
            shader("bad") {
                val out = varyings("V")
                val a by out.varying(GpuDataShape.Vec3, location = 0)
                val b by out.varying(GpuDataShape.Vec2, location = 0)
                requireNotNull(a to b)
            }
        }
    }

    @Test
    fun duplicateInputLocationThrows() {
        assertFailsWith<AslDefinitionException> {
            shader("bad") {
                varyings("V")
                vertex {
                    val a by input(GpuDataShape.Vec3, location = 0)
                    val b by input(GpuDataShape.Vec2, location = 0)
                    requireNotNull(a to b)
                }
            }
        }
    }

    @Test
    fun duplicateUniformBindingThrows() {
        assertFailsWith<AslDefinitionException> {
            shader("bad") {
                uniformBlock("A", group = 0, binding = 0)
                uniformBlock("B", group = 0, binding = 0)
            }
        }
    }

    @Test
    fun fragmentReadingUnwrittenVaryingThrows() {
        assertFailsWith<AslDefinitionException> {
            shader("bad") {
                val out = varyings("V")
                val color by out.varying(GpuDataShape.Vec4, location = 0)
                vertex {
                    val inPosition by input(GpuDataShape.Vec3, location = 0)
                    out.position set vec4(inPosition, 1f.lit)
                }
                fragment { colorOutput(color) }
            }
        }
    }

    @Test
    fun vertexNeverSettingPositionThrows() {
        assertFailsWith<AslDefinitionException> {
            shader("bad") {
                val out = varyings("V")
                val color by out.varying(GpuDataShape.Vec4, location = 0)
                vertex {
                    val inColor by input(GpuDataShape.Vec4, location = 0)
                    color set inColor
                }
                fragment { colorOutput(color) }
            }
        }
    }

    @Test
    fun constructArityMismatchThrows() {
        assertFailsWith<AslDefinitionException> {
            vec4(vec2(1f.lit, 2f.lit), 3f.lit)
        }
    }

    @Test
    fun mismatchedBinaryShapesThrow() {
        assertFailsWith<AslDefinitionException> {
            vec3(1f.lit) + vec2(1f.lit, 2f.lit)
        }
    }

    @Test
    fun swizzleBeyondComponentCountThrows() {
        assertFailsWith<AslDefinitionException> {
            vec2(1f.lit, 2f.lit).xyz
        }
    }

    @Test
    fun noVaryingsWithoutReturnPositionThrows() {
        assertFailsWith<AslDefinitionException> {
            shader("bad") {
                vertex {
                    val inPosition by input(GpuDataShape.Vec3, location = 0)
                    let("unused", vec4(inPosition, 1f.lit))
                }
                fragment { }
            }
        }
    }

    @Test
    fun functionCallArgumentTypeMismatchThrows() {
        assertFailsWith<AslDefinitionException> {
            shader("bad") {
                val helper = fn("helper") {
                    val value by param(F32)
                    returnValue(value)
                }
                vertex {
                    val inPosition by input(GpuDataShape.Vec3, location = 0)
                    returnPosition(vec4(inPosition, helper(vec3(1f.lit))))
                }
                fragment { }
            }
        }
    }

    @Test
    fun mixedIntFloatArithmeticThrows() {
        assertFailsWith<AslDefinitionException> {
            1.lit + 1f.lit
        }
    }

    @Test
    fun formatDrivenInputsRecordTheFormatOnTheDefinition() {
        kotlin.test.assertEquals(VertexFormat.PositionNormalColor, TriangleShader.vertexFormat)
    }

    @Test
    fun unusedConstThrows() {
        assertFailsWith<AslDefinitionException> {
            shader("bad") {
                const("NEVER_READ", 1f)
                vertex {
                    val inPosition by input(GpuDataShape.Vec3, location = 0)
                    returnPosition(vec4(inPosition, 1f.lit))
                }
                fragment { }
            }
        }
    }

    @Test
    fun varyingWrittenButNeverReadThrows() {
        assertFailsWith<AslDefinitionException> {
            shader("bad") {
                val out = varyings("V")
                val tint by out.varying(GpuDataShape.Vec3, location = 0)
                vertex {
                    val inPosition by input(GpuDataShape.Vec3, location = 0)
                    out.position set vec4(inPosition, 1f.lit)
                    tint set inPosition
                }
                fragment { colorOutput(vec4(1f.lit)) }
            }
        }
    }

    @Test
    fun unusedFunctionThrows() {
        assertFailsWith<AslDefinitionException> {
            shader("bad") {
                fn("orphan") {
                    val value by param(F32)
                    returnValue(value)
                }
                vertex {
                    val inPosition by input(GpuDataShape.Vec3, location = 0)
                    returnPosition(vec4(inPosition, 1f.lit))
                }
                fragment { }
            }
        }
    }

    @Test
    fun unusedTextureThrows() {
        assertFailsWith<AslDefinitionException> {
            shader("bad") {
                val orphanTexture by texture2d(group = 0, binding = 1)
                requireNotNull(orphanTexture)
                vertex {
                    val inPosition by input(GpuDataShape.Vec3, location = 0)
                    returnPosition(vec4(inPosition, 1f.lit))
                }
                fragment { }
            }
        }
    }

    @Test
    fun readingAnAttributeTheFormatLacksThrows() {
        assertFailsWith<AslDefinitionException> {
            shader("bad") {
                vertex {
                    val ins = inputsFrom(VertexFormat.PositionNormalColor)
                    returnPosition(vec4(ins.input(VertexSemantic.Uv), 1f.lit, 1f.lit))
                }
                fragment { }
            }
        }
    }
}
