/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.asset.shaderpack

import io.github.awakelab.awake.asset.shaderdsl.AslShaderDefinition
import io.github.awakelab.awake.core.geometry.GpuDataShape
import io.github.awakelab.awake.render.renderer.UniformField
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

/**
 * Asserts the WGSL `Uniforms` structs are what [LitShadowUniformLayout] describes, declaration
 * for declaration -- name, type and array length.
 *
 * `shadow_depth.wgsl` deliberately binds `lit_shadow.wgsl`'s buffer rather than owning a second
 * uniform scheme, so its struct must be a byte-identical prefix of that shader's. Nothing
 * enforced that. When point lights were added to `lit_shadow.wgsl`, `shadow_depth.wgsl` was not
 * updated, which moved `lightMvp` from float offset 56 to 24: the depth pre-pass transformed
 * every vertex by a matrix assembled out of light positions and colours and wrote an empty
 * shadow map. Nothing failed to compile and nothing failed to run -- shadows just stopped.
 *
 * A three-way agreement, so no single edit can drift unnoticed: the layout is the source of
 * truth for the buffer's size and write order, `lit_shadow.wgsl` must match it exactly, and
 * `shadow_depth.wgsl` must match its leading fields.
 *
 * The expected WGSL is DERIVED from the layout rather than written out here. A hand-written
 * copy would be one more declaration of the same struct, drifting the same way the others did.
 * That also makes the check total: an offset moves when a field is renamed, retyped, or
 * resized, and deriving from [UniformField]'s own `type`/`count` catches all three, where
 * comparing field names alone caught only the first.
 *
 * Since 2026-08-24 ASL's field list is DERIVED from [LitShadowUniformLayout] too
 * (`ShadowUniforms.kt`'s `fieldsFrom`), and the loop bound reads `MAX_POINT_LIGHTS` itself,
 * so the chain is closed at the source: layout -> ASL struct -> committed WGSL. This test is
 * now a regression witness over that chain rather than the only thing holding it together --
 * it stays because it is the one check reading the actual synced artifact off the classpath,
 * which would catch a broken record/sync step that every upstream link is blind to.
 *
 * Desktop-only: it reads the shaders off the test runtime classpath.
 */
class ShaderUniformStructTest {

    @Test
    fun litShadowsStructIsExactlyWhatTheKotlinLayoutDeclares() {
        assertEquals(
            expectedFields(),
            uniformStructFields(LitShadowShader),
            "lit_shadow.wgsl's Uniforms struct and LitShadowUniformLayout disagree. They size " +
                "and order the same buffer, so whichever one changed has to be matched in the " +
                "other -- and check shadow_depth.wgsl too, it binds this same buffer.",
        )
    }

    @Test
    fun shadowDepthsStructIsALeadingPrefixOfLitShadows() {
        val depth = uniformStructFields(ShadowDepthShader)
        assertEquals(
            expectedFields().take(depth.size),
            depth,
            "shadow_depth.wgsl's Uniforms struct is no longer a prefix of lit_shadow.wgsl's, so " +
                "every field past the first difference reads at the wrong offset from the " +
                "buffer it shares with that shader. The pass still compiles and still runs; the " +
                "shadow map just comes out empty.",
        )
    }

    /** [LitShadowUniformLayout] as the WGSL declarations a shader must spell out to match it. */
    private fun expectedFields(): List<String> =
        LitShadowUniformLayout.fields.map { "${it.name} : ${it.wgslType()}" }

    private fun UniformField.wgslType(): String {
        val element = when (type) {
            GpuDataShape.Mat4 -> "mat4x4<f32>"
            GpuDataShape.Vec4 -> "vec4f"
            // Deliberately not a full GpuDataShape mapping: only the shapes these shaders
            // actually use are spelled here, so a new one has to be named rather than guessed.
            else -> fail("No WGSL spelling for $type ($name) -- add one when a shader uses it.")
        }
        return if (count > 1) "array<$element, $count>" else element
    }

    /** Each `name : type` inside the shader's `Uniforms` struct, comments and spacing removed. */
    private fun uniformStructFields(shader: AslShaderDefinition): List<String> {
        return shader.emitWgsl()
            .substringAfter("struct Uniforms {")
            .substringBefore("}")
            .lineSequence()
            .map { it.substringBefore("//").trim().removeSuffix(",") }
            .filter { it.contains(':') }
            .map { "${it.substringBefore(':').trim()} : ${it.substringAfter(':').trim()}" }
            .toList()
    }
}
