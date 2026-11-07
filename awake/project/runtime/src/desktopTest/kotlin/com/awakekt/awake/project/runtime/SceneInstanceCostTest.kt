/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.compose.ui.platform.InputOwnership
import com.awakekt.awake.core.input.Input
import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.ecs.World
import com.awakekt.awake.physics.jolt.createJoltPhysicsWorld
import com.awakekt.awake.scene.binding.instantiate
import com.awakekt.awake.scene.controls.GameplayInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.TimeSource

/**
 * What a second copy of a map costs: [newSceneInstance] against loading the project again, each until
 * the copy has stepped once, so its bodies, the map's triangle-mesh collider among them, are in its
 * physics world. The map is a [CELLS] by [CELLS] collision mesh with a navigation grid the same size
 * and a few crates, a small instanced area.
 *
 * It prints the median time per copy and the JVM heap [COPIES] live copies retain, so a change's cost
 * shows in the log. The heap is the real check: an instance holds only its world and systems, while a
 * reload holds a document, collision mesh and grid of its own. Native physics memory is the same for
 * both, a world per copy, and the JVM heap doesn't see it. Times aren't asserted on a shared CI machine.
 */
class SceneInstanceCostTest {
    private val idle = { GameplayInput(Input().currentSnapshot, InputOwnership()) }

    @Test
    fun anInstanceCostsLessThanLoadingTheProjectAgain() = runTest {
        val files = files()
        val project = loadProject(files, hasRenderer = false, physicsWorld = ::createJoltPhysicsWorld)
        // Warm both paths once, so neither pays class loading or Jolt's first-world set-up.
        reload(files, backgroundScope).close()
        instance(project, backgroundScope).close()

        val reloadTimes = timed { reload(files, backgroundScope) }
        val instanceTimes = timed { instance(project, backgroundScope) }
        val reloadHeap = retained { reload(files, backgroundScope) }
        val instanceHeap = retained { instance(project, backgroundScope) }

        println(
            "scene instance cost ($COPIES copies of a ${CELLS}x$CELLS map): " +
                "reload ${reloadTimes.median()} ms and ${reloadHeap / KIB} KiB, " +
                "instance ${instanceTimes.median()} ms and ${instanceHeap / KIB} KiB",
        )
        assertTrue(instanceHeap < reloadHeap, "an instance shares the map's data; instance $instanceHeap B, reload $reloadHeap B")
        project.close()
    }

    /** A copy played from a project loaded again. */
    private suspend fun reload(files: AssetSource, scope: CoroutineScope): AutoCloseable {
        val project = loadProject(files, hasRenderer = false, physicsWorld = ::createJoltPhysicsWorld)
        val world = World()
        project.scene.instantiate(world = world)
        val systems = project.sceneSystems(idle, coroutineScope = scope)
        systems.fixed.forEach { it.update(world, STEP) }
        return AutoCloseable {
            systems.close()
            world.clear()
            project.close()
        }
    }

    /** A copy made from the project already loaded. */
    private suspend fun instance(project: LoadedProject, scope: CoroutineScope): AutoCloseable {
        val instance = project.newSceneInstance()
        instance.sceneSystems(idle, coroutineScope = scope).fixed.forEach { it.update(instance.world, STEP) }
        return instance
    }

    private suspend fun timed(copy: suspend () -> AutoCloseable): List<Long> = List(COPIES) {
        val start = TimeSource.Monotonic.markNow()
        val made = copy()
        val elapsed = start.elapsedNow().inWholeMilliseconds
        made.close()
        elapsed
    }

    /** The heap [COPIES] live copies hold, over what was in use before they were made. */
    private suspend fun retained(copy: suspend () -> AutoCloseable): Long {
        val before = usedHeap()
        val copies = List(COPIES) { copy() }
        val after = usedHeap()
        copies.forEach(AutoCloseable::close)
        return after - before
    }

    /**
     * The heap in use once collection has stopped freeing anything for [QUIET_PASSES] passes in a
     * row. The copies' garbage goes a layer per collection, their cleaners releasing what the next
     * pass frees: measured, the heap fell 98, 97, 97, 96, 93, 84, 50, 12 then 9.6 MiB over nine
     * passes. Three fixed passes read it partway down, so a reading swung by tens of MiB either way
     * and a reload's eight live copies once measured -13 MiB.
     */
    @Suppress("ExplicitGarbageCollectionCall") // Collecting until the heap settles is the measurement.
    private fun usedHeap(): Long {
        val runtime = Runtime.getRuntime()
        var lowest = Long.MAX_VALUE
        var quiet = 0
        var passes = 0
        while (quiet < QUIET_PASSES && passes < MAX_GC_PASSES) {
            System.gc()
            Thread.sleep(GC_PAUSE_MS)
            val used = runtime.totalMemory() - runtime.freeMemory()
            quiet = if (used > lowest - SETTLED_BYTES) quiet + 1 else 0
            lowest = minOf(lowest, used)
            passes += 1
        }
        return lowest
    }

    private fun List<Long>.median(): Long = sorted()[size / 2]

