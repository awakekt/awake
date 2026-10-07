/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaderdocument

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ShaderDocumentsDecodeTest {
    @Test
    fun aDocumentSurvivesARoundTrip() {
        listOf(ShaderDocumentFixtures.SKY, ShaderDocumentFixtures.TINT, ShaderDocumentFixtures.WATER).forEach { json ->
            val document = ShaderDocuments.decode(json)
            assertEquals(document, ShaderDocuments.decode(ShaderDocuments.encode(document)))
        }
    }

    @Test
    fun theNodesDecodeToTheirTypes() {
        val color = ShaderDocuments.decode(ShaderDocumentFixtures.SKY).fragment.color as ShaderExpr.Call
        assertEquals(ShaderFunction.Mix, color.fn)
        assertEquals(ShaderExpr.Param("bottom"), color.args[0])
        val water = ShaderDocuments.decode(ShaderDocumentFixtures.WATER)
        assertTrue(water.fragment.statements[1] is ShaderStatement.For)
        assertEquals(ShaderSurface.Plane, water.surface)
        assertEquals(ShaderBlend.Alpha, water.blend)
    }

    /** A misspelt key would otherwise be a setting silently ignored. */
    @Test
    fun anUnknownKeyIsAnError() {
        val error = assertFailsWith<ShaderDocumentException> {
            ShaderDocuments.decode("""{"name":"x","surface":"overlay","blend":"alpha","colour":1,"fragment":{"color":{"op":"const","value":[1,1,1,1]}}}""")
        }
        assertTrue("colour" in error.message.orEmpty(), error.message)
    }

    /** Functions, storage and raw source are not in the format, so a document cannot ask for them. */
    @Test
    fun anOperationOutsideTheFormatIsAnError() {
        listOf("""{"op":"wgsl","source":"loop {}"}""", """{"op":"storage","name":"buffer"}""").forEach { node ->
            assertFailsWith<ShaderDocumentException> { ShaderDocuments.decode(ShaderDocumentFixtures.overlay(color = node)) }
        }
    }

    @Test
    fun aDocumentOverTheSizeLimitIsRefusedBeforeParsing() {
        val error = assertFailsWith<ShaderDocumentException> { ShaderDocuments.decode(ShaderDocumentFixtures.WATER, ShaderDocumentLimits(maxBytes = 100)) }
        assertTrue(error.issues.single().message.contains("the limit is 100"), error.issues.toString())
    }

    /** A parser recurses once per level; a deep enough file would exhaust the stack before any check ran. */
    @Test
    fun deepNestingIsRefusedBeforeParsing() {
        val deep = "[".repeat(100_000) + "]".repeat(100_000)
        val error = assertFailsWith<ShaderDocumentException> { ShaderDocuments.decode(deep) }
        assertTrue(error.issues.any { it.message.contains("nests 100000 deep") }, error.issues.toString())
    }

    @Test
    fun bracketsInsideStringsDoNotCountAsNesting() {
        assertEquals(1, jsonNesting("""{"name":"[[[{{{ \" ]]]"}"""))
        assertEquals(3, jsonNesting("""{"a":[{"b":1}]}"""))
    }

    @Test
    fun aValueTooLargeForAFloatIsRejected() {
        val issues = ShaderDocumentFixtures.issuesOf(ShaderDocumentFixtures.overlay(color = """{"op":"vec4","args":[{"op":"const","value":[1e40]}]}"""))
        assertTrue(issues.isNotEmpty())
    }

    @Test
    fun theBlendDefaultsToTheSurfaces() {
        assertEquals(ShaderBlend.Opaque, ShaderDocuments.compile(ShaderDocumentFixtures.SKY).blend)
        assertEquals(ShaderBlend.Alpha, ShaderDocuments.compile(ShaderDocumentFixtures.TINT).blend)
    }
}
