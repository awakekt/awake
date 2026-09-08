/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.document

import com.awakekt.awake.core.host.readResourceBytes
import kotlinx.serialization.json.Json

/**
 * Exception thrown when attempting to load a scene document with an unsupported schema version.
 *
 * @property documentVersion Version integer declared in the document.
 */
class SceneSchemaVersionException(
    val documentVersion: Int,
) : IllegalArgumentException(
    "Scene document declares schema version $documentVersion, but this build supports up to " +
        "$SCENE_SCHEMA_VERSION. Upgrade the engine or re-export the scene.",
)

/** Default JSON serializer configured for Awake scene documents. */
val DefaultSceneJson: Json
    get() = SceneSerializers.createJson()

/**
 * Engine service for encoding, decoding, and instantiating Awake scene documents (`.scene.json`).
 */
object SceneLoader {
    /** Serializes [document] to JSON string format. */
    fun encode(document: SceneDocument, json: Json = DefaultSceneJson): String =
        json.encodeToString(document)

    /** Deserializes [text] JSON string into a [SceneDocument]. */
    fun decode(text: String, json: Json = DefaultSceneJson): SceneDocument {
        val normalized = normalizeLegacyDiscriminators(text)
        return json.decodeFromString<SceneDocument>(normalized).also { document ->
            if (document.version > SCENE_SCHEMA_VERSION) throw SceneSchemaVersionException(document.version)
        }
    }

    private val LEGACY_DISCRIMINATORS = listOf("meshRenderer", "spinControl", "pbrMaterial", "prefabLink")

    internal fun normalizeLegacyDiscriminators(text: String): String {
        if (LEGACY_DISCRIMINATORS.none { text.contains(it) }) return text
        return text
            .replace("\"component\": \"meshRenderer\"", "\"component\": \"mesh_renderer\"")
            .replace("\"component\":\"meshRenderer\"", "\"component\":\"mesh_renderer\"")
            .replace("\"component\": \"spinControl\"", "\"component\": \"spin_control\"")
            .replace("\"component\":\"spinControl\"", "\"component\":\"spin_control\"")
            .replace("\"component\": \"pbrMaterial\"", "\"component\": \"pbr_material\"")
            .replace("\"component\":\"pbrMaterial\"", "\"component\":\"pbr_material\"")
            .replace("\"component\": \"prefabLink\"", "\"component\": \"prefab_link\"")
            .replace("\"component\":\"prefabLink\"", "\"component\":\"prefab_link\"")
    }

    /** Loads and decodes a scene document from a resource asset path. */
    suspend fun loadFromResource(path: String, json: Json = DefaultSceneJson): SceneDocument =
        decode(readResourceBytes(path).decodeToString(), json)

    /** Instantiates [document] using a custom [SceneInstantiationAdapter]. */
    fun <Node, Instance> instantiate(
        document: SceneDocument,
        adapter: SceneInstantiationAdapter<Node, Instance>,
    ): Instance {
        SceneValidator.requireValid(document)
        val roots = document.nodes.mapIndexed { index, node ->
            instantiateNode(
                adapter,
                node,
                parent = null,
                path = node.name?.takeIf { it.isNotBlank() } ?: "#$index",
            )
        }
        return adapter.complete(roots)
    }

    private fun <Node> instantiateNode(
        adapter: SceneInstantiationAdapter<Node, *>,
        node: SceneNode,
        parent: Node?,
        path: String,
    ): SceneNodeHandle<Node> {
        val created = adapter.createNode(node, parent)
        adapter.attachTransform(created, node.transform, parent)
        node.name?.takeIf { it.isNotBlank() }?.let { adapter.attachName(created, it) }
        node.components.forEach { adapter.attachComponent(created, it) }

        val children = node.children.mapIndexed { index, child ->
            instantiateNode(
                adapter = adapter,
                node = child,
                parent = created,
                path = child.name?.takeIf { it.isNotBlank() } ?: "$path/#$index",
            )
        }
        return SceneNodeHandle(node.name, created, children)
    }
}
