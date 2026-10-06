/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.compose.ui.platform.InputOwnership
import com.awakekt.awake.core.input.Input
import com.awakekt.awake.core.input.Key
import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World
import com.awakekt.awake.engine.bootstrap.dsl.app
import com.awakekt.awake.engine.platform.dsl.requireService
import com.awakekt.awake.particles.ParticleSystem
import com.awakekt.awake.physics.PhysicsWorld
import com.awakekt.awake.physics.jolt.createJoltPhysicsWorld
import com.awakekt.awake.render.command.GpuDrawPreparationSource
import com.awakekt.awake.render.command.GpuDrawPreparer
import com.awakekt.awake.render.passes.uniforms.TextureAnimation
import com.awakekt.awake.render.testing.NoopRenderer
import com.awakekt.awake.scene.authoring.scene
import com.awakekt.awake.scene.binding.instantiate
import com.awakekt.awake.scene.character.CharacterControllerSystem
import com.awakekt.awake.scene.controls.GameplayInput
import com.awakekt.awake.scene.controls.camera.CameraSystem
import com.awakekt.awake.scene.controls.movement.MatrixRelativeMovementSystem
import com.awakekt.awake.scene.controls.movement.PlayerInputSystem
import com.awakekt.awake.scene.core.Name
import com.awakekt.awake.scene.core.transform.SpinSystem
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.document.SceneLoader
import com.awakekt.awake.scene.particles.ParticleContentSystem
import com.awakekt.awake.scene.physics.PhysicsSystem
import com.awakekt.awake.scene.rendering.animation.AnimationSystem
import com.awakekt.awake.scene.rendering.animation.KeyframeAnimationSystem
import com.awakekt.awake.scene.rendering.light.DayCycleSystem
import com.awakekt.awake.scene.rendering.light.Light
import com.awakekt.awake.scene.rendering.mesh.TextureClipSystem
import com.awakekt.awake.scene.rendering.mesh.TextureClips
import com.awakekt.awake.scene.runtime.SceneAppLifecycleRuntime
import kotlinx.coroutines.test.runTest
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [playSystemsFor] is the decision of which systems a scene needs, taken out of `playProject` so a
 * host that plays a scene in a world of its own, as an editor's Play does, runs the same set. These
 * use it as such a host does: no app builder, only the systems, driven by hand.
 */
class PlaySystemsTest {

    private val input = Input()

    private fun services(physics: PhysicsWorld? = null) = PlayServices(
        input = { GameplayInput(input.currentSnapshot, InputOwnership()) },
        renderer = NoopRenderer(),
        physics = physics,
    )

    private fun systemsFor(scene: String, physics: PhysicsWorld? = null): PlaySystems {
        installPlayableComponents()
        return playSystemsFor(SceneLoader.decode(scene), services(physics))
    }

    private fun List<System>.has(type: KClass<out System>) = any { type.isInstance(it) }

    // --- which systems a scene gets

    @Test
    fun aSceneWithNothingToRunGetsOnlyTheSkinnedAnimation() {
        val systems = systemsFor(PLAIN_SCENE)

        assertEquals(emptyList(), systems.fixed)
        assertEquals(1, systems.frame.size)
        assertTrue(systems.frame.has(AnimationSystem::class))
    }

    @Test
    fun keyframesAndSpinGetTheirSystems() {
        assertTrue(systemsFor(KEYFRAME_SCENE).frame.has(KeyframeAnimationSystem::class))
        assertTrue(systemsFor(SPIN_SCENE).frame.has(SpinSystem::class))
    }

    @Test
    fun aSpriteSheetWithClipsGetsTheClipSystemAndAPlainSceneDoesNot() {
        assertTrue(systemsFor(CLIPS_SCENE).frame.has(TextureClipSystem::class))
        assertTrue(!systemsFor(PLAIN_SCENE).frame.has(TextureClipSystem::class))
    }

    @Test
    fun aDayCycleGetsItsSystemAndAHostRunningItMovesTheSun() {
        assertTrue(!systemsFor(PLAIN_SCENE).frame.has(DayCycleSystem::class))
        installPlayableComponents()
        val world = World()
        SceneLoader.decode(DAY_SCENE).instantiate(world = world)
        val systems = systemsFor(DAY_SCENE)
        val sun = world.get<Light>(world.named("Sun"))!!

        assertTrue(systems.frame.has(DayCycleSystem::class))
        repeat(FRAMES) { systems.frame.forEach { it.update(world, DELTA) } }

        // A four-second day from sunrise: one second later it is noon, at 60 degrees.
        assertEquals(0.866f, sun.direction.y, 1e-3f, "the sun must have climbed to its noon height")
    }

