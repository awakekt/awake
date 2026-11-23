/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.authoring

import com.awakekt.awake.compose.foundation.text.Text
import com.awakekt.awake.core.state.ContextualEffectDrainer
import com.awakekt.awake.core.state.childStoreScope
import com.awakekt.awake.core.state.reducerStore
import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World
import com.awakekt.awake.engine.bootstrap.dsl.app
import com.awakekt.awake.engine.platform.dsl.requireService
import com.awakekt.awake.scene.runtime.SceneAppLifecycleRuntime
import com.awakekt.awake.scene.runtime.bridge
import com.awakekt.awake.scene.runtime.session.SceneBridge
import com.awakekt.awake.scene.runtime.session.SceneBridgeHandle
import com.awakekt.awake.scene.runtime.session.SceneSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SceneBridgeTest {
    private data class CounterComponent(var value: Int = 0, var pendingDelta: Int = 0)

    private data class HudState(val value: Int = 0)

    private sealed interface HudIntent {
        data class Synced(val value: Int) : HudIntent
        data class RequestIncrement(val delta: Int) : HudIntent
    }

    private sealed interface HudEffect {
        data class ApplyIncrement(val delta: Int) : HudEffect
    }

    private fun recordingBridge(tag: String, trace: MutableList<String>): SceneBridge = object : SceneBridge {
        override fun beforeAdvance(world: World, delta: Float) {
            trace += "$tag:before"
        }

        override fun afterAdvance(world: World, delta: Float) {
            trace += "$tag:after"
        }

        override fun close() {
            trace += "$tag:close"
        }
    }

    @Test
    fun bridgeRunsAroundEcsScheduleBeforeUiAndDisposesInReverseOrder() = runTest {
        val trace = mutableListOf<String>()
        val game = app {
            scene("bridge-order") {
                fixedSystem("fixed-sim") {
                    object : System {
                        override fun update(world: World, delta: Float) {
                            trace += "fixed"
                        }
                    }
                }
                frameSystem("frame-sim") {
                    object : System {
                        override fun update(world: World, delta: Float) {
                            trace += "frame"
                        }
                    }
                }
                bridge { recordingBridge("bridge1", trace) }
                bridge { recordingBridge("bridge2", trace) }
                ui {
                    trace += "ui"
                    Text("Bridge")
                }
            }
        }

        game.ready(RecordingRenderer())
        trace.clear() // Ignore initial schedule.synchronize(world) pass during ready().
        val session = game.requireService<SceneSession>()
        var detachedClosed = false
        val dynamicHandle = session.addBridge(
            object : SceneBridge {
                override fun afterAdvance(world: World, delta: Float) {
                    trace += "dynamic:after"
                }

                override fun close() {
                    detachedClosed = true
                }
            },
        )

        game.update(1f / 60f, 320f, 240f)
        assertEquals(
            listOf("bridge1:before", "bridge2:before", "fixed", "frame", "bridge1:after", "bridge2:after", "dynamic:after", "ui"),
            trace,
        )

        trace.clear()
        dynamicHandle.close()
        assertFalse(detachedClosed)
        game.update(1f / 60f, 320f, 240f)
        assertEquals(listOf("bridge1:before", "bridge2:before", "fixed", "frame", "bridge1:after", "bridge2:after", "ui"), trace)

        trace.clear()
        game.dispose()
        assertEquals(listOf("bridge2:close", "bridge1:close"), trace)
        assertFalse(detachedClosed)
    }

    @Test
    fun contextualDrainerInsideSceneBridgeDeliversUiEffectsWithZeroFrameLag() = runTest {
        val store = reducerStore<HudState, HudIntent, HudEffect>(HudState()) { state, intent ->
            when (intent) {
                is HudIntent.Synced -> state.copy(value = intent.value) to null
                is HudIntent.RequestIncrement -> state to HudEffect.ApplyIncrement(intent.delta)
            }
        }
        val renderedValues = mutableListOf<Int>()

        val game = app {
            scene("hud-bridge") {
                scene {
                    entity("player") {
                        with(CounterComponent(value = 10))
                    }
                }
                // Pure ECS simulation system: zero Compose or Store imports.
                frameSystem("apply-counter") {
                    object : System {
                        override fun update(world: World, delta: Float) {
                            world.queryEach(CounterComponent::class) { _, counter ->
                                if (counter.pendingDelta != 0) {
                                    counter.value += counter.pendingDelta
                                    counter.pendingDelta = 0
                                }
                            }
                        }
                    }
                }
                bridge {
                    val drainer = ContextualEffectDrainer<World>()
                    drainer.bind(
                        store = store,
                        closeWithDrainer = true,
                        sync = { world, state ->
                            world.queryEach(CounterComponent::class) { _, counter ->
                                if (counter.value != state.value) {
                                    store.dispatch(HudIntent.Synced(counter.value))
                                }
                            }
                        },
                        onEffect = { world, effect ->
                            when (effect) {
                                is HudEffect.ApplyIncrement -> {
                                    world.queryEach(CounterComponent::class) { _, counter ->
                                        counter.pendingDelta += effect.delta
                                    }
                                }
                            }
                        },
                    )
                    object : SceneBridge {
                        override fun beforeAdvance(world: World, delta: Float) = drainer.drainEffects(world)
                        override fun afterAdvance(world: World, delta: Float) = drainer.sync(world)
                        override fun close() = drainer.close()
                    }
                }
                ui {
                    renderedValues += store.value.value
                    Text("Count: ${store.value.value}")
                }
            }
        }

        game.ready(RecordingRenderer())

        // Frame 1: afterAdvance syncs initial value 10 before ui composes.
        game.update(1f / 60f, 320f, 240f)
        assertEquals(listOf(10), renderedValues)

        // Simulated UI button click between frames queues an effect.
        store.dispatch(HudIntent.RequestIncrement(5))

        // Frame 2: beforeAdvance drains +5 into ECS, frameSystem applies it, afterAdvance syncs 15 before ui composes!
        game.update(1f / 60f, 320f, 240f)
        assertEquals(listOf(10, 15), renderedValues)

        assertTrue(store.coroutineScope.isActive)
        game.dispose()
        assertFalse(store.coroutineScope.isActive)
    }

    private class CounterBridge(parentScope: CoroutineScope) : SceneBridge {
        private val drainer = ContextualEffectDrainer<World>()
        val store = reducerStore<HudState, HudIntent, HudEffect>(
            initialState = HudState(),
            scope = parentScope.childStoreScope(),
        ) { state, intent ->
            when (intent) {
                is HudIntent.Synced -> state.copy(value = intent.value) to null
                is HudIntent.RequestIncrement -> state to HudEffect.ApplyIncrement(intent.delta)
            }
        }

        init {
            drainer.bind(
                store = store,
                closeWithDrainer = true,
                sync = { world, state ->
                    world.queryEach(CounterComponent::class) { _, counter ->
                        if (counter.value != state.value) store.dispatch(HudIntent.Synced(counter.value))
                    }
                },
                onEffect = { world, effect ->
                    if (effect is HudEffect.ApplyIncrement) {
                        world.queryEach(CounterComponent::class) { _, counter -> counter.pendingDelta += effect.delta }
                    }
                },
            )
        }

        override fun beforeAdvance(world: World, delta: Float) = drainer.drainEffects(world)

        override fun afterAdvance(world: World, delta: Float) = drainer.sync(world)

        override fun close() = drainer.close()
    }

    private fun applyCounterSystem(): System = object : System {
        override fun update(world: World, delta: Float) {
            world.queryEach(CounterComponent::class) { _, counter ->
                if (counter.pendingDelta != 0) {
                    counter.value += counter.pendingDelta
                    counter.pendingDelta = 0
                }
            }
        }
    }

    @Test
    fun pairedUiBridgeShorthandActsAsSceneScopedViewModelAndSurvivesSpecReuse() = runTest {
        val renderedA = mutableListOf<Int>()
        val renderedB = mutableListOf<Int>()
        var sink = renderedA
        lateinit var handle: SceneBridgeHandle<CounterBridge>

        val reusableSpec = sceneApp {
            scene { entity("counter") { with(CounterComponent(value = 4)) } }
            frameSystem("apply-counter") { applyCounterSystem() }
            handle = ui(bridge = { CounterBridge(session.coroutineScope) }) { vm ->
                val state = vm.store.value
                sink += state.value
                Text("Count: ${state.value}")
            }
        }

        val appA = app { scene(reusableSpec) }
        appA.ready(RecordingRenderer())
        val vmA = appA.requireService<SceneAppLifecycleRuntime>().bridge(handle)
        appA.update(1f / 60f, 320f, 240f)
        vmA.store.dispatch(HudIntent.RequestIncrement(3))
        appA.update(1f / 60f, 320f, 240f)
        assertEquals(listOf(4, 7), renderedA)

        // Installing the same SceneAppSpec again produces an isolated session-scoped bridge instance.
        sink = renderedB
        val appB = app { scene(reusableSpec) }
        appB.ready(RecordingRenderer())
        val vmB = appB.requireService<SceneAppLifecycleRuntime>().bridge(handle)
        assertTrue(vmA !== vmB)
        appB.update(1f / 60f, 320f, 240f)
        assertEquals(listOf(4), renderedB)

        appA.dispose()
        assertFalse(vmA.store.coroutineScope.isActive)
        assertTrue(vmB.store.coroutineScope.isActive)

        appB.dispose()
        assertFalse(vmB.store.coroutineScope.isActive)
    }
}
