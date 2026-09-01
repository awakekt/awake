/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.physics

/** Whether a pair of bodies started touching or stopped. */
enum class ContactPhase {
    /** They were apart and now touch. */
    BEGAN,

    /** They touched and now do not -- including because one of them was destroyed. */
    ENDED,
}

/**
 * Two bodies started or stopped touching.
 *
 * [a] and [b] are unordered: which body is which is Jolt's internal sort order, not the order they
 * were created in. A trigger that cares about only one of the pair has to check both.
 *
 * There is deliberately no contact point, normal or impulse. [ContactPhase.ENDED] is reported from
 * a callback that carries none of them, so they would be present half the time and absent the other
 * half -- and the verbs this exists for (pickups, checkpoints, damage volumes) need the pair and
 * nothing else. A hit reaction that wants an impact point can add them.
 */
data class ContactEvent(val a: BodyHandle, val b: BodyHandle, val phase: ContactPhase)
