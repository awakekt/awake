/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.showcase.docs.debugtools

import com.awakekt.awake.asset.shaderpack.LitShadowUniformLayout
import com.awakekt.awake.core.diagnostics.DebugRegistry
import com.awakekt.awake.core.geometry.generate.generate
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World
import com.awakekt.awake.engine.bootstrap.dsl.app
import com.awakekt.awake.engine.platform.HeadlessSurface
import com.awakekt.awake.engine.platform.lifecycle.AwakeAppLifecycle
import com.awakekt.awake.render.capture.PixelMap
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.render.renderer.createMaterial
import com.awakekt.awake.render.testing.writePng
import com.awakekt.awake.scene.authoring.dsl.camera
import com.awakekt.awake.scene.authoring.dsl.directionalLight
import com.awakekt.awake.scene.authoring.dsl.mesh
import com.awakekt.awake.scene.authoring.dsl.scene
import com.awakekt.awake.scene.authoring.dsl.transform
import com.awakekt.awake.scene.authoring.scene
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.world.StreamObserver
import com.awakekt.awake.scene.world.WorldPartitionSystem
import com.awakekt.awake.showcase.docs.GameRenderPlan
import com.awakekt.awake.showcase.docs.SpawnerDebugSource
import com.awakekt.awake.showcase.docs.SpawnerProbe
import com.awakekt.awake.vulkan.application.VulkanEngine
import com.awakekt.awake.world.AsyncWorldCellStreamListener
import com.awakekt.awake.world.CellContent
import com.awakekt.awake.world.WorldCellCoord
import com.awakekt.awake.world.WorldPartitionConfig
import com.awakekt.awake.world.WorldPartitionDebugSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Renders the "Debug tools" guide's figure on Vulkan with no window: a player streaming cells, the `cells` and
 * `spawner` sources on, so the frame has the cell grid, the radius circles and the debug panel.
 *
 * The figure is `build/reports/render-captures/debug-tools.png`, copied to
 * `website/docs/assets/guides/debug-tools/cells-and-panel.png`.
 *
 * Negative control: the same scene with [DebugRegistry.Off], measured on lavapipe at green 20, yellow 41, red 8,
 * blue 107 (the cube's coloured faces) and no panel pixels, against green 1239, yellow 847, red 666, blue 1129 and
 * 30469 panel pixels with the sources on. Each colour is asserted against that control, so the count is the lines'.
 */
class DebugToolsMainDocsSampleTest {

    @Test
    fun theCellGridAndThePanelAreDrawn() = runBlocking {
        val on = render(DebugRegistry(setOf(WorldPartitionDebugSource.ID, "spawner")))
        val off = render(DebugRegistry.Off)
        PixelMap(WIDTH, HEIGHT, on.copyOf()).writePng(File("build/reports/render-captures/debug-tools.png"))

        val counts = Counts(on)
        val control = Counts(off)
        println("Debug tools on: $counts; off: $control")
        // The cube's coloured faces count too, so each colour is measured against the same frame with the sources off.
        assertTrue(counts.green - control.green > MIN_LINE_PIXELS, "shown cells are outlined in green: $counts / $control")
        assertTrue(counts.yellow - control.yellow > MIN_LINE_PIXELS, "reading cells are outlined in yellow: $counts / $control")
        assertTrue(counts.red - control.red > MIN_LINE_PIXELS, "leaving cells are outlined in red: $counts / $control")
        assertTrue(counts.blue - control.blue > MIN_LINE_PIXELS, "the loading circle is drawn: $counts / $control")
        assertTrue(counts.panel > MIN_PANEL_PIXELS, "the panel is drawn in the bottom right: $counts")
        assertTrue(control.panel == 0, "no panel with the sources off: $control")
    }

    private suspend fun render(registry: DebugRegistry): ByteArray {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val app = debugToolsFigure(registry, SpawnerProbe().apply { alive = 64; spawned = 210; spawnPoint.set(-6f, 0f, 6f) }, scope)
        val renderer: Renderer = FigureEngine(app).boot()
        try {
            app.ready(renderer)
            repeat(FRAMES) { app.update(1f / 60f, WIDTH.toFloat(), HEIGHT.toFloat()) }
            return renderer.readPresentedPixels().data.copyOf()
        } finally {
            app.dispose()
            renderer.destroy()
            scope.cancel()
        }
    }

    /** Line and panel pixels, by colour. */
    private class Counts(rgba: ByteArray) {
        var green = 0
        var yellow = 0
        var red = 0
        var blue = 0
        var panel = 0

        init {
            for (pixel in 0 until rgba.size / 4) {
                val r = rgba[pixel * 4].toInt() and BYTE
                val g = rgba[pixel * 4 + 1].toInt() and BYTE
                val b = rgba[pixel * 4 + 2].toInt() and BYTE
                when {
                    r > BRIGHT && g > BRIGHT && b < DIM -> yellow++
                    g > BRIGHT && r < DIM && b < BRIGHT -> green++
                    r > BRIGHT && g < DIM && b < DIM -> red++
                    b > BRIGHT && r < DIM && g > DIM -> blue++
                }
                // The panel is a near-black box pinned to the bottom right; the lit ground is far brighter.
                val inCorner = pixel % WIDTH > WIDTH - PANEL_REGION && pixel / WIDTH > HEIGHT - PANEL_REGION
                if (inCorner && maxOf(r, g, b) < DARK) panel++
            }
        }

        override fun toString() = "green=$green yellow=$yellow red=$red blue=$blue panel=$panel"
    }

    private class FigureEngine(app: AwakeAppLifecycle) : VulkanEngine(app, GameRenderPlan) {
        suspend fun boot(): Renderer = createBackendResources(HeadlessSurface(WIDTH, HEIGHT)).renderer
    }

    private companion object {
        const val WIDTH = 960
        const val HEIGHT = 540
        const val FRAMES = 30
        const val BYTE = 0xFF
        const val BRIGHT = 150
        const val DIM = 110
        const val DARK = 80
        const val PANEL_REGION = 200
        const val MIN_LINE_PIXELS = 100
        const val MIN_PANEL_PIXELS = 5_000
    }
}

private val FIGURE_CELLS = WorldPartitionConfig(cellSize = 4f, loadingRadius = 8f, unloadRadius = 12f)

/** Where the player starts, and where it walks to on [WALK_FRAME], leaving cells behind it. */
private const val START_X = -6f
private const val END_X = 2f
private const val WALK_FRAME = 3

/**
 * Loads every cell at once, except the columns from x = 1 on, whose loads stay in flight: the figure then shows
 * cells reading as well as shown and leaving.
 */
private object HeldLoads : AsyncWorldCellStreamListener {
    override suspend fun loadCell(coord: WorldCellCoord): CellContent {
        if (coord.x >= 1) awaitCancellation()
        return CellContent { }
    }

    override fun onCellUnload(world: World, coord: WorldCellCoord) = Unit
}

/** Moves the player from [START_X] to [END_X] on [WALK_FRAME]. */
private class Walk : System {
    private var frame = 0

    override fun update(world: World, delta: Float) {
        if (++frame != WALK_FRAME) return
        world.queryEach(Transform::class, StreamObserver::class) { _, transform, _ -> transform.position.x = END_X }
    }
}

/** The guide's figure: a ground, a player cube streaming cells, and the sources in [registry]. */
internal fun debugToolsFigure(registry: DebugRegistry, spawner: SpawnerProbe, loads: CoroutineScope): AwakeAppLifecycle = app {
    window { size(960, 540) }
    service(DebugRegistry::class, registry)
    scene("debug-tools") {
        assets {
            mesh("ground") { renderer.createMesh(generate { plane(size = 200f) }) }
            mesh("cube") { renderer.createMesh(generate { cube(size = 1f, colored = true) }) }
            material("lit") { renderer.createMaterial(LitShadowUniformLayout) }
        }
        val cells = frameSystem("cells") { WorldPartitionSystem(FIGURE_CELLS, asyncStreamListener = HeldLoads, loadScope = loads) }
        frameSystem("walk") { Walk() }
        onReady {
            registry.register(WorldPartitionDebugSource(system(cells).probe))
            registry.register(SpawnerDebugSource(spawner))
            world.scene {
                entity("camera") {
                    camera(lens = Lens.perspective(eye = Vec3f(6f, 25f, 23f), center = Vec3f(5.5f, 0f, 1.5f)))
                }
                entity("sun") { directionalLight(direction = Vec3f(0.4f, 0.8f, 0.4f)) }
                entity("ground") {
                    transform()
                    mesh(requireMesh("ground"), requireMaterial("lit"))
                }
                entity("player") {
                    transform(x = START_X)
                    with(StreamObserver)
                    entity("body") {
                        transform(y = 0.5f)
                        mesh(requireMesh("cube"), requireMaterial("lit"))
                    }
                }
            }
        }
    }
}
