/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.scene.runtime

// Behaviour-parameter checks, kept beside each other rather than inside SceneValidator: they are
// about what makes a behaviour work, not about what makes a scene graph well-formed.

internal fun validatePatrol(
    component: ScenePatrol,
    path: String,
    issues: MutableList<SceneValidationIssue>,
) {
    validateRoute(component.speed, component.repathInterval, "patrol", path, issues)
    if (component.dwellSeconds < 0f) {
        issues += SceneValidationIssue(path, "patrol.dwellSeconds must not be negative")
    }
}

internal fun validateFlee(
    component: SceneFlee,
    path: String,
    issues: MutableList<SceneValidationIssue>,
) {
    validateRoute(component.speed, component.repathInterval, "flee", path, issues)
    // Equal radii is the twitch case the two-radius design exists to prevent, not a rounding
    // detail: an entity sitting exactly at the threshold starts and stops every frame.
    if (component.safeRadius <= component.panicRadius) {
        issues += SceneValidationIssue(path, "flee.safeRadius must be greater than flee.panicRadius")
    }
    if (component.fleeDistance <= 0f) {
        issues += SceneValidationIssue(path, "flee.fleeDistance must be > 0")
    }
}

/** What every route-following behaviour shares, so one bad value reads the same way in all three. */
internal fun validateRoute(
    speed: Float,
    repathInterval: Float,
    component: String,
    path: String,
    issues: MutableList<SceneValidationIssue>,
) {
    if (speed <= 0f) {
        issues += SceneValidationIssue(path, "$component.speed must be > 0")
    }
    if (repathInterval <= 0f) {
        issues += SceneValidationIssue(path, "$component.repathInterval must be > 0")
    }
}
