/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.authoring

import com.awakekt.awake.compose.ui.platform.InputOwnership
import com.awakekt.awake.core.input.Input
import com.awakekt.awake.core.input.InputSnapshot
import com.awakekt.awake.core.input.Key
import com.awakekt.awake.core.input.PointerButton
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World
import com.awakekt.awake.engine.bootstrap.dsl.app
import com.awakekt.awake.engine.platform.dsl.requireService
import com.awakekt.awake.scene.controls.GameplayInput
import com.awakekt.awake.scene.controls.input.keybindingProfile
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

// --8<-- [start:actions]
enum class Action { Forward, Back, Left, Right, Jump }
// --8<-- [end:actions]

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
    fun aKeybindingProfileMapsActionsToKeys() {
        val input = Input()
        input.setKeyDown(Key.ArrowUp, true)
        input.setKeyDown(Key.Space, true)
        val snapshot = input.updateSnapshot()

        // --8<-- [start:keybindings]
        val keys = keybindingProfile<Action> {
            bind(Action.Forward, Key.W, secondary = Key.ArrowUp)
            bind(Action.Back, Key.S)
            bind(Action.Left, Key.A)
            bind(Action.Right, Key.D)
            bind(Action.Jump, Key.Space)
        }

        val jump = keys.isPressed(Action.Jump, snapshot)
        val move = keys.getAxis2D(Action.Forward, Action.Back, Action.Left, Action.Right, snapshot)
        keys.rebind(Action.Jump, Key.J)
        // --8<-- [end:keybindings]

        assertTrue(jump)
        assertEquals(Vec3f(0f, 0f, -1f), move, "forward is -z")
        assertFalse(keys.isDown(Action.Jump, snapshot))
    }
}

