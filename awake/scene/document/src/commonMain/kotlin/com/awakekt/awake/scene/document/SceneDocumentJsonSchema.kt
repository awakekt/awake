/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.document

import com.awakekt.awake.core.schema.JsonSchemaExportOptions
import com.awakekt.awake.core.schema.PropertyKind
import com.awakekt.awake.core.schema.PropertySchema
import com.awakekt.awake.core.schema.toJsonSchema
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private fun sortedJsonObject(entries: Map<String, JsonElement>): JsonObject {
    val sortedMap = LinkedHashMap<String, JsonElement>()
    entries.keys.sorted().forEach { key ->
        sortedMap[key] = entries.getValue(key)
    }
    return JsonObject(sortedMap)
}

/**
 * Generator producing a standard JSON Schema (Draft 2020-12) for Awake scene documents (`*.scene.json`).
 *
 * The output is deterministic: all keys, property maps and `$defs` are sorted so that successive exports
 * diff cleanly in git.
 */
object SceneDocumentJsonSchema {
    /** JSON Schema Draft 2020-12 dialect identifier. */
    const val SCHEMA_DIALECT: String = "https://json-schema.org/draft/2020-12/schema"

    /** Canonical schema identifier URI. */
    const val SCHEMA_ID: String = "https://awakekt.com/schemas/v1/scene.schema.json"

    private val json = Json { prettyPrint = true }

