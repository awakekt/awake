/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.document

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlin.jvm.JvmInline

/** Unique identifier for a scene extension record. */
@JvmInline
@Serializable
value class SceneExtensionId(val value: String) {
    init {
        require(value.isNotBlank()) { "A scene extension ID must not be blank." }
    }
}

/**
 * Serializable scene extension data record.
 *
 * @property id Extension identifier.
 * @property version Extension data version integer.
 * @property payload JSON element extension payload.
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

/** Validation severity level for scene extensions. */
enum class SceneExtensionValidationSeverity {
    /** Warning message; extension data is preserved. */
    Warning,

    /** Error message; extension data contains fatal validation errors. */
    Error,
}

/**
 * Validation message issue produced when validating a scene extension record.
 *
 * @property severity Validation severity level.
 * @property message Human-readable validation issue description.
 */
data class SceneExtensionValidationMessage(
    val severity: SceneExtensionValidationSeverity,
    val message: String,
) {
    init {
        require(message.isNotBlank()) { "A scene extension validation message must not be blank." }
    }
}

/** Provider contract validating custom scene extensions. */
interface SceneExtensionProvider {
    /** Target extension ID. */
    val id: SceneExtensionId

    /** Validates an extension [record]. */
    fun validate(record: SceneExtensionRecord): List<SceneExtensionValidationMessage>
}

/** Registry for custom scene extension providers. */
class SceneExtensionRegistry {
    private val providers = linkedMapOf<SceneExtensionId, SceneExtensionProvider>()

    /** Read-only list of registered providers. */
    val all: List<SceneExtensionProvider> get() = providers.values.toList()

    /** Registers a scene extension [provider]. */
    fun register(provider: SceneExtensionProvider) {
        require(provider.id !in providers) {
            "A scene extension provider is already registered for '${provider.id.value}'."
        }
        providers[provider.id] = provider
    }

    /** Finds a provider by [id]. */
    fun find(id: SceneExtensionId): SceneExtensionProvider? = providers[id]

    /** Validates an extension [record]. */
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

    /** Validates all extension records in [document]. */
    fun validate(document: SceneDocument): List<SceneExtensionValidationMessage> =
        document.extensions.flatMap(::validate)
}
