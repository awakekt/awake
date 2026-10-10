/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.authoring

import com.awakekt.awake.ecs.System
import com.awakekt.awake.engine.bootstrap.dsl.AppSpecDsl
import com.awakekt.awake.scene.authoring.dsl.AwakeSceneDsl
import com.awakekt.awake.scene.authoring.dsl.EntityScope
import com.awakekt.awake.scene.authoring.dsl.SceneBuilder
import com.awakekt.awake.scene.authoring.dsl.scene
import com.awakekt.awake.scene.binding.SceneComponentRegistry
import com.awakekt.awake.scene.binding.instantiate
import com.awakekt.awake.scene.document.SceneDocument
import com.awakekt.awake.scene.runtime.SceneAppLifecycleRuntime
import com.awakekt.awake.scene.runtime.SceneAppSpec
import com.awakekt.awake.scene.runtime.SceneAssetLibrary
import com.awakekt.awake.scene.runtime.SceneContent
import com.awakekt.awake.scene.runtime.SceneDisposeBlock
import com.awakekt.awake.scene.runtime.SceneReadyBlock
import com.awakekt.awake.scene.runtime.SceneRenderableFactory
import com.awakekt.awake.scene.runtime.SceneServiceRegistration
import com.awakekt.awake.scene.runtime.SceneSystemHandle
import com.awakekt.awake.scene.runtime.SceneSystemPhase
import com.awakekt.awake.scene.runtime.SceneSystemRegistration
import com.awakekt.awake.scene.runtime.SceneUpdateBlock
import com.awakekt.awake.scene.runtime.attachRenderableComponents
import com.awakekt.awake.scene.runtime.defaultInfrastructureSystems
import kotlin.reflect.KClass

/**
 * Installs an ECS scene specification built via [SceneAppDsl] into this [AppSpecDsl].
 *
 * @param block The configuration block defining systems, entities, assets, and scene lifecycle.
 */
fun AppSpecDsl.ecs(block: SceneAppDsl.() -> Unit) {
    install(sceneApp(block))
}

/**
 * Installs a pre-configured [SceneAppSpec] into this [AppSpecDsl].
 *
 * @param spec The scene application specification to install.
 */
fun AppSpecDsl.ecs(spec: SceneAppSpec) {
    install(spec)
}

/**
 * Installs an ECS scene into this [AppSpecDsl] with an optional scene name.
 *
 * @param name The optional descriptive identifier for the scene.
 * @param block The configuration block defining systems, entities, assets, and scene lifecycle.
 */
fun AppSpecDsl.scene(
    name: String? = null,
    block: SceneAppDsl.() -> Unit,
) {
    install(
        sceneApp {
            if (name != null) {
                this.name(name)
            }
            block()
        },
    )
}

/**
 * Installs a pre-configured [SceneAppSpec] into this [AppSpecDsl].
 *
 * @param spec The scene application specification to install.
 */
fun AppSpecDsl.scene(spec: SceneAppSpec) {
    install(spec)
}

/**
 * Builds a [SceneAppSpec] by applying the provided declarative builder block.
 *
 * @param block The configuration block defining systems, entities, assets, and scene lifecycle.
 * @return The constructed [SceneAppSpec] ready for installation or execution.
 */
fun sceneApp(block: SceneAppDsl.() -> Unit): SceneAppSpec = SceneAppDsl().apply(block).build()

/**
 * Builds a [SceneAppSpec] by applying the provided declarative builder block.
 *
 * @param block The configuration block defining systems, entities, assets, and scene lifecycle.
 * @return The constructed [SceneAppSpec].
 */
@Deprecated("Use ecs { ... } or scene { ... } instead.", ReplaceWith("ecs(block)"))
fun sceneSession(block: SceneAppDsl.() -> Unit): SceneAppSpec = SceneAppDsl().apply(block).build()

