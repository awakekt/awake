/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ecs

/**
 * A [System] that can show a partly-elapsed step, for one that simulates on a fixed timestep.
 *
 * A fixed-step simulation advances in whole steps and a display refreshes whenever it likes, so the
 * two do not line up: at 60 Hz simulation and 144 Hz display, most frames draw a pose that has not
 * changed since the last one and every third or so jumps. That reads as stutter even though the
 * simulation is perfectly smooth — the frames are fine, they are just showing the same instant
 * twice.
 *
 * [interpolate] is called once per rendered frame, after the fixed steps for that frame have run,
 * with how far the clock has carried past the last one. A system that implements it blends the two
 * most recent states it holds; a system that does not is simply drawn at whatever its last step
 * wrote, which is the behaviour everything had before this existed.
 */
interface InterpolatedSystem : System {
    /**
     * Blends this system's last two simulated states into whatever the renderer reads.
     *
     * [alpha] runs `[0, 1)`: `0` is the state the last fixed step produced, and approaching `1` is
     * approaching the state the next step will produce. **So this draws one step behind**, which is
     * the trade the pattern makes — a frame of latency in exchange for never showing an instant
     * twice. Extrapolating forward instead avoids the latency and is worse: it invents poses the
     * simulation never had, and has to visibly correct them on the frame a body hits something.
     */
    fun interpolate(world: World, alpha: Float)
}
