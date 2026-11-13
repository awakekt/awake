/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime

/**
 * One system a scene run planned: the name it is known by and the phase it runs in. The project
 * runtime and the declarative scene app both list their systems as these, in the order they run
 * (fixed, then pre-animation, then frame), so a diagnostic or a test reads either the same way.
 *
 * @property name The system's name, such as a Core constant or the name a game gave it.
 * @property phase The [SceneSystemPhase] it runs in.
 */
class PlannedSystem(val name: String, val phase: SceneSystemPhase) {
    override fun equals(other: Any?): Boolean = other is PlannedSystem && other.name == name && other.phase == phase

    override fun hashCode(): Int = 31 * name.hashCode() + phase.hashCode()

    override fun toString(): String = "$name ($phase)"
}

/**
 * Refuses a request to disable a system no one knows by that name: a typo would otherwise leave the
 * system running with no sign. The message lists every name in [known], so the right one is at hand.
 * Both the project runtime and the declarative scene app call this, so they refuse alike.
 *
 * @param disabled The names the host or consumer asked to disable.
 * @param known Every name a run could plan.
 * @param owner What was asked, for the message, such as "the scene app".
 */
fun requireKnownSystemNames(disabled: Set<String>, known: Collection<String>, owner: String) {
    val unknown = disabled.filterNot { it in known }
    require(unknown.isEmpty()) {
        "Cannot disable ${unknown.joinToString { "'$it'" }} in $owner: no system has that name. " +
            "Known systems: ${known.distinct().sorted().joinToString()}"
    }
}
