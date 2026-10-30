/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.core.logging.Log
import com.awakekt.awake.core.logging.LogLevel
import com.awakekt.awake.core.logging.LogRecord
import com.awakekt.awake.core.logging.LogSink
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World
import com.awakekt.awake.engine.bootstrap.dsl.app
import com.awakekt.awake.engine.platform.dsl.requireService
import com.awakekt.awake.engine.platform.lifecycle.AwakeAppLifecycle
import com.awakekt.awake.physics.PhysicsWorld
import com.awakekt.awake.physics.jolt.createJoltPhysicsWorld
import com.awakekt.awake.render.command.GpuDrawPreparationSource
import com.awakekt.awake.render.command.GpuDrawPreparer
import com.awakekt.awake.render.testing.NoopRenderer
import com.awakekt.awake.scene.authoring.scene
import com.awakekt.awake.scene.binding.SceneComponentBinding
import com.awakekt.awake.scene.binding.SceneResolutionContext
import com.awakekt.awake.scene.controls.camera.ActiveCamera
import com.awakekt.awake.scene.core.Name
import com.awakekt.awake.scene.document.SceneComponent
import com.awakekt.awake.scene.document.SceneDocument
import com.awakekt.awake.scene.document.SceneLoader
import com.awakekt.awake.scene.rendering.Camera
import com.awakekt.awake.scene.rendering.mesh.MeshRenderer
import com.awakekt.awake.scene.runtime.SceneAppLifecycleRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.fail

/** A played project moves from one of its scenes to another, as a menu's Play button or a level's exit does. */
class SceneSwitchTest {
    /**
     * The menu's entities, systems, spawned nodes and physics world go; the level's come, with the
     * systems its components call for and the collision mesh it names, read as the switch loads it.
     */
    @Test
    fun aRequestPlaysTheNextSceneInPlaceOfTheLast() = runTest {
        val gates = GateCapability()
        val worlds = mutableListOf<CountedWorld>()
        val project = loadProject(files(), listOf(gates)) { CountedWorld(createJoltPhysicsWorld()).also { worlds += it } }
        val game = play(project)
        val hostInput = game.runtime.hostInputState
        game.runtime.showTouchControls = true
        val menuPhysics = worlds.single()
        val spawned = game.runtime.spawn(project, SceneLoader.decode(SPAWNED).nodes.single())
        game.frames(FRAMES)
        val menuGate = gates.made.single()
        assertTrue(menuGate.steps > 0, "the menu's systems run")

        game.switch.request(LEVEL)
        game.updateUntil { project.scene.name == "level" }
        game.frames(FRAMES)

        val world = game.world
        assertSame(hostInput, game.runtime.hostInputState, "a scene switch keeps the host input service")
        assertTrue(hostInput.showTouchControls)
        assertNull(world.find("Title"), "the menu's entities are gone")
        assertNull(world.find("MenuCamera"))
        assertFalse(world.isAlive(spawned.root), "a node spawned into the menu goes with it")
        assertTrue(menuGate.closed, "the menu's systems are closed")
        val stepsInMenu = menuGate.steps
        game.frames(FRAMES)
        assertEquals(stepsInMenu, menuGate.steps, "and run no more")
        assertEquals(1, menuPhysics.destroyed, "the menu's physics world is destroyed")

        val levelGate = gates.made.last()
        assertEquals("level", levelGate.label)
        assertTrue(levelGate.steps > 0 && !levelGate.closed, "the level's own systems run")
        val levelPhysics = assertNotNull(project.physics, "the level has a physics world of its own")
        assertEquals(1f, assertNotNull(levelPhysics.groundAt(0f, 0f), "the level's mesh collider is built from the mesh it loaded"), TOLERANCE)
        assertEquals(listOf("LevelCamera"), world.cameras(), "the level's camera is the one rendered from")
        assertEquals(2, world.drawn(), "both of the level's cubes draw")
        assertEquals(2, game.runtime.requireAssetLibrary().meshHolderCount("cube"), "the menu's and the spawned node's holds are given back")

        project.close()
        assertEquals(1, worlds.last().destroyed, "closing the project closes the scene playing now")
    }

