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
     * The serializers registered so far, in registration order.
     *
     * A snapshot: a component registered afterwards is not in a list already returned, so ask again after
     * the plugins and kits that register components have been installed.
     */
    fun registeredSerializers(): List<KSerializer<out SceneComponent>> = registered.values.toList()

    /**
     * Constructs a [SerializersModule] containing all registered polymorphic [SceneComponent] serializers.
     */
    fun buildSerializersModule(): SerializersModule = moduleOf(registered)

    /**
     * Creates a new [Json] configuration wired with all registered polymorphic serializers.
     */
    fun createJson(): Json = jsonWith(buildSerializersModule())

    /**
     * Creates a [Json] that decodes only [components], besides custom components and prefab links, and
     * leaves the registered serializers alone. A scoped component registry decodes with it, so one
     * project's component names never meet another's in the same process.
     *
     * Each serializer must be [KSerializer] of its own class, as [register] requires.
     */
    fun createJson(components: Map<KClass<out SceneComponent>, KSerializer<out SceneComponent>>): Json =
        jsonWith(moduleOf(BUILT_IN + components))

    private val BUILT_IN: Map<KClass<out SceneComponent>, KSerializer<out SceneComponent>> = mapOf(
        SceneCustomComponent::class to SceneCustomComponent.serializer(),
        ScenePrefabLink::class to ScenePrefabLink.serializer(),
    )

    private fun moduleOf(serializers: Map<KClass<out SceneComponent>, KSerializer<out SceneComponent>>): SerializersModule =
        SerializersModule {
            polymorphic(SceneComponent::class) {
                serializers.forEach { (kClass, serializer) ->
                    // Safe: `register()` only admits (KClass<S>, KSerializer<S>) pairs, and `createJson(components)`
                    // requires the same, so each kClass and serializer agree at KClass<SceneComponent>.
                    @Suppress("UNCHECKED_CAST")
                    subclass(kClass as KClass<SceneComponent>, serializer as KSerializer<SceneComponent>)
                }
            }
        }

    private fun jsonWith(module: SerializersModule): Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
        serializersModule = module
    }
}
