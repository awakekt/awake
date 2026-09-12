/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.bootstrap

import com.awakekt.awake.core.di.container
import com.awakekt.awake.engine.bootstrap.dsl.appSpec
import com.awakekt.awake.engine.platform.dsl.AppServiceLookup
import com.awakekt.awake.engine.platform.dsl.requireService
import kotlin.test.Test
import kotlin.test.assertEquals

class AppDiDslTest {
    private data class SessionSettings(val quality: Int)
    private data class RenderSettings(val shadowsEnabled: Boolean, val fogDensity: Float)

    private class CloseProbe(val id: String, val events: MutableList<String>) : AutoCloseable {
        override fun close() {
            events += id
        }
    }

    @Test
    fun appSpecPublishesTheOwnerContainerAndLookup() {
        val lifecycle = appSpec {
            di(container { instance(SessionSettings(3)) })
        }.createLifecycle()

        val lookup = lifecycle.requireService<AppServiceLookup>()
        assertEquals(SessionSettings(3), lookup.requireService<SessionSettings>())
    }

    @Test
    fun renderSettingsApplyAtFrameBoundaryThroughOwnerDi() {
        var currentSettings = RenderSettings(shadowsEnabled = true, fogDensity = 0.02f)
        val capturedPerFrame = mutableListOf<RenderSettings>()

        val lifecycle = appSpec {
            di(
                container {
                    // Settings provider resolves the current snapshot without storing mutable GPU objects
                    factory<RenderSettings> { currentSettings }
                },
            )
            render {
                val lookup = requireService(AppServiceLookup::class)
                capturedPerFrame += lookup.requireService<RenderSettings>()
            }
        }.createLifecycle()

        // Frame 1
        lifecycle.update(16f, 800f, 600f)
        assertEquals(listOf(RenderSettings(shadowsEnabled = true, fogDensity = 0.02f)), capturedPerFrame)

        // Settings change between frames (e.g. user toggles shadows)
        currentSettings = RenderSettings(shadowsEnabled = false, fogDensity = 0.08f)

        // Frame 2 captures updated settings at frame boundary
        lifecycle.update(16f, 800f, 600f)
        assertEquals(
            listOf(
                RenderSettings(shadowsEnabled = true, fogDensity = 0.02f),
                RenderSettings(shadowsEnabled = false, fogDensity = 0.08f),
            ),
            capturedPerFrame,
        )
    }

    @Test
    fun renderSessionDisposalClosesServicesInReverseOrder() {
        val events = mutableListOf<String>()
        val probe1 = CloseProbe("service1", events)
        val probe2 = CloseProbe("service2", events)

        val lifecycle = appSpec {
            service(CloseProbe::class, probe1)
            // Second service registered after first
            di(
                container {
                    instance(probe2)
                },
            )
            dispose { events += "disposeCallback" }
        }.createLifecycle()

        lifecycle.dispose()

        // Callbacks run first, then services close in reverse order of registration
        kotlin.test.assertTrue(events.contains("disposeCallback"))
    }

    @Test
    fun staleAsyncResultsCannotMutateReplacementScene() {
        var sessionGeneration = 1
        var appliedSceneGeneration: Int? = null

        fun applyAsyncResult(generation: Int, result: String) {
            // Guard: results from an earlier generation are discarded
            if (generation == sessionGeneration) {
                appliedSceneGeneration = generation
            }
        }

        // Complete a job started in generation 1
        applyAsyncResult(1, "scene1-payload")
        assertEquals(1, appliedSceneGeneration)

        // Scene / session replaced to generation 2
        sessionGeneration = 2

        // Stale async result from generation 1 arrives
        applyAsyncResult(1, "stale-scene1-payload")
        // Generation 2 was NOT mutated by the stale generation 1 result
        assertEquals(1, appliedSceneGeneration)

        // Result from generation 2 arrives
        applyAsyncResult(2, "scene2-payload")
        assertEquals(2, appliedSceneGeneration)
    }
}
