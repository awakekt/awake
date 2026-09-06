/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.document

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlin.jvm.JvmInline

@JvmInline
@Serializable
value class SceneExtensionId(val value: String) {
    init {
        require(value.isNotBlank()) { "A scene extension ID must not be blank." }
    }
}

@Serializable
data class SceneExtensionRecord(
    val id: SceneExtensionId,
    val version: Int,
    val payload: JsonElement,
) {
    init {
        require(version >= 1) { "A scene extension version must be positive." }
    }
}

enum class SceneExtensionValidationSeverity { Warning, Error }

data class SceneExtensionValidationMessage(
    val severity: SceneExtensionValidationSeverity,
    val message: String,
) {
    init {
        require(message.isNotBlank()) { "A scene extension validation message must not be blank." }
    }
}

interface SceneExtensionProvider {
    val id: SceneExtensionId

    fun validate(record: SceneExtensionRecord): List<SceneExtensionValidationMessage>
}

class SceneExtensionRegistry {
    private val providers = linkedMapOf<SceneExtensionId, SceneExtensionProvider>()

    val all: List<SceneExtensionProvider> get() = providers.values.toList()

    fun register(provider: SceneExtensionProvider) {
        require(provider.id !in providers) {
            "A scene extension provider is already registered for '${provider.id.value}'."
        }
        providers[provider.id] = provider
    }

    fun find(id: SceneExtensionId): SceneExtensionProvider? = providers[id]

    fun validate(record: SceneExtensionRecord): List<SceneExtensionValidationMessage> {
        val provider = find(record.id)
        return if (provider == null) {
            listOf(
                SceneExtensionValidationMessage(
                    SceneExtensionValidationSeverity.Warning,
                    "Scene extension '${record.id.value}' is unavailable; its data is preserved.",
                ),
            )
        } else {
            provider.validate(record)
        }
    }

    fun validate(document: SceneDocument): List<SceneExtensionValidationMessage> =
        document.extensions.flatMap(::validate)
}
