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

    /**
     * Defers a node-to-node entity link resolution callback until all scene nodes are created.
     *
     * @param targetNodeName The name of the target node to resolve.
     * @param onResolved Callback invoked with the resolved entity once created.
     */
    fun deferNodeLink(targetNodeName: String, onResolved: (target: Entity) -> Unit)

    /**
     * Records an arbitrary capability request (e.g. mesh rendering) during scene instantiation.
     *
     * @param request The capability request payload.
     */
    fun recordRequest(request: Any)
}

/** Resolver contract attaching serializable [SceneComponent]s onto ECS [Entity] instances. */
interface SceneComponentResolver {
    /**
     * Returns true if this resolver handles [component].
     *
     * @param component The candidate [SceneComponent].
     * @return `true` if this resolver can process [component], `false` otherwise.
     */
    fun canResolve(component: SceneComponent): Boolean

    /**
     * Attaches [component] onto [entity] in [world].
     *
     * @param world The target active ECS world.
     * @param entity The target entity to attach the component to.
     * @param component The document component to attach.
     * @param context Resolution context for recording deferred links or requests.
     */
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

        /**
         * Registers a global [resolver] active across all registry instances.
         *
         * @param resolver The component resolver to register globally.
         */
        fun registerGlobal(resolver: SceneComponentResolver) {
            if (resolver !in globalResolvers) {
                globalResolvers += resolver
            }
            if (resolver is SceneComponentBinding<*, *> && resolver !in globalBindings) {
                globalBindings += resolver
                registerSerializer(resolver)
            }
        }

        /**
         * Registers a global bi-directional [binding] active across all registry instances.
         *
         * @param binding The component binding to register globally.
         */
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

    /**
     * Registers a component [resolver].
     *
     * @param resolver The resolver to register with this registry instance.
     * @return This registry instance for chaining.
     */
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

    /**
     * Registers a bi-directional component [binding].
     *
     * @param binding The component binding to register with this registry instance.
     * @return This registry instance for chaining.
     */
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

    /**
     * Exports all registered components on [entity] in [world] into a list of [SceneComponent]s.
     *
     * @param world The active ECS world containing [entity].
     * @param entity The entity whose components should be exported.
     * @return A list of serialized [SceneComponent] instances.
     */
    fun exportComponents(world: World, entity: Entity): List<SceneComponent> = buildList {
        val seenClasses = HashSet<KClass<*>>()
        for (binding in registeredBindings) {
            if (seenClasses.add(binding.componentClass)) {
                binding.exportFrom(world, entity)?.let { add(it) }
            }
        }
    }

    /**
     * Resolves and attaches [component] onto [entity] in [world].
     *
     * @param world The target active ECS world.
     * @param entity The entity receiving the component.
     * @param component The document component to resolve and attach.
     * @param context Resolution context for deferring node links or recording requests.
     * @return `true` if a matching resolver attached the component, `false` otherwise.
     */
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

/**
 * A node that places a prefab: [path] names the prefab file. The prefab's entities hang under this
 * one; exporting the world writes this link and leaves them out.
 */
data class PrefabLink(val path: String)

/** Built-in binding for [ScenePrefabLink]: the entity's [PrefabLink], exported back as the link. */
object PrefabLinkBinding : SceneComponentBinding<PrefabLink, ScenePrefabLink> {
    override val componentClass: KClass<PrefabLink> = PrefabLink::class
    override val schemaClass: KClass<ScenePrefabLink> = ScenePrefabLink::class

    override fun attachTyped(
        world: World,
        entity: Entity,
        component: ScenePrefabLink,
        context: SceneResolutionContext,
    ) {
        world.add(entity, PrefabLink(component.path))
    }

    override fun export(world: World, entity: Entity, component: PrefabLink): ScenePrefabLink =
        ScenePrefabLink(component.path)
}