/**
 * Installs an ECS scene specification into this [AppSpecDsl].
 *
 * @param block The configuration block defining systems, entities, assets, and scene lifecycle.
 */
@Deprecated("Use ecs { ... } or scene { ... } instead.", ReplaceWith("ecs(block)"))
fun AppSpecDsl.sceneSession(block: SceneAppDsl.() -> Unit) {
    install(SceneAppDsl().apply(block).build())
}

/**
 * Marked with [AwakeSceneDsl] so the enclosing `AppSpecDsl` receiver is hidden inside this
 * block. Without it, a `scene(name) { ... }` call here silently resolves to the outer
 * `AppSpecDsl.scene` extension and installs a whole second scene module -- two `World`s, two
 * render callbacks, and a `requireService<SceneAppLifecycleRuntime>()` that returns the wrong one.
 */
@AwakeSceneDsl
class SceneAppDsl internal constructor() {
    private var sceneName: String? = null
    private var scenePopulationBlock: SceneAppLifecycleRuntime.() -> Unit = {}
    private var renderableFactory: SceneRenderableFactory = {
        error("ecs { assets { ... } } or ecs { renderables { ... } } must resolve scene mesh/material requests.")
    }
    private var assetLibraryFactory: (() -> SceneAssetLibrary)? = null
    private val systemsDsl = SceneSystemsDsl()
    private var updateBlock: SceneUpdateBlock = { _, _ -> }
    private var ui: SceneContent? = null
    private val onReadyBlocks = mutableListOf<SceneReadyBlock>()
    private val onDisposeBlocks = mutableListOf<SceneDisposeBlock>()
    private val serviceRegistrations = mutableListOf<SceneServiceRegistration<*>>()
    private var infrastructureSystemsFactory: SceneAppLifecycleRuntime.() -> List<System> =
        SceneAppLifecycleRuntime::defaultInfrastructureSystems

    /**
     * Sets the descriptive name of the scene.
     *
     * @param value The name string, or `null` to leave unnamed.
     */
    fun name(value: String?) {
        this.sceneName = value
    }

    /**
     * Captures the declarative entity layout block without running it yet.
     *
     * Delays execution until the actual runtime engine assigns a World.
     *
     * @param name The optional descriptive name for the scene.
     * @param block The declarative scene builder block executed within [SceneBuilder].
     */
    fun scene(name: String? = null, block: SceneBuilder.() -> Unit) {
        if (name != null) {
            this.sceneName = name
        }
        this.scenePopulationBlock = {
            world.scene(block)
        }
    }

    /**
     * Integrates an existing [SceneDocument] into the population block. Its components attach with the
     * globally registered resolvers as they are when the scene populates, after the runtime installed
     * the default ones.
     *
     * @param document The scene document to instantiate into the world.
     */
    fun scene(document: SceneDocument) = populate(document) { SceneComponentRegistry() }

    /**
     * Integrates an existing [SceneDocument] into the population block, attaching its components with
     * [componentRegistry]: a scoped registry for a document decoded with that registry's `sceneJson`.
     *
     * @param document The scene document to instantiate into the world.
     * @param componentRegistry The resolvers that attach the document's components.
     */
    fun scene(document: SceneDocument, componentRegistry: SceneComponentRegistry) = populate(document) { componentRegistry }

    private fun populate(document: SceneDocument, registry: () -> SceneComponentRegistry) {
        this.sceneName = document.name
        this.scenePopulationBlock = {
            val scene = document.instantiate(world = world, componentRegistry = registry())
            scene.attachRenderableComponents { request -> spec.renderableFactory(this, request) }
        }
    }

    /**
     * Shortcut to spawn a root-level entity cleanly without nesting.
     *
     * @param name The optional descriptive name for the entity.
     * @param block The configuration block executed within an [EntityScope].
     */
    fun entity(
        name: String? = null,
        block: EntityScope.() -> Unit = {},
    ) {
        // Since we want to preserve the population block, we append to it.
        val previous = scenePopulationBlock
        scenePopulationBlock = {
            previous()
            SceneBuilder(world).entity(name, block)
        }
    }

