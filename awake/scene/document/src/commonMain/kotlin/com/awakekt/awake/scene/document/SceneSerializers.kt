/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.document

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import kotlin.reflect.KClass

/**
 * Dynamic registry of polymorphic serializers for [SceneComponent]s across all engine modules.
 */
object SceneSerializers {
    private val registered = LinkedHashMap<KClass<out SceneComponent>, KSerializer<out SceneComponent>>()

    init {
        register(SceneCustomComponent::class, SceneCustomComponent.serializer())
        register(ScenePrefabLink::class, ScenePrefabLink.serializer())
    }

    /**
     * Registers a serializer for a specific [SceneComponent] subclass.
     */
    fun <S : SceneComponent> register(kClass: KClass<S>, serializer: KSerializer<S>) {
        registered[kClass] = serializer
    }

    /**
     * Constructs a [SerializersModule] containing all registered polymorphic [SceneComponent] serializers.
     */
    fun buildSerializersModule(): SerializersModule = SerializersModule {
        polymorphic(SceneComponent::class) {
            registered.forEach { (kClass, serializer) ->
                @Suppress("UNCHECKED_CAST")
                subclass(kClass as KClass<SceneComponent>, serializer as KSerializer<SceneComponent>)
            }
        }
    }

    /**
     * Creates a new [Json] configuration wired with all registered polymorphic serializers.
     */
    fun createJson(): Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
        serializersModule = buildSerializersModule()
    }
}
