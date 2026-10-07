/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.project.AwakeProjectManifest
import com.awakekt.awake.scene.document.SceneDocument
import com.awakekt.awake.scene.document.SceneLoader
import com.awakekt.awake.scene.document.SceneSerializers
import com.awakekt.awake.scene.document.withPrefabs
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Refuses a manifest whose `required` plugins have no capability among [installed], naming them. */
internal fun requireRequiredPlugins(manifest: AwakeProjectManifest, installed: List<SceneCapability>) {
    val ids = installed.mapTo(HashSet()) { it.id }
    val missing = manifest.plugins.filter { it.required && it.id !in ids }.map { it.id }
    require(missing.isEmpty()) {
        "$PROJECT_MANIFEST requires ${missing.joinToString()}, which no capability provides; pass its capability to loadProject"
    }
}

/**
 * Decodes the scene at [path] with its prefabs. A component id that nothing registered is named, with
 * the fix, instead of the decoder's own message.
 */
internal suspend fun decodeScene(path: String, files: AssetSource): SceneDocument {
    val text = files.readText(path)
    return try {
        SceneLoader.decode(text).withPrefabs { files.readText(it) }
    } catch (failure: SerializationException) {
        val unknown = unregisteredComponents(text)
        if (unknown.isEmpty()) throw failure
        throw IllegalArgumentException(
            "$path uses ${unknown.joinToString { "'$it'" }}, which no capability registers; " +
                "pass the capability that adds it to loadProject",
            failure,
        )
    }
}

/**
 * The component ids in [sceneJson] that no registered binding decodes, in the order they first
 * appear: what a project needs a capability for. A legacy camelCase id counts as its snake_case form,
 * as the scene loader reads it. Empty when the text is not JSON.
 */
internal fun unregisteredComponents(sceneJson: String): List<String> {
    val document = runCatching { Json.parseToJsonElement(sceneJson) }.getOrNull() ?: return emptyList()
    val known = SceneSerializers.registeredSerializers().mapTo(HashSet()) { it.descriptor.serialName }
    val found = LinkedHashSet<String>()
    collectComponentIds(document, found)
    return found.filter { it !in known && it.snakeCase() !in known }
}

private fun collectComponentIds(element: JsonElement, into: MutableSet<String>) {
    when (element) {
        is JsonObject -> {
            (element["components"] as? JsonArray)?.forEach { component ->
                ((component as? JsonObject)?.get("component") as? JsonPrimitive)?.contentOrNull?.let(into::add)
            }
            element.values.forEach { collectComponentIds(it, into) }
        }
        is JsonArray -> element.forEach { collectComponentIds(it, into) }
        else -> Unit
    }
}

private fun String.snakeCase(): String = replace(CAMEL_HUMP) { "_" + it.value.lowercase() }

private val CAMEL_HUMP = Regex("[A-Z]")