    /** A scene that is missing, that names a component nothing registers, or that can't draw leaves the menu playing. */
    @Test
    fun aSceneThatFailsToLoadOrPlayLeavesTheLastOneRunning() = runTest {
        for ((target, why) in listOf(MISSING to "missing", BROKEN to "an unregistered component", UNDRAWABLE to "a mesh nothing resolves")) {
            val gates = GateCapability()
            val project = loadProject(files(), listOf(gates), physicsWorld = ::createJoltPhysicsWorld)
            val game = play(project)
            game.frames(FRAMES)
            val errors = Errors()
            Log.install(errors)
            try {
                game.switch.request(target)
                game.updateUntil { errors.records.isNotEmpty() }
                game.frames(FRAMES)
            } finally {
                Log.remove(errors)
            }

            val error = errors.records.single()
            assertTrue(target in error.message && "scenes/menu.scene.json" in error.message, "for $why, one error names both scenes: ${error.message}")
            assertEquals("menu", project.scene.name, "for $why, the menu plays on")
            assertNotNull(game.world.find("Title"), "for $why, the menu's entities stay")
            assertNull(game.world.find("Ghost"), "for $why, nothing of the scene that failed stays")
            assertNull(game.world.find("Crate"))
            val menuGate = gates.made.first()
            assertFalse(menuGate.closed, "for $why, the menu's systems still run")
            assertEquals(listOf("MenuCamera"), game.world.cameras())
            assertEquals(1, game.runtime.requireAssetLibrary().meshHolderCount("cube"), "for $why, only the menu holds the cube")
            project.close()
        }
    }

    /**
     * A system that asks during a fixed step finishes that step with the menu around it; the level takes
     * over at the start of the next step of the same frame, and the menu's systems never run again.
     */
    @Test
    fun aRequestMadeDuringAFixedStepIsCarriedOutAfterIt() = runTest {
        val gates = GateCapability(requestAtStep = 2)
        val project = loadProject(files(), listOf(gates), physicsWorld = ::createJoltPhysicsWorld)
        // The level loads as it is asked for, so only the safe point decides when it takes over.
        project.sceneLoads = Dispatchers.Unconfined
        val game = play(project)
        val menuGate = gates.made.single()
        gates.steps.clear()

        game.update(STEPS_PER_FRAME * DELTA)

        val menu = gates.steps.takeWhile { it == "menu" }
        val level = gates.steps.drop(menu.size)
        assertEquals(2, menuGate.steps, "the menu ran up to the step that asked, and not after it")
        assertTrue(menuGate.sawItselfAfterAsking, "the step that asked finished with the menu in the world")
        assertEquals("level", project.scene.name)
        assertTrue(menu.isNotEmpty() && level.isNotEmpty() && level.all { it == "level" }, "the level ran the frame's next steps: ${gates.steps}")
        project.close()
    }

    /** Asking again for the scene on its way changes nothing, and asking for the one playing starts it again. */
    @Test
    fun askingForThePlayingSceneStartsItAgain() = runTest {
        val gates = GateCapability()
        val project = loadProject(files(), listOf(gates), physicsWorld = ::createJoltPhysicsWorld)
        project.sceneLoads = Dispatchers.Unconfined
        val game = play(project)
        val title = assertNotNull(game.world.find("Title"))

        game.switch.request(MENU)
        game.switch.request(MENU)
        game.frames(1)

        assertEquals(2, gates.made.size, "one restart for two asks")
        assertFalse(game.world.isAlive(title), "the menu starts again from its file")
        assertNotNull(game.world.find("Title"))
        assertTrue(gates.made.first().closed)
        project.close()
    }

    private class Game(val app: AwakeAppLifecycle) {
        val runtime: SceneAppLifecycleRuntime = app.requireService()
        val switch: SceneSwitch = app.requireService()
        val world: World get() = runtime.world

        fun update(delta: Float = DELTA) = app.update(delta, WIDTH, HEIGHT)

        fun frames(count: Int) = repeat(count) { update() }

        suspend fun updateUntil(done: () -> Boolean) {
            repeat(ATTEMPTS) {
                update()
                if (done()) return
                withContext(Dispatchers.Default) { delay(WAIT_MILLIS) }
            }
            fail("The next scene did not finish loading")
        }
    }

    private suspend fun play(project: LoadedProject): Game {
        val app = app { scene("play") { runProject(project) } }
        app.ready(TestRenderer())
        return Game(app).also { it.frames(1) }
    }

