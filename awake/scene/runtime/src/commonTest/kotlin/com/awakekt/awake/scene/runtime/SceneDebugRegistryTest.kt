/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.diagnostics.DebugLines
import com.awakekt.awake.core.diagnostics.DebugRegistry
import com.awakekt.awake.core.diagnostics.DebugRows
import com.awakekt.awake.core.diagnostics.DebugSource
import com.awakekt.awake.core.input.Input
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.engine.platform.dsl.AppServiceLookup
import com.awakekt.awake.engine.platform.lifecycle.AppFrame
import com.awakekt.awake.render.command.GpuDrawPreparationSource
import com.awakekt.awake.render.command.GpuDrawPreparer
import com.awakekt.awake.render.renderer.LineSegment
import com.awakekt.awake.render.testing.NoopRenderer
import kotlinx.coroutines.test.runTest
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals

/** A game on the default infrastructure systems turns its debug sources' lines on with one app service. */
class SceneDebugRegistryTest {
    private class LineRecorder :
        NoopRenderer(),
        GpuDrawPreparationSource {
        override val gpuDrawPreparer = GpuDrawPreparer { _, _, _ -> null }
        val lineCalls = ArrayList<List<LineSegment>>()

        override fun drawDebugLines(lines: List<LineSegment>) {
            lineCalls += lines.toList()
        }
    }

    private class OneLine : DebugSource {
        override val id = "cells"
        override val title = "Cells"
        override fun rows(out: DebugRows) = Unit
        override fun lines(out: DebugLines) = out.line(Vec3f(), Vec3f(0f, 1f, 0f), Color.White)
    }

    private fun frameWith(registry: DebugRegistry?): LineRecorder = LineRecorder().also { renderer ->
        val input = Input()
        val runtime = SceneAppLifecycleRuntime(
            SceneAppSpec(
                sceneName = null,
                systems = emptyList(),
                scenePopulationBlock = {},
                renderableFactory = { error("no renderable requested in this test") },
                assetLibraryFactory = null,
                updateBlock = { _, _ -> },
                ui = null,
                onReadyBlock = {},
                onDisposeBlock = {},
                serviceRegistrations = emptyList(),
            ),
        )
        runtime.initialize(
            object : AppServiceLookup {
                @Suppress("UNCHECKED_CAST")
                override fun <T : Any> service(type: KClass<T>): T? = when (type) {
                    Input::class -> input as T
                    DebugRegistry::class -> registry as T?
                    else -> null
                }
            },
        )
        runTest { runtime.ready(renderer) }
        runtime.update(AppFrame(1f / 60f, 800f, 600f, input.snapshot(), density = 1f))
    }

    @Test
    fun aRegisteredRegistrysSourcesDrawThroughTheDefaultSystems() {
        val registry = DebugRegistry(setOf("cells")).also { it.register(OneLine()) }

        val renderer = frameWith(registry)

        // ready() runs a first frame to synchronize, then update() runs one: each draws the source's line once.
        assertEquals(2, renderer.lineCalls.size)
        for (lines in renderer.lineCalls) assertEquals(listOf(Vec3f(0f, 1f, 0f)), lines.map { it.end })
    }

    @Test
    fun withNoRegistryTheDefaultSystemsDrawNoLines() {
        assertEquals(0, frameWith(null).lineCalls.size)
    }
}
