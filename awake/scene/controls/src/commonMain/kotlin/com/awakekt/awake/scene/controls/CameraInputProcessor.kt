/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.controls

import com.awakekt.awake.core.input.InputSnapshot
import com.awakekt.awake.core.input.Key
import com.awakekt.awake.core.math.CameraMathUtils
import com.awakekt.awake.core.math.CameraPoseState

/**
 * Stateful processor bridging [InputSnapshot] events into [CameraPoseState] updates.
 *
 * Supports LMB orbit, RMB drag zoom, MMB / Shift+LMB screen pan, scroll wheel, WASD movement,
 * and PageUp/PageDown keys with zero boilerplate for callers.
 */
class CameraInputProcessor {
    private var lastPointerX = 0f
    private var lastPointerY = 0f
    private var wasPrimaryDragging = false
    private var wasSecondaryDragging = false

    /**
     * Processes [input] and updates [poseState] according to [config].
     *
     * @return true if camera coordinates were modified this frame.
     */
    fun process(
        poseState: CameraPoseState,
        config: CameraControlConfig,
        input: InputSnapshot,
        delta: Float,
    ): Boolean {
        var changed = false

        val isPrimary = input.pointerDown
        val isSecondary = input.secondaryPointerDown
        val isShift = Key.Shift in input.keysDown

        val hasPriorDrag = wasPrimaryDragging || wasSecondaryDragging
        val dx = if (hasPriorDrag) input.pointerX - lastPointerX else 0f
        val dy = if (hasPriorDrag) input.pointerY - lastPointerY else 0f

        lastPointerX = input.pointerX
        lastPointerY = input.pointerY
        wasPrimaryDragging = isPrimary
        wasSecondaryDragging = isSecondary

        // 1. Shift + Primary Drag -> Screen Space Pan
        if (config.allowPan && isPrimary && isShift && (dx != 0f || dy != 0f)) {
            if (CameraMathUtils.applyScreenSpacePan(poseState, dx, dy, config.panScale)) {
                changed = true
            }
        }
        // 2. Primary Drag -> Orbit Rotation
        else if (config.allowOrbit && isPrimary && (dx != 0f || dy != 0f)) {
            val yawSign = if (config.invertYaw) -1f else 1f
            val pitchSign = if (config.invertPitch) -1f else 1f
            if (CameraMathUtils.applyOrbitDrag(poseState, dx * yawSign, dy * pitchSign, config.orbitSensitivity)) {
                changed = true
            }
        }

        // 3. Zoom Modalities:
        if (config.allowZoom) {
            // a) Mouse Scroll Wheel
            val scrollDelta = if (config.invertScroll) -input.scrollDeltaY else input.scrollDeltaY
            if (scrollDelta != 0f) {
                if (CameraMathUtils.applyZoom(poseState, scrollDelta, config.zoomRate, config.minDistance, config.maxDistance)) {
                    changed = true
                }
            }

            // b) Secondary Pointer Drag (RMB Drag up/down)
            if (isSecondary && dy != 0f) {
                val rmbDelta = -dy * 0.02f
                if (CameraMathUtils.applyZoom(poseState, rmbDelta, config.zoomRate, config.minDistance, config.maxDistance)) {
                    changed = true
                }
            }

            // c) Keyboard PageUp / PageDown
            if (Key.PageUp in input.keysDown) {
                if (CameraMathUtils.applyZoom(poseState, 1.5f * delta * 60f, rate = 0.02f, config.minDistance, config.maxDistance)) {
                    changed = true
                }
            }
            if (Key.PageDown in input.keysDown) {
                if (CameraMathUtils.applyZoom(poseState, -1.5f * delta * 60f, rate = 0.02f, config.minDistance, config.maxDistance)) {
                    changed = true
                }
            }
        }

        // 4. Keyboard WASD / QE / Space Gliding
        if (config.allowKeyboardFlight) {
            val keys = input.keysDown
            var moveX = 0f
            var moveZ = 0f
            var moveY = 0f

            if (Key.W in keys) moveZ += 1f
            if (Key.S in keys) moveZ -= 1f
            if (Key.A in keys) moveX -= 1f
            if (Key.D in keys) moveX += 1f
            if (Key.Q in keys) moveY -= 1f
            if (Key.E in keys || Key.Space in keys) moveY += 1f

            if (moveX != 0f || moveY != 0f || moveZ != 0f) {
                val speed = if (isShift) config.baseMoveSpeed * config.boostMultiplier else config.baseMoveSpeed
                if (CameraMathUtils.applyHorizontalPan(poseState, moveX, moveY, moveZ, speed * delta)) {
                    changed = true
                }
            }
        }

        return changed
    }

    /** Resets transient drag tracking on viewport loss-of-focus or map change. */
    fun reset() {
        wasPrimaryDragging = false
        wasSecondaryDragging = false
    }
}