    private fun World.find(name: String): Entity? {
        var found: Entity? = null
        queryEach(Name::class) { entity, value -> if (value.value == name) found = entity }
        return found
    }

    private fun World.cameras(): List<String> = buildList {
        queryEach(Camera::class) { entity, camera ->
            if (camera.isPrimary && has(entity, ActiveCamera::class)) add(get<Name>(entity)?.value.orEmpty())
        }
    }

    private fun World.drawn(): Int {
        var count = 0
        queryEach(MeshRenderer::class) { _, _ -> count++ }
        return count
    }

    private fun PhysicsWorld.groundAt(x: Float, z: Float): Float? =
        raycast(Vec3f(x, RAY_HEIGHT, z), Vec3f(0f, -1f, 0f), RAY_HEIGHT * 2f)?.point?.y

    private fun files(): AssetSource {
        val texts = mapOf(MANIFEST_PATH to MANIFEST, MENU to MENU_SCENE, LEVEL to LEVEL_SCENE, BROKEN to BROKEN_SCENE, UNDRAWABLE to UNDRAWABLE_SCENE)
        return AssetSource { path ->
            runCatching { if (path.value == PLATFORM) platformGlb() else texts.getValue(path.value).encodeToByteArray() }
        }
    }

    /** A flat square over x and z in -1..1 at y = 1, facing up: float positions then uint indices, in one GLB. */
    private fun platformGlb(): ByteArray {
        val positions = floatArrayOf(-1f, 1f, -1f, 1f, 1f, -1f, 1f, 1f, 1f, -1f, 1f, 1f)
        val indices = intArrayOf(3, 2, 1, 3, 1, 0)
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

    /** Collects the error records a test logs. */
    private class Errors : LogSink {
        val records = mutableListOf<LogRecord>()

        override fun emit(record: LogRecord) {
            if (record.level == LogLevel.Error) records += record
        }
    }

    /** A physics world that counts how often it is destroyed. */
    private class CountedWorld(private val inner: PhysicsWorld) : PhysicsWorld by inner {
        var destroyed = 0

        override fun destroy() {
            destroyed++
            inner.destroy()
        }
    }

    private class TestRenderer :
        NoopRenderer(),
        GpuDrawPreparationSource {
        override val gpuDrawPreparer = GpuDrawPreparer { _, _, _ -> null }
    }

    private companion object {
        const val DELTA = 1f / 60f
        const val STEPS_PER_FRAME = 5
        const val WIDTH = 800f
        const val HEIGHT = 600f
        const val FRAMES = 3
        const val ATTEMPTS = 400
        const val WAIT_MILLIS = 10L
        const val TOLERANCE = 0.01f
        const val RAY_HEIGHT = 10f
        const val GLB_MAGIC = 0x46546C67
        const val JSON_CHUNK = 0x4E4F534A
        const val BIN_CHUNK = 0x004E4942
        const val GLB_HEADER = 12
        const val CHUNK_HEADER = 8

        const val MANIFEST_PATH = "awake.project.json"
        const val MENU = "scenes/menu.scene.json"
        const val LEVEL = "scenes/level.scene.json"
        const val MISSING = "scenes/missing.scene.json"
        const val BROKEN = "scenes/broken.scene.json"
        const val UNDRAWABLE = "scenes/undrawable.scene.json"
        const val PLATFORM = "props/platform.glb"
        const val MANIFEST = """{"formatVersion":1,"id":"com.example.harbor-town","name":"Harbor Town","version":"1.0.0","entryScene":"scenes/menu.scene.json"}"""

        const val MENU_SCENE = """
{ "version": 1, "name": "menu", "nodes": [
  { "name": "MenuCamera", "transform": { "position": { "x": 0.0, "y": 2.0, "z": 6.0 } }, "components": [ { "component": "camera" } ] },
  { "name": "Floor", "components": [
    { "component": "physics_body", "shape": { "type": "box", "halfExtents": { "x": 5.0, "y": 0.1, "z": 5.0 } } }
  ] },
  { "name": "Title", "components": [
    { "component": "meshRenderer", "mesh": "cube", "material": "lit-shadow" },
    { "component": "test_gate", "label": "menu" }
  ] }
] }
"""

        const val LEVEL_SCENE = """
{ "version": 1, "name": "level", "nodes": [
  { "name": "LevelCamera", "transform": { "position": { "x": 0.0, "y": 4.0, "z": 8.0 } }, "components": [ { "component": "camera" } ] },
  { "name": "Platform", "components": [ { "component": "physics_body", "shape": { "type": "mesh", "mesh": "props/platform.glb" } } ] },
  { "name": "Door", "components": [
    { "component": "meshRenderer", "mesh": "cube", "material": "lit-shadow" },
    { "component": "test_gate", "label": "level" }
  ] },
  { "name": "Crate", "components": [ { "component": "meshRenderer", "mesh": "cube", "material": "lit-shadow" } ] }
] }
"""

        const val BROKEN_SCENE = """
{ "version": 1, "name": "broken", "nodes": [
  { "name": "Ghost", "components": [ { "component": "grappling_hook" } ] }
] }
"""

        const val UNDRAWABLE_SCENE = """
{ "version": 1, "name": "undrawable", "nodes": [
  { "name": "Crate", "components": [ { "component": "meshRenderer", "mesh": "cube", "material": "lit-shadow" } ] },
  { "name": "Ghost", "components": [ { "component": "meshRenderer", "mesh": "no-such-mesh", "material": "lit-shadow" } ] }
] }
"""

        const val SPAWNED = """
{ "version": 1, "name": "drop", "nodes": [
  { "name": "Drop", "components": [ { "component": "meshRenderer", "mesh": "cube", "material": "lit-shadow" } ] }
] }
"""
    }
}

/** A gate in a test scene: a node whose fixed system counts the steps it runs. */
@Serializable
@SerialName("test_gate")
private data class SceneGate(val label: String = "") : SceneComponent

/** The gate on an entity. */
private class Gate(val label: String)

private object GateBinding : SceneComponentBinding<Gate, SceneGate> {
    override val componentClass: KClass<Gate> = Gate::class
    override val schemaClass: KClass<SceneGate> = SceneGate::class
    override val serializer = SceneGate.serializer()

