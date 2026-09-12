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

class SceneAppSpec(
    val sceneName: String?,
    val systems: List<SceneSystemRegistration>,
    // Delayed setup mechanism for entities
    val scenePopulationBlock: SceneAppLifecycleRuntime.() -> Unit,
    val renderableFactory: SceneRenderableFactory,
    internal val assetLibraryFactory: (() -> SceneAssetLibrary)?,
    val updateBlock: SceneUpdateBlock,
    /** Null when a scene draws no UI at all. */
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
    override fun install(into: AppSpecBuilder) {
        installInto(into)
    }

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

class SceneServiceRegistration<T : Any>(
    val type: KClass<T>,
    val factory: SceneAppLifecycleRuntime.() -> T,
) {
    fun install(into: AppSpecBuilder, runtime: SceneAppLifecycleRuntime) {
        into.service(type, runtime.factory())
    }
}