    /**
     * Configures the asset library and renderable resolution mappings for this scene.
     *
     * @param block The configuration block executed within a [SceneAssetsDsl] scope.
     */
    fun assets(block: SceneAssetsDsl.() -> Unit) {
        val dsl = SceneAssetsDsl().apply(block)
        assetLibraryFactory = dsl::buildLibrary
        renderableFactory = { request ->
            requireAssetLibrary().resolve(this, request)
        }
    }

    /**
     * Registers a lifecycle-managed system in the specified execution phase.
     *
     * @param T The type of [System] to instantiate.
     * @param name The descriptive name of the system.
     * @param phase The [SceneSystemPhase] in which this system executes.
     * @param factory The factory lambda creating the system using the runtime context.
     * @return A typed [SceneSystemHandle] for runtime lookup.
     */
    fun <T : System> system(
        name: String,
        phase: SceneSystemPhase,
        factory: SceneAppLifecycleRuntime.() -> T,
    ): SceneSystemHandle<T> = systemsDsl.system(name, phase, factory)

    /**
     * Registers a lifecycle-managed system in the fixed update phase.
     *
     * @param T The type of [System] to instantiate.
     * @param name The descriptive name of the system.
     * @param factory The factory lambda creating the system using the runtime context.
     * @return A typed [SceneSystemHandle] for runtime lookup.
     */
    fun <T : System> fixedSystem(
        name: String,
        factory: SceneAppLifecycleRuntime.() -> T,
    ): SceneSystemHandle<T> = systemsDsl.fixedSystem(name, factory)

    /**
     * Registers a lifecycle-managed system in the per-frame update phase.
     *
     * @param T The type of [System] to instantiate.
     * @param name The descriptive name of the system.
     * @param factory The factory lambda creating the system using the runtime context.
     * @return A typed [SceneSystemHandle] for runtime lookup.
     */
    fun <T : System> frameSystem(
        name: String,
        factory: SceneAppLifecycleRuntime.() -> T,
    ): SceneSystemHandle<T> = systemsDsl.frameSystem(name, factory)

    /**
     * Configures multiple lifecycle-managed systems within a [SceneSystemsDsl] block.
     *
     * @param block The configuration block declaring systems and phases.
     */
    fun systems(block: SceneSystemsDsl.() -> Unit) {
        systemsDsl.apply(block)
    }

    /**
     * Registers a custom scene update callback executed on each frame tick.
     *
     * @param block The callback receiving delta time in seconds and elapsed total time.
     */
    fun update(block: SceneUpdateBlock) {
        updateBlock = block
    }

    /**
     * Declares scene-level Compose UI overlay content.
     *
     * @param block The composable content lambda rendered over the 3D scene.
     */
    fun ui(block: SceneContent) {
        ui = block
    }

    /**
     * Declares scene-level Compose UI overlay content.
     *
     * @param block The composable content lambda rendered over the 3D scene.
     */
    @Deprecated("Use ui { ... } instead.", ReplaceWith("ui(block)"))
    fun content(block: SceneContent) {
        ui(block)
    }

    /**
     * Registers a callback executed once when the scene and its runtime have been fully initialized.
     *
     * @param block The lifecycle hook executed with the [SceneAppLifecycleRuntime] receiver.
     */
    fun onReady(block: SceneReadyBlock) {
        onReadyBlocks += block
    }

    /**
     * Registers a callback executed when the scene is being disposed and torn down. It undoes what
     * [onReady] did, so it runs only for a scene that became ready, not one whose backend never started.
     *
     * @param block The lifecycle hook executed with the [SceneAppLifecycleRuntime] receiver.
     */
    fun onDispose(block: SceneDisposeBlock) {
        onDisposeBlocks += block
    }

