/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.scene.runtime.schedule

import io.github.awakelab.awake.core.host.FixedTimestepLoop
import io.github.awakelab.awake.ecs.System
import io.github.awakelab.awake.ecs.World
import io.github.awakelab.awake.scene.runtime.SceneAppLifecycleRuntime
import io.github.awakelab.awake.scene.runtime.SceneAppSpec
import io.github.awakelab.awake.scene.runtime.SceneSystemHandle
import io.github.awakelab.awake.scene.runtime.SceneSystemPhase

/**
 * Owns one scene's ECS execution order.
 *
 * Systems retain their feature ownership; this type only assigns them to fixed, frame, or standard
 * infrastructure stages. It deliberately has no UI, renderer staging, document, or app lifecycle
 * responsibilities.
 */
class SceneSchedule internal constructor(
    private val spec: SceneAppSpec,
) {
    private val fixedTimestepLoop = FixedTimestepLoop()
    private val registeredSystems = linkedMapOf<SceneSystemHandle<out System>, System>()
    private val fixedSystems = mutableListOf<System>()
    private val frameSystems = mutableListOf<System>()
    private lateinit var infrastructureSystems: List<System>

    internal fun initialize(runtime: SceneAppLifecycleRuntime) {
        infrastructureSystems = spec.infrastructureSystemsFactory(runtime)
        spec.systems.forEach { registration ->
            val system = registration.factory(runtime)
            registeredSystems[registration.handle] = system
            when (registration.phase) {
                SceneSystemPhase.Fixed -> fixedSystems += system
                SceneSystemPhase.Frame -> frameSystems += system
            }
        }
    }

    internal fun synchronize(world: World) {
        runFrame(world, 0f)
    }

    internal fun advance(
        world: World,
        delta: Float,
        fixedUpdate: (Float) -> Unit,
    ) {
        fixedTimestepLoop.advance(
            frameDelta = delta,
            fixedUpdate = { step ->
                fixedSystems.forEach { it.update(world, step) }
                fixedUpdate(step)
            },
            render = {
                runFrame(world, delta)
            },
        )
    }

    @Suppress("UNCHECKED_CAST")
    internal fun <T : System> system(handle: SceneSystemHandle<T>): T =
        registeredSystems[handle] as? T ?: error("System ${handle.name} not found")

    internal fun system(name: String): System =
        registeredSystems.entries.firstOrNull { it.key.name == name }?.value
            ?: error("System $name not found")

    internal fun dispose() {
        registeredSystems.clear()
        fixedSystems.clear()
        frameSystems.clear()
        if (::infrastructureSystems.isInitialized) infrastructureSystems = emptyList()
    }

    private fun runFrame(world: World, delta: Float) {
        frameSystems.forEach { it.update(world, delta) }
        infrastructureSystems.forEach { it.update(world, delta) }
    }
}
