/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.compose.foundation.background
import com.awakekt.awake.compose.foundation.clickable
import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.BoxScope
import com.awakekt.awake.compose.foundation.layout.fillMaxSize
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.CompositionLocalProvider
import com.awakekt.awake.compose.runtime.compositionLocalOf
import com.awakekt.awake.compose.runtime.current
import com.awakekt.awake.compose.testing.rasterize
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.layout.onPlaced
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.math2d.Dp
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.ui.canvas.CanvasAnchor
import com.awakekt.awake.ui.canvas.CanvasElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * `compose { }` draws Compose UI in a canvas element's box: laid out in that box, drawn in the
 * element's place in canvas order, and reached by the pointer through the canvas's hit testing.
 */
class CanvasContentComposeTest {
    private val world = World()
    private val hud = world.create().also { world.add(it, Transform()) }
    private val host = ComposeHost()

    private fun CanvasElementScope.box(x: Float, y: Float, w: Float = 60f, h: Float = 40f) {
        anchor = CanvasAnchor.TopLeft
        offset(Dp(x), Dp(y))
        size(Dp(w), Dp(h))
    }

    private fun frame(x: Int = -1, y: Int = -1, pressed: Boolean = false, down: Boolean = false, released: Boolean = false) =
        host.frame(FrameInput(WIDTH, HEIGHT, pointerX = x, pointerY = y, pointerDown = down, pointerPressed = pressed, pointerReleased = released)) {
            SceneCanvas(world)
        }

    private fun tap(x: Int, y: Int) {
        frame(x, y)
        frame(x, y, pressed = true, down = true)
        frame(x, y, released = true)
    }

    private fun pixelAt(x: Int, y: Int): Int {
        val pixels = frame().primitives.rasterize(WIDTH, HEIGHT, Color.Black)
        val offset = (y * WIDTH + x) * 4
        return ((pixels[offset].toInt() and 0xFF) shl 16) or ((pixels[offset + 1].toInt() and 0xFF) shl 8) or (pixels[offset + 2].toInt() and 0xFF)
    }

    @Test
    fun theContentIsLaidOutInTheElementsBox() {
        var placed = IntArray(0)
        canvasContent(world, hud) {
            panel(id = "frame", settings = { box(10f, 20f) }) {
                compose { Box(Modifier.fillMaxSize().onPlaced { x, y, w, h -> placed = intArrayOf(x, y, w, h) }) }
            }
        }

        frame()

        assertEquals(listOf(10, 20, 60, 40), placed.toList())
    }

    @Test
    fun anElementDrawnLaterCoversTheContent() {
        canvasContent(world, hud) {
            panel(id = "under", settings = { box(0f, 0f) }) { compose { Box(Modifier.fillMaxSize().background(Color(1f, 0f, 0f, 1f))) } }
            panel(id = "over", settings = { box(30f, 0f); background = Color(0f, 1f, 0f, 1f) })
        }

        assertEquals(RED, pixelAt(10, 10), "the content fills its element")
        assertEquals(GREEN, pixelAt(40, 10), "the later sibling draws over it")
    }

    @Test
    fun theElementsOwnChildrenDrawOverTheContent() {
        canvasContent(world, hud) {
            panel(id = "frame", settings = { box(0f, 0f) }) {
                compose { Box(Modifier.fillMaxSize().background(Color(1f, 0f, 0f, 1f))) }
                panel(id = "badge", settings = { box(0f, 0f, 20f, 20f); background = Color(0f, 1f, 0f, 1f) })
            }
        }

        assertEquals(GREEN, pixelAt(10, 10))
        assertEquals(RED, pixelAt(40, 30))
    }

    @Test
    fun aTapReachesTheContent() {
        var clicks = 0
        canvasContent(world, hud) {
            panel(id = "frame", settings = { box(0f, 0f) }) { compose { Box(Modifier.fillMaxSize().clickable { clicks++ }) } }
        }

        tap(20, 20)

        assertEquals(1, clicks)
    }

    @Test
    fun aButtonDrawnOverTheContentTakesTheTapFirst() {
        var clicks = 0
        canvasContent(world, hud) {
            panel(id = "frame", settings = { box(0f, 0f) }) { compose { Box(Modifier.fillMaxSize().clickable { clicks++ }) } }
            button(id = "close") { box(0f, 0f, 30f, 30f) }
        }

        tap(10, 10)

        assertEquals(0, clicks, "the canvas's own hit testing put the button first")
        assertEquals(true, world.get<CanvasElement>(checkNotNull(world.findCanvasElement(hud, "close")))?.consumePress())
    }

    @Test
    fun stateAndLocalsReachTheContent() {
        val theme = compositionLocalOf { "default" }
        var label = "one"
        var seen = ""
        val hudContent = canvasContent(world, hud) {
            val read = label
            CompositionLocalProvider(theme, "dark") {
                panel(id = "frame", settings = { box(0f, 0f) }) { compose { seen = "$read/${theme.current}" } }
            }
        }
        frame()
        assertEquals("one/dark", seen)

        label = "two"
        hudContent.recompose()
        frame()

        assertEquals("two/dark", seen)
    }

    @Test
    fun anUnchangedRecomposeWritesNothing() {
        val ui: context(Composer)
        BoxScope.() -> Unit = { Box(Modifier.fillMaxSize()) }
        val hudContent = canvasContent(world, hud) { panel(id = "frame", settings = { box(0f, 0f) }) { compose(ui) } }
        val writes = hudContent.applier.writes

        repeat(3) { hudContent.recompose() }

        assertEquals(writes, hudContent.applier.writes, "the same lambda is no write")
    }

    @Test
    fun theContentIsNeverSaved() {
        canvasContent(world, hud) { panel(id = "frame", settings = { box(0f, 0f) }) { compose { Box(Modifier.fillMaxSize()) } } }

        val saved = world.saveCanvas()
        val loaded = loadCanvas(saved)

        assertEquals(saved, loaded.saveCanvas())
        val root = loaded.query<Transform>().first { loaded.get<Transform>(it)?.parent == null }
        assertNull(loaded.get<CanvasElement>(checkNotNull(loaded.findCanvasElement(root, "frame")))?.composeContent)
    }

    @Test
    fun anElementThatStopsDeclaringItStopsDrawingIt() {
        var shown = true
        val hudContent = canvasContent(world, hud) {
            panel(id = "frame", settings = { box(0f, 0f) }) { if (shown) compose { Box(Modifier.fillMaxSize()) } }
        }

        shown = false
        hudContent.recompose()

        assertNull(world.get<CanvasElement>(checkNotNull(world.findCanvasElement(hud, "frame")))?.composeContent)
    }

    private companion object {
        const val WIDTH = 120
        const val HEIGHT = 80
        const val RED = 0xFF0000
        const val GREEN = 0x00FF00
    }
}