    @Test
    fun movementWithoutACharacterMovesStraightThroughTheWorld() {
        val systems = systemsFor(MOVEMENT_SCENE)

        assertTrue(systems.frame.has(PlayerInputSystem::class), "keyboard intent")
        assertTrue(systems.frame.has(MatrixRelativeMovementSystem::class), "moved directly")
        assertEquals(emptyList(), systems.fixed, "no physics world was given")
    }

    @Test
    fun aCameraRigGetsTheCameraSystem() {
        assertTrue(systemsFor(CAMERA_SCENE).frame.has(CameraSystem::class))
    }

    @Test
    fun aPhysicsSceneWithAWorldGetsPhysicsAndCharactersOnTheFixedStep() = runTest {
        val systems = systemsFor(PHYSICS_SCENE, createJoltPhysicsWorld())

        assertTrue(systems.fixed.has(PhysicsSystem::class))
        assertTrue(systems.fixed.has(CharacterControllerSystem::class))
        assertTrue(!systems.frame.has(MatrixRelativeMovementSystem::class), "the character controller moves it")
        assertTrue(systems.frame.has(PlayerInputSystem::class))
    }

    @Test
    fun aPhysicsSceneWithoutAWorldRunsWithoutPhysics() {
        val systems = systemsFor(PHYSICS_SCENE)

        assertEquals(emptyList(), systems.fixed)
    }

    @Test
    fun particlesGetTheirContentAndSimulationSystems() {
        val systems = systemsFor(PARTICLE_SCENE)

        assertTrue(systems.frame.has(ParticleContentSystem::class))
        assertTrue(systems.frame.has(ParticleSystem::class))
        systems.close()
        systems.close() // releasing twice is harmless
    }

    @Test
    fun systemsRunInTheOrderPlayProjectRunsThem() {
        val frame = systemsFor(ORDER_SCENE).frame
        val order = listOf(
            PlayerInputSystem::class,
            MatrixRelativeMovementSystem::class,
            CameraSystem::class,
            KeyframeAnimationSystem::class,
            AnimationSystem::class,
        ).map { type -> frame.indexOfFirst { type.isInstance(it) } }

        assertTrue(order.all { it >= 0 }, "every one is present: $order")
        assertEquals(order.sorted(), order, "input, movement, camera, keyframes, then animation")
    }

    // --- running them as a host does

    @Test
    fun aHostCanRunKeyframesWithoutAnAppBuilder() {
        installPlayableComponents()
        val world = World()
        SceneLoader.decode(KEYFRAME_SCENE).instantiate(world = world)
        val systems = systemsFor(KEYFRAME_SCENE)
        val crate = world.named("Crate")

        repeat(HALF_A_LOOP) { systems.frame.forEach { it.update(world, DELTA) } }

        assertTrue(world.get<Transform>(crate)!!.position.x > 0.5f, "the keyframed crate must slide")
    }

    @Test
    fun aHostCanPlayASpritesClipsWithoutAnAppBuilder() {
        installPlayableComponents()
        val world = World()
        SceneLoader.decode(CLIPS_SCENE).instantiate(world = world)
        val systems = systemsFor(CLIPS_SCENE)
        val hero = world.named("Hero")

        fun frames(count: Int) = repeat(count) { systems.frame.forEach { it.update(world, DELTA) } }
        fun cell() = world.get<TextureAnimation>(hero)!!.firstFrame

        assertEquals(8, cell(), "the loaded scene shows the first cell of its first clip before any step")
        frames(TENTH_OF_A_SECOND)
        assertEquals(9, cell(), "walk is ten cells a second")
        world.get<TextureClips>(hero)!!.play("attack")
        frames(2 * TENTH_OF_A_SECOND)
        assertEquals(14, cell(), "attack's third cell, two tenths in")
        assertTrue(!world.get<TextureClips>(hero)!!.isFinished)
        frames(FRAMES)
        assertEquals(14, cell(), "a clip that does not loop holds its last cell")
        assertTrue(world.get<TextureClips>(hero)!!.isFinished)
    }

    @Test
    fun playProjectStepsSpriteClipsToo() = runTest {
        val game = launchProject(CLIPS_SCENE)
        val hero = game.world.named("Hero")

        repeat(TENTH_OF_A_SECOND) { game.frame() }

        // launchProject has already run one warm-up frame, so the clock is a frame past a tenth.
        assertEquals(9, game.world.get<TextureAnimation>(hero)!!.firstFrame)
    }

