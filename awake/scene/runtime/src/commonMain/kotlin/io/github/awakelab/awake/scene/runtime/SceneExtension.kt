/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.scene.runtime

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlin.jvm.JvmInline

/** Stable identifier for a provider-owned authored scene extension. */
@JvmInline
@Serializable
value class SceneExtensionId(val value: String) {
    init {
        require(value.isNotBlank()) { "A scene extension ID must not be blank." }
    }
}

/**
 * Opaque, versioned authored data owned by a registered extension provider.
 *
 * The scene document stores this record even when its provider is absent. Only the provider may
 * interpret [payload]; the public scene runtime preserves it during load/save.
 */
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

/** KMP-safe extension seam; no reflection or runtime code loading is involved. */
interface SceneExtensionProvider {
    val id: SceneExtensionId

    fun validate(record: SceneExtensionRecord): List<SceneExtensionValidationMessage>
}

/**
 * Explicit registry for providers that understand [SceneExtensionRecord] data.
 *
 * An absent provider produces a warning instead of a load failure: its record remains authored
 * source data and is preserved until the provider is available again.
 */
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
