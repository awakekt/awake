/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime

import com.awakekt.awake.compose.ui.semantics.SemanticsNode
import com.awakekt.awake.core.input.Input
import com.awakekt.awake.engine.platform.dsl.AppServiceLookup
import com.awakekt.awake.engine.platform.lifecycle.AppFrame
import com.awakekt.awake.engine.platform.lifecycle.DisplayInsets
import com.awakekt.awake.render.command.GpuDrawPreparationSource
import com.awakekt.awake.render.command.GpuDrawPreparer
import com.awakekt.awake.render.testing.NoopRenderer
import com.awakekt.awake.ui.canvas.CanvasElement
import com.awakekt.awake.ui.canvas.CanvasInsets
import com.awakekt.awake.ui.canvas.CanvasScaleMode
import com.awakekt.awake.ui.canvas.CanvasScaler
import kotlinx.coroutines.test.runTest
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * The scene canvas keeps clear of the safe area and draws at the screen's real dots per inch as the
 * platform reports them with each frame, unless the host sets its own.
 */
class SceneCanvasPlatformTest {
    /** A renderer the scene's render planner accepts, that draws nothing. */
    private class PlainRenderer :
        NoopRenderer(),
        GpuDrawPreparationSource {
        override val gpuDrawPreparer = GpuDrawPreparer { _, _, _ -> null }
    }

    private val renderer = PlainRenderer()
    private val input = Input()
    private val hud = CanvasElement().apply { offsetX = 0f; offsetY = 0f; width = 100f; height = 20f }

    private val runtime = SceneAppLifecycleRuntime(
        SceneAppSpec(
            sceneName = null,
            systems = emptyList(),
            scenePopulationBlock = { world.add(world.create(), hud) },
            renderableFactory = { error("no renderable requested in this test") },
            assetLibraryFactory = null,
            updateBlock = { _, _ -> },
            ui = null,
            onReadyBlock = {},
            onDisposeBlock = {},
            serviceRegistrations = emptyList(),
        ),
    ).also {
        it.initialize(
            object : AppServiceLookup {
                @Suppress("UNCHECKED_CAST")
                override fun <T : Any> service(type: KClass<T>): T? = if (type == Input::class) input as T else null
            },
        )
    }

    private fun frame(safeArea: DisplayInsets = DisplayInsets.None, dpi: Float = 0f): List<Int> {
        runtime.update(AppFrame(1f / 60f, 800f, 600f, input.snapshot(), density = 1f, safeArea = safeArea, dpi = dpi))
        val node = assertNotNull(runtime.uiSemantics.findTag("canvas-element-"))
        return listOf(node.x, node.y, node.width, node.height)
    }

    @Test
    fun theCanvasKeepsClearOfThePlatformsSafeArea() = runTest {
        runtime.ready(renderer)

        assertEquals(listOf(0, 0, 100, 20), frame())
        assertEquals(listOf(44, 24, 100, 20), frame(DisplayInsets(left = 44f, top = 24f)))
    }

    @Test
    fun aHostsOwnSafeAreaTakesThePlatformsPlace() = runTest {
        runtime.ready(renderer)
        runtime.canvasSafeArea = CanvasInsets(left = 10f, top = 5f)

        assertEquals(listOf(10, 5, 100, 20), frame(DisplayInsets(left = 44f, top = 24f)), "an editor previewing a device")
    }

    @Test
    fun aPhysicalSizeScalerDrawsAtThePlatformsDpiUnlessTheHostSetsOne() = runTest {
        runtime.ready(renderer)
        runtime.world.add(runtime.world.create(), CanvasScaler(mode = CanvasScaleMode.ConstantPhysicalSize, referenceDpi = 160f))

        assertEquals(100, frame()[2], "an unknown dpi takes 160 per dp: a unit is a dp")
        assertEquals(200, frame(dpi = 320f)[2], "320 real dpi at 1 pixel per dp: a unit is two pixels")
        runtime.canvasDpi = 80f
        assertEquals(50, frame(dpi = 320f)[2], "the host's own dpi wins")
    }
}

private fun List<SemanticsNode>.findTag(prefix: String): SemanticsNode? =
    firstNotNullOfOrNull { if (it.testTag?.startsWith(prefix) == true) it else it.children.findTag(prefix) }