    @Test
    fun aHostCanRunAPhysicsCharacterOnTheFixedStep() = runTest {
        installPlayableComponents()
        val physics = createJoltPhysicsWorld()
        val world = World()
        SceneLoader.decode(PHYSICS_SCENE).instantiate(world = world)
        val systems = systemsFor(PHYSICS_SCENE, physics)
        val player = world.named("Player")
        val position = world.get<Transform>(player)!!.position

        fun frame() {
            systems.fixed.forEach { it.update(world, DELTA) }
            systems.frame.forEach { it.update(world, DELTA) }
        }
        repeat(FRAMES * 2) { frame() }
        val standingAt = position.y
        val z = position.z
        input.setKeyDown(Key.W, true)
        input.updateSnapshot()
        repeat(FRAMES) { frame() }

        assertEquals(1f, standingAt, GROUND_TOLERANCE, "the character must stand on the authored floor")
        assertTrue(position.z < z - 1f, "W must walk the character; z $z -> ${position.z}")
        systems.close()
    }

    @Test
    fun playProjectAndAHostRunningPlaySystemsMoveTheSameScene() = runTest {
        val viaProject = launchProject(KEYFRAME_SCENE)
        repeat(HALF_A_LOOP) { viaProject.frame() }

        installPlayableComponents()
        val world = World()
        SceneLoader.decode(KEYFRAME_SCENE).instantiate(world = world)
        val systems = systemsFor(KEYFRAME_SCENE)
        // launchProject has already run one warm-up frame; the host runs the same number.
        repeat(HALF_A_LOOP + 1) { systems.frame.forEach { it.update(world, DELTA) } }

        val project = viaProject.world.get<Transform>(viaProject.world.named("Crate"))!!.position
        val host = world.get<Transform>(world.named("Crate"))!!.position
        assertTrue(project.x > 0.5f && host.x > 0.5f, "both must have moved; project ${project.x}, host ${host.x}")
        assertEquals(project.x, host.x, 1e-3f, "the same scene slides the same way under either")
    }

    private class Game(val runtime: SceneAppLifecycleRuntime, private val update: () -> Unit) {
        val world: World get() = runtime.world
        fun frame() = update()
    }

    private suspend fun launchProject(scene: String): Game {
        val project = loadPlayableProject(files(scene))
        val game = app { scene("play") { playProject(project) } }
        game.ready(TestRenderer())
        val runtime = game.requireService<SceneAppLifecycleRuntime>()
        return Game(runtime) { game.update(DELTA, WIDTH, HEIGHT) }.also { it.frame() }
    }

    private class TestRenderer :
        NoopRenderer(),
        GpuDrawPreparationSource {
        override val gpuDrawPreparer = GpuDrawPreparer { _, _, _ -> null }
    }

    private fun files(scene: String) = AssetSource { path ->
        runCatching { mapOf(MANIFEST_PATH to MANIFEST, "scenes/main.scene.json" to scene).getValue(path.value).encodeToByteArray() }
    }

    private fun World.named(name: String): Entity {
        var found: Entity? = null
        queryEach(Name::class) { entity, value -> if (value.value == name) found = entity }
        return found!!
    }

