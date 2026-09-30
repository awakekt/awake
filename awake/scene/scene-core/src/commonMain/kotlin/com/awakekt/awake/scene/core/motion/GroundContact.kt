/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.core.motion

/**
 * Whether an entity stands on something, kept by whatever moves it (a character controller) for
 * whatever reacts to it, such as an animation that plays a jump while it is off the ground.
 */
class GroundContact(var grounded: Boolean = true)