    override fun attachTyped(world: World, entity: Entity, component: SceneGate, context: SceneResolutionContext) {
        world.add(entity, Gate(component.label))
    }

    override fun export(world: World, entity: Entity, component: Gate): SceneGate = SceneGate(component.label)
}

/**
 * Plans a fixed system for each scene with a gate, keeping every one it made. With [requestAtStep], a
 * scene's system asks for the level on that step of its own, through its services.
 */
private class GateCapability(private val requestAtStep: Int = 0) : SceneCapability {
    override val id = "com.example.harbor-town.gate"
    override val components = listOf(GateBinding)
    val made = mutableListOf<GateSystem>()

    /** The label of each gate step, in the order they ran. */
    val steps = mutableListOf<String>()

    override fun plan(scene: SceneDocument, plan: SceneSystemPlan) {
        if (!scene.uses(SceneGate::class)) return
        plan.fixed("gate") { services ->
            val label = scene.nodes.firstNotNullOf { node -> node.components.filterIsInstance<SceneGate>().firstOrNull()?.label }
            GateSystem(label, services.sceneSwitch, requestAtStep.takeIf { label == "menu" } ?: 0, steps).also { made += it }
        }
        plan.fixed("gate-watch") { GateWatch { made.lastOrNull() } }
    }
}

/** Counts its steps, asks for the level on [requestAt], and records that it was closed. */
private class GateSystem(
    val label: String,
    private val switch: SceneSwitch?,
    private val requestAt: Int,
    private val log: MutableList<String>,
) : System,
    AutoCloseable {
    var steps = 0
    var closed = false
    var asked = false
    var sawItselfAfterAsking = false

    override fun update(world: World, delta: Float) {
        steps++
        log += label
        if (steps == requestAt) {
            checkNotNull(switch) { "runProject gives its systems a scene switch" }.request("scenes/level.scene.json")
            asked = true
        }
    }

    override fun close() {
        closed = true
    }
}

/** Runs after the gate in the same step: whether the gate's own entity is still there once it has asked. */
private class GateWatch(private val gate: () -> GateSystem?) : System {
    override fun update(world: World, delta: Float) {
        val system = gate() ?: return
        if (!system.asked || system.sawItselfAfterAsking) return
        var present = false
        world.queryEach(Gate::class) { _, entry -> if (entry.label == system.label) present = true }
        system.sawItselfAfterAsking = present
    }
}