    private companion object {
        const val DELTA = 1f / 60f
        const val WIDTH = 800f
        const val HEIGHT = 600f
        const val FRAMES = 60
        const val GROUND_TOLERANCE = 0.05f

        /** The keyframe scene loops every second; half a loop leaves the crate mid-slide, not back at the start. */
        const val HALF_A_LOOP = 30

        /** Six frames at 60 a second. */
        const val TENTH_OF_A_SECOND = 6
        const val MANIFEST_PATH = "awake.project.json"
        const val MANIFEST = """{"formatVersion":1,"id":"com.example.harbor-town","name":"Harbor Town","version":"1.0.0","entryScene":"scenes/main.scene.json"}"""

        const val KEYFRAME_SCENE = """
{ "version": 1, "name": "slide", "nodes": [
  { "name": "Crate", "transform": { "position": { "x": 0.0, "y": 0.0, "z": 0.0 } }, "components": [
    { "component": "keyframe_animation", "duration": 1.0,
      "position": [ { "time": 0.0, "value": { "x": 0.0, "y": 0.0, "z": 0.0 } },
                    { "time": 1.0, "value": { "x": 4.0, "y": 0.0, "z": 0.0 } } ] }
  ] }
] }
"""

        /** A 4 x 4 sheet: walk is cells 8 to 11 at ten a second, attack is cells 12 to 14 once at ten a second. */
        const val CLIPS_SCENE = """
{ "version": 1, "name": "sprite", "nodes": [
  { "name": "Hero", "components": [
    { "component": "texture_clips", "columns": 4, "rows": 4, "clip": "walk",
      "clips": {
        "walk": { "firstFrame": 8, "frameCount": 4, "framesPerSecond": 10.0 },
        "attack": { "firstFrame": 12, "frameCount": 3, "framesPerSecond": 10.0, "loop": false }
      } }
  ] }
] }
"""

        const val DAY_SCENE = """
{ "version": 1, "name": "day", "nodes": [
  { "name": "Sun", "components": [
    { "component": "light", "type": "Directional" },
    { "component": "day_cycle", "dayLengthSeconds": 4.0, "time": 0.25 } ] }
] }
"""

        const val SPIN_SCENE = """
{ "version": 1, "name": "spin", "nodes": [
  { "name": "Spinner", "transform": { "position": { "x": 0.0, "y": 0.0, "z": 0.0 } }, "components": [
    { "component": "spin_control", "speed": 2.0 } ] }
] }
"""

        const val PLAIN_SCENE = """
{ "version": 1, "name": "still", "nodes": [
  { "name": "Rock", "transform": { "position": { "x": 0.0, "y": 0.0, "z": 0.0 } }, "components": [] } ] }
"""

        const val MOVEMENT_SCENE = """
{ "version": 1, "name": "walk", "nodes": [
  { "name": "Player", "components": [ { "component": "movement_control", "speed": 6.0 } ] }
] }
"""

        const val CAMERA_SCENE = """
{ "version": 1, "name": "look", "nodes": [
  { "name": "Camera", "transform": { "position": { "x": 0.0, "y": 0.0, "z": 0.0 } }, "components": [
    { "component": "camera", "primary": true },
    { "component": "camera_rig", "mode": "ThirdPerson", "target": "Player", "distance": 9.0, "pitch": -0.3,
      "offset": { "x": 0.0, "y": 1.5, "z": 0.0 } }
  ] },
  { "name": "Player", "transform": { "position": { "x": 0.0, "y": 0.0, "z": 0.0 } }, "components": [] }
] }
"""

        const val PARTICLE_SCENE = """
{ "version": 1, "name": "sparks", "nodes": [
  { "name": "Fountain", "transform": { "position": { "x": 0.0, "y": 0.0, "z": 0.0 } }, "components": [
    { "component": "particle_emitter", "texture": "dust.png" } ] }
] }
"""

        const val ORDER_SCENE = """
{ "version": 1, "name": "everything", "nodes": [
  { "name": "Camera", "transform": { "position": { "x": 0.0, "y": 0.0, "z": 0.0 } }, "components": [
    { "component": "camera", "primary": true },
    { "component": "camera_rig", "mode": "ThirdPerson", "target": "Player", "distance": 9.0, "pitch": -0.3,
      "offset": { "x": 0.0, "y": 1.5, "z": 0.0 } }
  ] },
  { "name": "Player", "transform": { "position": { "x": 0.0, "y": 0.0, "z": 0.0 } }, "components": [
    { "component": "movement_control", "speed": 6.0 } ] },
  { "name": "Crate", "transform": { "position": { "x": 0.0, "y": 0.0, "z": 0.0 } }, "components": [
    { "component": "keyframe_animation", "duration": 1.0,
      "position": [ { "time": 0.0, "value": { "x": 0.0, "y": 0.0, "z": 0.0 } },
                    { "time": 1.0, "value": { "x": 1.0, "y": 0.0, "z": 0.0 } } ] }
  ] }
] }
"""

        const val PHYSICS_SCENE = """
{ "version": 1, "name": "arena", "nodes": [
  { "name": "Floor", "transform": { "position": { "x": 0.0, "y": -0.1, "z": 0.0 } }, "components": [
    { "component": "physics_body", "shape": { "type": "box", "halfExtents": { "x": 20.0, "y": 0.1, "z": 20.0 } } }
  ] },
  { "name": "Player", "transform": { "position": { "x": 0.0, "y": 1.0, "z": 0.0 } }, "components": [
    { "component": "movement_control", "speed": 6.0 },
    { "component": "character_controller", "jumpSpeed": 5.0 }
  ] }
] }
"""
    }
}
