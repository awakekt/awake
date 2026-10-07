/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaderdocument

import com.awakekt.awake.asset.shaderdocument.ShaderDocumentFixtures.overlay
import com.awakekt.awake.core.math.ClipSpace
import com.awakekt.awake.render.pipeline.ResourceKind
import com.awakekt.awake.render.pipeline.ShaderStage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * What a compiled document emits. WGSL validity itself is checked with naga in
 * `:awake:asset:shader-compiler` (`ShaderDocumentCompileTest`); these pin the properties that make the
 * output safe and stable whatever a document contains.
 */
class ShaderDocumentEmitTest {
    private fun wgsl(json: String, clipSpace: ClipSpace = ClipSpace.Vulkan) = ShaderDocuments.compile(json).emitWgsl(clipSpace)

    /** A file's strings never reach the source: a name in a document cannot inject WGSL or collide with the engine's. */
    @Test
    fun noNameFromTheDocumentReachesTheShader() {
        val json = """
            {"name":"zzDocumentName\n@fragment","surface":"overlay",
             "parameters":[{"name":"zzParameter","type":"float","default":[0.5]}],
             "textures":[{"name":"zzTexture"}],
             "fragment":{"statements":[
               {"statement":"let","name":"zzLocal","value":{"op":"sample","texture":"zzTexture","uv":{"op":"input","input":"screenUv"}}},
               {"statement":"for","counter":"zzCounter","from":0,"until":2,"body":[]}],
               "color":{"op":"mul","a":{"op":"local","name":"zzLocal"},"b":{"op":"param","name":"zzParameter"}}}}
        """
        val source = wgsl(json)
        assertFalse("zz" in source, source)
    }

    @Test
    fun aNegationIsAMultiplicationAndNeverAUnaryMinus() {
        val json = overlay(color = """{"op":"vec4","args":[{"op":"neg","value":{"op":"add","a":{"op":"input","input":"time"},"b":{"op":"const","value":[1]}}}]}""")
        val source = wgsl(json)
        assertTrue("* -1.0;" in source, source)
        assertFalse(Regex("""[=(,]\s*-[a-z(]""").containsMatchIn(source), "no unary minus before a name or a parenthesis: $source")
    }

    /** `a && b || c` is a WGSL error; every condition is a named value before a logical operator reads it. */
    @Test
    fun logicalOperatorsOnlyEverCombineTwoNames() {
        val time = """{"op":"input","input":"time"}"""
        val zero = """{"op":"const","value":[0]}"""
        val mixed = """{"op":"or","a":{"op":"and","a":{"op":"lt","a":$time,"b":$zero},"b":{"op":"gt","a":$time,"b":$zero}},"b":{"op":"not","value":{"op":"eq","a":$time,"b":$zero}}}"""
        val source = wgsl(overlay(statements = """{"statement":"discard_if","condition":$mixed}"""))
        val logical = source.lines().filter { "&&" in it || "||" in it }
        assertEquals(2, logical.size, source)
        logical.forEach { assertTrue(Regex("""let e_\d+ = e_\d+ (&&|\|\|) e_\d+;""").matches(it.trim()), it) }
    }

    /** 32 names before a loop: a bare counter would be `i32`, which WGSL reserves as a type name. */
    @Test
    fun noGeneratedNameIsAReservedWord() {
        val time = """{"op":"input","input":"time"}"""
        val lets = (1..32).joinToString(",") { """{"statement":"let","name":"l$it","value":{"op":"add","a":$time,"b":$time}}""" }
        val loop = """{"statement":"for","counter":"i","from":0,"until":2,"body":[]}"""
        val source = wgsl(overlay(statements = "$lets,$loop"))
        assertFalse(Regex("""(let|var) [iuf](8|16|32|64)\b""").containsMatchIn(source), source)
        assertTrue("for (var i_32 = 0" in source, source)
    }

    @Test
    fun valuesKnownBeforeTheShaderRunsAreWrittenAsLiterals() {
        val sum = """{"op":"add","a":{"op":"const","value":[1]},"b":{"op":"mul","a":{"op":"const","value":[2]},"b":{"op":"const","value":[3]}}}"""
        val source = wgsl(overlay(color = """{"op":"vec4","args":[$sum]}"""))
        assertTrue("vec4f(7.0, 7.0, 7.0, 7.0)" in source, source)
        assertFalse("2.0 * 3.0" in source, source)
    }

    /** A full-screen stage that reads no uniform would leave the block out, and WebGPU needs one in the group. */
    @Test
    fun theUniformBlockIsBoundEvenWhenTheDocumentReadsNothing() {
        val compiled = ShaderDocuments.compile(ShaderDocumentFixtures.TINT)
        val group = assertNotNull(compiled.shaders.vulkan.bindingsByGroup[0])
        assertEquals(ResourceKind.UniformBuffer, group.at(0)?.kind)
    }

    @Test
    fun texturesFollowTheSharedSamplerInDeclarationOrder() {
        val group = assertNotNull(ShaderDocuments.compile(ShaderDocumentFixtures.WATER).shaders.webGpu.bindingsByGroup[0])
        assertEquals(listOf(0, 1, 2, 3), group.entries.map { it.binding })
        assertEquals(ResourceKind.Sampler, group.at(1)?.kind)
        assertEquals(ResourceKind.SampledTexture, group.at(2)?.kind)
        assertEquals(setOf(ShaderStage.Vertex, ShaderStage.Fragment), group.at(0)?.stages, "a plane reads the block in both stages")
    }

    @Test
    fun aPlaneReadsPositionAndUvAndADisplacementMovesAlongY() {
        val source = wgsl(ShaderDocumentFixtures.WATER)
        assertTrue("@location(0) inPosition : vec3f" in source, source)
        assertTrue("@location(1) inUv : vec2f" in source, source)
        assertTrue(Regex("""let displaced = inPosition \+ vec3f\(0\.0, e_\d+, 0\.0\);""").containsMatchIn(source), source)
    }

    @Test
    fun theEmittedSourceDependsOnlyOnTheDocument() {
        assertEquals(wgsl(ShaderDocumentFixtures.SKY), wgsl(ShaderDocumentFixtures.SKY))
        assertEquals(wgsl(ShaderDocumentFixtures.WATER), ShaderDocuments.compile(ShaderDocumentFixtures.WATER).emitWgsl(ClipSpace.Vulkan))
    }

    /** The two clip spaces differ in which way a full-screen V runs, and only there. */
    @Test
    fun clipSpacesDifferOnlyInScreenCoordinates() {
        val vulkan = wgsl(ShaderDocumentFixtures.SKY, ClipSpace.Vulkan)
        val webGpu = wgsl(ShaderDocumentFixtures.SKY, ClipSpace.WebGpu)
        assertEquals(vulkan, webGpu, "the sky reads the view direction, not the screen, so both clip spaces emit the same")
        val screen = overlay(color = """{"op":"vec4","args":[{"op":"input","input":"screenUv"},{"op":"const","value":[0,1]}]}""")
        assertTrue(wgsl(screen, ClipSpace.Vulkan) != wgsl(screen, ClipSpace.WebGpu))
    }
}
