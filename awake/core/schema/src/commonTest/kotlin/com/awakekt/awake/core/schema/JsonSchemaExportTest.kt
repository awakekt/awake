/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.schema

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@Serializable
private enum class TestEnum {
    Alpha,
    Beta,
}

@Serializable
private data class TestComponent(
    @PropertyRange(min = 0.0, max = 10.0)
    val speed: Float = 1.0f,
    @PropertyRange(min = 1.0, exclusiveMin = true)
    val count: Int = 2,
    val flag: Boolean = false,
    val name: String,
    val mode: TestEnum = TestEnum.Alpha,
    val tags: List<String> = emptyList(),
)

class JsonSchemaExportTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun exportsPropertySchemaToDraft202012CompatibleJson() {
        val schema = propertySchemaOf(TestComponent.serializer(), json)
        val jsonSchema = schema.toJsonSchema()

        assertEquals("object", jsonSchema["type"]?.jsonPrimitive?.content)
        val properties = assertNotNull(jsonSchema["properties"]?.jsonObject)

        // Speed: number with min and max
        val speedSchema = assertNotNull(properties["speed"]?.jsonObject)
        assertEquals("number", speedSchema["type"]?.jsonPrimitive?.content)
        assertEquals(0.0, speedSchema["minimum"]?.jsonPrimitive?.content?.toDouble())
        assertEquals(10.0, speedSchema["maximum"]?.jsonPrimitive?.content?.toDouble())
        assertEquals(JsonPrimitive(1.0f), speedSchema["default"])

        // Count: integer with exclusiveMinimum
        val countSchema = assertNotNull(properties["count"]?.jsonObject)
        assertEquals("integer", countSchema["type"]?.jsonPrimitive?.content)
        assertEquals(1.0, countSchema["exclusiveMinimum"]?.jsonPrimitive?.content?.toDouble())

        // Flag: boolean
        val flagSchema = assertNotNull(properties["flag"]?.jsonObject)
        assertEquals("boolean", flagSchema["type"]?.jsonPrimitive?.content)

        // Name: string, required
        val nameSchema = assertNotNull(properties["name"]?.jsonObject)
        assertEquals("string", nameSchema["type"]?.jsonPrimitive?.content)

        // Mode: enum
        val modeSchema = assertNotNull(properties["mode"]?.jsonObject)
        assertEquals("string", modeSchema["type"]?.jsonPrimitive?.content)
        val enumVals = assertNotNull(modeSchema["enum"]?.jsonArray?.map { it.jsonPrimitive.content })
        assertEquals(listOf("Alpha", "Beta"), enumVals)

        // Tags: array of string
        val tagsSchema = assertNotNull(properties["tags"]?.jsonObject)
        assertEquals("array", tagsSchema["type"]?.jsonPrimitive?.content)
        val itemsSchema = assertNotNull(tagsSchema["items"]?.jsonObject)
        assertEquals("string", itemsSchema["type"]?.jsonPrimitive?.content)

        // Required list contains "name"
        val required = assertNotNull(jsonSchema["required"]?.jsonArray?.map { it.jsonPrimitive.content })
        assertEquals(listOf("name"), required)
    }

    @Test
    fun keysAreSortedDeterministically() {
        val schema = propertySchemaOf(TestComponent.serializer(), json)
        val jsonSchema = schema.toJsonSchema()

        val keys = jsonSchema.keys.toList()
        assertEquals(keys.sorted(), keys, "Top-level keys are sorted")

        val properties = jsonSchema["properties"]!!.jsonObject
        val propKeys = properties.keys.toList()
        assertEquals(propKeys.sorted(), propKeys, "Properties keys are sorted")
    }
}
