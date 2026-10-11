/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.build.scene

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A spec becomes a scene document class, a binding that copies every field by name, and a
 * reference table, and a spec the generator can't fully read is refused naming what it lacks.
 */
class SceneComponentCodegenTest {
    @Test
    fun aSpecBecomesADocumentAndABindingThatCopiesEveryField() {
        val (document, binding) = SceneComponentCodegen.generate(spec()).map { it.toString() }

        assertContains(document, "@Serializable")
        assertContains(document, "@SerialName(\"harbor_buoy\")")
        assertContains(document, "public data class SceneHarborBuoy(")
        assertContains(document, "@PropertyRange(\n    min = 0.0,\n    exclusiveMin = true,\n  )")
        assertContains(document, "public val height: Float = 1f,")
        assertContains(document, "public val tint: BuoyTint = BuoyTint.Red,")
        assertContains(document, "public val trail: List<SceneVec3>? = null,")
        assertContains(document, "override fun validate(path: String): List<SceneValidationIssue> = validateHarborBuoy(path)")
        assertContains(document, "import com.awakekt.awake.scene.document.SceneVec3")

        assertContains(binding, "public object HarborBuoyBinding : SceneComponentBinding<HarborBuoy, SceneHarborBuoy>")
        assertContains(binding, "override val serializer: KSerializer<SceneHarborBuoy> = SceneHarborBuoy.serializer()")
        assertContains(binding, "world.add(entity, component.toComponent())")
        listOf("height", "tint", "trail").forEach { name ->
            assertContains(binding, "it.$name = $name", message = "toComponent copies $name")
            assertContains(binding, "$name = $name,", message = "toSceneComponent copies $name")
        }
    }

    @Test
    fun aComponentOfConstructorParametersIsBuiltWithNamedArguments() {
        val binding = SceneComponentCodegen.generate(spec(construction = "constructor"))[1].toString()

        assertContains(binding, "public fun SceneHarborBuoy.toComponent(): HarborBuoy = HarborBuoy(\n    height = height,")
        assertFalse("it.height" in binding)
    }

    @Test
    fun aComponentWithNothingToCheckGetsNoValidate() {
        val document = SceneComponentCodegen.generate(spec(validate = null))[0].toString()

        assertFalse("validate" in document)
    }

    @Test
    fun theReferenceTableTellsTypesAndDefaultsItCanAndTakesTheRestFromTheSpec() {
        assertEquals(
            """
            | Field | Type | Default | What it does |
            | --- | --- | --- | --- |
            | `height` | number | `1` | How high it floats. Above 0. |
            | `tint` | `Red` · `Green` | `Red` | Its colour. |
            | `trail` | list | none | Where it drifted. |
            """.trimIndent() + "\n",
            SceneComponentDocs.table(spec()),
        )
    }

    @Test
    fun aFieldWhoseCellsCantBeToldIsRefusedNamingIt() {
        val noValues = spec().let { it.copy(fields = it.fields.map { field -> if (field.name == "tint") field.copy(docType = null) else field }) }

        val failure = assertFailsWith<IllegalArgumentException> { SceneComponentDocs.table(noValues) }

        assertContains(failure.message.orEmpty(), "field tint: give a docType")
    }

    @Test
    fun onlyTheComponentsOwnTableOnThePageIsReplaced() {
        val page = """
            # Components

            ## `harbor_buoy`

            A buoy. `SceneHarborBuoy`.

            | Field | Type | Default | What it does |
            | --- | --- | --- | --- |
            | `height` | number | `9` | Stale. |

            See the guide.

            ## `other`

            | Field | Type | Default | What it does |
            | --- | --- | --- | --- |
            | `keep` | number | `0` | Untouched. |
        """.trimIndent()

        val updated = SceneComponentDocs.replaceTable(page, spec())

        assertContains(updated, "| `height` | number | `1` | How high it floats. Above 0. |")
        assertFalse("Stale." in updated)
        assertContains(updated, "A buoy. `SceneHarborBuoy`.\n\n| Field |")
        assertContains(updated, "| `trail` | list | none | Where it drifted. |\n\nSee the guide.")
        assertContains(updated, "| `keep` | number | `0` | Untouched. |")
        assertEquals(updated, SceneComponentDocs.replaceTable(updated, spec()), "a second pass changes nothing")
    }

    @Test
    fun aPageWithNoSectionForTheComponentIsRefused() {
        val failure = assertFailsWith<IllegalArgumentException> { SceneComponentDocs.replaceTable("# Components\n", spec()) }

        assertContains(failure.message.orEmpty(), "no ## `harbor_buoy` section")
    }

    @Test
    fun aSpecMissingWhatItNeedsIsRefusedNamingTheFileAndField() {
        val noDefault = SPEC.replace(""""default": "1f",""", "")

        val failure = assertFailsWith<IllegalArgumentException> { SceneComponentSpec.parse(noDefault, "scene-components/harbor_buoy.json") }

        assertContains(failure.message.orEmpty(), "scene-components/harbor_buoy.json, field height: \"default\"")
    }

    @Test
    fun typesResolveAgainstImportsThenTheSpecsPackage() {
        val spec = spec()

        assertEquals("kotlin.collections.List<com.awakekt.awake.scene.document.SceneVec3>?", SceneComponentCodegen.typeOf("List<SceneVec3>?", spec).toString())
        assertEquals("com.example.harbor.BuoyTint", SceneComponentCodegen.typeOf("BuoyTint", spec).toString())
        assertEquals("kotlin.collections.Map<kotlin.String, kotlin.collections.List<kotlin.Int>>", SceneComponentCodegen.typeOf("Map<String, List<Int>>", spec).toString())
        assertTrue(SceneComponentCodegen.typeOf("Float?", spec).isNullable)
    }

    private fun spec(construction: String = "properties", validate: String? = "validateHarborBuoy"): SceneComponentSpec =
        SceneComponentSpec.parse(
            SPEC.replace("\"properties\"", "\"$construction\"").let {
                if (validate == null) it.replace(""""validate": "validateHarborBuoy",""", "") else it
            },
            "scene-components/harbor_buoy.json",
        )

    private companion object {
        val SPEC = """
            {
              "package": "com.example.harbor",
              "serialName": "harbor_buoy",
              "sceneClass": "SceneHarborBuoy",
              "component": "HarborBuoy",
              "binding": "HarborBuoyBinding",
              "construction": "properties",
              "validate": "validateHarborBuoy",
              "imports": ["com.awakekt.awake.scene.document.SceneVec3"],
              "fields": [
                { "name": "height", "type": "Float", "default": "1f", "range": { "min": 0.0, "exclusiveMin": true }, "doc": "How high it floats. Above 0." },
                { "name": "tint", "type": "BuoyTint", "default": "BuoyTint.Red", "docType": "`Red` · `Green`", "docDefault": "`Red`", "doc": "Its colour." },
                { "name": "trail", "type": "List<SceneVec3>?", "default": "null", "doc": "Where it drifted." }
              ]
            }
        """.trimIndent()
    }
}
