/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.document

import com.awakekt.awake.core.logging.Logger
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import kotlin.reflect.KClass

interface SceneResolutionContext {
    val world: World

    fun deferNodeLink(targetNodeName: String, onResolved: (target: Entity) -> Unit)

    fun requestRenderable(entity: Entity, component: SceneMeshRenderer)
}

interface SceneComponentResolver {
    fun canResolve(component: SceneComponent): Boolean

    fun attach(world: World, entity: Entity, component: SceneComponent, context: SceneResolutionContext)
}

class SceneComponentRegistry(
    resolvers: List<SceneComponentResolver> = emptyList(),
    bindings: List<SceneComponentBinding<*, *>> = emptyList(),
) {
    private val registeredResolvers = ArrayList<SceneComponentResolver>()
    private val registeredBindings = ArrayList<SceneComponentBinding<*, *>>()
    private val log = Logger("scene-document")

    init {
        globalResolvers.forEach(::register)
        globalBindings.forEach(::register)
        resolvers.forEach(::register)
        bindings.forEach(::register)
        register(PrefabLinkBinding)
        register(MeshRendererBinding)
        register(SpinControlBinding)
    }

    companion object {
        private val globalResolvers = ArrayList<SceneComponentResolver>()
        private val globalBindings = ArrayList<SceneComponentBinding<*, *>>()

        fun registerGlobal(resolver: SceneComponentResolver) {
            if (resolver !in globalResolvers) {
                globalResolvers += resolver
            }
            if (resolver is SceneComponentBinding<*, *> && resolver !in globalBindings) {
                globalBindings += resolver
            }
        }

        fun registerGlobal(binding: SceneComponentBinding<*, *>) {
            if (binding !in globalBindings) {
                globalBindings += binding
            }
            if (binding !in globalResolvers) {
                globalResolvers += binding
            }
        }
    }

    val bindings: List<SceneComponentBinding<*, *>> get() = registeredBindings
    val resolvers: List<SceneComponentResolver> get() = registeredResolvers

    fun register(resolver: SceneComponentResolver): SceneComponentRegistry {
        if (resolver !in registeredResolvers) {
            registeredResolvers += resolver
        }
        if (resolver is SceneComponentBinding<*, *> && resolver !in registeredBindings) {
            registeredBindings += resolver
        }
        return this
    }

    fun register(binding: SceneComponentBinding<*, *>): SceneComponentRegistry {
        if (binding !in registeredBindings) {
            registeredBindings += binding
        }
        if (binding !in registeredResolvers) {
            registeredResolvers += binding
        }
        return this
    }

    fun exportComponents(world: World, entity: Entity): List<SceneComponent> = buildList {
        val seenClasses = HashSet<KClass<*>>()
        for (binding in registeredBindings) {
            if (seenClasses.add(binding.componentClass)) {
                binding.exportFrom(world, entity)?.let { add(it) }
            }
        }
    }

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
