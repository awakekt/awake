/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.director

import com.awakekt.awake.compose.ui.platform.InputOwnership
import com.awakekt.awake.core.input.InputRecorder
import com.awakekt.awake.core.input.InputReplayer
import com.awakekt.awake.core.input.InputSnapshot
import com.awakekt.awake.core.input.InputTake
import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.controls.GameplayInput
import com.awakekt.awake.scene.controls.camera.ActiveCamera
import com.awakekt.awake.scene.controls.camera.CameraRig

/**
 * Deterministic gameplay input replay bridge that steps an [InputReplayer] once per fixed step
 * and restores the recorded gameplay camera's yaw/pitch onto the non-primary `ActiveCamera` rig
 * so camera-relative movement (`MatrixRelativeMovementSystem`) reproduces the exact recorded
 * trajectory even while [DirectorSystem] orbits an independent primary camera 360 degrees around
 * the stage.
 *
 * @property take Recorded gameplay [InputTake] to replay.
 */
class InputReplaySession(
    val take: InputTake,
) {
    private val replayer = InputReplayer(take)
    private var currentSnapshot: InputSnapshot = emptySnapshot()
    private val noUiClaim = InputOwnership()

    /** Latest reconstructed [GameplayInput] for the current fixed step. */
    fun currentGameplayInput(): GameplayInput = GameplayInput(currentSnapshot, noUiClaim)

    /** Latest reconstructed hardware [InputSnapshot]. */
    val snapshot: InputSnapshot get() = currentSnapshot

    /** Whether replay has reached the end of [take]. */
    val isFinished: Boolean get() = replayer.isFinished

    /** Rewinds replay to frame `0`. */
    fun reset() {
        replayer.reset()
        currentSnapshot = emptySnapshot()
    }

    /** Advances replay by one fixed step and syncs the gameplay `CameraRig` basis angles. */
    fun stepFixed(world: World) {
        currentSnapshot = replayer.stepFrame()
        val yaw = replayer.currentCameraYaw
        val pitch = replayer.currentCameraPitch
        if (yaw != null || pitch != null) {
            world.queryEach(CameraRig::class, ActiveCamera::class) { _, rig, _ ->
                if (yaw != null) rig.yaw = yaw
                if (pitch != null) rig.pitch = pitch
            }
        }
    }

    private companion object {
        fun emptySnapshot(): InputSnapshot = InputSnapshot(
            pointerX = 0f,
            pointerY = 0f,
            pointerDown = false,
            scrollDeltaX = 0f,
            scrollDeltaY = 0f,
            keysDown = emptySet(),
            keysPressed = emptySet(),
            keysReleased = emptySet(),
            typedText = "",
            editActions = emptyList(),
        )
    }
}

/**
 * Fixed-step ECS system advancing an [InputReplaySession] cleanly before movement and camera
 * systems consume the frame's input.
 *
 * @param session Replay session driven each fixed step.
 */
class InputReplaySystem(
    private val session: InputReplaySession,
) : System {
    override fun update(world: World, delta: Float) {
        session.stepFixed(world)
    }
}

/**
 * Fixed-step ECS system recording live gameplay input and the active gameplay camera's yaw/pitch
 * into an [InputRecorder] so any live play session can later be re-shot with [DirectorSystem].
 *
 * @property recorder Recorder accumulating sparse frame deltas.
 * @param snapshotProvider Supplies this frame's live [InputSnapshot].
 */
class InputRecordSystem(
    val recorder: InputRecorder,
    private val snapshotProvider: () -> InputSnapshot,
) : System {
    override fun update(world: World, delta: Float) {
        var yaw: Float? = null
        var pitch: Float? = null
        world.queryEach(CameraRig::class, ActiveCamera::class) { _, rig, _ ->
            yaw = rig.yaw
            pitch = rig.pitch
        }
        recorder.recordFrame(snapshotProvider(), gameplayCameraYaw = yaw, gameplayCameraPitch = pitch)
    }
}
