/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.input

/**
 * One sparsely recorded frame inside an [InputTake], emitted only when hardware input or the
 * player's recorded camera basis changes from the previous frame.
 *
 * @property frameIndex Zero-based fixed-step frame index when this state takes effect.
 * @property pointerX Primary pointer X coordinate in screen pixels.
 * @property pointerY Primary pointer Y coordinate in screen pixels.
 * @property scrollDeltaX Horizontal scroll delta for this single frame (cleared on subsequent frames).
 * @property scrollDeltaY Vertical scroll delta for this single frame (cleared on subsequent frames).
 * @property keysDown Physical keys held down starting at [frameIndex].
 * @property buttonsDown Pointer buttons held down starting at [frameIndex].
 * @property gamepadButtonsDown Controller buttons held down starting at [frameIndex].
 * @property gameplayCameraYaw Optional recorded gameplay camera yaw (radians) so camera-relative
 *   movement replays identically while an independent director camera orbits freely.
 * @property gameplayCameraPitch Optional recorded gameplay camera pitch (radians).
 */
data class InputTakeFrame(
    val frameIndex: Int,
    val pointerX: Float = 0f,
    val pointerY: Float = 0f,
    val scrollDeltaX: Float = 0f,
    val scrollDeltaY: Float = 0f,
    val keysDown: Set<Key> = emptySet(),
    val buttonsDown: Set<PointerButton> = emptySet(),
    val gamepadButtonsDown: Set<GamepadButton> = emptySet(),
    val gameplayCameraYaw: Float? = null,
    val gameplayCameraPitch: Float? = null,
)

/**
 * Deterministic gameplay input recording that can be replayed frame-for-frame while directing
 * alternative camera angles, lighting passes, or slow-motion speed ramps.
 *
 * @property version Format version (currently [CURRENT_VERSION]).
 * @property scenePath Project-relative path to the recorded scene.
 * @property fixedStepSeconds Fixed simulation step duration in seconds (default `1f / 60f`).
 * @property totalFrames Total number of frames captured in this take.
 * @property seed Deterministic simulation seed recorded alongside the take.
 * @property frames Sparsely stored frame states ordered by ascending [InputTakeFrame.frameIndex].
 */
data class InputTake(
    val version: Int = CURRENT_VERSION,
    val scenePath: String = "",
    val fixedStepSeconds: Float = DEFAULT_FIXED_STEP_SECONDS,
    val totalFrames: Int = 0,
    val seed: Long = 0L,
    val frames: List<InputTakeFrame> = emptyList(),
) {
    /** Total recorded playback duration in seconds. */
    val durationSeconds: Float get() = totalFrames * fixedStepSeconds

    /** Constants for [InputTake]. */
    companion object {
        /** Current [InputTake] schema version. */
        const val CURRENT_VERSION: Int = 1

        /** Default fixed simulation step (60 Hz). */
        const val DEFAULT_FIXED_STEP_SECONDS: Float = 1f / 60f
    }
}

/**
 * Captures frame-by-frame [InputSnapshot] deltas into a compact [InputTake].
 *
 * Call [recordFrame] once per fixed simulation step during live gameplay, then call [finish] to
 * obtain the immutable [InputTake].
 *
 * @param scenePath Project-relative path of the scene being recorded.
 * @param fixedStepSeconds Fixed simulation step duration in seconds.
 * @param seed Deterministic simulation seed associated with the take.
 */
class InputRecorder(
    private val scenePath: String = "",
    private val fixedStepSeconds: Float = InputTake.DEFAULT_FIXED_STEP_SECONDS,
    private val seed: Long = 0L,
) {
    private val frames = mutableListOf<InputTakeFrame>()
    private var frameCount: Int = 0
    private var lastKeys: Set<Key> = emptySet()
    private var lastButtons: Set<PointerButton> = emptySet()
    private var lastGamepad: Set<GamepadButton> = emptySet()
    private var lastPointerX: Float = Float.NaN
    private var lastPointerY: Float = Float.NaN
    private var lastCameraYaw: Float? = null
    private var lastCameraPitch: Float? = null

    /** Number of frames recorded so far. */
    val recordedFrames: Int get() = frameCount

    /**
     * Records one fixed simulation frame's [snapshot] and optional gameplay camera basis angles.
     *
     * @param snapshot Hardware input snapshot for the current frame.
     * @param gameplayCameraYaw Optional active gameplay camera yaw in radians.
     * @param gameplayCameraPitch Optional active gameplay camera pitch in radians.
     */
    fun recordFrame(
        snapshot: InputSnapshot,
        gameplayCameraYaw: Float? = null,
        gameplayCameraPitch: Float? = null,
    ) {
        val heldButtons = if (snapshot.buttonsDown.isNotEmpty()) {
            snapshot.buttonsDown
        } else {
            buildSet {
                if (snapshot.pointerDown) add(PointerButton.Primary)
                if (snapshot.secondaryPointerDown) add(PointerButton.Secondary)
            }
        }
        val hasScroll = snapshot.scrollDeltaX != 0f || snapshot.scrollDeltaY != 0f
        val changed = frameCount == 0 ||
            hasScroll ||
            snapshot.keysDown != lastKeys ||
            heldButtons != lastButtons ||
            snapshot.gamepadButtonsDown != lastGamepad ||
            snapshot.pointerX != lastPointerX ||
            snapshot.pointerY != lastPointerY ||
            gameplayCameraYaw != lastCameraYaw ||
            gameplayCameraPitch != lastCameraPitch

        if (changed) {
            frames.add(
                InputTakeFrame(
                    frameIndex = frameCount,
                    pointerX = snapshot.pointerX,
                    pointerY = snapshot.pointerY,
                    scrollDeltaX = snapshot.scrollDeltaX,
                    scrollDeltaY = snapshot.scrollDeltaY,
                    keysDown = snapshot.keysDown.toSet(),
                    buttonsDown = heldButtons.toSet(),
                    gamepadButtonsDown = snapshot.gamepadButtonsDown.toSet(),
                    gameplayCameraYaw = gameplayCameraYaw,
                    gameplayCameraPitch = gameplayCameraPitch,
                ),
            )
            lastKeys = snapshot.keysDown.toSet()
            lastButtons = heldButtons.toSet()
            lastGamepad = snapshot.gamepadButtonsDown.toSet()
            lastPointerX = snapshot.pointerX
            lastPointerY = snapshot.pointerY
            lastCameraYaw = gameplayCameraYaw
            lastCameraPitch = gameplayCameraPitch
        }
        frameCount++
    }

    /** Produces an immutable [InputTake] snapshot of every frame recorded so far. */
    fun finish(): InputTake = InputTake(
        version = InputTake.CURRENT_VERSION,
        scenePath = scenePath,
        fixedStepSeconds = fixedStepSeconds,
        totalFrames = frameCount,
        seed = seed,
        frames = frames.toList(),
    )
}

