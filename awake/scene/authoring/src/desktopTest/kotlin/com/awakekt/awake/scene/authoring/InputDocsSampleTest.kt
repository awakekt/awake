/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.authoring

import com.awakekt.awake.compose.ui.platform.InputOwnership
import com.awakekt.awake.core.input.ActionTrigger
import com.awakekt.awake.core.input.AxisAction
import com.awakekt.awake.core.input.ButtonAction
import com.awakekt.awake.core.input.Input
import com.awakekt.awake.core.input.InputActions
import com.awakekt.awake.core.input.InputSnapshot
import com.awakekt.awake.core.input.Key
import com.awakekt.awake.core.input.PointerButton
import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World
import com.awakekt.awake.engine.bootstrap.dsl.app
import com.awakekt.awake.engine.platform.dsl.requireService
import com.awakekt.awake.scene.binding.SceneComponentRegistry
import com.awakekt.awake.scene.binding.instantiate
import com.awakekt.awake.scene.controls.GameplayInput
import com.awakekt.awake.scene.controls.input.inputActions
import com.awakekt.awake.scene.controls.movement.PlayerInputSystem
import com.awakekt.awake.scene.controls.movement.registerControls
import com.awakekt.awake.scene.document.SceneLoader
import com.awakekt.awake.scene.runtime.DefaultSceneComponentResolvers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// --8<-- [start:jump-system]
class Jumper {
    var jumps = 0
}

class JumpSystem(private val input: () -> InputSnapshot) : System {
    override fun update(world: World, delta: Float) {
        if (!input().wasPressed(Key.Space)) return
        world.queryEach<Jumper> { _, jumper -> jumper.jumps += 1 }
    }
}
// --8<-- [end:jump-system]

// --8<-- [start:walk-system]
class Walker {
    var steps = 0
}

class WalkSystem(private val input: () -> GameplayInput) : System {
    override fun update(world: World, delta: Float) {
        if (!input().isDown(Key.W)) return // not down while a text field has focus
        world.queryEach<Walker> { _, walker -> walker.steps += 1 }
    }
}
// --8<-- [end:walk-system]

// --8<-- [start:interact-system]
class Interactions {
    var count = 0
}

class InteractSystem : System {
    override fun update(world: World, delta: Float) {
        val actions = world.inputActions() ?: return
        if (!actions.wasPressed("interact")) return
        world.queryEach<Interactions> { _, interactions -> interactions.count += 1 }
    }
}
// --8<-- [end:interact-system]

/** The "Input" guide includes its samples from here, so they keep compiling and doing what it says. */
class InputDocsSampleTest {

    @Test
    fun aSnapshotHoldsLevelsAndOneFrameEdges() {
        // --8<-- [start:snapshot]
        val input = Input()
        input.setKeyDown(Key.Space, true)
        input.setPointer(down = true, x = 120f, y = 80f)

        val frame1 = input.updateSnapshot()
        frame1.isDown(Key.Space) // true: held
        frame1.wasPressed(Key.Space) // true: went down this frame
        frame1.isDown(PointerButton.Primary) // true

        val frame2 = input.updateSnapshot()
        frame2.isDown(Key.Space) // still true
        frame2.wasPressed(Key.Space) // false: the edge lasted one frame
        // --8<-- [end:snapshot]

        assertTrue(frame1.isDown(Key.Space))
        assertTrue(frame1.wasPressed(Key.Space))
        assertTrue(frame1.isDown(PointerButton.Primary))
        assertTrue(frame1.pointerPressed)
        assertEquals(120f, frame1.pointerX)
        assertTrue(frame2.isDown(Key.Space))
        assertFalse(frame2.wasPressed(Key.Space))
        assertFalse(frame2.pointerPressed)
    }

