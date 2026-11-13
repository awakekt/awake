/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime

import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.Column
import com.awakekt.awake.compose.foundation.layout.Spacer
import com.awakekt.awake.compose.foundation.layout.fillMaxSize
import com.awakekt.awake.compose.foundation.layout.fillMaxWidth
import com.awakekt.awake.compose.foundation.layout.height
import com.awakekt.awake.compose.foundation.layout.imePadding
import com.awakekt.awake.compose.foundation.layout.safeDrawingPadding
import com.awakekt.awake.compose.foundation.text.BasicTextField
import com.awakekt.awake.compose.foundation.text.TextFieldState
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.focus.FocusRequester
import com.awakekt.awake.compose.ui.focus.focusRequester
import com.awakekt.awake.compose.ui.layout.onPlaced
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.input.Input
import com.awakekt.awake.engine.platform.dsl.AppServiceLookup
import com.awakekt.awake.engine.platform.lifecycle.AppFrame
import com.awakekt.awake.engine.platform.lifecycle.DisplayInsets
import com.awakekt.awake.render.command.GpuDrawPreparationSource
import com.awakekt.awake.render.command.GpuDrawPreparer
import com.awakekt.awake.render.testing.NoopRenderer
import kotlinx.coroutines.test.runTest
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Compose UI the scene runtime hosts reads the platform's insets as the directly hosted path does:
 * `imePadding()` and `safeDrawingPadding()` follow what each [AppFrame] reports.
 */
class SceneHostWindowInsetsTest {
    private class PlainRenderer :
        NoopRenderer(),
        GpuDrawPreparationSource {
        override val gpuDrawPreparer = GpuDrawPreparer { _, _, _ -> null }
    }

    private data class Placed(val x: Int, val y: Int, val width: Int, val height: Int)

    private val input = Input()
    private var padded = Placed(-1, -1, -1, -1)
    private var field = Placed(-1, -1, -1, -1)
    private val focus = FocusRequester()
    private val text = TextFieldState()

    private suspend fun runtime(content: context(Composer) () -> Unit) = SceneAppLifecycleRuntime(
        SceneAppSpec(
            sceneName = null,
            systems = emptyList(),
            scenePopulationBlock = {},
            renderableFactory = { error("no renderable requested in this test") },
            assetLibraryFactory = null,
            updateBlock = { _, _ -> },
            ui = content,
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
        it.ready(PlainRenderer())
    }

    private fun SceneAppLifecycleRuntime.frame(
        density: Float = 1f,
        safeArea: DisplayInsets = DisplayInsets.None,
        ime: DisplayInsets = DisplayInsets.None,
    ) = update(AppFrame(1f / 60f, 400f, 800f, input.snapshot(), density = density, safeArea = safeArea, ime = ime))

    private suspend fun padding(pad: context(Composer) Modifier.() -> Modifier) = runtime {
        Box(Modifier.pad().fillMaxSize()) {
            Box(Modifier.fillMaxSize().onPlaced { x, y, w, h -> padded = Placed(x, y, w, h) })
        }
    }

    @Test
    fun contentUnderImePaddingIsLaidOutAboveTheKeyboardAndReturnsWhenItCloses() = runTest {
        val runtime = padding { imePadding() }

        runtime.frame()
        assertEquals(Placed(0, 0, 400, 800), padded)
        runtime.frame(ime = DisplayInsets(bottom = 300f))
        assertEquals(Placed(0, 0, 400, 500), padded, "keyboard open")
        runtime.frame(ime = DisplayInsets(bottom = 120f))
        assertEquals(Placed(0, 0, 400, 680), padded, "keyboard shorter")
        runtime.frame()
        assertEquals(Placed(0, 0, 400, 800), padded, "keyboard closed")
    }

    @Test
    fun safeDrawingPaddingInsetsContentByTheSafeAreaAndReturnsWhenItClears() = runTest {
        val runtime = padding { safeDrawingPadding() }
        val bars = DisplayInsets(left = 10f, top = 24f, right = 10f, bottom = 16f)

        runtime.frame(safeArea = bars)
        assertEquals(Placed(10, 24, 380, 760), padded)
        runtime.frame(safeArea = bars, ime = DisplayInsets(bottom = 300f))
        assertEquals(Placed(10, 24, 380, 476), padded, "the keyboard covers the bar behind it")
        runtime.frame()
        assertEquals(Placed(0, 0, 400, 800), padded)
    }

    @Test
    fun insetsAreDpSoTheyScaleWithDensity() = runTest {
        val runtime = padding { imePadding() }

        runtime.frame(density = 1f, ime = DisplayInsets(bottom = 100f))
        assertEquals(Placed(0, 0, 400, 700), padded)
        runtime.frame(density = 2f, ime = DisplayInsets(bottom = 100f))
        assertEquals(Placed(0, 0, 400, 600), padded)
    }

    @Test
    fun aFocusedFieldAtTheBottomRisesAboveTheKeyboardByPaddingAlone() = runTest {
        val runtime = runtime {
            Column(Modifier.imePadding().fillMaxSize()) {
                Spacer(Modifier.weight(1f))
                BasicTextField(
                    text,
                    Modifier.fillMaxWidth().height(40.dp).focusRequester(focus).onPlaced { x, y, w, h -> field = Placed(x, y, w, h) },
                )
            }
        }

        runtime.frame()
        focus.requestFocus()
        runtime.frame()
        assertTrue(input.textInputFocused, "the field holds focus")
        assertEquals(760, field.y, "bottom of the window while no keyboard shows")

        runtime.frame(ime = DisplayInsets(bottom = 300f))
        assertEquals(460, field.y)
        assertTrue(field.y + field.height <= 800 - 300, "the whole field is above the keyboard")
    }
}