    /**
     * Registers a custom service dependency accessible via the scene lifecycle runtime.
     *
     * @param T The service interface or class type.
     * @param type The [KClass] of the service to register.
     * @param factory The factory lambda creating the service instance.
     */
    fun <T : Any> service(type: KClass<T>, factory: SceneAppLifecycleRuntime.() -> T) {
        serviceRegistrations += SceneServiceRegistration(type, factory)
    }

    /**
     * Registers a custom service dependency accessible via the scene lifecycle runtime.
     *
     * @param T The reified service interface or class type.
     * @param factory The factory lambda creating the service instance.
     */
    inline fun <reified T : Any> service(noinline factory: SceneAppLifecycleRuntime.() -> T) {
        service(T::class, factory)
    }

    /**
     * Overrides the mandatory transform-resolution + draw-pass systems (default:
     * [defaultInfrastructureSystems]) -- e.g. to swap in a custom render backend. This DSL
     * never imports a concrete render system itself; the override lambda is free to import
     * whatever it needs from the caller's own module.
     *
     * @param factory Factory producing the list of infrastructure [System] instances.
     */
    fun infrastructureSystems(factory: SceneAppLifecycleRuntime.() -> List<System>) {
        infrastructureSystemsFactory = factory
    }

    internal fun build(): SceneAppSpec = SceneAppSpec(
        sceneName = sceneName,
        systems = systemsDsl.build(),
        scenePopulationBlock = scenePopulationBlock,
        renderableFactory = renderableFactory,
        assetLibraryFactory = assetLibraryFactory,
        updateBlock = updateBlock,
        ui = ui,
        onReadyBlock = { onReadyBlocks.forEach { it(this) } },
        onDisposeBlock = { onDisposeBlocks.forEach { it(this) } },
        serviceRegistrations = serviceRegistrations.toList(),
        infrastructureSystemsFactory = infrastructureSystemsFactory,
    )
}

/**
 * Builder scope for declaring lifecycle-managed systems within a scene.
 */
class SceneSystemsDsl internal constructor() {
    private val registrations = mutableListOf<SceneSystemRegistration>()

    /**
     * Registers a system in the specified execution phase.
     *
     * @param T The type of [System] to instantiate.
     * @param name The descriptive name of the system.
     * @param phase The [SceneSystemPhase] in which this system executes.
     * @param factory The factory lambda creating the system using the runtime context.
     * @return A typed [SceneSystemHandle] for runtime lookup.
     */
    fun <T : System> system(
        name: String,
        phase: SceneSystemPhase,
        factory: SceneAppLifecycleRuntime.() -> T,
    ): SceneSystemHandle<T> {
        val handle = SceneSystemHandle<T>(name)
        registrations += SceneSystemRegistration(
            handle = handle,
            phase = phase,
            factory = { factory() },
        )
        return handle
    }

    /**
     * Registers a system in the fixed update phase.
     *
     * @param T The type of [System] to instantiate.
     * @param name The descriptive name of the system.
     * @param factory The factory lambda creating the system using the runtime context.
     * @return A typed [SceneSystemHandle] for runtime lookup.
     */
    fun <T : System> fixedSystem(
        name: String,
        factory: SceneAppLifecycleRuntime.() -> T,
    ): SceneSystemHandle<T> = system(name, SceneSystemPhase.Fixed, factory)

    /**
     * Registers a system in the per-frame update phase.
     *
     * @param T The type of [System] to instantiate.
     * @param name The descriptive name of the system.
     * @param factory The factory lambda creating the system using the runtime context.
     * @return A typed [SceneSystemHandle] for runtime lookup.
     */
    fun <T : System> frameSystem(
        name: String,
        factory: SceneAppLifecycleRuntime.() -> T,
    ): SceneSystemHandle<T> = system(name, SceneSystemPhase.Frame, factory)

    internal fun build(): List<SceneSystemRegistration> = registrations.toList()
}
