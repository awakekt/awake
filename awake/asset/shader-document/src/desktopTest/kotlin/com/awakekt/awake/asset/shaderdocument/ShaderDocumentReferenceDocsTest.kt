/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaderdocument

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.elementDescriptors
import kotlinx.serialization.descriptors.elementNames
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The shader document reference page has a row for every name the document model writes: each key
 * of each object, surface, blend, value type, `op`, statement, input and function, and each limit.
 * The names come from the model's serializers, so a name added to the model without a row fails
 * here, and so does a row for a name the model dropped. Where a row states a number, such as an
 * input's component count or a function's argument count, it must match the model too.
 */
@OptIn(ExperimentalSerializationApi::class)
class ShaderDocumentReferenceDocsTest {
    private val page = File("../../../website/docs/reference/shader-document.md").readText().replace("\r\n", "\n")

    @Test
    fun everyKeyOfEveryObjectHasARow() {
        val objects = mapOf(
            "Document" to ShaderDocument.serializer(),
            "`plane`" to ShaderPlane.serializer(),
            "`parameters`" to ShaderParameter.serializer(),
            "`textures`" to ShaderTexture.serializer(),
            "`vertex`" to ShaderVertexStage.serializer(),
            "`fragment`" to ShaderFragmentStage.serializer(),
        )

        objects.forEach { (heading, serializer) ->
            assertEquals(serializer.names().sorted(), rows(heading).keys.sorted(), "keys under '$heading'")
        }
    }

    @Test
    fun everySurfaceBlendAndValueTypeHasARow() {
        assertEquals(ShaderSurface.serializer().names().sorted(), rows("Surfaces").keys.sorted(), "surfaces")
        assertEquals(ShaderBlend.serializer().names().sorted(), rows("Blends").keys.sorted(), "blends")
        assertEquals(ShaderValueType.serializer().names().sorted(), rows("Value types").keys.sorted(), "value types")
    }

    @Test
    fun aSurfacesRowNamesItsDefaultBlend() {
        val surfaces = ShaderSurface.serializer().names()
        val blends = ShaderBlend.serializer().names()

        ShaderSurface.entries.forEach { surface ->
            val row = rows("Surfaces").getValue(surfaces[surface.ordinal])
            assertEquals("`${blends[defaultBlend(surface).ordinal]}`", row[1], "default blend of '${surfaces[surface.ordinal]}'")
        }
    }

    @Test
    fun aValueTypesRowStatesHowManyNumbersItHolds() {
        val names = ShaderValueType.serializer().names()

        ShaderValueType.entries.forEach { type ->
            val row = rows("Value types").getValue(names[type.ordinal])
            assertEquals(type.shape.components.toString(), row[0], "numbers in '${names[type.ordinal]}'")
        }
    }

    @Test
    fun everyOpHasARowListingItsFields() {
        assertRowsMatchSubclasses(ShaderExpr.serializer(), rows("Expressions"), "op")
    }

    @Test
    fun everyStatementHasARowListingItsFields() {
        assertRowsMatchSubclasses(ShaderStatement.serializer(), rows("Statements"), "statement")
    }

    @Test
    fun everyInputHasARowStatingItsNumbers() {
        val names = ShaderInput.serializer().names()
        val rows = rows("Inputs")

        assertEquals(names.sorted(), rows.keys.sorted(), "inputs")
        ShaderInput.entries.forEach { input ->
            assertEquals(input.shape.components.toString(), rows.getValue(names[input.ordinal])[0], "numbers in '${names[input.ordinal]}'")
        }
    }

    @Test
    fun everyFunctionHasARowNamingAsManyArgumentsAsItTakes() {
        val names = ShaderFunction.serializer().names()
        val rows = rows("Functions")

        assertEquals(names.sorted(), rows.keys.sorted(), "functions")
        ShaderFunction.entries.forEach { function ->
            val arguments = codeTokens(rows.getValue(names[function.ordinal])[0])
            assertEquals(function.arity, arguments.size, "arguments of '${names[function.ordinal]}': $arguments")
            assertEquals(arguments.distinct(), arguments, "argument names of '${names[function.ordinal]}'")
        }
    }

    @Test
    fun everyLimitHasARowWithItsDefaultValue() {
        // A data class prints every property it has, so a limit added later is a row this test wants.
        val printed = ShaderDocumentLimits.Default.toString()
        val properties = printed.substringAfter("(").substringBeforeLast(")")
        val defaults = properties.split(", ").associate { it.substringBefore("=") to it.substringAfter("=") }
        val rows = rows("Limits")

        assertEquals(defaults.keys.sorted(), rows.keys.sorted(), "limits")
        defaults.forEach { (name, value) ->
            assertEquals(value, rows.getValue(name)[0].replace(",", ""), "default of '$name'")
        }
    }

    @Test
    fun theDocumentDefaultsOnThePageAreTheModelsAndThePageIsInTheNav() {
        val segments = ShaderPlane(size = listOf(1f, 1f)).segments

        assertEquals("`${ShaderDocument.CURRENT_FORMAT_VERSION}`", rows("Document").getValue("formatVersion")[1])
        assertEquals("`$segments`", rows("`plane`").getValue("segments")[1])
        assertTrue("reference/shader-document.md" in File("../../../website/mkdocs.yml").readText(), "the page is in the mkdocs nav")
    }

    /** The rows of the table under `## [heading]`: each row's first cell, without its backticks, to its other cells. */
    private fun rows(heading: String): Map<String, List<String>> {
        val section = page.substringAfter("\n## $heading\n", missingDelimiterValue = "").substringBefore("\n## ")
        assertTrue(section.isNotEmpty(), "the page has no '## $heading' section")
        val rows = section.lines().filter { it.startsWith("| `") }.map { line ->
            val cells = cellsOf(line)
            cells.first().removeSurrounding("`") to cells.drop(1)
        }
        assertEquals(rows.map { it.first }.distinct(), rows.map { it.first }, "a name has two rows under '$heading'")
        return rows.toMap()
    }

    private fun cellsOf(line: String): List<String> {
        val inner = line.trim().removePrefix("|").removeSuffix("|")
        return inner.split("|").map { it.trim() }
    }

    private fun assertRowsMatchSubclasses(serializer: KSerializer<*>, rows: Map<String, List<String>>, what: String) {
        // A sealed class's descriptor holds its subclasses in its second element, one element each.
        val subclassesElement = serializer.descriptor.getElementDescriptor(1)
        val subclasses = subclassesElement.elementDescriptors.toList()

        assertEquals(subclasses.map { it.serialName }.sorted(), rows.keys.sorted(), "${what}s")
        subclasses.forEach { subclass ->
            val listed = codeTokens(rows.getValue(subclass.serialName)[0])
            assertEquals(subclass.elementNames.toList().sorted(), listed.sorted(), "fields of $what '${subclass.serialName}'")
        }
    }

    /** What the descriptor says a document writes: the keys of an object, or the values of an enum. */
    private fun KSerializer<*>.names(): List<String> = descriptor.elementNames.toList()

    private fun codeTokens(cell: String): List<String> = Regex("`([^`]+)`").findAll(cell).map { it.groupValues[1] }.toList()
}