    @Test
    fun theUpdateBlockAndASystemBothSeeThisFramesInput() = runTest {
        var updateSawSpace = false
        // --8<-- [start:scene-input]
        val game = app {
            scene("harbor-town") {
                entity("player") { with(Jumper()) }

                // Once per fixed step, with the frame's snapshot.
                update { step, input ->
                    if (input.isDown(Key.Space)) updateSawSpace = true
                }

                // A system reads the session's Input service when it runs.
                frameSystem("jump") {
                    JumpSystem { requireService(Input::class).currentSnapshot }
                }
            }
        }
        // --8<-- [end:scene-input]

        game.ready(RecordingRenderer())
        val input = game.requireService<Input>()
        val jumper = game.requireService<com.awakekt.awake.scene.runtime.SceneAppLifecycleRuntime>()
            .world.let { world -> world.get<Jumper>(world.query(Jumper::class).single())!! }

        input.setKeyDown(Key.Space, true)
        input.updateSnapshot()
        game.update(1f / 60f, 320f, 240f)
        input.updateSnapshot()
        game.update(1f / 60f, 320f, 240f)

        assertTrue(updateSawSpace)
        assertEquals(1, jumper.jumps, "wasPressed fires on the first frame only")
        game.dispose()
    }

    @Test
    fun gameplayInputDropsKeysTheUiOwns() = runTest {
        // --8<-- [start:gameplay-input]
        val game = app {
            scene("harbor-town") {
                frameSystem("walk") {
                    WalkSystem { GameplayInput(requireService(Input::class).currentSnapshot, uiOwnership) }
                }
            }
        }
        // --8<-- [end:gameplay-input]
        game.ready(RecordingRenderer())
        game.dispose()

        val input = Input().apply { setKeyDown(Key.W, true) }
        val snapshot = input.updateSnapshot()
        assertTrue(GameplayInput(snapshot, InputOwnership()).isDown(Key.W))
        assertFalse(GameplayInput(snapshot, InputOwnership(isTextInputFocused = true)).isDown(Key.W))
        assertTrue(GameplayInput(snapshot, InputOwnership(isTextInputFocused = true)).keysOwnedByUi)
    }

    @Test
    fun aSystemReadsAnActionTheSceneBinds() {
        DefaultSceneComponentResolvers.install()
        val registry = SceneComponentRegistry().registerControls()
        val scene = SceneLoader.decode(
            """{ "version": 1, "name": "harbor-town", "nodes": [ { "name": "Controls", "components": [
                { "component": "input_actions", "actions": [ { "type": "button", "name": "interact", "keys": ["E"], "trigger": "Press" } ] }
            ] } ] }""",
        )
        val world = World()
        scene.instantiate(world = world, componentRegistry = registry)
        val interactions = Interactions().also { world.add(world.create(), it) }
        val input = Input().apply { setKeyDown(Key.E, true) }
        val players = PlayerInputSystem { GameplayInput(input.currentSnapshot, InputOwnership()) }

        input.updateSnapshot()
        players.update(world, 1f / 60f)
        InteractSystem().update(world, 1f / 60f)
        input.updateSnapshot()
        players.update(world, 1f / 60f)
        InteractSystem().update(world, 1f / 60f)

        assertEquals(1, interactions.count, "a press interacts once however long E is held")
    }

    @Test
    fun inputActionsReadASnapshot() {
        val input = Input()
        input.setKeyDown(Key.ArrowUp, true)
        input.setKeyDown(Key.Space, true)
        val snapshot = input.updateSnapshot()

        // --8<-- [start:code-actions]
        val actions = InputActions(
            listOf(
                AxisAction("move", up = setOf(Key.W, Key.ArrowUp), down = setOf(Key.S), left = setOf(Key.A), right = setOf(Key.D)),
                ButtonAction("jump", keys = setOf(Key.Space), trigger = ActionTrigger.Press),
            ),
        )

        // Once a frame:
        actions.beginFrame()
        actions.read(snapshot)
        val jump = actions.isActive("jump")
        val forward = actions.axisY("move")
        // --8<-- [end:code-actions]

        assertTrue(jump)
        assertEquals(1f, forward, "up is forward")
        actions.beginFrame()
        actions.read(input.updateSnapshot())
        assertFalse(actions.isActive("jump"), "a Press lasts the frame it was pressed")
    }
}

