/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime

import com.awakekt.awake.scene.document.JsonSchemaValidator
import com.awakekt.awake.scene.document.SceneComponentCatalog
import com.awakekt.awake.scene.document.SceneDocumentJsonSchema
import com.awakekt.awake.scene.document.SceneLoader
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SceneDocumentJsonSchemaTest {
    private val json = Json { ignoreUnknownKeys = false }

    @Test
    fun exportsStandardJsonSchemaDraft202012WithDeterministicOrderingAndVersion() {
        installEveryComponentKit()

        val schema = SceneDocumentJsonSchema.generate(SceneComponentCatalog)

        assertEquals("https://json-schema.org/draft/2020-12/schema", schema["\$schema"]?.let { (it as? JsonPrimitive)?.content })
        assertEquals(SceneDocumentJsonSchema.SCHEMA_ID, schema["\$id"]?.let { (it as? JsonPrimitive)?.content })
        assertEquals("AwakeSceneDocument", schema["title"]?.let { (it as? JsonPrimitive)?.content })

        // Check version const = 1
        val props = assertNotNull(schema["properties"])
        val versionProp = assertNotNull(props.let { it as kotlinx.serialization.json.JsonObject }["version"]?.let { it as kotlinx.serialization.json.JsonObject })
        assertEquals(1, versionProp["const"]?.let { (it as? JsonPrimitive)?.content?.toInt() })

        // Check determinism: re-exporting produces identical string
        val export1 = SceneDocumentJsonSchema.generateString(SceneComponentCatalog)
        val export2 = SceneDocumentJsonSchema.generateString(SceneComponentCatalog)
        assertEquals(export1, export2, "JSON schema export must be completely deterministic")
    }

    @Test
    fun customComponentIsAnOpenPayloadRatherThanDropped() {
        installEveryComponentKit()

        val schema = SceneDocumentJsonSchema.generate(SceneComponentCatalog)
        val defs = assertNotNull(schema["\$defs"]?.let { it as kotlinx.serialization.json.JsonObject })
        val customDef = assertNotNull(defs["component_custom"]?.let { it as kotlinx.serialization.json.JsonObject })

        val customProps = assertNotNull(customDef["properties"]?.let { it as kotlinx.serialization.json.JsonObject })
        assertEquals("custom", customProps["component"]?.let { it as kotlinx.serialization.json.JsonObject }["const"]?.let { (it as? JsonPrimitive)?.content })
        assertTrue("payload" in customProps)
        assertTrue("type" in customProps)

        val validator = JsonSchemaValidator(schema)
        val docWithCustom = json.parseToJsonElement(
            """
            {
                "version": 1,
                "nodes": [
                    {
                        "name": "customNode",
                        "components": [
                            {
                                "component": "custom",
                                "type": "my_extension_type",
                                "payload": { "arbitraryKey": [1, 2, "three"], "nested": { "foo": "bar" } }
                            }
                        ]
                    }
                ]
            }
            """.trimIndent()
        )
        val errors = validator.validate(docWithCustom)
        assertTrue(errors.isEmpty(), "Custom component with open payload should validate: $errors")
    }

    @Test
    fun validatesEveryExampleSceneInRepository() {
        installEveryComponentKit()

        val schema = SceneDocumentJsonSchema.generate(SceneComponentCatalog)
        val validator = JsonSchemaValidator(schema)

        // Find root git directory
        var rootDir = File(".").canonicalFile
        while (rootDir.parentFile != null && !File(rootDir, "settings.gradle.kts").exists()) {
            rootDir = rootDir.parentFile
        }

        val sceneFiles = rootDir.walkTopDown()
            .filter { it.isFile && it.name.endsWith(".scene.json") }
            .filter { !it.path.contains(".gradle") && !it.path.contains("build") && !it.path.contains(".claude") }
            .toList()

        assertTrue(sceneFiles.isNotEmpty(), "Found scene files in repository: ${sceneFiles.size}")

        for (sceneFile in sceneFiles) {
            val content = sceneFile.readText()
            val jsonDoc = json.parseToJsonElement(content)
            val issues = validator.validate(jsonDoc)
            assertTrue(
                issues.isEmpty(),
                "Scene file ${sceneFile.relativeTo(rootDir).path} failed JSON Schema validation: ${issues.joinToString { "${it.path}: ${it.message}" }}",
            )
        }
    }

    @Test
    fun negativeControlRejectsUnknownField() {
        installEveryComponentKit()

        val schema = SceneDocumentJsonSchema.generate(SceneComponentCatalog)
        val validator = JsonSchemaValidator(schema)

        val docWithUnknownField = json.parseToJsonElement(
            """
            {
                "version": 1,
                "unknownTopLevelField": 123,
                "nodes": []
            }
            """.trimIndent()
        )
        val errors = validator.validate(docWithUnknownField)
        assertFalse(errors.isEmpty(), "Unknown field should be rejected by schema")
        assertTrue(errors.any { it.message.contains("Unknown property 'unknownTopLevelField'") }, "Error mentions unknown field: $errors")
    }

    @Test
    fun negativeControlRejectsWrongDataType() {
        installEveryComponentKit()

        val schema = SceneDocumentJsonSchema.generate(SceneComponentCatalog)
        val validator = JsonSchemaValidator(schema)

        val docWithWrongType = json.parseToJsonElement(
            """
            {
                "version": "not-an-integer",
                "nodes": []
            }
            """.trimIndent()
        )
        val errors = validator.validate(docWithWrongType)
        assertFalse(errors.isEmpty(), "Wrong type should be rejected by schema")
        assertTrue(errors.any { it.message.contains("Expected integer") }, "Error mentions expected type: $errors")
    }

    @Test
    fun negativeControlRejectsOutOfRangeValue() {
        installEveryComponentKit()

        val schema = SceneDocumentJsonSchema.generate(SceneComponentCatalog)
        val validator = JsonSchemaValidator(schema)

        // Color channel r must be between 0.0 and 1.0 (minimum: 0.0, maximum: 1.0)
        val docWithOutOfRange = json.parseToJsonElement(
            """
            {
                "version": 1,
                "nodes": [
                    {
                        "name": "lightNode",
                        "components": [
                            {
                                "component": "ambient_light",
                                "color": { "r": 2.5, "g": 0.5, "b": 0.5, "a": 1.0 }
                            }
                        ]
                    }
                ]
            }
            """.trimIndent()
        )
        val errors = validator.validate(docWithOutOfRange)
        assertFalse(errors.isEmpty(), "Out-of-range value should be rejected by schema")
        assertTrue(errors.any { it.message.contains("greater than maximum 1.0") }, "Error mentions range violation: $errors")
    }
}