    private fun files(): AssetSource {
        val sources = mapOf(
            PROJECT_MANIFEST to MANIFEST.encodeToByteArray(),
            "scenes/main.scene.json" to scene().encodeToByteArray(),
            "maps/floor.glb" to gridGlb(),
        )
        return AssetSource { path -> runCatching { sources.getValue(path.value) } }
    }

    private fun scene(): String {
        val rows = List(CELLS) { "\"${".".repeat(CELLS)}\"" }.joinToString(",")
        val crates = (0 until CRATES).joinToString(",") { index ->
            """{ "name": "Crate $index", "transform": { "position": { "x": ${index * 2}.0, "y": 3.0, "z": 4.0 } },
              "components": [ { "component": "physics_body", "shape": { "type": "box", "halfExtents": { "x": 0.5, "y": 0.5, "z": 0.5 } }, "motion": "DYNAMIC" } ] }"""
        }
        return """
{ "version": 1, "name": "dungeon", "nodes": [
  { "name": "Map", "components": [ { "component": "navigation", "cellSize": 1.0, "rows": [$rows] } ] },
  { "name": "Floor", "components": [ { "component": "physics_body", "shape": { "type": "mesh", "mesh": "maps/floor.glb" } } ] },
  $crates
] }
"""
    }

    /** A flat [CELLS] by [CELLS] grid of quads at y = 0, wound to face up, as one GLB of float positions and uint indices. */
    private fun gridGlb(): ByteArray {
        val side = CELLS + 1
        val positions = FloatArray(side * side * 3)
        for (vertex in 0 until side * side) {
            positions[vertex * 3] = (vertex % side).toFloat()
            positions[vertex * 3 + 2] = (vertex / side).toFloat()
        }
        val indices = IntArray(CELLS * CELLS * 6)
        for (cell in 0 until CELLS * CELLS) {
            val corner = (cell / CELLS) * side + cell % CELLS
            intArrayOf(corner, corner + side, corner + 1, corner + 1, corner + side, corner + side + 1).copyInto(indices, cell * 6)
        }
        val bin = ByteArray(positions.size * 4 + indices.size * 4)
        positions.forEachIndexed { i, value -> bin.putInt(i * 4, value.toRawBits()) }
        indices.forEachIndexed { i, value -> bin.putInt(positions.size * 4 + i * 4, value) }
        val json = """
            {"asset":{"version":"2.0"},"scene":0,"scenes":[{"nodes":[0]}],"nodes":[{"mesh":0}],
             "meshes":[{"primitives":[{"attributes":{"POSITION":0},"indices":1}]}],
             "buffers":[{"byteLength":${bin.size}}],
             "bufferViews":[{"buffer":0,"byteOffset":0,"byteLength":${positions.size * 4}},
                            {"buffer":0,"byteOffset":${positions.size * 4},"byteLength":${indices.size * 4}}],
             "accessors":[{"bufferView":0,"componentType":5126,"count":${positions.size / 3},"type":"VEC3"},
                          {"bufferView":1,"componentType":5125,"count":${indices.size},"type":"SCALAR"}]}
        """.trimIndent().encodeToByteArray()
        val paddedJson = json + ByteArray((4 - json.size % 4) % 4) { ' '.code.toByte() }
        val glb = ByteArray(GLB_HEADER + CHUNK_HEADER + paddedJson.size + CHUNK_HEADER + bin.size)
        glb.putInt(0, GLB_MAGIC)
        glb.putInt(4, 2)
        glb.putInt(8, glb.size)
        glb.putInt(12, paddedJson.size)
        glb.putInt(16, JSON_CHUNK)
        paddedJson.copyInto(glb, GLB_HEADER + CHUNK_HEADER)
        val binAt = GLB_HEADER + CHUNK_HEADER + paddedJson.size
        glb.putInt(binAt, bin.size)
        glb.putInt(binAt + 4, BIN_CHUNK)
        bin.copyInto(glb, binAt + CHUNK_HEADER)
        return glb
    }

    private fun ByteArray.putInt(at: Int, value: Int) {
        for (byte in 0 until 4) this[at + byte] = (value ushr (byte * 8)).toByte()
    }

    private companion object {
        const val STEP = 1f / 60f
        const val COPIES = 8
        const val CELLS = 160
        const val CRATES = 8
        const val MAX_GC_PASSES = 60
        const val QUIET_PASSES = 3

        /** A pass that frees less than this has settled the heap. */
        const val SETTLED_BYTES = 64L * 1024
        const val GC_PAUSE_MS = 50L
        const val KIB = 1024
        const val GLB_MAGIC = 0x46546C67
        const val JSON_CHUNK = 0x4E4F534A
        const val BIN_CHUNK = 0x004E4942
        const val GLB_HEADER = 12
        const val CHUNK_HEADER = 8
        const val MANIFEST = """{"formatVersion":1,"id":"com.example.harbor-town","name":"Harbor Town","version":"1.0.0","entryScene":"scenes/main.scene.json"}"""
    }
}
