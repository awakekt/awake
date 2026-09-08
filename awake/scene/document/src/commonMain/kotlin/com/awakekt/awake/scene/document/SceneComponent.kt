/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.document

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Polymorphic
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlinx.serialization.json.JsonElement

/**
 * Base interface for all serializable scene components.
 * Non-sealed interfaces in Kotlinx Serialization are polymorphically serializable by default.
 */
@Polymorphic
@OptIn(ExperimentalSerializationApi::class)
@JsonClassDiscriminator("component")
interface SceneComponent {
    /**
     * Self-validates component properties for a given node [path] within a scene document.
     */
    fun validate(path: String): List<SceneValidationIssue> = emptyList()

    /**
     * Whether multiple instances of this component type are allowed on a single scene node.
     */
    val allowsMultiplePerNode: Boolean get() = true
}

/**
 * Generic custom extension component holding unparsed JSON payloads.
 *
 * @property type Unique custom component discriminator type identifier.
 * @property payload Associated JSON element payload.
 */
@Serializable
@SerialName("custom")
data class SceneCustomComponent(
    val type: String,
    val payload: JsonElement,
) : SceneComponent {
    override fun validate(path: String): List<SceneValidationIssue> = buildList {
        if (type.isBlank()) {
            add(SceneValidationIssue(path, "custom.type must not be blank"))
        }
    }
}
