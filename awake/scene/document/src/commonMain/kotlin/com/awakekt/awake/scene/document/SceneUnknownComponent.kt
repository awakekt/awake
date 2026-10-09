/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.document

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * A component whose type nothing registered provides, kept as the JSON it was written as, so an
 * editor or tool without the game's code still opens a scene and saves it unchanged.
 *
 * A scene `Json` made with `keepUnknownComponents` decodes one for each component it has no
 * serializer for; the default refuses them. Every scene `Json` encodes one back as it was. It runs
 * nothing: instantiating keeps it as data on its entity, and a world exported again writes it back.
 * [SceneValidator.unknownComponentIssues] names each one.
 *
 * @property type The component's `component` name.
 * @property fields Every other key of the component, in the order written.
 */
data class SceneUnknownComponent(val type: String, val fields: JsonObject) : SceneComponent

/** Writes and reads a [SceneUnknownComponent] under its own [type], as the scene `Json`'s polymorphism names it. */
internal class SceneUnknownComponentSerializer(type: String) : KSerializer<SceneUnknownComponent> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor(type)

    override fun serialize(encoder: Encoder, value: SceneUnknownComponent) {
        val json = encoder as? JsonEncoder ?: throw SerializationException("A component kept as data is written as JSON only")
        json.encodeJsonElement(value.fields)
    }

    override fun deserialize(decoder: Decoder): SceneUnknownComponent {
        val json = decoder as? JsonDecoder ?: throw SerializationException("A component kept as data is read from JSON only")
        // The discriminator may or may not reach here, depending on how the decoder found it.
        val fields = json.decodeJsonElement().jsonObject.filterKeys { it != COMPONENT_DISCRIMINATOR }
        return SceneUnknownComponent(descriptor.serialName, JsonObject(fields))
    }
}

/** The key a scene component's type is written under. */
internal const val COMPONENT_DISCRIMINATOR = "component"
