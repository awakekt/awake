/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.navigation

import io.github.awakelab.awake.core.math.Vec3f

/**
 * A coarse-grained facade over whatever backs navigation on a given platform -- same principle
 * as D5's physics facade in docs/mvp-plan.md (a small, hand-designed contract, not a 1:1 mirror
 * of a backend's own API), so swapping backends stays contained to one implementation.
 *
 * Implemented by `NavGrid` and `StreamedNavGrid` in `:awake:scene:navigation`. The signature stays
 * synchronous and allocating on purpose: [PathRequestSystem] is what moves a search off the frame
 * thread, so the asynchrony lives in one place rather than in every implementation of this. An
 * implementation used with a search scope is therefore called from another thread and must
 * tolerate that.
 */
interface NavMesh {
    /** Returns waypoints from [start] to [end], or an empty list if no path exists. */
    fun findPath(start: Vec3f, end: Vec3f): List<Vec3f>
}