/**
 * Deterministically replays an [InputTake] frame by frame, regenerating exact press/release edges
 * (`keysPressed`, `keysReleased`, `buttonsPressed`, `buttonsReleased`, `gamepadButtonsPressed`,
 * `gamepadButtonsReleased`) identical to live [Input.updateSnapshot].
 *
 * @property take The recorded [InputTake] being played back.
 */
class InputReplayer(
    val take: InputTake,
) {
    private var cursor: Int = 0
    private var nextFrameIndex: Int = 0

    private var pointerX: Float = 0f
    private var pointerY: Float = 0f
    private var keysDown: Set<Key> = emptySet()
    private var buttonsDown: Set<PointerButton> = emptySet()
    private var gamepadDown: Set<GamepadButton> = emptySet()

    /** Most recent recorded gameplay camera yaw in radians, or `null` if not recorded. */
    var currentCameraYaw: Float? = null
        private set

    /** Most recent recorded gameplay camera pitch in radians, or `null` if not recorded. */
    var currentCameraPitch: Float? = null
        private set

    /** Zero-based index of the next frame to be returned by [stepFrame]. */
    val frameIndex: Int get() = nextFrameIndex

    /** Whether replay has reached or passed [InputTake.totalFrames]. */
    val isFinished: Boolean get() = nextFrameIndex >= take.totalFrames

    /** Rewinds the replayer back to frame 0 cleanly. */
    fun reset() {
        cursor = 0
        nextFrameIndex = 0
        pointerX = 0f
        pointerY = 0f
        keysDown = emptySet()
        buttonsDown = emptySet()
        gamepadDown = emptySet()
        currentCameraYaw = null
        currentCameraPitch = null
    }

    /**
     * Advances replay by one fixed step and returns the reconstructed [InputSnapshot].
     * Once [isFinished] is true, held keys/buttons are released cleanly and empty snapshots follow.
     *
     * @return The reconstructed frame's [InputSnapshot].
     */
    fun stepFrame(): InputSnapshot {
        val prevKeys = keysDown
        val prevButtons = buttonsDown
        val prevGamepad = gamepadDown
        val prevPrimaryDown = PointerButton.Primary in prevButtons

        var scrollX = 0f
        var scrollY = 0f

        if (nextFrameIndex < take.totalFrames) {
            while (cursor < take.frames.size && take.frames[cursor].frameIndex <= nextFrameIndex) {
                val frame = take.frames[cursor++]
                pointerX = frame.pointerX
                pointerY = frame.pointerY
                keysDown = frame.keysDown
                buttonsDown = frame.buttonsDown
                gamepadDown = frame.gamepadButtonsDown
                currentCameraYaw = frame.gameplayCameraYaw
                currentCameraPitch = frame.gameplayCameraPitch
                if (frame.frameIndex == nextFrameIndex) {
                    scrollX = frame.scrollDeltaX
                    scrollY = frame.scrollDeltaY
                }
            }
        } else {
            keysDown = emptySet()
            buttonsDown = emptySet()
            gamepadDown = emptySet()
        }

        val primaryDown = PointerButton.Primary in buttonsDown
        val secondaryDown = PointerButton.Secondary in buttonsDown
        val snapshot = InputSnapshot(
            pointerX = pointerX,
            pointerY = pointerY,
            pointerDown = primaryDown,
            scrollDeltaX = scrollX,
            scrollDeltaY = scrollY,
            keysDown = keysDown,
            keysPressed = if (keysDown.isEmpty()) emptySet() else keysDown - prevKeys,
            keysReleased = if (prevKeys.isEmpty()) emptySet() else prevKeys - keysDown,
            typedText = "",
            editActions = emptyList(),
            secondaryPointerDown = secondaryDown,
            pointerPressed = primaryDown && !prevPrimaryDown,
            pointerReleased = !primaryDown && prevPrimaryDown,
            buttonsDown = buttonsDown,
            buttonsPressed = if (buttonsDown.isEmpty()) emptySet() else buttonsDown - prevButtons,
            buttonsReleased = if (prevButtons.isEmpty()) emptySet() else prevButtons - buttonsDown,
            gamepadButtonsDown = gamepadDown,
            gamepadButtonsPressed = if (gamepadDown.isEmpty()) emptySet() else gamepadDown - prevGamepad,
            gamepadButtonsReleased = if (prevGamepad.isEmpty()) emptySet() else prevGamepad - gamepadDown,
        )
        nextFrameIndex++
        return snapshot
    }
}