    /**
     * Generates the complete JSON Schema for scene documents as a [JsonObject].
     *
     * It uses [SceneComponentCatalog] to reflect registered scene components.
     *
     * @param catalog The catalog of scene components to include in the schema.
     * @return Deterministic JSON Schema object.
     */
    @Suppress("LongMethod")
    fun generate(catalog: SceneComponentCatalog = SceneComponentCatalog): JsonObject {
        val root = LinkedHashMap<String, JsonElement>()
        root["\$schema"] = JsonPrimitive(SCHEMA_DIALECT)
        root["\$id"] = JsonPrimitive(SCHEMA_ID)
        root["title"] = JsonPrimitive("AwakeSceneDocument")
        root["description"] = JsonPrimitive("Awake Engine scene document specification")
        root["type"] = JsonPrimitive("object")

        val defs = LinkedHashMap<String, JsonElement>()

        // 1. Primitive shared types: vec3, color
        defs["vec3"] = buildJsonObject {
            put("type", "object")
            put("description", "3-component floating-point vector (x, y, z)")
            val vecProps = mapOf<String, JsonElement>(
                "x" to buildJsonObject { put("type", "number"); put("default", 0.0) },
                "y" to buildJsonObject { put("type", "number"); put("default", 0.0) },
                "z" to buildJsonObject { put("type", "number"); put("default", 0.0) },
            )
            put("properties", sortedJsonObject(vecProps))
            put("required", buildJsonArray {
                add(JsonPrimitive("x"))
                add(JsonPrimitive("y"))
                add(JsonPrimitive("z"))
            })
            put("additionalProperties", false)
        }

        defs["color"] = buildJsonObject {
            put("description", "RGBA color specification: object with r,g,b,a in [0,1], hex string '#RRGGBBAA', or array")
            val anyOf = buildJsonArray {
                add(buildJsonObject {
                    put("type", "object")
                    val colProps = mapOf<String, JsonElement>(
                        "a" to buildJsonObject { put("type", "number"); put("minimum", 0.0); put("maximum", 1.0); put("default", 1.0) },
                        "b" to buildJsonObject { put("type", "number"); put("minimum", 0.0); put("maximum", 1.0) },
                        "g" to buildJsonObject { put("type", "number"); put("minimum", 0.0); put("maximum", 1.0) },
                        "r" to buildJsonObject { put("type", "number"); put("minimum", 0.0); put("maximum", 1.0) },
                        "w" to buildJsonObject { put("type", "number"); put("minimum", 0.0); put("maximum", 1.0) },
                        "x" to buildJsonObject { put("type", "number"); put("minimum", 0.0); put("maximum", 1.0) },
                        "y" to buildJsonObject { put("type", "number"); put("minimum", 0.0); put("maximum", 1.0) },
                        "z" to buildJsonObject { put("type", "number"); put("minimum", 0.0); put("maximum", 1.0) },
                    )
                    put("properties", sortedJsonObject(colProps))
                    put("additionalProperties", false)
                })
                add(buildJsonObject {
                    put("type", "string")
                    put("pattern", "^#([0-9a-fA-F]{3}|[0-9a-fA-F]{4}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})$")
                })
                add(buildJsonObject {
                    put("type", "array")
                    put("items", buildJsonObject { put("type", "number") })
                    put("minItems", 3)
                    put("maxItems", 4)
                })
            }
            put("anyOf", anyOf)
        }

        // 2. Transform definition
        defs["transform"] = buildJsonObject {
            put("type", "object")
            put("description", "Local 3D transform spatial orientation")
            val transProps = mapOf<String, JsonElement>(
                "position" to buildJsonObject { put("\$ref", "#/\$defs/vec3") },
                "rotation" to buildJsonObject { put("\$ref", "#/\$defs/vec3") },
                "scale" to buildJsonObject { put("\$ref", "#/\$defs/vec3") },
            )
            put("properties", sortedJsonObject(transProps))
            put("additionalProperties", false)
        }

        // 3. Extension Record definition
        defs["extension_record"] = buildJsonObject {
            put("type", "object")
            put("description", "Scene extension data record")
            val extProps = mapOf<String, JsonElement>(
                "id" to buildJsonObject { put("type", "string"); put("minLength", 1) },
                "payload" to buildJsonObject { }, // Open JSON
                "version" to buildJsonObject { put("type", "integer"); put("minimum", 1) },
            )
            put("properties", sortedJsonObject(extProps))
            put("required", buildJsonArray {
                add(JsonPrimitive("id"))
                add(JsonPrimitive("payload"))
                add(JsonPrimitive("version"))
            })
            put("additionalProperties", false)
        }

        // 4. Component definitions
        val exportOptions = JsonSchemaExportOptions(
            vector3Ref = "#/\$defs/vec3",
            colorRef = "#/\$defs/color",
            includeHints = true,
        )

        val componentRefList = mutableListOf<String>()
        val schemas = catalog.schemas()

        // Also ensure "custom" is treated as an open payload if registered or not
        for ((id, propSchema) in schemas) {
            val defName = "component_$id"
            defs[defName] = if (id == "custom") {
                // Special case: open payload
                buildJsonObject {
                    put("type", "object")
                    put("description", "Generic custom extension component holding unparsed JSON payloads")
                    val customProps = mapOf<String, JsonElement>(
                        "component" to buildJsonObject { put("const", "custom") },
                        "payload" to buildJsonObject { },
                        "type" to buildJsonObject { put("type", "string"); put("minLength", 1) },
                    )
                    put("properties", sortedJsonObject(customProps))
                    put("required", buildJsonArray {
                        add(JsonPrimitive("component"))
                        add(JsonPrimitive("payload"))
                        add(JsonPrimitive("type"))
                    })
                    put("additionalProperties", false)
                }
            } else {
                buildComponentSchema(id, propSchema, exportOptions)
            }
            componentRefList += "#/\$defs/$defName"
        }

        // If "custom" was not in catalog, still add it
        if ("custom" !in schemas) {
            defs["component_custom"] = buildJsonObject {
                put("type", "object")
                put("description", "Generic custom extension component holding unparsed JSON payloads")
                val customProps = mapOf<String, JsonElement>(
                    "component" to buildJsonObject { put("const", "custom") },
                    "payload" to buildJsonObject { },
                    "type" to buildJsonObject { put("type", "string"); put("minLength", 1) },
                )
                put("properties", sortedJsonObject(customProps))
                put("required", buildJsonArray {
                    add(JsonPrimitive("component"))
                    add(JsonPrimitive("payload"))
                    add(JsonPrimitive("type"))
                })
                put("additionalProperties", false)
            }
            componentRefList += "#/\$defs/component_custom"
        }

        defs["component"] = buildJsonObject {
            put("description", "Attached serializable component polymorphic instance")
            put("anyOf", buildJsonArray {
                componentRefList.sorted().forEach { ref ->
                    add(buildJsonObject { put("\$ref", ref) })
                }
            })
        }

        // 5. Node definition (recursive)
        defs["node"] = buildJsonObject {
            put("type", "object")
            put("description", "Serializable node in a scene hierarchy")
            val nodeProps = mapOf<String, JsonElement>(
                "children" to buildJsonObject {
                    put("type", "array")
                    put("items", buildJsonObject { put("\$ref", "#/\$defs/node") })
                },
                "components" to buildJsonObject {
                    put("type", "array")
                    put("items", buildJsonObject { put("\$ref", "#/\$defs/component") })
                },
                "name" to buildJsonObject {
                    put("type", "string")
                },
                "transform" to buildJsonObject {
                    put("\$ref", "#/\$defs/transform")
                },
            )
            put("properties", sortedJsonObject(nodeProps))
            put("additionalProperties", false)
        }

        root["\$defs"] = sortedJsonObject(defs)

        // Root properties
        val rootProps = mapOf<String, JsonElement>(
            "extensions" to buildJsonObject {
                put("type", "array")
                put("items", buildJsonObject { put("\$ref", "#/\$defs/extension_record") })
            },
            "name" to buildJsonObject {
                put("type", "string")
                put("description", "Optional human-readable scene title")
            },
            "nodes" to buildJsonObject {
                put("type", "array")
                put("items", buildJsonObject { put("\$ref", "#/\$defs/node") })
            },
            "version" to buildJsonObject {
                put("type", "integer")
                put("const", SCENE_SCHEMA_VERSION)
                put("description", "Scene document schema version integer")
            },
        )
        root["properties"] = sortedJsonObject(rootProps)
        root["required"] = buildJsonArray {
            add(JsonPrimitive("version"))
        }
        root["additionalProperties"] = JsonPrimitive(false)

        return sortedJsonObject(root)
    }

    /**
     * Generates the JSON Schema formatted string.
     */
    fun generateString(catalog: SceneComponentCatalog = SceneComponentCatalog): String =
        json.encodeToString(JsonObject.serializer(), generate(catalog))

    private fun buildComponentSchema(
        id: String,
        schema: PropertySchema,
        options: JsonSchemaExportOptions,
    ): JsonObject {
        val compMap = LinkedHashMap<String, JsonElement>()
        compMap["type"] = JsonPrimitive("object")
        schema.hints.tooltip?.let { compMap["description"] = JsonPrimitive(it) }

        val props = LinkedHashMap<String, JsonElement>()
        // Discriminator property
        props["component"] = buildJsonObject { put("const", id) }

        val requiredList = mutableListOf("component")

        schema.children.sortedBy { it.name }.forEach { child ->
            props[child.name] = child.toJsonSchema(options)
            if (child.required) {
                requiredList += child.name
            }
        }

        compMap["properties"] = sortedJsonObject(props)
        compMap["required"] = buildJsonArray {
            requiredList.sorted().forEach { add(JsonPrimitive(it)) }
        }
        compMap["additionalProperties"] = JsonPrimitive(false)

        return sortedJsonObject(compMap)
    }
}
