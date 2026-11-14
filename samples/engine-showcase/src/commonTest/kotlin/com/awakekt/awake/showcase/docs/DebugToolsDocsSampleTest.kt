/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("ktlint:standard:import-ordering")

// Docs snippets mark out the imports a page shows, which splits the import list.

package com.awakekt.awake.showcase.docs

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.config.LaunchOptions
import com.awakekt.awake.core.diagnostics.DebugLines
import com.awakekt.awake.core.diagnostics.DebugRegistry
import com.awakekt.awake.core.diagnostics.DebugRowBuffer
import com.awakekt.awake.core.diagnostics.DebugRows
import com.awakekt.awake.core.diagnostics.DebugSource
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.engine.bootstrap.dsl.app
import com.awakekt.awake.engine.platform.dsl.requireService
import com.awakekt.awake.engine.platform.lifecycle.AwakeAppLifecycle
import com.awakekt.awake.engine.window.LaunchParameters
import com.awakekt.awake.render.renderer.LineSegment
import com.awakekt.awake.scene.authoring.dsl.camera
import com.awakekt.awake.scene.authoring.dsl.scene
import com.awakekt.awake.scene.authoring.dsl.transform
import com.awakekt.awake.scene.authoring.scene
import com.awakekt.awake.scene.runtime.SceneAppLifecycleRuntime
import com.awakekt.awake.scene.world.StreamObserver
import com.awakekt.awake.scene.world.WorldPartitionSystem
import com.awakekt.awake.world.WorldPartitionConfig
import com.awakekt.awake.world.WorldPartitionDebugSource
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// --8<-- [start:source]
/** The spawner on the debug panel and over the scene: `--debug=spawner`. */
class SpawnerDebugSource(private val probe: SpawnerProbe) : DebugSource {
    override val id = "spawner"
    override val title = "Spawner"

    // Reused for every line: the host copies the points, so drawing allocates nothing.
    private val a = Vec3f(0f, 0f, 0f)
    private val b = Vec3f(0f, 0f, 0f)

    override fun rows(out: DebugRows) {
        out.row("Alive", probe.alive, warn = probe.alive > MAX_ALIVE)
        out.row("Spawned", probe.spawned)
    }

    override fun lines(out: DebugLines) {
        // A post two metres tall at the spawn point.
        val at = probe.spawnPoint
        a.set(at.x, at.y, at.z)
        b.set(at.x, at.y + 2f, at.z)
        out.line(a, b, POST)
    }

    private companion object {
        const val MAX_ALIVE = 50
        val POST = Color(1f, 0.4f, 0.8f) // a constant: a Color made per line would allocate
    }
}
// --8<-- [end:source]

// --8<-- [start:probe]
// What a spawner counts, whether or not anyone is looking: its probe. The spawner writes it every frame.
class SpawnerProbe {
    var alive = 0
    var spawned = 0
    val spawnPoint = Vec3f(0f, 0f, 0f)
}
// --8<-- [end:probe]

// --8<-- [start:app]
fun debugTools(registry: DebugRegistry, spawner: SpawnerProbe): AwakeAppLifecycle = app {
    // A scene on the default infrastructure systems draws the lines and the panel of every source that is on.
    service(DebugRegistry::class, registry)
    scene("debug-tools") {
        val cells = frameSystem("cells") {
            WorldPartitionSystem(WorldPartitionConfig(cellSize = 4f, loadingRadius = 8f, unloadRadius = 12f))
        }
        onReady {
            // Off unless `cells` is in the flags: an off source is never added, so it costs nothing.
            registry.register(WorldPartitionDebugSource(system(cells).probe))
            registry.register(SpawnerDebugSource(spawner))
            world.scene {
                entity("camera") { camera(lens = Lens.perspective(eye = Vec3f(0f, 24f, 18f), center = Vec3f(0f, 0f, 0f))) }
                entity("player") {
                    transform()
                    with(StreamObserver) // cells stream, and are drawn, around this entity
                }
            }
        }
    }
}
// --8<-- [end:app]

// --8<-- [start:options]
object DebugLaunch : LaunchOptions() {
    val debug = ids("debug", description = "Debug sources to show, comma-separated")
}
// --8<-- [end:options]

/** The "Debug tools" guide includes the samples above; this checks they do what it says. */
class DebugToolsDocsSampleTest {

    @Test
    fun theFlagsTurnSourcesOnByName() {
        // --8<-- [start:flags]
        val args = listOf("--debug=cells,spawner")
        val desktop = DebugRegistry(DebugLaunch.parse(LaunchParameters.fromArguments(args).toMap())[DebugLaunch.debug])
        val web = DebugRegistry(DebugLaunch.parse(LaunchParameters.fromQuery("?debug=cells").toMap())[DebugLaunch.debug])
        // --8<-- [end:flags]

        assertTrue(desktop.isOn("cells") && desktop.isOn("spawner"))
        assertTrue(web.isOn("cells") && !web.isOn("spawner"))
    }

    @Test
    fun theSourcesThatAreOnDrawTheirLinesAndRows() = runTest {
        val registry = DebugRegistry(setOf(WorldPartitionDebugSource.ID, "spawner"))
        val spawner = SpawnerProbe().apply { alive = 3 }
        val renderer = LineRecordingRenderer()
        val game = debugTools(registry, spawner)
        game.ready(renderer)
        val runtime = game.requireService<SceneAppLifecycleRuntime>()

        repeat(FRAMES) { game.update(FRAME_SECONDS, 640f, 360f) }

        val probe = runtime.partitionProbe()
        val cellLines = probe.cellCount * WorldPartitionDebugSource.CELL_LINES
        assertTrue(probe.cellCount > 0, "the player's cells stream in")
        assertEquals(cellLines + 2 * WorldPartitionDebugSource.CIRCLE_SEGMENTS + 1, renderer.lines.size)
        val rows = DebugRowBuffer().apply { refresh(registry) }
        assertEquals(listOf("Cells", "Cell", "Reading", "Shown", "Leaving", "Spawner", "Alive", "Spawned"), List(8, rows::label))
        assertEquals("0, 0", rows.value(1))
        game.dispose()
    }

    @Test
    fun withNoFlagNothingIsDrawn() = runTest {
        val renderer = LineRecordingRenderer()
        val game = debugTools(DebugRegistry.Off, SpawnerProbe())
        game.ready(renderer)

        repeat(FRAMES) { game.update(FRAME_SECONDS, 640f, 360f) }

        assertEquals(0, renderer.calls)
        assertTrue(game.requireService<SceneAppLifecycleRuntime>().partitionProbe().cellCount > 0, "the probe still counts")
        game.dispose()
    }

    private fun SceneAppLifecycleRuntime.partitionProbe() = (system("cells") as WorldPartitionSystem).probe

    private class LineRecordingRenderer : DrawlessRenderer() {
        var calls = 0
        var lines: List<LineSegment> = emptyList()

        override fun drawDebugLines(lines: List<LineSegment>) {
            calls++
            this.lines = lines.toList()
        }
    }

    private companion object {
        const val FRAMES = 3
        const val FRAME_SECONDS = 1f / 60f
    }
}
