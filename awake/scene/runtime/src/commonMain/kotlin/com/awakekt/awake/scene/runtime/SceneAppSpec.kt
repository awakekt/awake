/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime

import com.awakekt.awake.ecs.System
import com.awakekt.awake.engine.compose.ComposeAppRuntime
import com.awakekt.awake.engine.platform.core.AppModule
import com.awakekt.awake.engine.platform.dsl.AppSpecBuilder
import com.awakekt.awake.scene.runtime.session.SceneSession
import kotlin.reflect.KClass

/**
 * Application module specification encapsulating all lifecycle, systems, and content for a scene.
 *
 * @property sceneName The optional descriptive name of the scene.
 * @property systems List of registered lifecycle-managed systems.
 * @property scenePopulationBlock Setup block populating entities into the world.
 * @property renderableFactory Factory resolving scene renderable requests into renderers.
 * @property assetLibraryFactory Optional factory constructing the [SceneAssetLibrary].
 * @property updateBlock Per-frame tick update callback.
 * @property ui Scene-level Compose UI overlay content lambda, or `null` if none.
 * @property onReadyBlock Lifecycle callback executed once the scene is fully initialized.
 * @property onDisposeBlock Lifecycle callback executed during scene teardown.
 * @property serviceRegistrations List of custom services to register into the application container.
 * @property infrastructureSystemsFactory Factory instantiating mandatory transform and render infrastructure systems.
 */
@Suppress("LongParameterList")
class SceneAppSpec(
    val sceneName: String?,
    val systems: List<SceneSystemRegistration>,
    // Delayed setup mechanism for entities
    val scenePopulationBlock: SceneAppLifecycleRuntime.() -> Unit,
    val renderableFactory: SceneRenderableFactory,
    internal val assetLibraryFactory: (() -> SceneAssetLibrary)?,
    val updateBlock: SceneUpdateBlock,
    val ui: SceneContent?,
    val onReadyBlock: SceneReadyBlock,
    val onDisposeBlock: SceneDisposeBlock,
    internal val serviceRegistrations: List<SceneServiceRegistration<*>>,
    // Mandatory, not user-configurable data (every scene needs transform resolution + a draw
    // pass) -- pluggable so a game can swap the render backend, but defaults to the standard
    // pair so `authoring` never has to import RenderSystem3D just to get one running. See
    // defaultInfrastructureSystems() in SceneAppLifecycleRuntime.kt.
    val infrastructureSystemsFactory: SceneAppLifecycleRuntime.() -> List<System> =
        SceneAppLifecycleRuntime::defaultInfrastructureSystems,
) : AppModule {
    /**
     * Installs this scene module into the host application specification.
     *
     * @param into The builder receiving module configuration.
     */
    override fun install(into: AppSpecBuilder) {
        installInto(into)
    }

    /**
     * Installs this scene specification into the application builder and returns the instantiated runtime.
     *
     * @param into The application builder where services and lifecycle hooks are installed.
     * @return The active [SceneAppLifecycleRuntime] instance managing this scene.
     */
    fun installInto(into: AppSpecBuilder): SceneAppLifecycleRuntime {
        check(ui == null || into.service(ComposeAppRuntime::class) == null) {
            "A scene cannot declare legacy content { } when an application-level Compose module is installed."
        }
        val runtime = SceneAppLifecycleRuntime(this)
        runtime.initialize(into.serviceLookup())
        into.service(SceneAppLifecycleRuntime::class, runtime)
        into.service(SceneSession::class, runtime.session)
        serviceRegistrations.forEach { registration ->
            registration.install(into, runtime)
        }
        into.ready { renderer -> runtime.ready(renderer) }
        into.render { frame -> runtime.update(frame) }
        into.resize { width, height -> runtime.resize(width, height) }
        into.pause { runtime.pause() }
        into.resume { runtime.resume() }
        into.dispose { runtime.dispose() }
        return runtime
    }
}

/**
 * Service registration record binding a service class type to its runtime factory.
 *
 * @param T The registered service interface or class type.
 * @property type The [KClass] reflection handle of the registered service.
 * @property factory Factory lambda producing the service instance within a [SceneAppLifecycleRuntime] scope.
 */
class SceneServiceRegistration<T : Any>(
    val type: KClass<T>,
    val factory: SceneAppLifecycleRuntime.() -> T,
) {
    /**
     * Installs this service into the target application container.
     *
     * @param into The application builder where the service is registered.
     * @param runtime The active [SceneAppLifecycleRuntime] context.
     */
    fun install(into: AppSpecBuilder, runtime: SceneAppLifecycleRuntime) {
        into.service(type, runtime.factory())
    }
}
