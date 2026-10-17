/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.compose.ui.semantics.SemanticsNode
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.core.input.Input
import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.core.text.font.UiFont
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.engine.bootstrap.dsl.app
import com.awakekt.awake.engine.platform.dsl.requireService
import com.awakekt.awake.physics.jolt.createJoltPhysicsWorld
import com.awakekt.awake.render.command.GpuDrawPreparationSource
import com.awakekt.awake.render.command.GpuDrawPreparer
import com.awakekt.awake.render.testing.NoopRenderer
import com.awakekt.awake.render.texture.RenderTarget
import com.awakekt.awake.scene.authoring.scene
import com.awakekt.awake.scene.controls.camera.CameraRig
import com.awakekt.awake.scene.controls.input.inputActions
import com.awakekt.awake.scene.core.Name
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.runtime.SceneAppLifecycleRuntime
import com.awakekt.awake.ui.canvas.CanvasElement
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * The guide's mobile controls scene, played with touches as a phone would: a floating stick moves
 * the player, a jump button jumps while the stick is held, two skills fire and cool down, one dragged
 * to aim, and a swipe on the right half turns the camera.
 */
class MobileControlsSampleTest {
    private class Game(val runtime: SceneAppLifecycleRuntime, val input: Input, private val update: () -> Unit) {
        val world: World get() = runtime.world

        fun frames(count: Int) = repeat(count) { update() }

        /** Sets touch [id] at ([x], [y]), [down] or lifted, and runs a frame. */
        fun touch(id: Long, x: Float, y: Float, down: Boolean = true) {
            input.setTouch(id, x, y, down)
            input.updateSnapshot()
            frames(1)
        }

        fun named(name: String): Entity {
            var found: Entity? = null
            world.queryEach(Name::class) { entity, value -> if (value.value == name) found = entity }
            return checkNotNull(found) { "no node named $name" }
        }

        fun centreOf(name: String): Pair<Float, Float> {
            val node = assertNotNull(runtime.uiSemantics.findTag("canvas-element-${named(name).id}"), "no $name drawn")
            return node.x + node.width / 2f to node.y + node.height / 2f
        }

        private fun List<SemanticsNode>.findTag(tag: String): SemanticsNode? =
            firstNotNullOfOrNull { if (it.testTag == tag) it else it.children.findTag(tag) }
    }

    /** The sample, its first skill's cooldown shortened so a test need not wait three seconds. */
    private suspend fun play(): Game {
        val scene = File(DOCS_SNIPPETS, "ui/mobile-controls.scene.json").readText().replace("\"cooldownSeconds\": 3.0", "\"cooldownSeconds\": 0.2")
        val sources = mapOf(PROJECT_MANIFEST to MANIFEST, "scenes/main.scene.json" to scene)
        val project = loadProject(AssetSource { path -> runCatching { sources.getValue(path.value).encodeToByteArray() } }, physicsWorld = ::createJoltPhysicsWorld)
        val app = app { scene("play") { runProject(project, touchControls = true) } }
        app.ready(LayeredRenderer())
        val runtime = app.requireService<SceneAppLifecycleRuntime>()
        return Game(runtime, app.requireService()) { app.update(FRAME, WIDTH, HEIGHT) }.also { it.frames(SETTLE) }
    }

    @Test
    fun theStickMovesAndTheJumpButtonJumpsWhileItIsHeld() = runTest {
        val game = play()
        val position = game.world.get<Transform>(game.named("Player"))!!.position
        val start = position.copy()
        val (sx, sy) = game.centreOf("Stick")
        val (jx, jy) = game.centreOf("Jump")

        game.touch(STICK, sx, sy)
        repeat(SETTLE) { game.touch(STICK, sx, sy - PUSH) }
        var peak = position.y
        game.touch(THUMB, jx, jy)
        repeat(SETTLE / 2) {
            game.touch(STICK, sx, sy - PUSH)
            peak = maxOf(peak, position.y)
        }

        assertTrue(position.z < start.z - 1f, "pushing the stick up walks forward; z ${start.z} -> ${position.z}")
        assertTrue(peak > start.y + 0.5f, "the jump button jumps while the other thumb steers; peak $peak")
    }

    @Test
    fun aSkillFiresThenCoolsDownUntilItIsReadyAgain() = runTest {
        val game = play()
        val actions = assertNotNull(game.world.inputActions())
        val skill = game.world.get<CanvasElement>(game.named("Skill 1"))!!
        val (x, y) = game.centreOf("Skill 1")

        game.touch(THUMB, x, y)
        game.touch(THUMB, x, y, down = false)
        game.frames(1)
        assertTrue(actions.wasPressed("skill1"), "a tap fires the skill")
        assertTrue(skill.coolingDown, "and starts its cooldown")

        game.touch(THUMB, x, y)
        game.touch(THUMB, x, y, down = false)
        game.frames(1)
        assertFalse(actions.wasPressed("skill1"), "a cooling skill does not fire")

        val deadline = TimeSource.Monotonic.markNow() + 5.seconds
        while (skill.coolingDown && deadline.hasNotPassedNow()) game.frames(1)
        assertFalse(skill.coolingDown, "the cooldown runs out")
        assertTrue(skill.consumeReady(), "and says it is ready")
    }

    @Test
    fun aSkillDraggedToAimFiresWithTheAim() = runTest {
        val game = play()
        val actions = assertNotNull(game.world.inputActions())
        val (x, y) = game.centreOf("Skill 2")

        game.touch(THUMB, x, y)
        game.touch(THUMB, x - AIM, y)
        game.touch(THUMB, x - AIM, y)
        assertTrue(actions.axisX("aim") < 0f, "dragging left aims left")
        game.touch(THUMB, x - AIM, y, down = false)
        game.frames(1)

        assertTrue(actions.wasPressed("skill2"), "letting go fires")
        assertTrue(actions.axisX("aim") < 0f, "with the aim")
    }

    @Test
    fun aSwipeOnTheRightHalfTurnsTheCamera() = runTest {
        val game = play()
        val rig = game.world.get<CameraRig>(game.named("Camera"))!!
        val yaw = rig.yaw
        val x = WIDTH * 0.75f
        val y = HEIGHT * 0.25f

        game.touch(THUMB, x, y)
        game.touch(THUMB, x + PUSH, y)
        game.touch(THUMB, x + PUSH, y)
        game.touch(THUMB, x + PUSH, y, down = false)

        assertTrue(rig.yaw > yaw + 0.1f, "a swipe right turns the camera right; yaw $yaw -> ${rig.yaw}")
    }

    /**
     * A GPU-free renderer that also takes the offscreen UI a faded or scaled control draws through a
     * graphics layer, which every real backend composites.
     */
    private class LayeredRenderer :
        NoopRenderer(),
        GpuDrawPreparationSource {
        override val gpuDrawPreparer = GpuDrawPreparer { _, _, _ -> null }

        override fun drawUiToTexture(target: RenderTarget, primitives: List<UiDrawPrimitive>, font: UiFont?) = Unit
    }

    private companion object {
        const val SETTLE = 60
        const val STICK = 1L
        const val THUMB = 2L
        const val PUSH = 60f
        const val AIM = 100f
        const val MANIFEST = """{"formatVersion":1,"id":"com.example.harbor-town","name":"Harbor Town","version":"1.0.0","entryScene":"scenes/main.scene.json"}"""
    }
}
