/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime.session

import com.awakekt.awake.ecs.World

/**
 * Connects an external presentation, UI, editor, or session layer to a [SceneSession] at the frame
 * boundary without masquerading as an ECS [com.awakekt.awake.ecs.System].
 *
 * Keep Compose UI, HUD registries, and store coordinators out of ECS systems: attach a
 * [SceneBridge] instead so simulation schedules stay pure and headless hosts never run UI bridges.
 *
 * Frame order inside [SceneSession]:
 * 1. [beforeAdvance] — drain queued UI/store effects into ECS request components so simulation sees
 *    them on this frame with zero extra frame latency.
 * 2. ECS fixed, pre-animation, and frame systems run.
 * 3. [afterAdvance] — project fresh ECS state into stores or UI state holders right before UI
 *    composes.
 * 4. [close] — invoked automatically in reverse registration order when the [SceneSession]
 *    disposes.
 */
interface SceneBridge : AutoCloseable {
    /** Called on the frame thread immediately before the session's ECS schedule advances. */
    fun beforeAdvance(world: World, delta: Float) = Unit

    /** Called on the frame thread immediately after the session's ECS schedule advances. */
    fun afterAdvance(world: World, delta: Float) = Unit

    /** Releases stores, handles, or UI registrations held by this bridge when the session disposes. */
    override fun close() = Unit
}

internal class SceneBridgeList {
    private val bridges = ArrayList<SceneBridge>()

    fun add(bridge: SceneBridge): AutoCloseable {
        bridges.add(bridge)
        return AutoCloseable { bridges.remove(bridge) }
    }

    fun beforeAdvance(world: World, delta: Float) {
        var index = 0
        while (index < bridges.size) {
            bridges[index].beforeAdvance(world, delta)
            index += 1
        }
    }

    fun afterAdvance(world: World, delta: Float) {
        var index = 0
        while (index < bridges.size) {
            bridges[index].afterAdvance(world, delta)
            index += 1
        }
    }

    fun closeAll(): Throwable? {
        var failure: Throwable? = null
        var index = bridges.size - 1
        while (index >= 0) {
            @Suppress("TooGenericExceptionCaught") // Every remaining bridge must still close on session teardown.
            try {
                bridges[index].close()
            } catch (error: Throwable) {
                if (failure == null) failure = error else failure.addSuppressed(error)
            }
            index -= 1
        }
        bridges.clear()
        return failure
    }
}
