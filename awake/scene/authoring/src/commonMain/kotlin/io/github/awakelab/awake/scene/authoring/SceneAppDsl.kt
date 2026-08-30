/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.scene.authoring

import io.github.awakelab.awake.ecs.System
import io.github.awakelab.awake.engine.bootstrap.dsl.AppSpecDsl
import io.github.awakelab.awake.scene.authoring.dsl.AwakeSceneDsl
import io.github.awakelab.awake.scene.authoring.dsl.EntityScope
import io.github.awakelab.awake.scene.authoring.dsl.SceneBuilder
import io.github.awakelab.awake.scene.authoring.dsl.scene
import io.github.awakelab.awake.scene.runtime.SceneAppLifecycleRuntime
import io.github.awakelab.awake.scene.runtime.SceneAppSpec
import io.github.awakelab.awake.scene.runtime.SceneAssetLibrary
import io.github.awakelab.awake.scene.runtime.SceneContent
import io.github.awakelab.awake.scene.runtime.SceneDisposeBlock
import io.github.awakelab.awake.scene.runtime.SceneDocument
import io.github.awakelab.awake.scene.runtime.SceneReadyBlock
import io.github.awakelab.awake.scene.runtime.SceneRenderableFactory
import io.github.awakelab.awake.scene.runtime.SceneServiceRegistration
import io.github.awakelab.awake.scene.runtime.SceneSystemHandle
import io.github.awakelab.awake.scene.runtime.SceneSystemPhase
import io.github.awakelab.awake.scene.runtime.SceneSystemRegistration
import io.github.awakelab.awake.scene.runtime.SceneUpdateBlock
import io.github.awakelab.awake.scene.runtime.attachRenderableComponents
import io.github.awakelab.awake.scene.runtime.defaultInfrastructureSystems
import io.github.awakelab.awake.scene.runtime.instantiate
import kotlin.reflect.KClass

fun AppSpecDsl.ecs(block: SceneAppDsl.() -> Unit) {
    install(sceneApp(block))
}

fun AppSpecDsl.ecs(spec: SceneAppSpec) {
    install(spec)
}

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

fun AppSpecDsl.scene(spec: SceneAppSpec) {
    install(spec)
}

fun sceneApp(block: SceneAppDsl.() -> Unit): SceneAppSpec = SceneAppDsl().apply(block).build()

/**
 * Primary name for an installed ECS scene. The returned compatibility spec remains source
 * compatible while the app lifecycle adapter is retired.
 */
fun sceneSession(block: SceneAppDsl.() -> Unit): SceneAppSpec = SceneAppDsl().apply(block).build()

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

    fun name(value: String?) {
        this.sceneName = value
    }

    /**
     * Captures the declarative entity layout block without running it yet.
     * It delays execution until the actual runtime engine assigns a World.
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
     * Integrates an existing [io.github.awakelab.awake.scene.runtime.SceneDocument] into the population block.
     */
    fun scene(document: SceneDocument) {
        this.sceneName = document.name
        this.scenePopulationBlock = {
            val scene = document.instantiate(world = world)
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

    fun assets(block: SceneAssetsDsl.() -> Unit) {
        val dsl = SceneAssetsDsl().apply(block)
        assetLibraryFactory = dsl::buildLibrary
        renderableFactory = { request ->
            requireAssetLibrary().resolve(this, request)
        }
    }

    fun renderables(factory: SceneRenderableFactory) {
        renderableFactory = factory
    }

    fun <T : System> system(
        name: String,
        phase: SceneSystemPhase,
        factory: SceneAppLifecycleRuntime.() -> T,
    ): SceneSystemHandle<T> = systemsDsl.system(name, phase, factory)

    fun <T : System> fixedSystem(
        name: String,
        factory: SceneAppLifecycleRuntime.() -> T,
    ): SceneSystemHandle<T> = systemsDsl.fixedSystem(name, factory)

    fun <T : System> frameSystem(
        name: String,
        factory: SceneAppLifecycleRuntime.() -> T,
    ): SceneSystemHandle<T> = systemsDsl.frameSystem(name, factory)

    fun systems(block: SceneSystemsDsl.() -> Unit) {
        systemsDsl.apply(block)
    }

    fun update(block: SceneUpdateBlock) {
        updateBlock = block
    }

    /** The scene's UI. Compose's `setContent`, declared rather than called. */
    fun content(block: SceneContent) {
        ui = block
    }

    fun onReady(block: SceneReadyBlock) {
        onReadyBlocks += block
    }

    fun onDispose(block: SceneDisposeBlock) {
        onDisposeBlocks += block
    }

    fun <T : Any> service(type: KClass<T>, factory: SceneAppLifecycleRuntime.() -> T) {
        serviceRegistrations += SceneServiceRegistration(type, factory)
    }

    inline fun <reified T : Any> service(noinline factory: SceneAppLifecycleRuntime.() -> T) {
        service(T::class, factory)
    }

    /**
     * Overrides the mandatory transform-resolution + draw-pass systems (default:
     * [defaultInfrastructureSystems]) -- e.g. to swap in a custom render backend. This DSL
     * never imports a concrete render system itself; the override lambda is free to import
     * whatever it needs from the caller's own module.
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

class SceneSystemsDsl internal constructor() {
    private val registrations = mutableListOf<SceneSystemRegistration>()

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

    fun <T : System> fixedSystem(
        name: String,
        factory: SceneAppLifecycleRuntime.() -> T,
    ): SceneSystemHandle<T> = system(name, SceneSystemPhase.Fixed, factory)

    fun <T : System> frameSystem(
        name: String,
        factory: SceneAppLifecycleRuntime.() -> T,
    ): SceneSystemHandle<T> = system(name, SceneSystemPhase.Frame, factory)

    internal fun build(): List<SceneSystemRegistration> = registrations.toList()
}
