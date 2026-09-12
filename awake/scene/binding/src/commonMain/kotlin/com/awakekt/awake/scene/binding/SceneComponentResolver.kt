/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.binding

import com.awakekt.awake.core.logging.Logger
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.document.SceneComponent
import com.awakekt.awake.scene.document.SceneCustomComponent
import com.awakekt.awake.scene.document.ScenePrefabLink
import com.awakekt.awake.scene.document.SceneSerializers
import kotlin.reflect.KClass

/** Context provided during component resolution and instantiation. */
interface SceneResolutionContext {
    /** Target active ECS [World]. */
    val world: World

    /** Defers a node-to-node entity link resolution callback until all scene nodes are created. */
    fun deferNodeLink(targetNodeName: String, onResolved: (target: Entity) -> Unit)

    /** Records an arbitrary capability request (e.g. mesh rendering) during scene instantiation. */
    fun recordRequest(request: Any)
}

/** Resolver contract attaching serializable [SceneComponent]s onto ECS [Entity] instances. */
interface SceneComponentResolver {
    /** Returns true if this resolver handles [component]. */
    fun canResolve(component: SceneComponent): Boolean

    /** Attaches [component] onto [entity] in [world]. */
    fun attach(world: World, entity: Entity, component: SceneComponent, context: SceneResolutionContext)
}

/**
 * Registry holding component resolvers and bi-directional bindings for scene loading and export.
 *
 * @param resolvers Custom component resolvers to register.
 * @param bindings Custom component bindings to register.
 */
class SceneComponentRegistry(
    resolvers: List<SceneComponentResolver> = emptyList(),
    bindings: List<SceneComponentBinding<*, *>> = emptyList(),
) {
    private val registeredResolvers = ArrayList<SceneComponentResolver>()
    private val registeredBindings = ArrayList<SceneComponentBinding<*, *>>()
    private val log = Logger("scene-binding")

    init {
        globalResolvers.forEach(::register)
        globalBindings.forEach(::register)
        resolvers.forEach(::register)
        bindings.forEach(::register)
        register(PrefabLinkBinding)
    }

    /** Global static registry entrypoints. */
    companion object {
        private val globalResolvers = ArrayList<SceneComponentResolver>()
        private val globalBindings = ArrayList<SceneComponentBinding<*, *>>()

        /** Registers a global [resolver] active across all registry instances. */
        fun registerGlobal(resolver: SceneComponentResolver) {
            if (resolver !in globalResolvers) {
                globalResolvers += resolver
            }
            if (resolver is SceneComponentBinding<*, *> && resolver !in globalBindings) {
                globalBindings += resolver
                registerSerializer(resolver)
            }
        }

        /** Registers a global bi-directional [binding] active across all registry instances. */
        fun registerGlobal(binding: SceneComponentBinding<*, *>) {
            if (binding !in globalBindings) {
                globalBindings += binding
                registerSerializer(binding)
            }
            if (binding !in globalResolvers) {
                globalResolvers += binding
            }
        }

        // Safe: the binding's schemaClass and serializer were paired by `register()` which
        // constrains them to the same S, so both casts to KClass<SceneComponent> are consistent.
        @Suppress("UNCHECKED_CAST")
        private fun registerSerializer(binding: SceneComponentBinding<*, *>) {
            val serializer = binding.serializer
            if (serializer != null) {
                SceneSerializers.register(
                    binding.schemaClass as KClass<SceneComponent>,
                    serializer as kotlinx.serialization.KSerializer<SceneComponent>,
                )
            }
        }
    }

    /** Read-only view of registered bindings. */
    val bindings: List<SceneComponentBinding<*, *>> get() = registeredBindings

    /** Read-only view of registered resolvers. */
    val resolvers: List<SceneComponentResolver> get() = registeredResolvers

    /** Registers a component [resolver]. */
    fun register(resolver: SceneComponentResolver): SceneComponentRegistry {
        if (resolver !in registeredResolvers) {
            registeredResolvers += resolver
        }
        if (resolver is SceneComponentBinding<*, *> && resolver !in registeredBindings) {
            registeredBindings += resolver
            Companion.registerSerializer(resolver)
        }
        return this
    }

    /** Registers a bi-directional component [binding]. */
    fun register(binding: SceneComponentBinding<*, *>): SceneComponentRegistry {
        if (binding !in registeredBindings) {
            registeredBindings += binding
            Companion.registerSerializer(binding)
        }
        if (binding !in registeredResolvers) {
            registeredResolvers += binding
        }
        return this
    }

    /** Exports all registered components on [entity] in [world] into a list of [SceneComponent]s. */
    fun exportComponents(world: World, entity: Entity): List<SceneComponent> = buildList {
        val seenClasses = HashSet<KClass<*>>()
        for (binding in registeredBindings) {
            if (seenClasses.add(binding.componentClass)) {
                binding.exportFrom(world, entity)?.let { add(it) }
            }
        }
    }

    /** Resolves and attaches [component] onto [entity] in [world]. */
    fun resolve(
        world: World,
        entity: Entity,
        component: SceneComponent,
        context: SceneResolutionContext,
    ): Boolean {
        for (resolver in registeredResolvers) {
            if (resolver.canResolve(component)) {
                resolver.attach(world, entity, component, context)
                return true
            }
        }

        if (component is SceneCustomComponent) {
            log.warn {
                "SceneLoader: No SceneComponentResolver registered for custom component '${component.type}'. " +
                    "Component data is preserved in document but skipped during instantiation."
            }
        }
        return false
    }
}

/** Built-in resolver for [ScenePrefabLink] components. */
object PrefabLinkBinding : SceneComponentResolver {
    override fun canResolve(component: SceneComponent): Boolean = component is ScenePrefabLink

    override fun attach(
        world: World,
        entity: Entity,
        component: SceneComponent,
        context: SceneResolutionContext,
    ) {
        // Handled during prefab instantiation or ignored in plain scene hierarchy.
    }
}
