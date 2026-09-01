/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.studio

import io.github.awakelab.awake.compose.testing.rasterize
import io.github.awakelab.awake.compose.testing.toBufferedImage
import io.github.awakelab.awake.core.color.Color
import io.github.awakelab.awake.core.graphics2d.UiDrawPrimitive
import io.github.awakelab.awake.core.text.font.UiFont
import io.github.awakelab.awake.core.text.font.UiFonts
import io.github.awakelab.awake.engine.bootstrap.dsl.app
import io.github.awakelab.awake.engine.platform.lifecycle.AppFrame
import io.github.awakelab.awake.engine.bootstrap.dsl.module
import io.github.awakelab.awake.render.renderer.Renderer
import io.github.awakelab.awake.render.testing.NoopRenderer
import io.github.awakelab.awake.studio.state.StudioStore
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

    private val DENSITY = 2f

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
        val height = 900
        val renderer: Renderer = CapturingRenderer()
        val game = app { module(studioModule(StudioStore())) }
        game.ready(renderer)
        // Two frames: the first has no placed geometry to hit-test, so anything hover-driven is
        // resolved against nothing. The second is a steady-state frame.
        repeat(2) {
            game.update(
                AppFrame(
                    delta = 1f / 60f,
                    viewportWidth = width.toFloat(),
                    viewportHeight = height.toFloat(),
                    input = game.input.currentSnapshot,
                    density = DENSITY,
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

}
