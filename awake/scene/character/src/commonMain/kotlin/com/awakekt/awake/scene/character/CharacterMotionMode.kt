/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.character

/** How [CharacterControllerSystem] moves a [CharacterController]; see [CharacterController.motionMode]. */
enum class CharacterMotionMode {
    /**
     * Walks: the intent is a direction moved at the intent's speed, relative to the camera for a
     * player; gravity, jumps, steps, slopes and ground snapping apply, and the body turns toward
     * where it walks. The default.
     */
    Grounded,

    /**
     * Moves freely, as a swimmer, a flyer or a spectator does: the intent's `moveX`, `moveY` and
     * `moveZ` are a world velocity in units per second, used as given. No gravity, no ground
     * snapping, no steps or slope limit, and the body is never turned: the caller owns yaw and
     * pitch, and the capsule stays upright. The same capsule still collides with walls, floors,
     * ceilings and terrain, and [CharacterController.lastContact] says what it met.
     *
     * A game that computes the velocity each step, such as a flight model, plans its system with
     * `SceneSystemPlan.fixed(name, before = SceneSystemPlan.CHARACTER_CONTROLLER)` so the step it writes
     * is the step that moves the character. For a player's character it switches the
     * `MovementControl.driver` to `MovementDriver.Agent` while it drives, so player input doesn't
     * overwrite the velocity.
     */
    Free,
}
