/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.awakekt.awake.scene.player

import com.awakekt.awake.compose.foundation.background
import com.awakekt.awake.compose.foundation.clickable
import com.awakekt.awake.compose.foundation.gestures.draggable
import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.fillMaxSize
import com.awakekt.awake.compose.foundation.layout.offset
import com.awakekt.awake.compose.foundation.layout.padding
import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.current
import com.awakekt.awake.compose.ui.Alignment
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.draw.clip
import com.awakekt.awake.compose.ui.graphics.CircleShape
import com.awakekt.awake.compose.ui.platform.LocalDensity
import com.awakekt.awake.compose.ui.semantics.testTag
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.controls.movement.MovementControl
import kotlin.math.sqrt

/**
 * What the on-screen stick and jump button want this frame. [TouchControls] writes it and
 * [TouchMovementSystem] hands it to the player.
 */
class TouchControlsState {
    /** Stick deflection, -1..1 each way; forward is +[moveZ], matching the keyboard's W. */
    var moveX: Float = 0f
        private set
    var moveZ: Float = 0f
        private set

    internal var knobX: Float = 0f
    internal var knobY: Float = 0f
    private var jumpPending = false

    internal fun drag(dx: Float, dy: Float, radius: Float) {
        knobX += dx
        knobY += dy
        val length = sqrt(knobX * knobX + knobY * knobY)
        if (length > radius) {
            knobX *= radius / length
            knobY *= radius / length
        }
        moveX = knobX / radius
        moveZ = -knobY / radius
    }

    internal fun release() {
        knobX = 0f
        knobY = 0f
        moveX = 0f
        moveZ = 0f
    }

    internal fun jump() {
        jumpPending = true
    }

    /** True once per tap of the jump button. */
    internal fun consumeJump(): Boolean = jumpPending.also { jumpPending = false }
}

/** A stick in the bottom-left corner that moves the player and a jump button in the bottom-right. */
context(_: Composer)
fun TouchControls(state: TouchControlsState, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val radiusPx = STICK_RADIUS * density
    Box(modifier.fillMaxSize()) {
        Box(
            Modifier.align(Alignment.BottomStart)
                .padding(EDGE_INSET.dp)
                .size((STICK_RADIUS * 2).dp)
                .clip(CircleShape)
                .background(BASE_COLOR)
                .draggable(
                    onDrag = { dx, dy -> state.drag(dx.toFloat(), dy.toFloat(), radiusPx) },
                    onDragStopped = state::release,
                )
                .testTag(STICK_TAG),
        ) {
            Box(
                Modifier.align(Alignment.Center)
                    .offset((state.knobX / density).dp, (state.knobY / density).dp)
                    .size(KNOB_SIZE.dp)
                    .clip(CircleShape)
                    .background(KNOB_COLOR),
            )
        }
        Box(
            Modifier.align(Alignment.BottomEnd)
                .padding(EDGE_INSET.dp)
                .size(JUMP_SIZE.dp)
                .clip(CircleShape)
                .background(BASE_COLOR)
                .clickable { state.jump() }
                .testTag(JUMP_TAG),
        )
    }
}

/**
 * Applies [state] to every [MovementControl] after the keyboard: a deflected stick replaces the keys'
 * direction and a tapped jump button jumps. Register it after `playerInputSystem`.
 */
class TouchMovementSystem(private val state: TouchControlsState) : System {
    override fun update(world: World, delta: Float) {
        val jump = state.consumeJump()
        val stick = state.moveX != 0f || state.moveZ != 0f
        if (!stick && !jump) return
        world.queryEach(MovementControl::class) { _, control ->
            if (stick) {
                control.moveX = state.moveX
                control.moveZ = state.moveZ
            }
            if (jump) control.jump = true
        }
    }
}

internal const val STICK_TAG = "touch-stick"
internal const val JUMP_TAG = "touch-jump"
private const val STICK_RADIUS = 60f
private const val KNOB_SIZE = 56f
private const val JUMP_SIZE = 72f
private const val EDGE_INSET = 24f
private val BASE_COLOR = Color(1f, 1f, 1f, 0.18f)
private val KNOB_COLOR = Color(1f, 1f, 1f, 0.45f)
