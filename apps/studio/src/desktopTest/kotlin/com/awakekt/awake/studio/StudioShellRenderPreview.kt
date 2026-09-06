/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.studio
import com.awakekt.awake.compose.testing.rasterize
import com.awakekt.awake.compose.testing.toBufferedImage
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.core.text.font.UiFont
import com.awakekt.awake.core.text.font.UiFonts
import com.awakekt.awake.editor.core.store.EditorEntityId
import com.awakekt.awake.editor.core.store.EditorIntent
import com.awakekt.awake.editor.core.store.EditorStore
import com.awakekt.awake.engine.bootstrap.dsl.app
import com.awakekt.awake.engine.bootstrap.dsl.module
import com.awakekt.awake.engine.platform.lifecycle.AppFrame
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.render.testing.NoopRenderer
import com.awakekt.awake.studio.state.DOCK_TAB_TIMELINE
import com.awakekt.awake.studio.state.StudioContract
import com.awakekt.awake.studio.state.StudioStore
import kotlinx.coroutines.test.runTest
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Renders the real Studio shell to a PNG so a human can look at it.
 *
 * Investigation, not a gate. It exists because Studio has no parity coverage of any kind: the
 * shadcn harness compares components against browser captures, and nothing checks this shell's
 * layout. Porting its 471 lines to the compose engine with no before-and-after would be changing
 * a screen nobody can see.
 *
 * Drives the whole app rather than calling the shell directly -- `drawStudioShellBody` takes ten
 * parameters, seven of them services, and assembling those by hand would render a different screen
 * than the one that ships.
 */
class StudioShellRenderPreview {

    private val density = 2f

    private class CapturingRenderer : NoopRenderer() {
        var primitives: List<UiDrawPrimitive> = emptyList()
            private set
        var font: UiFont? = null
            private set

        override fun drawUi(primitives: List<UiDrawPrimitive>, font: UiFont?) {
            this.primitives = primitives
            this.font = font ?: this.font
        }
    }

    @Test
    fun writeStudioShell() = runTest {
        val width = 1440
        val height = 1200
        val renderer: Renderer = CapturingRenderer()
        val store = StudioStore()
        val bridge = com.awakekt.awake.studio.state.StudioEditorBridge(store)
        val game = app { module(studioModule(store = store, editorBridge = bridge)) }
        game.ready(renderer)
        // Select entity 3 (Cube) so the Inspector renders Transform with Position, Rotation, Scale!
        bridge.store.dispatch(EditorIntent.SelectEntity(EditorEntityId("3")))
        repeat(3) {
            game.update(
                AppFrame(
                    delta = 1f / 60f,
                    viewportWidth = width.toFloat(),
                    viewportHeight = height.toFloat(),
                    input = game.input.currentSnapshot,
                    density = density,
                ),
            )
        }

        val captured = renderer as CapturingRenderer
        assertTrue(captured.primitives.isNotEmpty(), "the studio shell drew nothing")

        // The count is not a regression signal: the status bar prints frame timings, so a run at
        // `sim+render 16.4ms` draws one more glyph than one at `1.6ms`. The image is the evidence.
        val out = File("build/reports/studio-preview").apply { mkdirs() }
        val file = File(out, "studio-shell.png")
        val pixels = captured.primitives.rasterize(
            width,
            height,
            background = Color(0.09f, 0.09f, 0.11f, 1f),
            font = captured.font ?: UiFonts.default(),
        )
        ImageIO.write(pixels.toBufferedImage(width, height), "png", file)
        println("studio-preview: ${file.absolutePath} (${captured.primitives.size} primitives)")
    }

    @Test
    fun writeStudioShellCesiumTimeline() = runTest {
        val width = 1440
        val height = 900
        val store = StudioStore()
        store.selectScene("cesium-man")
        store.dispatch(StudioContract.Intent.SelectDockTab(DOCK_TAB_TIMELINE))
        val renderer: Renderer = CapturingRenderer()
        val module = studioModule(store)
        val game = app { module(module) }
        game.ready(renderer)
        repeat(5) {
            game.update(
                AppFrame(
                    delta = 1f / 60f,
                    viewportWidth = width.toFloat(),
                    viewportHeight = height.toFloat(),
                    input = game.input.currentSnapshot,
                    density = density,
                ),
            )
        }

        val captured = renderer as CapturingRenderer
        assertTrue(captured.primitives.isNotEmpty(), "the studio shell drew nothing")

        val out = File("build/reports/studio-preview").apply { mkdirs() }
        val file = File(out, "studio-shell-cesium-timeline.png")
        val pixels = captured.primitives.rasterize(
            width,
            height,
            background = Color(0.09f, 0.09f, 0.11f, 1f),
            font = captured.font ?: UiFonts.default(),
        )
        ImageIO.write(pixels.toBufferedImage(width, height), "png", file)
        println("studio-preview-cesium: ${file.absolutePath} (${captured.primitives.size} primitives)")
    }
}
